# Scenario dataset card: OfflinePay settlement risk v2

## Purpose

This dataset is a reproducible simulator for engineering and evaluation of the
post-reconnection settlement-risk flow. It is not real payment data and must
not be used to claim production fraud-detection performance.

## Event sequences

Each row belongs to a chronological `sequence_id`, rather than being an
independent invented transaction. The generator includes these labelled cases:

| Scenario | Expected outcome | Why it exists |
|---|---|---|
| Student canteen day | APPROVE | Routine small purchases |
| Offline commute | APPROVE | Legitimate delayed synchronisation |
| Large legitimate purchase | HOLD | Unusual amount is not proof of fraud |
| New device / low signal | HOLD | Insufficient history must not auto-approve |
| Campus event | HOLD | Legitimate high velocity overlaps suspicious behaviour |
| Below-threshold velocity | HOLD | Adversary avoids fixed rule thresholds |
| Cross-merchant token conflict | REJECT before ML | Different claims reuse a token |
| Tampered receipt | REJECT before ML | Integrity conflict represented for rule testing |

The generator records the scenario, rationale, expected policy outcome and
whether a row is `ml_eligible`. Hard-reject rows are excluded from model
training and scoring: deterministic security checks always win.

## Leakage controls

- Feature values are limited to signals observable at settlement.
- Entire sequences, not individual rows, are assigned to train, validation or test.
- Splits are chronological: earlier sequences train, later sequences validate and test.
- The separately generated holdout uses a different seed.

## Reproduce the candidate

```powershell
python ml/generate_synthetic.py --seed 42 --n 12000 --out-dir ml/data/scenario-v2
python ml/train.py --data ml/data/scenario-v2/features.csv --out-dir ml/artifacts/scenario-v2
python ml/generate_synthetic.py --seed 99 --n 4000 --out-dir ml/data/scenario-v2-holdout
python ml/evaluate.py --model ml/artifacts/scenario-v2/model.joblib --holdout ml/data/scenario-v2-holdout/features.csv --out-dir ml/artifacts/scenario-v2
```

The resulting candidate is intentionally not copied to
`risk-model-service/artifacts/approved/`. It must run in shadow mode and be
reviewed against real settlement outcomes before it can influence policy.

To run the candidate in shadow mode on Windows, keep the approved v1 model in
place and start the model service as follows:

```powershell
$env:SHADOW_MODEL_DIR = (Resolve-Path "ml/artifacts/scenario-v2")
cd risk-model-service
uvicorn app.main:app --port 8600
```

`POST /v1/score` then returns `shadowAssessment` alongside the primary score.
The fraud service records both scores, versions, decision, reason codes and
assessment timestamp in `risk_assessments`; only the primary score is passed
to `DecisionPolicy`.

## Limitations

Scenario prevalence, labels and behaviour are design assumptions. The reported
metrics validate the software pipeline and handling of known cases; they do not
measure performance on card, UPI, bank, or real offline-payment fraud.
