# Phase 2 Evaluation Report

**Model:** lr-calibrated-v1.0.0
**Holdout set:** 1000 records
**Threshold:** 0.53

## AI Model vs Rules Baseline

| Metric | AI Model | Rules Baseline | Delta |
|---|---|---|---|
| PR-AUC | 0.613 | 0.3854 | +0.2276 |
| Precision | 1.0 | 0.5778 | -- |
| Recall | 0.46 | 0.39 | -- |
| F1 | 0.6301 | 0.4657 | -- |
| FP Cost (INR) | 0.0 | 28500.0 | -28500.0 |

## Confusion Matrix (AI Model)

```
                Predicted
              Legit   Fraud
Actual Legit     800       0
       Fraud      108      92
```

## Latency

- Mean: 4.11ms
- P50: 3.86ms
- P95: 5.04ms
- P99: 5.26ms
- Batch size: 1000

## Failure Modes

- Model unavailable -> HOLD_FOR_REVIEW (fail-closed)

## Limitations

- Synthetic labels: generator-created fraud patterns, not real chargeback data
- No merchant type/category in v1 features — AI lacks contextual merchant intelligence
- No device age or user account age — cannot detect newly registered suspicious accounts
- Threshold tuned on validation split; production threshold should be recalibrated on real data
- Latency measured on CPU with small batch; production latency may differ under load
