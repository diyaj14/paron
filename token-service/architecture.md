# token-service — Architecture

Issues and validates offline payment tokens. Port: 8081.

## Role in the system

- Issues a short-lived **offline token** (HS512, via `JWT_SECRET`) after the user passes a session JWT and enough funds have been reserved in ledger-service.
- Validates offline tokens during sync settlement (returns userId, reservationId, maxAmount, spentAmount).
- Marks tokens as spent (`mark-used`), enabling one-time-use enforcement.

## Components

| Class / file | Responsibility |
|---|---|
| `controller/TokenController.java` | HTTP endpoints (below). |
| `service/TokenService.java` | Core logic, `@Transactional`. Mints/validates tokens, tracks used state. |
| `security/JwtUtil.java` | HS512 JWT create/parse. Claims: `sub`, `tokenId`, `reservationId`, `maxAmount`, `iat`, `exp`. |
| `service/LedgerServiceClient.java` | Outbound client → `POST /api/v1/ledger/reserve` on ledger-service. |
| `application.yml` | Port, DB, Kafka-free (no messaging in/out). |

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| POST | `/gettoken` | 201 on success. Requires valid session (authenticated via gateway). Reserves funds in ledger-service, mints an offline token bound to that reservation. |
| GET | `/validateToken` | Validates an offline token; returns `valid`, `userId`, `reservationId`, `maxAmount`, `spentAmount`, `reason`. |
| POST | `/mark-used` | Marks an offline token as spent (one-time-use enforcement). |

## Token formats

- **Session JWT** — RS256, signed by the shared dev keypair; verified by api-gateway, never touched here.
- **Offline token** — HS512, signed with `JWT_SECRET`. Embedded claims tie the token to a specific ledger reservation and max spend amount.

## Interactions

- → ledger-service `POST /api/v1/ledger/reserve` at issue time.
- ← api-gateway routes `/api/v1/tokens/**` here (60 rpm, Retry 2).

## Run

```
cd E:\payment
. .\set-env.ps1
.\mvnw.cmd spring-boot:run -pl token-service
```

Requires `JWT_SECRET` env var. Port: 8081.
