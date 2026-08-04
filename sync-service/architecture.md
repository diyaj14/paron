# sync-service — Architecture

Ingests offline transactions from the mobile device, batches them, and settles them against the ledger. Port: 8083.

## Role in the system

- Receives a batch of offline transactions (receipt, not immediate settlement).
- Publishes each transaction to Kafka (`offline.transactions`) for fraud evaluation.
- Persists each transaction as RECEIVED.
- Runs a Spring Batch **settlement job** every 30s that:
  1. Claims RECEIVED transactions (Redis idempotency lock),
  2. Validates the offline token (token-service),
  3. Runs a fraud check (fraud-service),
  4. Settles via ledger-service and marks the token used,
  5. Persists SETTLED / REJECTED / FAILED outcome.

## Components

| Class / file | Responsibility |
|---|---|
| `controller/SyncController.java` | `POST /api/v1/sync/{userId}` → 202; stamps server-side `userId` onto each txn (never trusts the device). |
| `service/SyncService.java` | `submitTransactions` — MAX_BATCH_SIZE=100, per-txn publish, per-txn failure isolation; device retries only FAILED ones. |
| `kafka/TransactionProducer.java` | Publishes each `OfflineTransactionDto` to `offline.transactions`, key = `deviceTransactionId`. |
| `kafka/TransactionConsumer.java` | `@KafkaListener`; Redis idempotency check → save RECEIVED. |
| `service/IdempotencyService.java` | Redis `setIfAbsent` claim / release. Prevents double-processing. |
| `client/TokenServiceClient.java` | `@Retry(tokenService)` → validate-token + mark-used. |
| `client/LedgerServiceClient.java` | `@CircuitBreaker(ledgerService)` + `settleFallback` → `POST /api/v1/ledger/settle`. |
| `client/FraudCheckClient.java` | `@Retry(fraudService)` → `POST /api/v1/fraud/check`. |
| `batch/SettlementJobConfig.java` | Job/step wiring. `settlementReader` = all RECEIVED rows via `ListItemReader`; chunk size 50; fault-tolerant, skipLimit 50. |
| `batch/SettlementProcessor.java` | Per item: claim → validate token → fraud check → RECEIVED→PROCESSING (or REJECTED/FAILED). Returning `null` filters duplicates. |
| `batch/SettlementWriter.java` | Per chunk: settle via ledger-service, then `markAsUsed` (separate try — never retries settle after success to avoid double-debit), persist SETTLED; on failure reset → RECEIVED for next run. |
| `batch/SettlementContext.java` | Wraps `(OfflineTransaction, reservationId)` through the pipeline. |
| `batch/SettlementJobScheduler.java` | `@Scheduled(fixedRate = 30000)`; launches job with timestamped `JobParameters` (required so each run is a distinct execution). |
| `config/KafkaTopicConfig.java` | Declares `offline.transactions` topic: 3 partitions, 1 replica. Keyed by message key keeps per-user ordering. |
| `model/OfflineTransaction.java` | Entity: unique `device_transaction_id`, indexes on status + user_id. |
| `model/TransactionStatus.java` | Enum: RECEIVED / PROCESSING / SETTLED / REJECTED / FAILED. |
| `repository/OfflineTransactionRepository.java` | `findByStatus`, `findByUserIdOrderByReceivedAtDesc`. |

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/v1/sync/{userId}` | 202. Accepts `SyncRequest` (list of transactions); returns `SyncResponse` (message, acceptedCount, acceptedDeviceTransactionIds) — a receipt, not a settlement. |

## Transaction status lifecycle

```
RECEIVED ──(claimed + valid token + fraud approved)──▶ PROCESSING ──▶ SETTLED
    │
    ├──(token invalid / fraud rejected)──────────────▶ REJECTED
    ├──(processing error)────────────────────────────▶ FAILED   (device retries)
    └──(settle call failed)── reset back to ─────────▶ RECEIVED  (next job run retries)
```

## Key design points

- **Idempotency**: Redis `setIfAbsent` claim prevents double settlement across concurrent job runs / restarts.
- **Batching**: transactions accumulate in RECEIVED; a 30s scheduled job settles in chunks of 50.
- **Crash safety**: a restart never loses data — unprocessed RECEIVED rows are re-picked next run.
- **Money safety**: after `settle` succeeds, `markAsUsed` failure is logged but settle is never retried (would double-debit).
- **Submit-time**: `SyncService` does NOT persist at submit; persistence happens in the Kafka consumer (RECEIVED).

## Run

```
cd E:\payment
. .\set-env.ps1
.\mvnw.cmd spring-boot:run -pl sync-service
```

Port: 8083. Needs Kafka + Redis running (docker-compose).
