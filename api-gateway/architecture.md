# api-gateway — Architecture

Spring Cloud Gateway-based API gateway. Single entry point for all mobile/app traffic into the payment platform.

## Role in the system

- Sits in front of token-service (8081), ledger-service (8082), sync-service (8083), and fraud-service (8084).
- Performs authentication (`X-User-Id` / `X-User-Roles` header injection via RS256 session JWT) so downstream services never need to parse tokens.
- Enforces per-user rate limiting.
- Is the only service that terminates the session JWT; downstream services trust the injected headers.

## Components

| Class / file | Responsibility |
|---|---|
| `filter/AuthFilter.java` | Global pre-filter: verifies RS256 `Authorization: Bearer` JWT against `JWT_PUBLIC_KEY`, injects `X-User-Id` and `X-User-Roles` request headers. Allowlists `/actuator/**` and `/ping` (no auth required). |
| `filter/LoggingFilter.java` | Global request/response logging (method, path, status, timing). |
| `config/GatewayConfig.java` | Declares the RSA public key bean used by `AuthFilter` (parsed from `JWT_PUBLIC_KEY`). |
| `config/RateLimitConfig.java` | RequestRateLimiter config. Key = `user:{X-User-Id}` when present, else `ip:{remote-addr}`. |
| `application.yml` | Routes + filters + rate limits. |

## Routes

| Path | Target service | Rate limit | Retry / fallback |
|---|---|---|---|
| `/api/v1/tokens/**` → `lb://token-service` | token-service (8081) | 60 rpm | Retry 2 |
| `/api/v1/ledger/**` → `lb://ledger-service` | ledger-service (8082) | 100 rpm | — |
| `/api/v1/sync/**` → `lb://sync-service` | sync-service (8083) | 30 rpm | — |
| `/api/v1/fraud/**` → `lb://fraud-service` | fraud-service (8084) | — | — |
| `/actuator/**`, `/ping` | local | — | Auth allowlisted |

## Security model

- Session tokens are signed RS256 with a shared RSA keypair (private key in `dev-keys/private.pem`, public key injected as `JWT_PUBLIC_KEY`).
- The gateway validates the signature and expiry, extracts `sub` (userId) and `roles`, then forwards `X-User-Id` / `X-User-Roles`.
- No downstream service parses JWTs; they rely on the injected headers.

## Run

```
cd E:\payment
. .\set-env.ps1
.\mvnw.cmd spring-boot:run -pl api-gateway
```

Requires `JWT_PUBLIC_KEY` env var in addition to the common env setup. Port: 8080.
