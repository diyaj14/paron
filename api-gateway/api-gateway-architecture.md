# API Gateway Architecture

## What It Does

The API gateway is the single front door to the entire system. All requests from the mobile app or merchant POS come here first. The gateway routes them to the right service, enforces rate limits, and handles authentication.

---

## Module Layout

```
api-gateway/
├── config/
│   ├── GatewayConfig.java       # Route definitions (Spring Cloud Gateway)
│   └── RateLimitConfig.java     # Rate limiting configuration
├── filter/
│   ├── AuthFilter.java          # Validates user session JWT
│   └── LoggingFilter.java       # Logs every request with timing
├── resources/
│   └── application.yml          # Routes and gateway config
├── src/main/java/.../            # Application entry point
└── pom.xml
```

---

## Request Flow

```
Mobile App / POS
      |
      |  Authorization: Bearer <user-session-jwt>
      v
┌──────────────────────────────────────────────────┐
│              API Gateway (port 8080)              │
│                                                  │
│  1. AuthFilter                                    │
│     └─ validates RS256 user session JWT           │
│     └─ extracts userId + roles from claims        │
│     └─ sets headers: X-User-Id, X-User-Roles      │
│                                                  │
│  2. LoggingFilter                                 │
│     └─ logs method, path, status, duration        │
│                                                  │
│  3. Gateway router                                │
│     └─ forwards to backend service                │
└──────────────────────────────────────────────────┘
      |
      |  X-User-Id: user-123
      |  X-User-Roles: USER
      v
┌──────────────────────────────────────────────────┐
│          Backend Service (trusts gateway)          │
│                                                  │
│  Reads X-User-Id / X-User-Roles headers           │
│  (only accepts traffic from gateway network)      │
└──────────────────────────────────────────────────┘
```

---

## Two JWT Types

The system uses two completely separate JWTs for different purposes:

### 1. User Session JWT (verified by gateway)

| Property | Value |
|---|---|
| **Purpose** | Authenticate the user to the system |
| **Algorithm** | RS256 (asymmetric — private key signs, public key verifies) |
| **Where verified** | `AuthFilter` in the gateway |
| **Where issued** | A separate auth service or the gateway itself (out of scope for now) |
| **Claims** | `sub`=userId, `role`=USER\|ADMIN, `exp`, `iat` |
| **Lifetime** | Short (e.g. 15–60 minutes) |
| **Key management** | Gateway holds RS256 public key; private key held by issuing service |

### 2. Offline Spending Token JWT (verified by token-service)

| Property | Value |
|---|---|
| **Purpose** | Offline value transfer (user pays merchant without internet) |
| **Algorithm** | HS512 (symmetric — existing implementation in `token-service`) |
| **Where verified** | `token-service` (`JwtUtil.validateAndExtractClaims`) |
| **Where issued** | `token-service` (`POST /api/v1/tokens/gettoken`) |
| **Claims** | `sub`=userId, `tokenId`, `reservationId`, `maxAmount`, `iat`, `exp` |
| **Lifetime** | Configurable (default 6 hours) |

---

## Endpoint Classification

Each request type is handled differently by the gateway:

| Category | Endpoints | Gateway Action |
|---|---|---|
| **User-facing** (needs valid user JWT) | `GET /api/v1/tokens/history/{userId}`<br>`GET /api/v1/ledger/balance/{userId}`<br>`POST /api/v1/sync/{userId}`<br>`GET /api/v1/sync/{userId}`<br>`GET /api/v1/fraud/alerts`<br>`PUT /api/v1/fraud/alerts/{id}/review` | Validate JWT, inject user headers, proxy to service |
| **Internal-only** (service-to-service, NOT exposed through gateway) | `POST /api/v1/tokens/gettoken`<br>`POST /api/v1/tokens/validateToken`<br>`POST /api/v1/tokens/mark-used`<br>`POST /api/v1/ledger/reserve`<br>`POST /api/v1/ledger/release`<br>`POST /api/v1/ledger/settle`<br>`POST /api/v1/fraud/check` | **Blocked at gateway** (no route defined). Services call each other directly via HTTP. |
| **Health** | `/actuator/health` | Permit all (or restrict to monitoring infra) |

---

## Route Definitions (GatewayConfig)

```yaml
spring:
  cloud:
    gateway:
      routes:

        # ── User-facing routes (auth required) ──

        - id: token-history
          uri: http://token-service:8081
          predicates:
            - Path=/api/v1/tokens/history/**
          filters:
            - name: Retry
              args:
                retries: 2
                statuses: BAD_GATEWAY, SERVICE_UNAVAILABLE

        - id: ledger-balance
          uri: http://ledger-service:8082
          predicates:
            - Path=/api/v1/ledger/balance/**

        - id: sync
          uri: http://sync-service:8083
          predicates:
            - Path=/api/v1/sync/**

        - id: fraud-alerts
          uri: http://fraud-service:8084
          predicates:
            - Path=/api/v1/fraud/alerts/**

        # ── Internal-only routes (NOT defined — blocked by absence) ──
        # POST /api/v1/tokens/gettoken, /validateToken, /mark-used
        # POST /api/v1/ledger/reserve, /release, /settle
        # POST /api/v1/fraud/check
        # These are NOT proxied. Services call each other directly.

        # ── Actuator ──

        - id: actuator-token
          uri: http://token-service:8081
          predicates:
            - Path=/actuator/token-service/**
          filters:
            - StripPrefix=1

        - id: actuator-ledger
          uri: http://ledger-service:8082
          predicates:
            - Path=/actuator/ledger-service/**
          filters:
            - StripPrefix=1

        - id: actuator-sync
          uri: http://sync-service:8083
          predicates:
            - Path=/actuator/sync-service/**
          filters:
            - StripPrefix=1

        - id: actuator-fraud
          uri: http://fraud-service:8084
          predicates:
            - Path=/actuator/fraud-service/**
          filters:
            - StripPrefix=1
```

**Why internal endpoints are not routed:** Services that should only be called by other services (`/reserve`, `/release`, `/settle`, `/check`, `/gettoken`, `/validateToken`, `/mark-used`) are simply not included in the route table. If a request for one of these reaches the gateway, Spring Cloud Gateway returns 404. This prevents external clients from calling money-moving or internal endpoints directly.

---

## AuthFilter

The `AuthFilter` is a `GatewayFilter` (or `GlobalFilter`) that intercepts every request and:

1. Extracts the `Authorization: Bearer <token>` header
2. Validates the RS256 JWT signature against the public key
3. Checks expiry
4. Extracts `sub` (userId) and `role` from claims
5. Sets `X-User-Id` and `X-User-Roles` headers on the proxied request
6. Returns **401 Unauthorized** if the JWT is missing, expired, or invalid

**Endpoints that skip auth:** Public endpoints (`/actuator/health`, `/api/v1/tokens/ping`, etc.) are excluded from the filter via a path allowlist.

```java
// Pseudocode
public class AuthFilter implements GlobalFilter {

    private final PublicKey publicKey;  // RS256 public key

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (isPublicPath(path)) {
            return chain.filter(exchange);  // skip auth
        }

        String authHeader = exchange.getRequest().getHeaders()
                .getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = validateJwt(token);
            exchange = exchange.mutate()
                .request(r -> r
                    .header("X-User-Id", claims.getSubject())
                    .header("X-User-Roles", claims.get("role", String.class)))
                .build();
        } catch (JwtException e) {
            return unauthorized(exchange, "Invalid or expired token");
        }

        return chain.filter(exchange);
    }
}
```

---

## User Context Propagation

Backend services read the user identity from headers rather than from request parameters or their own auth logic. This is the standard microservice pattern:

| Header | Set by | Read by | Purpose |
|---|---|---|---|
| `X-User-Id` | `AuthFilter` | Backend controllers | Identifies the authenticated user |
| `X-User-Roles` | `AuthFilter` | Backend `SecurityConfig` / `@PreAuthorize` | Role-based authorization |

Backend services extract these headers via a `@RequestHeader` or a `UserContext` filter. This replaces request parameters like `?userId=` which are vulnerable to IDOR.

---

## RateLimitConfig

Rate limiting uses the token-bucket algorithm backed by Redis:

| Endpoint Group | Limit | Window |
|---|---|---|
| `/api/v1/fraud/check` (internal, but through gateway) | N/A (not routed) | — |
| `/api/v1/ledger/balance/*` | 100 req/min | 1 minute |
| `/api/v1/sync/*` | 30 req/min | 1 minute |
| `/api/v1/tokens/history/*` | 60 req/min | 1 minute |
| `/actuator/**` | 20 req/min | 1 minute |

---

## CORS

The gateway handles CORS globally since it is the single origin that the mobile app/POS talks to:

- **Allowed origins:** Configurable (development: `*`, production: specific mobile app origin)
- **Allowed methods:** `GET`, `POST`, `PUT`, `DELETE`
- **Allowed headers:** `Authorization`, `Content-Type`, `X-Requested-With`
- **Exposed headers:** `X-User-Id` (if needed by client)

---

## Dependencies

Add to `api-gateway/pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-gateway</artifactId>
</dependency>
<!-- JWT validation -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <scope>runtime</scope>
</dependency>
```

No `spring-boot-starter-web` when using Spring Cloud Gateway (it uses WebFlux, not Servlet).

---

## Summary

| Concern | How the gateway handles it |
|---|---|
| Authentication | Validates RS256 user session JWT in `AuthFilter` |
| Authorization | Injects `X-User-Roles` header for backend services |
| Routing | Spring Cloud Gateway route table in `application.yml` |
| Internal endpoints | Not routed — blocked by absence (returns 404) |
| Rate limiting | Token-bucket per endpoint group |
| Logging | `LoggingFilter` records method, path, status, duration |
| CORS | Centralized at gateway level |
| User identity propagation | `X-User-Id` / `X-User-Roles` headers set by `AuthFilter` |
