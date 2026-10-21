"""Scenario-based synthetic settlement dataset for Paron Guard.

This is a payment-event simulator, not a generator of independent random rows.
Each sequence has an explicit expected policy outcome. Labels describe simulated
ground truth only; they must never be presented as real-world fraud evidence.
"""
import argparse
import json
from collections import Counter
from pathlib import Path

import numpy as np
import pandas as pd

FEATURE_NAMES = [
    "amount", "amount_to_token_limit_ratio", "merchant_amount_deviation",
    "user_tx_count_5m", "user_tx_count_1h", "user_tx_count_24h",
    "device_tx_count_5m", "device_tx_count_1h", "device_tx_count_24h",
    "token_tx_count_24h", "user_tx_value_1h", "device_tx_value_24h",
    "token_age_seconds", "time_to_expiry_seconds", "offline_duration_seconds",
    "token_reuse_count", "duplicate_payload_hash_count", "previous_settlement_failed",
    "merchant_risk_aggregate", "hour_of_day", "day_of_week",
    "history_available", "token_age_known", "expiry_known",
]
GENERATOR_VERSION = "2.0.0-scenario-sequences"
TOKEN_LIMIT = 5000.0

SCENARIOS = {
    "student_canteen_day": {"label": 0, "rule": "PASS", "policy": "APPROVE", "why": "Several small, routine payments."},
    "offline_commute": {"label": 0, "rule": "PASS", "policy": "APPROVE", "why": "Valid payments synchronize after a normal long offline period."},
    "large_legitimate_purchase": {"label": 0, "rule": "PASS", "policy": "HOLD_FOR_REVIEW", "why": "Unusual but valid high-value purchase."},
    "new_device_low_signal": {"label": 0, "rule": "PASS", "policy": "HOLD_FOR_REVIEW", "why": "Legitimate payment with insufficient history."},
    "campus_event_legitimate": {"label": 0, "rule": "PASS", "policy": "HOLD_FOR_REVIEW", "why": "A legitimate event creates high but explainable payment velocity."},
    "below_threshold_velocity": {"label": 1, "rule": "PASS", "policy": "HOLD_FOR_REVIEW", "why": "Attacker stays below fixed rule thresholds but sequence is suspicious."},
    "cross_merchant_token_conflict": {"label": 1, "rule": "HARD_REJECT", "policy": "REJECT", "why": "Different claims reuse an offline token."},
    "tampered_receipt": {"label": 1, "rule": "HARD_REJECT", "policy": "REJECT", "why": "Signature/payload integrity failure represented for rule testing only."},
}


def base_row(rng, event_time, sequence_id, scenario):
    amount = float(np.clip(rng.lognormal(5.2, .45), 35, 900))
    count_5m = int(rng.poisson(.8))
    count_1h = max(count_5m, int(rng.poisson(2.5)))
    count_24h = max(count_1h, int(rng.poisson(8)))
    return {
        "event_time": int(event_time), "sequence_id": sequence_id, "scenario_type": scenario,
        "amount": amount, "amount_to_token_limit_ratio": amount / TOKEN_LIMIT,
        "merchant_amount_deviation": float(np.clip(rng.gamma(1.2, .35), 0, 1.8)),
        "user_tx_count_5m": count_5m, "user_tx_count_1h": count_1h, "user_tx_count_24h": count_24h,
        "device_tx_count_5m": max(0, count_5m - int(rng.integers(0, 2))),
        "device_tx_count_1h": max(0, count_1h - int(rng.integers(0, 2))),
        "device_tx_count_24h": max(0, count_24h - int(rng.integers(0, 3))),
        "token_tx_count_24h": int(rng.integers(1, 4)),
        "user_tx_value_1h": float(amount * max(1, count_1h)), "device_tx_value_24h": float(amount * max(1, count_24h)),
        "token_age_seconds": float(rng.uniform(300, 15000)), "time_to_expiry_seconds": float(rng.uniform(3000, 18000)),
        "offline_duration_seconds": float(np.clip(rng.exponential(500), 30, 3600)),
        "token_reuse_count": 0, "duplicate_payload_hash_count": 0, "previous_settlement_failed": 0,
        "merchant_risk_aggregate": float(rng.beta(2, 18)), "hour_of_day": int((event_time // 3600) % 24),
        "day_of_week": int((event_time // 86400) % 7), "history_available": 1, "token_age_known": 1, "expiry_known": 1,
    }


def apply_scenario(row, scenario, position, size, rng):
    if scenario == "student_canteen_day":
        row["amount"] = float(rng.uniform(40, 220)); row["amount_to_token_limit_ratio"] = row["amount"] / TOKEN_LIMIT
        row["user_tx_count_5m"] = min(position, 3); row["user_tx_count_1h"] = position + 1
    elif scenario == "offline_commute":
        row["offline_duration_seconds"] = float(rng.uniform(5400, 9000)); row["amount"] = float(rng.uniform(80, 500)); row["amount_to_token_limit_ratio"] = row["amount"] / TOKEN_LIMIT
    elif scenario == "large_legitimate_purchase":
        row["amount"] = float(rng.uniform(2600, 3250)); row["amount_to_token_limit_ratio"] = row["amount"] / TOKEN_LIMIT
        row["merchant_amount_deviation"] = float(rng.uniform(1.1, 1.8)); row["offline_duration_seconds"] = float(rng.uniform(300, 1800))
    elif scenario == "new_device_low_signal":
        row["history_available"] = 0; row["merchant_amount_deviation"] = float(rng.uniform(0, 1.2)); row["amount"] = float(rng.uniform(120, 1400)); row["amount_to_token_limit_ratio"] = row["amount"] / TOKEN_LIMIT
    elif scenario == "campus_event_legitimate":
        # Deliberately overlaps the cautious-fraud pattern: unusual does not
        # automatically mean fraudulent, so this should generally be held.
        row["amount"] = float(rng.uniform(350, 1350)); row["amount_to_token_limit_ratio"] = row["amount"] / TOKEN_LIMIT
        row["user_tx_count_5m"] = int(rng.integers(2, 5)); row["device_tx_count_5m"] = row["user_tx_count_5m"]
        row["user_tx_count_1h"] = int(rng.integers(6, 14)); row["device_tx_count_1h"] = row["user_tx_count_1h"]
        row["offline_duration_seconds"] = float(rng.uniform(2400, 6800)); row["merchant_risk_aggregate"] = float(rng.uniform(.08, .42))
    elif scenario == "below_threshold_velocity":
        row["amount"] = float(rng.uniform(300, 1400)); row["amount_to_token_limit_ratio"] = row["amount"] / TOKEN_LIMIT
        row["user_tx_count_5m"] = int(rng.integers(2, 5)); row["device_tx_count_5m"] = row["user_tx_count_5m"]
        row["user_tx_count_1h"] = int(rng.integers(7, 15)) + position; row["device_tx_count_1h"] = max(5, row["user_tx_count_1h"] - int(rng.integers(0, 3)))
        row["offline_duration_seconds"] = float(rng.uniform(2400, 7600)); row["merchant_risk_aggregate"] = float(rng.uniform(.10, .55))
    elif scenario == "cross_merchant_token_conflict":
        row["token_reuse_count"] = 1 + position; row["token_tx_count_24h"] = 2 + position; row["amount"] = float(rng.uniform(300, 900)); row["amount_to_token_limit_ratio"] = row["amount"] / TOKEN_LIMIT
    elif scenario == "tampered_receipt":
        row["duplicate_payload_hash_count"] = 1; row["amount"] = float(rng.uniform(900, 1800)); row["amount_to_token_limit_ratio"] = row["amount"] / TOKEN_LIMIT
    row["label"] = SCENARIOS[scenario]["label"]; row["expected_rule_outcome"] = SCENARIOS[scenario]["rule"]
    row["expected_policy_outcome"] = SCENARIOS[scenario]["policy"]; row["scenario_rationale"] = SCENARIOS[scenario]["why"]
    row["ml_eligible"] = int(row["expected_rule_outcome"] == "PASS")
    return row


def generate(n, rng):
    rows, event_time, seq = [], 0, 0
    scenario_names = list(SCENARIOS)
    weights = np.array([.24, .16, .11, .11, .12, .14, .07, .05]); weights /= weights.sum()
    while len(rows) < n:
        scenario = rng.choice(scenario_names, p=weights); size = int(rng.integers(2, 7)); sequence_id = f"seq-{seq:06d}"; seq += 1
        for position in range(size):
            if len(rows) >= n: break
            event_time += int(rng.integers(20, 900)); row = base_row(rng, event_time, sequence_id, scenario)
            rows.append(apply_scenario(row, scenario, position, size, rng))
    return pd.DataFrame(rows).sort_values("event_time").reset_index(drop=True)


def main():
    parser = argparse.ArgumentParser(); parser.add_argument("--seed", type=int, default=42); parser.add_argument("--n", type=int, default=10000); parser.add_argument("--out-dir", default="./data"); args = parser.parse_args()
    df = generate(args.n, np.random.default_rng(args.seed)); out = Path(args.out_dir); out.mkdir(parents=True, exist_ok=True); df.to_csv(out / "features.csv", index=False)
    counts = Counter(df["scenario_type"])
    manifest = {"generator_version": GENERATOR_VERSION, "seed": args.seed, "total_records": len(df), "feature_schema_version": "v1", "feature_names": FEATURE_NAMES, "scenario_definitions": SCENARIOS, "scenario_counts": dict(counts), "ml_eligible_count": int(df.ml_eligible.sum()), "limitation": "Simulated scenario labels only; not real-world fraud performance."}
    (out / "manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(f"Generated {len(df)} chronological events across {df.sequence_id.nunique()} sequences; {manifest['ml_eligible_count']} ML-eligible.")


if __name__ == "__main__": main()
