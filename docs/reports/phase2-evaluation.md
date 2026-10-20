# Phase 2 Evaluation Report

**Model:** lr-calibrated-v2.0.0-scenario
**Holdout set:** 4000 total records / 3554 ML-eligible records
**Threshold:** 0.46

## AI Model vs Rules Baseline

| Metric | AI Model | Rules Baseline | Difference (rules - AI) |
|---|---|---|---|
| PR-AUC | 0.9105 | 0.146 | 0.7645 |
| Precision | 0.8169 | 0.0 | -- |
| Recall | 0.786 | 0.0 | -- |
| F1 | 0.8012 | 0.0 | -- |
| FP Cost (INR) | 46500.0 | 0.0 | -46500.0 |

## Mandatory security gate

- Deterministic hard-reject scenarios: 446
- All detected before ML scoring: True
- Model scores applied to hard rejects: 0

## Confusion Matrix (AI Model)

```
                Predicted
              Legit   Fraud
Actual Legit    2933      93
       Fraud      113     415
```

## Production Decision (3-State: APPROVE / HOLD / REJECT)

The metric that matters operationally: does a fraud transaction get auto-approved?
Simulates policy thresholds and the deterministic token/integrity gate. Production rules that require live Redis state are tested separately in the service suite.

| Outcome on fraud txns | Value |
|---|---|
| Fraud caught (REJECT or HOLD — never auto-approved) | **0.9877** (98.8%) |
| Fraud slips through (APPROVED) | 0.0123 |

| Outcome on legitimate txns | Value |
|---|---|
| Approved (good precision) | 0.7383 |
| Held for review (false-hold) | 0.2551 |
| Hard-rejected (false-reject) | 0.0066 |

Decision distribution: 2246 APPROVE / 946 HOLD / 808 REJECT

## Latency

- Mean: 2.96ms
- P50: 2.62ms
- P95: 4.18ms
- P99: 4.37ms
- Batch size: 3554

## Failure Modes

- Model unavailable -> HOLD_FOR_REVIEW (fail-closed)

## Limitations

- Synthetic scenario labels: useful for engineering validation, not evidence of real-world fraud performance
- Scenario prevalence and behaviour are design assumptions; validate transfer to production with monitored real outcomes
- No merchant type/category in v1 features — AI lacks contextual merchant intelligence
- No device age or user account age — cannot detect newly registered suspicious accounts
- Threshold tuned on validation split; production threshold should be recalibrated on real data
- Latency measured on CPU with small batch; production latency may differ under load
