# Phase 2 Implementation Plan: AI Risk Layer

**Parent document:** [architecture.md](architecture.md) — delivery plan Phase 2 ("AI risk layer")
**Deliverable target:** Feature builder, calibrated model, explanation contract, policy thresholds.
**Demo proof required:** Compare rules-only vs hybrid metrics on an untouched held-out split.

---

## 1. Scope

### In scope

1. **Risk feature builder** — versioned tabular feature vector derived from the incoming `TransactionEvent` plus historical aggregates. No raw tokens, instrument numbers, contact data, or free text.
2. **Calibrated risk model service** — a separate `risk-model-service` that receives only the feature vector and returns a bounded probability, confidence/fallback flag, model metadata, and top-3 feature contributions as plain-language reason codes.
3. **Explanation contract** — stable JSON shape so every decision can be reconstructed from an audit record later.
4. **Decision policy** — converts rule hits + model output into exactly one of `APPROVE`, `HOLD_FOR_REVIEW`, `REJECT`, using version-controlled thresholds and a strict action ceiling.
5. **Evaluation pipeline + minimal model registry** — synthetic labelled dataset generation, time/device-based splits, calibration, held-out test report (precision, recall, PR-AUC, confusion matrix, threshold, sample count, false-positive rupee cost), and an approval gate before a model version is loadable.

### Out of scope (later phases)

- Chained-hash audit store, dashboard, review-queue UX polish (Phase 3).
- Merchant protection policy / guarantee reserve accounting (separate workstream).
- LLM summarisation of evidence (optional, later).
- Real bank/NPCI/Razorpay credentials; production PII.

---

## 2. Current state (grounded in code)

| Fact | Where |
|---|---|
| Maven multi-module, Spring Boot 4.1.0 / Java 26 | root `pom.xml` |
| Settlement flow calls fraud check per transaction | `sync-service/.../batch/SettlementProcessor.java` → `FraudCheckClient.check()` |
| Fraud check is HTTP over RestTemplate with `@Retry` | `sync-service/.../client/FraudCheckClient.java` (`POST /api/v1/fraud/check`) |
| Scoring is rules-only, additive scores, **binary approve/reject** | `fraud-service/.../service/FraudScoringService.evaluate()` |
| Rules are stateless + Redis counters | `fraud-service/.../rules/*` (e.g. `VelocityRule` uses `StringRedisTemplate`) |
| Input DTO carries userId, deviceTransactionId, offlineToken, deviceId, amount, merchantId, transactedAt, tokenExpiryTime | `fraud-service/.../dto/TransactionEvent.java` |
| **Fail-open defect:** null response from fraud-service currently defaults to APPROVE | `FraudCheckClient.check()` |

Two structural gaps this phase must close:

- The decision contract is boolean (`approved`). It needs three outcomes with an action ceiling.
- Failure mode is fail-open. Architecture requires fail-closed (`HOLD_FOR_REVIEW`).

---

## 3. Target end state

```
SettlementProcessor ──► FraudCheckClient ──► fraud-service /api/v1/fraud/check
                                                │
                                                ├─► Rules engine (unchanged hard controls)
                                                ├─► FeatureBuilder ──► risk-model-service /v1/score
                                                │        (timeout + circuit breaker, fail-closed)
                                                └─► DecisionPolicy(rules, model) ──► RiskDecision
                                                        APPROVE            → settle (as today)
                                                        HOLD_FOR_REVIEW    → no settlement, case opened
                                                        REJECT             → no settlement, reason shown
```

The model service is deliberately dumb and isolated: read-only feature input, bounded probability out, no access to ledger/token/gateway, no DB credentials.

---

## 4. Workstreams and tasks

### WS1 — Feature builder (fraud-service)

| # | Task | Notes |
|---|---|---|
| 1.1 | Create `feature/RiskFeatureVector` record with **feature schema version** field (`featureSchemaVersion: "v1"`) | Version is embedded in every request/response and audit payload |
| 1.2 | Implement `feature/RiskFeatureBuilder.build(TransactionEvent)` producing v1 features | See feature list below |
| 1.3 | Back historical aggregates with Redis windows (reuse the pattern in `VelocityRule`): trailing 5m / 1h / 24h tx-count and tx-value per user/device/token | Keys must be pseudonymous (hashed userId/deviceId), TTL-bound |
| 1.4 | Add freshness/missingness indicator fields (e.g. `historyAvailable`, `tokenAgeKnown`) | Model must see "unknown", not silent zeros |

**v1 feature set** (from architecture.md §6): `amount`, `amount_to_token_limit_ratio`, merchant-relative amount deviation, windowed counts/values (user/device/token × 5m/1h/24h), token age, time-to-expiry, offline duration, token reuse count, duplicate payload-hash count, previous settlement outcome flag, merchant risk aggregate, hour-of-day/day-of-week encodings, missingness indicators.

**Acceptance criteria**
- Unit tests cover every feature with known-input→known-output cases, including missing-field paths.
- A golden-file test pins the exact JSON of the v1 vector for a fixture event (schema drift breaker).
- No raw JWT/token value ever appears in the vector (assert in test).

### WS2 — Training pipeline (`ml/` at repo root, Python)

| # | Task | Notes |
|---|---|---|
| 2.1 | `ml/generate_synthetic.py` — labelled generator: benign traffic + injected fraud archetypes (replay, burst velocity, amount anomaly, impossible timing, odd-hour) consistent with what the rules already catch *and some they don't* (sub-threshold patterns) | Labels: 0 legit / 1 confirmed fraud; emit CSV/Parquet + generator seed manifest |
| 2.2 | `ml/train.py` — regularised logistic regression baseline (optionally small GBT behind a flag), class-weighted; split by **time then device** into train/validation/test; calibrate on validation (isotonic or Platt) | Never touch the test split until final report |
| 2.3 | Export artefacts: `model.joblib`, `feature_schema.json`, `metrics.json`, `manifest.json` (modelVersion, training snapshot id, feature schema version, threshold-policy version, git SHA) | Deterministic given seed |
| 2.4 | `ml/evaluate.py` — produces the held-out report: precision, recall, PR-AUC, confusion matrix, chosen threshold, sample count, false-positive rupee cost (configurable ₹ assumption per FP) | Also runs rules-only baseline on the same test rows for comparison |
| 2.5 | Approval gate: `ml/promote.py` copies artefacts to `risk-model-service/artifacts/approved/<version>/` **only if** quality gates pass (min recall, max FP cost, calibration error bound) | Registry = this approved directory; nothing else is loadable |

**Acceptance criteria**
- Running generate→train→evaluate end-to-end reproduces metrics within tolerance for a fixed seed.
- Test split is provably untouched during training (split recorded before fit).
- Report includes both rules-only and hybrid numbers side by side.

### WS3 — Model service (`risk-model-service/`, Python FastAPI)

| # | Task | Notes |
|---|---|---|
| 3.1 | `POST /v1/score` — accepts `{correlationId, featureSchemaVersion, features{...}}`; validates schema version matches loaded model; returns `{score, confidence, fallback:false, modelVersion, thresholdPolicyVersion, topContributions:[{reasonCode, plainLanguage, weight}]}` | Score always in [0,1]; top-3 contributions sorted by absolute weight |
| 3.2 | Reason-code dictionary: stable codes (e.g. `AMOUNT_DEVIATION_HIGH`, `TOKEN_NEAR_EXPIRY`) mapped to human phrases | Contract documented in `docs/` |
| 3.3 | Load **only** from `artifacts/approved/`; refuse to start otherwise | Enforces the registry gate |
| 3.4 | Hard request timeout (~150ms budget) and health endpoint `/healthz` reporting loaded modelVersion | Used by the fail-closed demo |
| 3.5 | Dockerfile + docker-compose entry alongside existing services | Consistent local dev experience |

**Malformed output, unknown version, or any internal error ⇒ non-200; caller treats it as model-unavailable (fail-closed).**

**Acceptance criteria**
- Contract tests: valid request → well-formed response; wrong schema version → 409; oversized/missing features → 400.
- Latency smoke test under seeded 50-tx batch stays within budget.

### WS4 — Decision policy (fraud-service)

| # | Task | Notes |
|---|---|---|
| 4.1 | Enum `DecisionType { APPROVE, HOLD_FOR_REVIEW, REJECT }` | Replaces boolean `approved` in responses (keep old field temporarily for compatibility) |
| 4.2 | `policy/DecisionPolicy.decide(ruleHits, modelOutput)` implementing the §5 table from architecture.md: hard rule hit ⇒ REJECT regardless of score; score < approveThreshold ⇒ APPROVE; review band / low confidence / feature-quality warning ⇒ HOLD_FOR_REVIEW; > rejectThreshold ⇒ REJECT; **model unavailable/timeout/malformed ⇒ HOLD_FOR_REVIEW (fail-closed)** | Thresholds via config: `policy.approve-below`, `policy.reject-above`, `policy.min-confidence` |
| 4.3 | Stamp every response with `policyVersion` and `modelVersion` | Config/version-controlled, e.g. `"thresholds-v1"` |
| 4.4 | Extend `FraudScoringService` (or wrap it in a `RiskManagerService`) to orchestrate: rules → features → model call (Resilience4j TimeLimiter + CircuitBreaker around the new `ModelClient`) → policy | Keep existing `FraudRule` beans untouched |

**Acceptance criteria**
- Table-driven unit tests: every row of the §5 condition table maps to the mandated decision, including model-timeout and malformed-explanation rows.
- Property test: no code path can emit a settlement-permitting decision when a hard rule fired.
- Config test: removing the model service does not yield APPROVE anywhere.

### WS5 — Integration (sync-service + contracts)

| # | Task | Notes |
|---|---|---|
| 5.1 | Update `FraudCheckResult` DTO to carry `decision`, `score`, `confidence`, `modelVersion`, `policyVersion`, `reasonCodes[]` | Keep `score` populated for the existing `fraud_score` column |
| 5.2 | `FraudCheckClient`: parse new contract; **replace the null→APPROVE default with null→HOLD_FOR_REVIEW** | Fixes the fail-open defect |
| 5.3 | Add `HELD_FOR_REVIEW` to `TransactionStatus`; `SettlementProcessor` routes HOLD → persist status, skip ledger settle, raise a fraud-service alert/case with reasons | Money never moves on HOLD |
| 5.4 | Idempotency unchanged: `deviceTransactionId` + payload hash still governs retries | Held transactions remain replayable/resolvable |
| 5.5 | Update sync-service tests: approve settles as before; hold leaves balance untouched and creates a case; reject path preserved | Existing `SettlementProcessorTest` patterns extended |

**Acceptance criteria**
- Full-flow tests: normal tx settles; replay still rejects; sub-threshold suspicious tx holds with zero ledger movement.
- Killing risk-model-service mid-batch results in held cases, no settlements, no exceptions surfaced to the POS.

### WS6 — Evaluation evidence & handoff to Phase 3

| # | Task |
|---|---|
| 6.1 | Commit the generated held-out report (markdown table + confusion matrix) under `docs/reports/phase2-evaluation.md` |
| 6.2 | Record the rules-only vs hybrid delta explicitly — this is the Phase 2 demo proof |
| 6.3 | Seed-script extension: batch containing ≥1 should-hold and ≥1 model-timeout scenario for the Phase 3 graceful-failure demo |
| 6.4 | Note limitations honestly (synthetic labels ≠ production performance) |

---

## 5. Sequencing

```
WS1 features ──┐
               ├──► WS4 policy ──► WS5 integration ──► WS6 evidence
WS2 training ──┤        ▲
WS3 service ───┘────────┘ (contract frozen first)
```

| Milestone | Contents | Exit test |
|---|---|---|
| M1 | WS1 complete | Golden-vector + coverage tests green |
| M2 | WS2 artefacts approved | `promote.py` succeeds; report drafted |
| M3 | WS3 service serving approved model | Contract + latency tests green |
| M4 | WS4 + WS5 wired | Three-outcome flow tests green; fail-closed proven |
| M5 | WS6 docs committed | Hybrid-vs-rules table exists; demo seeds ready |

Contract-first note: freeze the `/v1/score` request/response JSON (WS3.1–3.2) early — WS1's builder, WS4's policy tests, and the trainer all depend on it.

## 6. Risks / open questions

- **Synthetic label realism** — if the generator only creates fraud the rules already catch, hybrid ≈ rules-only and the phase proves nothing. Mitigate in WS2.1 with sub-rule-threshold archetypes.
- **Java↔Python boundary latency** — one extra HTTP hop inside the settlement path; keep the 150ms budget and circuit breaker from day one.
- **`TransactionStatus` migration** — adding `HELD_FOR_REVIEW` touches DB constraints and any status-machine assumptions in sync-service.
- **Compatibility shim** — how long do we keep the legacy boolean `approved` field before removing it?
- **Threshold values** — initial approve/reject cut-offs come from the validation curve, not intuition; document why they were chosen.

## 7. Definition of done (Phase 2)

1. Every transaction produces `{decision ∈ {APPROVE, HOLD_FOR_REVIEW, REJECT}, score, confidence, modelVersion, policyVersion, reasonCodes[]}`.
2. Model unavailable ⇒ HOLD, never APPROVE (verified by test and reproducible in demo).
3. Approved-model-only loading enforced by the registry layout.
4. Held-out report shows precision/recall/PR-AUC/confusion/false-positive rupee cost for both rules-only and hybrid on the identical untouched split.
5. All existing flows (settle, reject-on-replay, idempotent retry) regress green.
