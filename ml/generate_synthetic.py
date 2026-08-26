"""Synthetic offline-payment fraud dataset generator for Paron Guard.

Produces a CSV matching the v1 feature schema with labels:
  0 = legitimate
  1 = fraud

Usage:
    python generate_synthetic.py --seed 42 --n 10000 --out-dir ./data

Outputs:
    features.csv      — (N rows × 25 cols: 24 features + label)
    manifest.json     — seed, counts, fraud-rate, generator version
"""

import argparse
import json
import math
import os
from pathlib import Path

import numpy as np
import pandas as pd

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

TOKEN_LIMIT = 5000.0
TOKEN_VALIDITY_SECONDS = 21600
GENERATOR_VERSION = "1.0.0"


def generate_base_legitimate(n: int, rng: np.random.Generator) -> pd.DataFrame:
    amounts = rng.lognormal(mean=6.0, sigma=1.0, size=n).clip(10, TOKEN_LIMIT)
    ratio = (amounts / TOKEN_LIMIT).clip(0.0, 1.0)
    merchant_dev = rng.exponential(0.3, size=n).clip(0.0, 5.0)
    user_5m = rng.poisson(0.8, size=n).clip(0, 20)
    user_1h = rng.poisson(2.5, size=n).clip(0, 50)
    user_24h = rng.poisson(8.0, size=n).clip(0, 200)
    dev_5m = rng.poisson(0.5, size=n).clip(0, 15)
    dev_1h = rng.poisson(1.8, size=n).clip(0, 40)
    dev_24h = rng.poisson(6.0, size=n).clip(0, 150)
    tok_count_24h = rng.poisson(1.0, size=n).clip(0, 10)
    user_val_1h = (user_1h * rng.lognormal(5.5, 0.8, size=n)).clip(0, 500000)
    dev_val_24h = (dev_24h * rng.lognormal(5.5, 0.8, size=n)).clip(0, 1000000)
    token_age = rng.uniform(60, TOKEN_VALIDITY_SECONDS * 0.8, size=n)
    tte = rng.uniform(3600, TOKEN_VALIDITY_SECONDS * 0.9, size=n)
    offline_dur = rng.exponential(300, size=n).clip(0, 7200)
    token_reuse = rng.poisson(0.05, size=n).clip(0, 2)
    dup_hash = rng.poisson(0.02, size=n).clip(0, 2)
    prev_fail = rng.choice([0, 1], size=n, p=[0.95, 0.05]).astype(int)
    merchant_risk = rng.beta(2, 20, size=n).clip(0.0, 1.0)
    hour = rng.choice(24, size=n)
    dow = rng.choice(7, size=n)
    history = rng.choice([0, 1], size=n, p=[0.1, 0.9]).astype(int)
    age_known = rng.choice([0, 1], size=n, p=[0.05, 0.95]).astype(int)
    expiry_known = rng.choice([0, 1], size=n, p=[0.05, 0.95]).astype(int)

    return pd.DataFrame({
        "amount": amounts,
        "amount_to_token_limit_ratio": ratio,
        "merchant_amount_deviation": merchant_dev,
        "user_tx_count_5m": user_5m,
        "user_tx_count_1h": user_1h,
        "user_tx_count_24h": user_24h,
        "device_tx_count_5m": dev_5m,
        "device_tx_count_1h": dev_1h,
        "device_tx_count_24h": dev_24h,
        "token_tx_count_24h": tok_count_24h,
        "user_tx_value_1h": user_val_1h,
        "device_tx_value_24h": dev_val_24h,
        "token_age_seconds": token_age,
        "time_to_expiry_seconds": tte,
        "offline_duration_seconds": offline_dur,
        "token_reuse_count": token_reuse,
        "duplicate_payload_hash_count": dup_hash,
        "previous_settlement_failed": prev_fail,
        "merchant_risk_aggregate": merchant_risk,
        "hour_of_day": hour,
        "day_of_week": dow,
        "history_available": history,
        "token_age_known": age_known,
        "expiry_known": expiry_known,
    })


def inject_fraud_token_reuse(df: pd.DataFrame, rng: np.random.Generator) -> pd.DataFrame:
    mask = rng.random(len(df)) < 0.12
    n = mask.sum()
    df.loc[mask, "token_reuse_count"] = rng.poisson(10, size=n).clip(5, 30)
    df.loc[mask, "token_tx_count_24h"] = df.loc[mask, "token_reuse_count"] + rng.integers(3, 10)
    df.loc[mask, "duplicate_payload_hash_count"] = rng.poisson(5, size=n).clip(2, 15)
    df.loc[mask, "previous_settlement_failed"] = rng.choice([0, 1], size=n, p=[0.4, 0.6])
    return df


def inject_fraud_velocity(df: pd.DataFrame, rng: np.random.Generator) -> pd.DataFrame:
    mask = rng.random(len(df)) < 0.12
    n = mask.sum()
    df.loc[mask, "user_tx_count_5m"] = rng.poisson(25, size=n).clip(15, 80)
    df.loc[mask, "user_tx_count_1h"] = rng.poisson(80, size=n).clip(40, 200)
    df.loc[mask, "user_tx_count_24h"] = rng.poisson(150, size=n).clip(80, 400)
    df.loc[mask, "device_tx_count_5m"] = rng.poisson(20, size=n).clip(10, 60)
    df.loc[mask, "device_tx_count_1h"] = rng.poisson(50, size=n).clip(25, 150)
    df.loc[mask, "device_tx_count_24h"] = rng.poisson(100, size=n).clip(50, 300)
    df.loc[mask, "user_tx_value_1h"] = rng.uniform(80000, 300000, size=n)
    df.loc[mask, "device_tx_value_24h"] = rng.uniform(150000, 600000, size=n)
    return df


def inject_fraud_amount(df: pd.DataFrame, rng: np.random.Generator) -> pd.DataFrame:
    mask = rng.random(len(df)) < 0.10
    n = mask.sum()
    df.loc[mask, "amount"] = rng.uniform(4200, 5000, size=n)
    df.loc[mask, "amount_to_token_limit_ratio"] = rng.uniform(0.85, 1.0, size=n)
    df.loc[mask, "merchant_amount_deviation"] = rng.uniform(8.0, 20.0, size=n)
    df.loc[mask, "merchant_risk_aggregate"] = rng.uniform(0.5, 0.95, size=n)
    return df


def inject_fraud_odd_hour(df: pd.DataFrame, rng: np.random.Generator) -> pd.DataFrame:
    mask = rng.random(len(df)) < 0.08
    n = mask.sum()
    df.loc[mask, "hour_of_day"] = rng.choice([1, 2, 3], size=n)
    df.loc[mask, "offline_duration_seconds"] = rng.uniform(12000, 25000, size=n)
    df.loc[mask, "user_tx_count_5m"] = rng.poisson(5, size=n).clip(3, 15)
    df.loc[mask, "merchant_risk_aggregate"] = rng.uniform(0.4, 0.8, size=n)
    df.loc[mask, "amount"] = rng.uniform(2000, 4500, size=n)
    df.loc[mask, "amount_to_token_limit_ratio"] = rng.uniform(0.4, 0.9, size=n)
    return df


def inject_fraud_long_offline(df: pd.DataFrame, rng: np.random.Generator) -> pd.DataFrame:
    mask = rng.random(len(df)) < 0.06
    n = mask.sum()
    df.loc[mask, "offline_duration_seconds"] = rng.uniform(25000, 86400, size=n)
    df.loc[mask, "previous_settlement_failed"] = rng.choice([0, 1], size=n, p=[0.2, 0.8])
    df.loc[mask, "time_to_expiry_seconds"] = rng.uniform(50, 1500, size=n)
    df.loc[mask, "token_age_seconds"] = rng.uniform(19000, 21600, size=n)
    df.loc[mask, "amount"] = rng.uniform(1500, 4000, size=n)
    df.loc[mask, "amount_to_token_limit_ratio"] = rng.uniform(0.3, 0.8, size=n)
    return df


def inject_fraud_duplicate_payload(df: pd.DataFrame, rng: np.random.Generator) -> pd.DataFrame:
    mask = rng.random(len(df)) < 0.05
    n = mask.sum()
    df.loc[mask, "duplicate_payload_hash_count"] = rng.poisson(10, size=n).clip(5, 30)
    df.loc[mask, "token_reuse_count"] = rng.poisson(6, size=n).clip(2, 20)
    df.loc[mask, "previous_settlement_failed"] = rng.choice([0, 1], size=n, p=[0.3, 0.7])
    df.loc[mask, "merchant_risk_aggregate"] = rng.uniform(0.3, 0.7, size=n)
    return df


def inject_subtle_fraud(df: pd.DataFrame, rng: np.random.Generator) -> pd.DataFrame:
    mask = rng.random(len(df)) < 0.10
    n = mask.sum()
    df.loc[mask, "amount"] = rng.uniform(2000, 3800, size=n)
    df.loc[mask, "amount_to_token_limit_ratio"] = rng.uniform(0.4, 0.76, size=n)
    df.loc[mask, "merchant_amount_deviation"] = rng.uniform(3.0, 7.0, size=n)
    df.loc[mask, "user_tx_count_1h"] = rng.poisson(10, size=n).clip(5, 30)
    df.loc[mask, "user_tx_count_5m"] = rng.poisson(4, size=n).clip(2, 12)
    df.loc[mask, "merchant_risk_aggregate"] = rng.uniform(0.5, 0.9, size=n)
    df.loc[mask, "hour_of_day"] = rng.choice([0, 1, 2, 3, 4, 22, 23], size=n)
    df.loc[mask, "offline_duration_seconds"] = rng.uniform(3000, 10000, size=n)
    return df


def generate(n: int, rng: np.random.Generator) -> pd.DataFrame:
    n_legit = int(n * 0.80)
    n_fraud = n - n_legit

    legit = generate_base_legitimate(n_legit, rng)
    legit["label"] = 0

    fraud = generate_base_legitimate(n_fraud, rng)
    fraud = inject_fraud_token_reuse(fraud, rng)
    fraud = inject_fraud_velocity(fraud, rng)
    fraud = inject_fraud_amount(fraud, rng)
    fraud = inject_fraud_odd_hour(fraud, rng)
    fraud = inject_fraud_long_offline(fraud, rng)
    fraud = inject_fraud_duplicate_payload(fraud, rng)
    fraud = inject_subtle_fraud(fraud, rng)
    fraud["label"] = 1

    df = pd.concat([legit, fraud], ignore_index=True)
    df = df.sample(frac=1.0, random_state=rng).reset_index(drop=True)
    return df


def main():
    parser = argparse.ArgumentParser(description="Generate synthetic Paron Guard dataset")
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--n", type=int, default=10000)
    parser.add_argument("--out-dir", type=str, default="./data")
    args = parser.parse_args()

    rng = np.random.default_rng(args.seed)
    df = generate(args.n, rng)

    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    csv_path = out_dir / "features.csv"
    df.to_csv(csv_path, index=False)

    manifest = {
        "generator_version": GENERATOR_VERSION,
        "seed": args.seed,
        "total_records": len(df),
        "legitimate_count": int((df["label"] == 0).sum()),
        "fraud_count": int((df["label"] == 1).sum()),
        "fraud_rate": float((df["label"] == 1).mean()),
        "feature_schema_version": "v1",
        "feature_names": FEATURE_NAMES,
        "csv_path": str(csv_path),
    }
    manifest_path = out_dir / "manifest.json"
    with open(manifest_path, "w") as f:
        json.dump(manifest, f, indent=2)

    print(f"Generated {len(df)} records ({(df['label']==0).sum()} legit, {(df['label']==1).sum()} fraud)")
    print(f"CSV: {csv_path}")
    print(f"Manifest: {manifest_path}")


if __name__ == "__main__":
    main()
