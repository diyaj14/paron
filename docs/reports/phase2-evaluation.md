# Phase 2 Evaluation Report — Paron Guard AI Risk Layer

**Date:** 2026-08-25
**Model:** lr-calibrated-v1.0.0
**Feature schema:** v1 (24 features)
**Threshold policy:** thresholds-v1 (approve < 0.35, reject >= 0.75, review band in between)

---

## 1. AI Model vs Rules Baseline

Tested on 1,000 holdout records (seed=99), never seen during training.

| Metric | AI Model | Rules Baseline | Delta |
|---|---|---|---|
| **PR-AUC** | 0.613 | 0.385 | **+0.228** |
| **Precision** | 100% | 57.8% | **+42.2%** |
| **Recall** | 46% | 39% | **+7%** |
| **F1 Score** | 0.630 | 0.466 | **+0.164** |
| **False Positive Cost** | INR 0 | INR 28,500 | **-INR 28,500** |

**Key result:** The AI model catches fraud with 100% precision (zero false alarms) while the rules baseline flags many legitimate transactions as fraud, costing INR 28,500 in unnecessary holds.

## 2. Confusion Matrix (AI Model)

```
                Predicted
              Legit   Fraud
Actual Legit     800       0
       Fraud      108      92
```

- **True Negatives (800):** Legitimate transactions correctly approved
- **False Positives (0):** No legitimate transactions wrongly flagged
- **False Negatives (108):** Fraud transactions that slipped through
- **True Positives (92):** Fraud transactions correctly caught

## 3. Latency

Measured on CPU (batch of 1,000 transactions):

| Percentile | Latency |
|---|---|
| Mean | 1.74ms |
| P50 | 1.60ms |
| P95 | 2.07ms |
| P99 | 2.07ms |

Well within the 150ms budget specified in the contract.

## 4. Architecture

```
Device -> sync-service -> fraud-service -> rules engine (unchanged)
                                      -> feature builder (v1, 24 features)
                                      -> risk-model-service (Python FastAPI)
                                      -> decision policy (APPROVE/HOLD/REJECT)
                                      <- response
                                  -> SettlementProcessor routes:
                                       APPROVE -> settle via ledger
                                       HOLD_FOR_REVIEW -> persist, skip settle, open case
                                       REJECT -> reject, release claim
```

## 5. Failure Modes

| Scenario | Behaviour | Proof |
|---|---|---|
| Model service down | HOLD_FOR_REVIEW (fail-closed) | `FraudCheckClient` returns null -> SettlementProcessor sets HELD_FOR_REVIEW |
| Model returns error | HOLD_FOR_REVIEW (fail-closed) | Any non-200 from Python service treated as model unavailable |
| Model low confidence | HOLD_FOR_REVIEW | DecisionPolicy enforces min-confidence threshold |
| Hard rule hit | REJECT regardless of model score | DecisionPolicy checks rules first |

## 6. Test Coverage

| Module | Tests | Status |
|---|---|---|
| fraud-service (feature builder) | 7 | All pass |
| fraud-service (decision policy) | 8 | All pass |
| fraud-service (scoring service) | 6 | All pass |
| sync-service (settlement processor) | 6 | All pass |
| **Total** | **27** | **All green** |

## 7. Deliverables

| File | Purpose |
|---|---|
| `fraud-service/.../feature/RiskFeatureBuilder.java` | Extracts 24 v1 features from transaction + Redis |
| `fraud-service/.../feature/RiskFeatureVector.java` | Java record with toMap() bridge to Python |
| `fraud-service/.../policy/DecisionPolicy.java` | Three-outcome decision logic |
| `fraud-service/.../policy/ModelClient.java` | HTTP client to Python model service |
| `fraud-service/.../service/FraudScoringService.java` | Orchestrates rules -> features -> model -> policy |
| `risk-model-service/app/main.py` | FastAPI serving POST /v1/score |
| `ml/generate_synthetic.py` | Synthetic fraud dataset generator |
| `ml/train.py` | Model training with calibration |
| `ml/evaluate.py` | Holdout evaluation + evidence generation |
| `ml/artifacts/model.joblib` | Trained model artifact |
| `ml/artifacts/evaluation-evidence.json` | Machine-readable evaluation bundle |
| `docs/reports/phase2-evaluation.md` | This report |

## 8. Limitations (Honest)

- **Synthetic labels:** Generator-created fraud patterns, not real chargeback data. Production performance may differ.
- **Missing merchant context:** No merchant type/category in v1 features. AI cannot distinguish tea stalls from supermarkets.
- **No device/user age:** Cannot detect newly registered suspicious accounts.
- **Threshold tuning:** Cut-offs chosen from validation split. Production should recalibrate on real data.
- **Sub-threshold fraud:** Some fraud patterns deliberately designed to be subtle (the 108 missed cases). Real attackers would be even more sophisticated.
- **Single model:** Logistic regression chosen for explainability. Gradient boosting would improve metrics but reduce interpretability.

## 9. What Phase 3 Would Add

- Chained-hash audit store for tamper-proof decision logging
- Dashboard for human reviewers to resolve HELD_FOR_REVIEW cases
- Merchant category features (requires data pipeline extension)
- Model retraining pipeline with real transaction labels
- A/B testing framework for threshold tuning
