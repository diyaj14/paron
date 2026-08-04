# ledger-service — Architecture

Authoritative ledger / accounting service. The only place account balances change. Port: 8082.

## Role in the system

- Maintains user accounts and fund **reservations**.
- **Reserve** is called by token-service at offline-token issue time (locks funds for a future offline spend).
- **Settle** is called by sync-service once an offline transaction is approved (converts reservation → actual debit).
- **Release** frees a reservation (e.g., token expired/never spent).
- **Balance** queries feed the gateway's balance route.

## Components

| Class / file | Responsibility |
|---|---|
| `controller/LedgerController.java` | Endpoints under `/api/v1/ledger`. |
| `controller/AccountController.java` | Dev utility endpoints under `/api/v1/ledger/accounts`. |
| `service/ReservationService.java` | `@Transactional` core logic — the "accountant". Sole place balances change. |
| `model/Account.java` | Account entity: `totalBalance`, `availableBalance`; unique index `idx_account_user_id`. |
| `model/Reservation.java` | Reservation entity: `reservedAmount`, `settledAmount`, status ACTIVE → RELEASED/SETTLED; indexes `idx_reservation_user_id`, `idx_reservation_status`. |
| `model/ReservationStatus.java` | Enum: ACTIVE / RELEASED / SETTLED. |
| `repository/AccountRepository.java` | `findByUserIdForUpdate` — PESSIMISTIC_WRITE lock via explicit JPQL (derived queries can't carry `@Lock`). |
| `repository/ReservationRepository.java` | Reservation lookups. |
| `dto/*.java` | Request/response DTOs. `ReserveResponse` JSON key is `reservationId` (must match for downstream clients). |

## Endpoints

### LedgerController — `/api/v1/ledger`

| Method | Path | Purpose |
|---|---|---|
| POST | `/reserve` | 201. Reserves funds. Returns `ReserveResponse` containing `reservationId`. |
| POST | `/release` | Releases a reservation back to available balance. |
| POST | `/settle` | Settles a reservation (moves reserved → settled). Request: `SettleRequest` (reservationId + amount). |
| GET | `/balance/{userId}` | Returns `BalanceResponse` for a user. |

### AccountController — `/api/v1/ledger/accounts`

| Method | Path | Purpose |
|---|---|---|
| POST | `/create-test-account` | 201. Dev utility to seed a test account with funds. |

## Concurrency model

- All balance mutations happen inside `ReservationService` (`@Transactional`).
- `findByUserIdForUpdate` (PESSIMISTIC_WRITE) serializes concurrent operations on the same account row, preventing double-spend / lost-update corruption.

## Interactions

- ← token-service: `POST /api/v1/ledger/reserve`.
- ← sync-service: `POST /api/v1/ledger/settle`.
- ← api-gateway routes `/api/v1/ledger/**` here (100 rpm).

## Run

```
cd E:\payment
. .\set-env.ps1
.\mvnw.cmd spring-boot:run -pl ledger-service
```

Port: 8082.
