# Software Requirements Specification: Paron Guard

| Field | Value |
|---|---|
| Product | Paron Guard — AI Risk Manager for offline-payment settlement |
| Version | 1.0 — Buildathon upgrade target |
| Track | Razorpay AI Buildathon: AI Risk Manager |
| Status | Implementation specification |
| Baseline | Existing Paron Java/Spring microservices: API gateway, token, ledger, sync, and fraud services |

## 1. Purpose

Paron Guard reduces merchant loss caused by suspicious offline-payment transactions that are uploaded after reconnection. It augments Paron’s current rule-based fraud service with explainable ML, a constrained decision policy, explicit exposure limits, a capped guarantee/escrow option, a merchant review/dispute workflow, and measurable offline evaluation.

This specification distinguishes **implemented baseline** components from **Buildathon upgrade requirements**. A feature must not be presented as live until it satisfies its acceptance criteria.

## 2. Users and goals

| User | Goal |
|---|---|
| Customer / merchant POS | Upload queued transactions reliably after reconnecting and learn their final status. |
| Merchant reviewer | Understand why a transaction was held or rejected and resolve a case without altering the original evidence. |
| Merchant operator | Monitor loss prevented, false-positive impact, queue health, and model fallbacks. |
| Platform operator | Approve model/policy releases, investigate an audit trail, and roll back safely. |

## 3. Functional requirements

### Transaction intake and settlement

| ID | Requirement | Priority | Acceptance criterion |
|---|---|---|---|
| FR-01 | Accept a validated batch of up to 100 offline transactions through the sync API and return an acknowledgement without waiting for settlement. | Must | Valid 1–100 item batch returns `202`; a 101-item batch returns a documented validation error. |
| FR-02 | Preserve and expose transaction states: `RECEIVED`, `PROCESSING`, `SETTLED`, `REJECTED`, `FAILED`, and `HELD_FOR_REVIEW`. | Must | Status endpoint returns the state, correlation ID, and safe reason code for each caller-owned transaction. |
| FR-03 | Validate token signature, expiry, reservation binding, amount limit, and one-time use before a risk decision can approve settlement. | Must | A failed validation never triggers ledger settlement. |
| FR-04 | Enforce idempotency with device transaction ID plus immutable payload hash. | Must | Replaying an identical request yields the original result; conflicting reuse is rejected and audited. |
| FR-05 | Settle only an approved transaction, only once, and never above the reservation amount. | Must | Ledger integration test proves duplicate/held/rejected requests produce no additional debit. |
| FR-06 | Calculate and enforce merchant/customer offline exposure limits before issuing or accepting a token. | Must | A payment above the reservation, merchant cap, device velocity cap, or token expiry is refused and audited. |
| FR-07 | Support an optional capped guarantee/escrow reserve with explicit eligibility, amount, and risk owner. | Should | A simulated confirmed-fraud case applies only the eligible reserve and records uncovered residual exposure. |

### Hybrid AI risk decisioning

| ID | Requirement | Priority | Acceptance criterion |
|---|---|---|---|
| FR-08 | Evaluate deterministic rules for replay, velocity, unusual amount, impossible time pattern, and token reuse. | Must | Each rule has unit tests covering a positive, negative, and boundary example. |
| FR-09 | Build a versioned, minimal feature vector without raw JWTs or direct PII. | Must | Feature-contract test rejects undeclared fields and records schema version. |
| FR-10 | Return calibrated risk score `[0,1]`, model version, confidence/fallback flag, and top explanation reason codes. | Must | All model responses validate against the decision schema; scores outside the range are rejected. |
| FR-11 | Apply a versioned policy: hard control → `REJECT`; low risk → `APPROVE`; uncertain/mid risk → `HOLD_FOR_REVIEW`; high risk → `REJECT`. | Must | Threshold boundary tests show the correct action and action ceiling. |
| FR-12 | Fail closed on model timeout, unavailable model, stale/missing critical features, or unrecognised model version. | Must | Fault injection produces `HOLD_FOR_REVIEW` and zero ledger settlement calls. |
| FR-13 | Keep the model unable to invoke settlement, token, guarantee, or gateway actions. | Must | Deployment permissions and integration test demonstrate no mutation endpoint is reachable from the model service identity. |

### Review, explanation, and audit

| ID | Requirement | Priority | Acceptance criterion |
|---|---|---|---|
| FR-14 | Create a review case for held transactions with score band, rules, evidence summary, and safe recommended action. | Must | Reviewer can list and inspect a case without seeing raw tokens or other users’ data. |
| FR-15 | Require an authorised reviewer, final decision, and reason for any case resolution. | Must | Unauthorised resolution is denied; authorised resolution appends, rather than overwrites, an audit event. |
| FR-16 | Create a dispute/compensation case for confirmed fraud after delivery, applying only eligible reserved or guarantee funds. | Must | Case records protected amount, paid amount, residual exposure, and risk owner. |
| FR-17 | Record immutable decision, protection, and settlement audit events. | Must | Audit trace contains correlation ID, payload hash, model/policy versions, reserve/guarantee amount, actor, decision, and settlement outcome. |
| FR-18 | Detect broken audit hash-chain continuity. | Should | Verification job flags a deliberately modified historical event. |

### Evaluation and reporting

| ID | Requirement | Priority | Acceptance criterion |
|---|---|---|---|
| FR-19 | Generate and version a labelled synthetic dataset representing legitimate, duplicate, high-velocity, token-reuse, and anomalous-amount transactions. | Must | Dataset is reproducible from a seed and contains no real personal or payment data. |
| FR-20 | Split data by time and device/user group into train, validation, and untouched held-out test data. | Must | Evaluation report shows split counts and confirms no entity overlap between train and test. |
| FR-21 | Report precision, recall, PR-AUC, confusion matrix, decision latency, and false-positive monetary cost on the held-out test set. | Must | A generated report includes all metrics, threshold/policy version, and sample size. |
| FR-22 | Compare hybrid model+rules against rules-only baseline. | Should | Report states performance and false-positive cost deltas; no cherry-picked subset is used. |
| FR-23 | Block a model release when it misses a documented quality or false-positive-cost threshold. | Should | A deliberately degraded model is rejected by the release check. |

## 4. Non-functional requirements

| ID | Requirement | Target / acceptance criterion |
|---|---|---|
| NFR-01 | Security | Public gateway exposes no internal token, ledger, or fraud-decision endpoint; authenticated merchant access is enforced. |
| NFR-02 | Privacy | Feature and audit payloads use pseudonymous IDs; raw JWTs are never stored in model/evaluation data or UI output. |
| NFR-03 | Reliability | At-least-once Kafka delivery is safe through idempotent processing; retries never create a second settlement. |
| NFR-04 | Performance | P95 synchronous risk decision under 300 ms in the demo environment; sync acknowledgement under 1 s for a 100-record batch excluding client network time. |
| NFR-05 | Availability | Risk dependency failure defaults to review; it may not silently default to approval. |
| NFR-06 | Explainability | Every non-approved decision includes at least one stable reason code; every approved AI decision records score and policy version. |
| NFR-07 | Observability | All services log correlation ID and emit decision count, fallback count, latency, queue age, and settlement-result metrics. |
| NFR-08 | Reproducibility | One documented command seeds the synthetic dataset, runs evaluation, and reproduces the submission report. |
| NFR-09 | Accessibility | Dashboard reason codes use concise plain language; colour is not the only indicator of risk state. |

## 5. API contracts to add or extend

All externally reachable endpoints are authenticated through the API gateway. Internal contracts require service identity and are not gateway-routed.

| Interface | Method and path | Audience | Contract |
|---|---|---|---|
| Sync upload | `POST /api/v1/sync/{userId}` | Device/POS | Existing batch intake; extend response with correlation IDs and accepted/rejected item reason codes. |
| Transaction status | `GET /api/v1/sync/{userId}` | Device/POS | Existing status history; restrict to authenticated caller and include `HELD_FOR_REVIEW`. |
| Risk decision | `POST /internal/v1/risk/decisions` | Settlement orchestrator | Feature schema/version in; score, confidence, rules, explanations, model/policy versions, and decision out. |
| Review cases | `GET /api/v1/risk/cases`, `POST /api/v1/risk/cases/{id}/resolve` | Merchant reviewer | Paginated cases and authorised, reasoned resolution. |
| Audit trace | `GET /api/v1/risk/audit/{correlationId}` | Merchant operator | Redacted chronological events and verification status. |
| Metrics report | `GET /internal/v1/model/evaluations/{version}` | Operator/demo | Read-only held-out evaluation report. |

Representative risk response:

```json
{
  "correlationId": "c_01J...",
  "decision": "HOLD_FOR_REVIEW",
  "riskScore": 0.63,
  "confidence": "MEDIUM",
  "reasonCodes": ["DEVICE_VELOCITY_HIGH", "AMOUNT_ABOVE_MERCHANT_NORM"],
  "ruleHits": ["VelocityRule"],
  "modelVersion": "risk-2026-08-01",
  "featureSchemaVersion": "v1",
  "policyVersion": "risk-policy-v1",
  "fallbackApplied": false
}
```

## 6. Data requirements

| Entity | Key fields | Retention / handling |
|---|---|---|
| Offline transaction | transaction ID, pseudonymous user/device IDs, merchant ID, amount, timestamp, status, payload hash | System-of-record transaction retention; never copy raw token into analytics. |
| Risk decision | correlation ID, score, decision, reason codes, rules, model/features/policy versions | Append-only; redacted audit viewing. |
| Review case | case ID, decision reference, reviewer identity, resolution, notes, timestamps | Append-only resolution history; role restricted. |
| Feature snapshot | decision reference, derived values, feature schema version, freshness flags | Minimal fields only; no raw identifiers beyond pseudonymous keys. |
| Model evaluation | data snapshot ID, split metadata, metrics, cost assumptions, approval state | Versioned, reproducible, synthetic data only for Buildathon. |

## 7. Evaluation protocol and success criteria

The demo dataset must contain at least 50 records; the final metric report should use a materially larger generated set (recommended: 1,000+ records) with a documented fraud prevalence. The test split is locked before threshold selection. Group records by device/user and split by time to avoid leakage.

False-positive cost is reported as:

`sum(amount for legitimate transactions sent to HOLD_FOR_REVIEW or REJECT) + documented review-cost assumption × held legitimate transactions`

Buildathon success means showing the measured result, not inventing a target. Before a release can be promoted in the demo, the team sets these configuration values and records them in the report:

- minimum precision and recall appropriate to the synthetic scenario;
- maximum false-positive rupee cost per 1,000 transactions;
- maximum fallback rate; and
- P95 decision latency.

If a model does not meet the gate, the release remains rejected and the rules-only/fail-closed path stays active. This makes the model’s limitations visible rather than hiding them.

## 8. Test plan

| Test layer | Required cases |
|---|---|
| Unit | Every fraud rule, feature transform, threshold boundary, explanation formatter, audit hash creation, and authorization decision. |
| Contract | Sync, token validation, risk decision, ledger settlement, and review-case schema compatibility. |
| Integration | Valid transaction → approval → one settlement; duplicate → one outcome; high risk → review/reject → no settlement. |
| Fault injection | Model timeout, Kafka redelivery, token-service timeout, ledger timeout, malformed model response, and stale feature. |
| Evaluation | Reproducible seeded data, leakage checks, held-out metrics, baseline comparison, and cost calculation. |
| Security | Gateway denies internal paths, caller cannot access another user’s transactions, raw token absent from audit/UI/log test fixtures. |
| Demo | 50+ record batch, dashboard summary, one suspicious trace, one graceful model failure, and audit-chain verification. |

## 9. Build order

1. Correctly label the current rule engine as the baseline and add correlation IDs, audit events, and `HELD_FOR_REVIEW` state.
2. Build synthetic generator, rules-only evaluation, and an honest baseline report.
3. Add feature contract, calibrated risk model service, decision policy, and explanation output.
4. Add review queue/dashboard and fault-injection demo.
5. Automate tests, report generation, demo seed data, architecture image/video, and README submission instructions.

## 10. Risks and mitigations

| Risk | Mitigation |
|---|---|
| Synthetic metrics overstated as real-world performance | Clearly label all data and results synthetic; publish generator, seed, splits, and limitations. |
| Model falsely blocks legitimate payments | Use review band, report rupee impact, calibrate threshold on validation data, and track reviewer overrides. |
| Model outage creates unsafe approvals | Fail closed to `HOLD_FOR_REVIEW`; queue safely for retry/review. |
| Duplicate settlement from retries | Use end-to-end idempotency key and verify ledger-side outcome before retry. |
| Sensitive payment data reaches AI/evaluation | Enforce feature allowlist, pseudonymisation, redaction tests, and no raw JWT retention. |
| Scope too large for Buildathon | Ship rules + evaluation + audit + one model first; keep external payment integrations behind an adapter. |
