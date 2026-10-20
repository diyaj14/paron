"""Train a calibrated logistic-regression risk model for Paron Guard.

Usage:
    python train.py --data ./data/features.csv --out-dir ./artifacts --seed 42

Outputs (in --out-dir):
    model.joblib              — calibrated sklearn pipeline
    metrics.json              — test-set metrics at chosen threshold
    rules_baseline.json       — rules-only baseline for comparison
    manifest.json             — model metadata + approval metadata
"""

import argparse
import json
import math
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.calibration import CalibratedClassifierCV
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    average_precision_score,
    confusion_matrix,
    f1_score,
    precision_score,
    recall_score,
    roc_auc_score,
)
from sklearn.preprocessing import StandardScaler
from sklearn.pipeline import Pipeline
from joblib import dump

FEATURE_NAMES = [
    "amount",
    "amount_to_token_limit_ratio",
    "merchant_amount_deviation",
    "user_tx_count_5m",
    "user_tx_count_1h",
    "user_tx_count_24h",
    "device_tx_count_5m",
    "device_tx_count_1h",
    "device_tx_count_24h",
    "token_tx_count_24h",
    "user_tx_value_1h",
    "device_tx_value_24h",
    "token_age_seconds",
    "time_to_expiry_seconds",
    "offline_duration_seconds",
    "token_reuse_count",
    "duplicate_payload_hash_count",
    "previous_settlement_failed",
    "merchant_risk_aggregate",
    "hour_of_day",
    "day_of_week",
    "history_available",
    "token_age_known",
    "expiry_known",
]

MODEL_VERSION = "lr-calibrated-v2.0.0-scenario"
FEATURE_SCHEMA_VERSION = "v1"
THRESHOLD_POLICY_VERSION = "thresholds-v1"


def load_and_split(csv_path: str):
    """Make a chronological, group-safe split.

    Every offline-payment sequence belongs to exactly one split.  This avoids
    learning from an earlier event in a sequence and evaluating on a later
    event from that same sequence.  We train only rows which passed mandatory
    security rules: the model must never learn to replace deterministic gates.
    """
    df = pd.read_csv(csv_path)
    required = set(FEATURE_NAMES + ["label", "event_time", "sequence_id", "ml_eligible"])
    missing = required - set(df.columns)
    if missing:
        raise ValueError(f"Scenario dataset missing required columns: {sorted(missing)}")

    eligible = df[df["ml_eligible"] == 1].copy()
    if eligible.empty:
        raise ValueError("No ML-eligible records. Mandatory-rule rejects must not enter model training.")

    # Each sequence has a monotonic event time.  Split by the first event so a
    # sequence can never straddle train/validation/test.
    sequence_order = (
        eligible.groupby("sequence_id", as_index=False)["event_time"]
        .min()
        .sort_values(["event_time", "sequence_id"])
        .reset_index(drop=True)
    )
    n_sequences = len(sequence_order)
    if n_sequences < 10:
        raise ValueError("Need at least 10 sequences for chronological train/validation/test splitting.")
    train_end = max(1, int(n_sequences * 0.60))
    val_end = max(train_end + 1, int(n_sequences * 0.80))
    val_end = min(val_end, n_sequences - 1)
    train_ids = set(sequence_order.iloc[:train_end]["sequence_id"])
    val_ids = set(sequence_order.iloc[train_end:val_end]["sequence_id"])
    test_ids = set(sequence_order.iloc[val_end:]["sequence_id"])

    def arrays(ids):
        part = eligible[eligible["sequence_id"].isin(ids)].sort_values("event_time")
        return part[FEATURE_NAMES].values, part["label"].values

    X_train, y_train = arrays(train_ids)
    X_val, y_val = arrays(val_ids)
    X_test, y_test = arrays(test_ids)
    for name, labels in (("train", y_train), ("validation", y_val), ("test", y_test)):
        if len(np.unique(labels)) < 2:
            raise ValueError(f"{name} split has one class; regenerate a larger/more diverse scenario dataset.")
    split_info = {
        "strategy": "chronological_sequence_group_split",
        "total_records": int(len(df)),
        "ml_eligible_records": int(len(eligible)),
        "mandatory_rule_records_excluded": int(len(df) - len(eligible)),
        "sequence_counts": {"train": len(train_ids), "validation": len(val_ids), "test": len(test_ids)},
    }
    return X_train, X_val, X_test, y_train, y_val, y_test, split_info


def train_model(X_train, y_train, seed: int):
    base = Pipeline([
        ("scaler", StandardScaler()),
        ("clf", LogisticRegression(
            class_weight="balanced",
            max_iter=2000,
            solver="lbfgs",
            random_state=seed,
        )),
    ])
    calibrated = CalibratedClassifierCV(base, cv=3, method="isotonic")
    calibrated.fit(X_train, y_train)
    return calibrated


def find_best_threshold(y_true, y_prob):
    best_f1 = 0
    best_t = 0.5
    for t in np.arange(0.05, 0.95, 0.01):
        preds = (y_prob >= t).astype(int)
        f1 = f1_score(y_true, preds, zero_division=0)
        if f1 > best_f1:
            best_f1 = f1
            best_t = round(float(t), 2)
    return best_t


def evaluate(y_true, y_prob, threshold):
    preds = (y_prob >= threshold).astype(int)
    tn, fp, fn, tp = confusion_matrix(y_true, preds).ravel()
    fp_cost_per = 500.0
    return {
        "threshold": threshold,
        "precision": round(float(precision_score(y_true, preds, zero_division=0)), 4),
        "recall": round(float(recall_score(y_true, preds, zero_division=0)), 4),
        "f1": round(float(f1_score(y_true, preds, zero_division=0)), 4),
        "pr_auc": round(float(average_precision_score(y_true, y_prob)), 4),
        "roc_auc": round(float(roc_auc_score(y_true, y_prob)), 4),
        "confusion_matrix": {"tn": int(tn), "fp": int(fp), "fn": int(fn), "tp": int(tp)},
        "false_positive_rupee_cost": round(float(fp * fp_cost_per), 2),
        "sample_count": int(len(y_true)),
        "fraud_prevalence": round(float(y_true.mean()), 4),
        "cost_per_false_positive_inr": fp_cost_per,
    }


def rules_only_baseline(X_test, y_test):
    token_reuse_col = FEATURE_NAMES.index("token_reuse_count")
    dup_hash_col = FEATURE_NAMES.index("duplicate_payload_hash_count")
    velocity_5m_col = FEATURE_NAMES.index("user_tx_count_5m")
    amount_ratio_col = FEATURE_NAMES.index("amount_to_token_limit_ratio")
    merchant_dev_col = FEATURE_NAMES.index("merchant_amount_deviation")
    offline_col = FEATURE_NAMES.index("offline_duration_seconds")

    scores = np.zeros(len(y_test))
    for i in range(len(y_test)):
        s = 0.0
        if X_test[i, token_reuse_col] > 0:
            s += 1.0
        if X_test[i, dup_hash_col] > 0:
            s += 1.0
        if X_test[i, velocity_5m_col] > 5:
            s += 1.0
        if X_test[i, amount_ratio_col] > 0.7:
            s += 1.0
        if X_test[i, merchant_dev_col] > 2.0:
            s += 0.5
        if X_test[i, offline_col] > 7200:
            s += 0.5
        scores[i] = min(s, 1.0)

    threshold = 0.7
    preds = (scores >= threshold).astype(int)
    tn, fp, fn, tp = confusion_matrix(y_test, preds).ravel()
    fp_cost_per = 500.0
    return {
        "threshold": threshold,
        "precision": round(float(precision_score(y_test, preds, zero_division=0)), 4),
        "recall": round(float(recall_score(y_test, preds, zero_division=0)), 4),
        "f1": round(float(f1_score(y_test, preds, zero_division=0)), 4),
        "pr_auc": round(float(average_precision_score(y_test, scores)), 4),
        "confusion_matrix": {"tn": int(tn), "fp": int(fp), "fn": int(fn), "tp": int(tp)},
        "false_positive_rupee_cost": round(float(fp * fp_cost_per), 2),
        "sample_count": int(len(y_test)),
    }


def main():
    parser = argparse.ArgumentParser(description="Train Paron Guard risk model")
    parser.add_argument("--data", type=str, default="./data/features.csv")
    parser.add_argument("--out-dir", type=str, default="./artifacts")
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    X_train, X_val, X_test, y_train, y_val, y_test, split_info = load_and_split(args.data)

    model = train_model(X_train, y_train, args.seed)

    y_val_prob = model.predict_proba(X_val)[:, 1]
    threshold = find_best_threshold(y_val, y_val_prob)

    y_test_prob = model.predict_proba(X_test)[:, 1]
    test_metrics = evaluate(y_test, y_test_prob, threshold)
    baseline = rules_only_baseline(X_test, y_test)

    dump(model, out_dir / "model.joblib")

    with open(out_dir / "metrics.json", "w") as f:
        json.dump(test_metrics, f, indent=2)

    with open(out_dir / "rules_baseline.json", "w") as f:
        json.dump(baseline, f, indent=2)

    manifest = {
        "model_version": MODEL_VERSION,
        "feature_schema_version": FEATURE_SCHEMA_VERSION,
        "threshold_policy_version": THRESHOLD_POLICY_VERSION,
        "seed": args.seed,
        "total_records": split_info["total_records"],
        "ml_eligible_records": split_info["ml_eligible_records"],
        "mandatory_rule_records_excluded": split_info["mandatory_rule_records_excluded"],
        "split": split_info,
        "train_count": int(len(y_train)),
        "val_count": int(len(y_val)),
        "test_count": int(len(y_test)),
        "threshold": threshold,
        "test_pr_auc": test_metrics["pr_auc"],
        "baseline_pr_auc": baseline["pr_auc"],
        "improvement_over_baseline": round(test_metrics["pr_auc"] - baseline["pr_auc"], 4),
    }
    with open(out_dir / "manifest.json", "w") as f:
        json.dump(manifest, f, indent=2)

    print(f"Model: {MODEL_VERSION} (chronological, sequence-safe split)")
    print(f"Threshold: {threshold}")
    print(f"AI   — PR-AUC: {test_metrics['pr_auc']}, F1: {test_metrics['f1']}, FP cost: INR {test_metrics['false_positive_rupee_cost']}")
    print(f"Rules — PR-AUC: {baseline['pr_auc']}, F1: {baseline['f1']}, FP cost: INR {baseline['false_positive_rupee_cost']}")
    print(f"Improvement: +{manifest['improvement_over_baseline']} PR-AUC")
    print(f"Artifacts saved to: {out_dir}")


if __name__ == "__main__":
    main()
