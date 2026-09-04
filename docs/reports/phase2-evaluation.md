# Phase 2 Evaluation Report

**Model:** lr-calibrated-v1.0.0
**Holdout set:** 2000 records
**Threshold:** 0.37

## AI Model vs Rules Baseline

| Metric | AI Model | Rules Baseline | Delta |
|---|---|---|---|
| PR-AUC | 0.7089 | 0.4449 | +0.264 |
| Precision | 0.9784 | 0.6024 | -- |
| Recall | 0.5675 | 0.5075 | -- |
| F1 | 0.7184 | 0.5509 | -- |
| FP Cost (INR) | 2500.0 | 67000.0 | -64500.0 |

## Confusion Matrix (AI Model)

```
                Predicted
              Legit   Fraud
Actual Legit    1595       5
       Fraud      173     227
```

## Production Decision (3-State: APPROVE / HOLD / REJECT)

The metric that matters operationally: does a fraud transaction get auto-approved?
Simulates the exact DecisionPolicy path (rules gate + calibrated model + low-signal guard) on the same holdout.

| Outcome on fraud txns | Value |
|---|---|
| Fraud caught (REJECT or HOLD — never auto-approved) | **0.6425** (64.2%) |
| Fraud slips through (APPROVED) | 0.3575 |

| Outcome on legitimate txns | Value |
|---|---|
| Approved (good precision) | 0.8094 |
| Held for review (false-hold) | 0.1062 |
| Hard-rejected (false-reject) | 0.0844 |

Decision distribution: 1438 APPROVE / 187 HOLD / 375 REJECT

## Latency

- Mean: 3.73ms
- P50: 3.74ms
- P95: 4.9ms
- P99: 5.08ms
- Batch size: 2000

## Failure Modes

- Model unavailable -> HOLD_FOR_REVIEW (fail-closed)

## Limitations

- Synthetic labels: generator-created fraud patterns, not real chargeback data
- No merchant type/category in v1 features — AI lacks contextual merchant intelligence
- No device age or user account age — cannot detect newly registered suspicious accounts
- Threshold tuned on validation split; production threshold should be recalibrated on real data
- Latency measured on CPU with small batch; production latency may differ under load
