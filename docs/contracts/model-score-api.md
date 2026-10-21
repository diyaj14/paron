# Model Score API Contract (`POST /v1/score`)

> **Status: FROZEN for Phase 2** — changes require updating `featureSchemaVersion`.
> This is the single source of truth binding three consumers: the Java feature builder (fraud-service WS1), the Python trainer (ml/ WS2), and the model service (risk-model-service WS3).
> Owner: Paron Guard team. Related: [phase2-implementation-plan.md](../phase2-implementation-plan.md), [architecture.md §6](../architecture.md)

---

## 1. Purpose

One HTTP call. The caller (fraud-service) sends an **anonymous feature vector** for one offline transaction. The model service replies with a **bounded risk probability, metadata, and plain-language explanations**.

Hard rules of the relationship:

- The model service receives **no raw tokens, no JWTs, no PII, no free text** — only the fields below.
- It is **read-only and stateless**: no DB access, no callbacks, cannot move money.
- Any non-200 response, timeout (>150 ms), or malformed body is treated by the caller as **model unavailable → HOLD_FOR_REVIEW** (fail-closed).
- Scores are always in `[0.0, 1.0]`. Higher = riskier.

---

## 2. Request

`POST /v1/score`
`Content-Type: application/json`

```json
{
  "correlationId": "01J8ZQ4T9M6K2XWV3P",
  "featureSchemaVersion": "v1",
  "features": {
    "amount": 1250.00,
    "amount_to_token_limit_ratio": 0.25,
    "merchant_amount_deviation": 1.84,
    "user_tx_count_5m": 1,
    "user_tx_count_1h": 3,
    "user_tx_count_24h": 11,
    "device_tx_count_5m": 1,
    "device_tx_count_1h": 2,
    "device_tx_count_24h": 9,
    "token_tx_count_24h": 1,
    "user_tx_value_1h": 3400.50,
    "device_tx_value_24h": 12750.00,
    "token_age_seconds": 3600,
    "time_to_expiry_seconds": 18000,
    "offline_duration_seconds": 900,
    "token_reuse_count": 0,
    "duplicate_payload_hash_count": 0,
    "previous_settlement_failed": 0,
    "merchant_risk_aggregate": 0.12,
    "hour_of_day": 14,
    "day_of_week": 2,
    "history_available": 1,
    "token_age_known": 1,
    "expiry_known": 1
  }
}
```

### Field rules

| Field | Type | Rules |
|---|---|---|
| `correlationId` | string | Required. Echoed verbatim in the response. Used to join audit events across services. |
| `featureSchemaVersion` | string | Required. Must equal a version the loaded model supports (currently `"v1"`). |
| `features` | object | Required. Keys must match the schema below **exactly** (no extra, none missing). |

### Feature schema `v1`

Types: `num` = any JSON number. `flag` = integer `0` or `1`.

| Name | Type | Meaning |
|---|---|---|
| `amount` | num | Transaction amount |
| `amount_to_token_limit_ratio` | num | amount ÷ token limit (≤ 1.0 expected) |
| `merchant_amount_deviation` | num | How many deviations above/below this merchant's typical ticket size |
| `user_tx_count_5m` / `_1h` / `_24h` | int | Settled+attempted tx count for this user in trailing window |
| `device_tx_count_5m` / `_1h` / `_24h` | int | Same, per device |
| `token_tx_count_24h` | int | Submissions seen for this token payload in 24h |
| `user_tx_value_1h` | num | Total value for user in trailing 1h |
| `device_tx_value_24h` | num | Total value for device in trailing 24h |
| `token_age_seconds` | num | Time since token issuance |
| `time_to_expiry_seconds` | num | Remaining life of token (can be negative if expired) |
| `offline_duration_seconds` | num | Time between transaction and sync attempt |
| `token_reuse_count` | int | Prior uses of this one-time-use token |
| `duplicate_payload_hash_count` | int | Other transactions with identical immutable payload hash |
| `previous_settlement_failed` | flag | 1 if this user/device had a failed settlement recently |
| `merchant_risk_aggregate` | num | Historical dispute/fraud rate of merchant, smoothed, `[0,1]` |
| `hour_of_day` | int | 0–23, transaction local time |
| `day_of_week` | int | 0=Monday … 6=Sunday |
| `history_available` | flag | 1 if aggregate windows had data (else velocity features are untrustworthy) |
| `token_age_known` | flag | 1 if issuance timestamp was parseable |
| `expiry_known` | flag | 1 if expiry timestamp was parseable |

**Null policy:** a feature may be `null` **only** if its paired indicator (e.g. `token_age_known`) is `0`. The trainer imputes consistently; silent zeros are forbidden because they fake certainty.

---

## 3. Response (200 OK)

```json
{
  "correlationId": "01J8ZQ4T9M6K2XWV3P",
  "score": 0.82,
  "confidence": 0.91,
  "fallback": false,
  "modelVersion": "lr-calibrated-v1.0.0",
  "thresholdPolicyVersion": "thresholds-v1",
  "featureSchemaVersion": "v1",
  "topContributions": [
    {
      "reasonCode": "AMOUNT_DEVIATION_HIGH",
      "plainLanguage": "Amount is much higher than this merchant's usual payments",
      "weight": 0.34
    },
    {
      "reasonCode": "TOKEN_NEAR_EXPIRY",
      "plainLanguage": "Token was close to expiry when submitted",
      "weight": 0.21
    },
    {
      "reasonCode": "ODD_HOUR_PATTERN",
      "plainLanguage": "Unusual time of day for this merchant",
      "weight": 0.12
    }
  ]
}
```

### Response field rules

| Field | Type | Rules |
|---|---|---|
| `score` | number | Calibrated probability of fraud. Always `[0.0, 1.0]`, rounded to 4 dp. |
| `confidence` | number | Model self-assessed reliability `[0,1]` (e.g. distance from decision boundary × calibration quality). Low confidence ⇒ caller holds for review even at moderate scores. |
| `fallback` | boolean | `true` only when serving a degraded/default scorer instead of the approved model (must also lower `confidence`). |
| `modelVersion` | string | Immutable artifact identifier, matches registry manifest. |
| `thresholdPolicyVersion` | string | Threshold set this artifact was **validated against** — informational for audit. The caller's active policy version may differ and is stamped by fraud-service on the final decision. |
| `featureSchemaVersion` | string | Echo of request value. |
| `topContributions` | array | Exactly ≤ 3 items, sorted by absolute `weight` descending. Weights are signed contributions to the logit (positive = pushes toward fraud). |

---

## 4. Errors (everything non-200 ⇒ fail-closed upstream)

Body shape for all errors:

```json
{ "error": { "code": "SCHEMA_VERSION_UNSUPPORTED", "message": "model supports: v1, got: v7" } }
```

| HTTP | `code` | When |
|---|---|---|
| 400 | `MALFORMED_REQUEST` | Invalid JSON, missing required keys, wrong types, out-of-range values (e.g. `score`-relevant flags ≠ 0/1) |
| 400 | `UNKNOWN_FEATURE` / `MISSING_FEATURE` | Extra or absent key in `features` |
| 409 | `SCHEMA_VERSION_UNSUPPORTED` | Version mismatch with loaded model |
| 503 | `MODEL_NOT_LOADED` | No approved artifact loaded (service up but unusable) |
| 500 | `INTERNAL_ERROR` | Anything else |

**Contract test requirement:** every row above has an automated test on both sides (client stub in Java, handler test in Python).

---

## 5. Reason-code dictionary (stable identifiers — never rename, only add)

| Code | Plain language (en) |
|---|---|
| `AMOUNT_DEVIATION_HIGH` | Amount is much higher than this merchant's usual payments |
| `VELOCITY_ELEVATED` | Unusually many payments in a short window |
| `VALUE_SPIKE_1H` | Unusually high total value within the last hour |
| `TOKEN_REUSE_DETECTED` | This token shows signs of being used before |
| `DUPLICATE_PAYLOAD` | An identical submission already exists |
| `TOKEN_NEAR_EXPIRY` | Token was close to expiry when submitted |
| `OFFLINE_DURATION_LONG` | Payment stayed offline unusually long before syncing |
| `ODD_HOUR_PATTERN` | Unusual time of day for this merchant |
| `PREVIOUS_SETTLEMENT_FAILED` | Recent settlement failures for this user/device |
| `MERCHANT_RISK_ELEVATED` | Merchant has elevated historical dispute rate |
| `HISTORY_MISSING` | Insufficient history — low signal quality |

Adding a code = additive change (allowed within same schema version, trainer + server ship together). Removing/renaming = new `featureSchemaVersion`.

---

## 6. Operational constraints

- **Latency budget:** p99 < 150 ms excluding network. Enforced client-side via Resilience4j TimeLimiter (~150 ms) + CircuitBreaker.
- **Idempotency:** endpoint is pure/stateless — identical request always yields identical response for the same `modelVersion`.
- **Auth:** internal network only; service-to-service header `X-Internal-Token` (shared secret from env, never logged). Not exposed via api-gateway.
- **Health:** `GET /healthz` → `{ "status": "ok", "modelVersion": "...", "featureSchemaVersions": ["v1"] }`; non-200 if artifact missing.
- **Startup rule:** refuses to boot unless `artifacts/approved/<version>/manifest.json` validates (registry gate, WS3.3).

---

## 7. Example calls

```bash
# happy path
curl -s -X POST http://localhost:8600/v1/score \
  -H 'Content-Type: application/json' \
  -H 'X-Internal-Token: ***' \
  -d '{"correlationId":"c-123","featureSchemaVersion":"v1","features":{"amount":1250.0,"amount_to_token_limit_ratio":0.25,"merchant_amount_deviation":1.84,"user_tx_count_5m":1,"user_tx_count_1h":3,"user_tx_count_24h":11,"device_tx_count_5m":1,"device_tx_count_1h":2,"device_tx_count_24h":9,"token_tx_count_24h":1,"user_tx_value_1h":3400.5,"device_tx_value_24h":12750.0,"token_age_seconds":3600,"time_to_expiry_seconds":18000,"offline_duration_seconds":900,"token_reuse_count":0,"duplicate_payload_hash_count":0,"previous_settlement_failed":0,"merchant_risk_aggregate":0.12,"hour_of_day":14,"day_of_week":2,"history_available":1,"token_age_known":1,"expiry_known":1}}'

# schema mismatch -> 409, caller will HOLD_FOR_REVIEW
curl -s -X POST http://localhost:8600/v1/score \
  -d '{"correlationId":"c-124","featureSchemaVersion":"v9","features":{}}'
```

---

## 8. Versioning rules

1. `featureSchemaVersion` bumps **only** when features are added/removed/renamed or null policy changes. Additive reason codes don't bump it.
2. A new version means: updated `feature_schema.json` in ml/, retrained artifact, new approved registry entry, and fraud-service builder updated in lockstep. Both sides must support overlapping versions during migration; unknown version = 409, never a guess.
3. `modelVersion` bumps freely (retrains); it never changes request/response shape.

*Freeze agreed: Phase 2 kickoff. First consumer implementations may begin against this document.*
