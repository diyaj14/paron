# fraud-service — Architecture

Scores offline transactions for fraud risk and persists alerts. Port: 8084.

## Role in the system

- Consumes every message on `offline.transactions` (Kafka) independently of sync-service (separate consumer group).
- Evaluates each transaction against pluggable fraud rules (Redis-backed).
- Rejects/scores transactions (response consumed synchronously by sync-service's `SettlementProcessor` via `POST /api/v1/fraud/check`).
- Persists a `FraudAlert` for any transaction that fails the score threshold (status PENDING) for human review.

## Components

| Class / file | Responsibility |
|---|---|
| `kafka/TransactionFraudConsumer.java` | `@KafkaListener("offline.transactions")`; saves a `FraudAlert` only when the evaluation is **not approved**. |
| `service/FraudScoringService.java` | Injects `List<FraudRule>`, sums rule scores, clamps to 1.0, `approved = totalScore < scoreThreshold` (default 0.7), maps risk level. `transactionId = event.getDeviceTransactionId()`. |
| `service/FraudAlertService.java` | Query alerts by userId / all; `reviewAlert` sets status + reviewedAt + reviewerNotes; throws `FraudException("ALERT_NOT_FOUND")`. |
| `controller/FraudController.java` | Endpoints under `/api/v1/fraud`. |
| `rules/FraudRule.java` | Interface: `evaluate(TransactionEvent)`, `name()`. |
| `rules/VelocityRule.java` | Redis `velocity:{userId}` counter; window 60s (default), max 5 → `VELOCITY_BREACH`. |
| `rules/TokenReuseRule.java` | Redis `token:{token}` stores first device, TTL 24h → flags different device. |
| `rules/TimePatternRule.java` | Redis ZSET hourly histogram + total count; min 10 txns; flags unusual hour. |
| `rules/TemporalImpossibilityRule.java` | Compares `transactedAt` vs `tokenExpiryTime`; flags transaction after expiry. |
| `rules/AmountAnomalyRule.java` | Redis running total/count; average × 3.0 multiplier, min 5 txns. |
| `model/FraudAlert.java` | `fraud_alerts` table: UUID id, transactionId, userId, amount, riskScore, riskLevel, triggeredRules, status, createdAt, reviewedAt, reviewerNotes. |
| `model/RiskLevel.java` | Enum: LOW / MEDIUM / HIGH / CRITICAL. |
| `repository/FraudAlertRepository.java` | `findById` + derived ordering queries. |
| `exception/FraudException.java` | errorCode + message. |
| `dto/*.java` | `TransactionEvent` (userId, deviceTransactionId, offlineToken, amount required; deviceId/merchantId/transactedAt/tokenExpiryTime optional), `FraudCheckResponse`, `FraudAlertResponse`, `ReviewAlertRequest`. |

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| POST | `/check` | Synchronous fraud decision: `APPROVE`/`REJECT` + score + reason (first triggered rule, or `NONE`). |
| GET | `/alerts` | List alerts; optional `?userId=` filter. |
| PUT | `/alerts/{id}/review` | Review an alert (set status, reviewer notes). |
| POST | `/ping` | Liveness: returns `fraud check is active`. |

## Config (application.yml)

- Consumer only. Group `fraud-service-group`, `auto-offset-reset: earliest`, JsonDeserializer with trusted packages `org.paron.fraudservice.dto,org.paron.syncservice.dto`.
- Topic hardcoded literal `offline.transactions`.
- Rule tuning: `fraud.rules.*` (velocity window/count, amount-anomaly multiplier/min-transactions, time-pattern min-transactions/hour-range, token-reuse TTL hours) and `fraud.score-threshold: 0.7`.

## Run

```
cd E:\payment
. .\set-env.ps1
.\mvnw.cmd spring-boot:run -pl fraud-service
```

Port: 8084. Needs Kafka + Redis running (docker-compose), and Postgres (Supabase) for `fraud_alerts`.
