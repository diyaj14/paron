# Offline Bluetooth PWA implementation plan

## 1. Goal

Deliver a demonstrable progressive web app (PWA) for Paron's existing offline-payment flow. A customer can obtain a spending token while online, make a payment while offline, transfer the payment payload over Bluetooth Low Energy (BLE), and later synchronise the locally queued transaction with the existing sync service.

The product shown in the demo is an **offline payment acceptance demo**, not final-settlement digital cash. A merchant may accept a payment offline subject to an exposure limit; the authoritative validation and settlement occur only after reconnection.

## 2. Scope and constraints

### In scope

- Installable customer PWA with offline app-shell support.
- Token acquisition while online through `POST /api/v1/tokens/gettoken`.
- Local, durable storage of a token and pending transactions.
- Customer payment UI: merchant, amount, review, confirmation, and receipt.
- BLE transfer to a merchant BLE endpoint.
- Merchant receipt acknowledgement and a visible `PENDING_SYNC` state.
- Batch upload to `POST /api/v1/sync/{userId}` after reconnection.
- A simulated BLE transport for development, automated tests, and a hardware-free presentation.

### Browser/device support

- Target Chrome for Android first; Chrome/Edge desktop may be used for development.
- Serve over HTTPS (localhost is acceptable for local development).
- BLE device selection must be initiated from an explicit user action.
- Do not claim iOS Safari or Firefox support for the Bluetooth payment path. They should show a clear unsupported-browser message and can still use the simulated transport.
- A device in airplane mode can participate only when its user manually re-enables Bluetooth. The PWA cannot enable a radio itself.

### Important BLE limitation

Web Bluetooth lets a web page act as a BLE **central/client** that connects to a BLE peripheral. Two browser PWAs cannot reliably be the customer and merchant endpoints directly. The demo therefore needs one of these merchant endpoints:

1. a small BLE peripheral (ESP32/nRF52/Raspberry Pi with BLE GATT service), or
2. a native Android merchant companion that advertises the same GATT service.

The PWA's simulated transport must follow the same payload protocol so the UI is fully demoable without that endpoint.

## 3. Proposed architecture

```text
             online only                         offline-capable

Customer PWA ── token-service                    Customer PWA
     │              │                                  │
     │              └── signed spending token          │ BLE GATT write
     │                                                  ▼
     └── IndexedDB: token + transaction queue    Merchant BLE endpoint
                                                        │
                                                        └── receipt acknowledgement

when connectivity returns:
Customer PWA ── POST /api/v1/sync/{userId} ── sync-service ── Kafka/settlement
```

Create the client as a separate `pwa/` application. Keep browser code independent of Spring services and route all online calls through the API gateway in deployment.

Suggested modules:

```text
pwa/
├── src/
│   ├── app/                 # screens and state
│   ├── api/                 # token and sync HTTP clients
│   ├── storage/             # IndexedDB repositories
│   ├── payments/            # envelope, validation, receipt logic
│   ├── transport/           # Transport interface, BLE and simulator
│   └── crypto/              # public-key verification and hashing
├── public/
│   ├── manifest.webmanifest
│   ├── service-worker.js
│   └── icons/
└── tests/
```

## 4. Data contracts

### Existing server contracts to consume

| Action | Existing endpoint | Required client data |
| --- | --- | --- |
| Obtain token | `POST /api/v1/tokens/gettoken` | `userId`, `amount`, `expiryHours` |
| Submit queue | `POST /api/v1/sync/{userId}` | `{ transactions: OfflineTransactionDto[] }` |
| View result | `GET /api/v1/sync/{userId}` | authenticated caller identity |

`OfflineTransactionDto` already contains `deviceTransactionId`, `offlineToken`, `amount`, `merchantId`, and `transactedAt`. The PWA must create a UUID client-side and generate it once only; retries reuse the same ID.

### BLE payment envelope (v1)

The customer sends a UTF-8 JSON envelope in framed chunks, no more than the negotiated characteristic payload size:

```json
{
  "protocolVersion": "paron-ble-v1",
  "type": "PAYMENT_OFFER",
  "transaction": {
    "deviceTransactionId": "uuid",
    "offlineToken": "signed-token",
    "amount": "125.00",
    "merchantId": "merchant-demo-001",
    "transactedAt": "2026-08-26T12:30:00"
  },
  "payloadHash": "sha256-base64url"
}
```

The merchant replies with `PAYMENT_ACCEPTED` or `PAYMENT_REJECTED`, its generated `merchantReceiptId`, the copied transaction ID, and its receipt timestamp. Receipt acknowledgement means the merchant stored the offer; it is not settlement approval.

BLE service definition for the demonstration:

| Item | Value |
| --- | --- |
| Service UUID | project-owned 128-bit UUID, fixed in both clients |
| Offer characteristic | write / write-without-response |
| Receipt characteristic | notify |
| Max transfer | chunked, sequence-numbered frames with length and SHA-256 payload hash |
| Timeout | 20 seconds, then keep transaction locally as `CREATED` |

## 5. Local data and state machine

Use IndexedDB rather than `localStorage`. Persist mutations before UI success feedback, and encrypting at-rest data should be treated as a follow-up platform-security feature rather than a claim of browser secure storage.

Stores:

| Store | Key | Contents |
| --- | --- | --- |
| `walletTokens` | `tokenId` | raw active token, max amount, expiry, status |
| `transactions` | `deviceTransactionId` | complete sync DTO, hash, receipt, state, retry metadata |
| `settings` | `key` | selected user and demo transport setting |

```text
CREATED → SENDING → ACCEPTED_OFFLINE → QUEUED_FOR_SYNC → SUBMITTED → SETTLED
             │              │                                  └── REJECTED / HELD_FOR_REVIEW / FAILED
             └── SEND_FAILED ┘
```

Do not silently delete a failed or rejected transaction. Preserve its immutable original payload and show the settlement result returned by the server.

## 6. Delivery phases

### Phase 0 — contracts and security decisions

1. Confirm the public API gateway base URL, authentication method, CORS policy, and how `userId` is derived.
2. Freeze `paron-ble-v1` schema, GATT UUIDs, frame size, timeout, and merchant acknowledgement semantics.
3. Publish a public verification key and switch spend-token signing from HS512 to an asymmetric algorithm (Ed25519 or ES256) before any offline merchant verification.
4. Define a demo exposure cap and a maximum amount per offline acceptance.

**Acceptance:** a versioned protocol document exists and a merchant can verify a token without access to the server signing secret.

### Phase 1 — PWA shell and online wallet

1. Scaffold `pwa/` with a production build, web manifest, icons, and service worker.
2. Cache the app shell and static assets; provide an offline fallback route.
3. Build wallet setup and online token-request screen.
4. Store returned `TokenResponse` in IndexedDB and display only a shortened token identifier.
5. Add online/offline status plus token expiry and remaining demo limit display.

**Acceptance:** after first load and installation, the wallet opens and its stored data remains available with networking disabled.

### Phase 2 — offline transaction creation

1. Build merchant selection and amount entry screens with local amount/expiry checks.
2. Generate `deviceTransactionId` with `crypto.randomUUID()`.
3. Produce a canonical transaction payload and SHA-256 hash using Web Crypto.
4. Persist a `CREATED` transaction before attempting a transfer.
5. Build a receipt and transaction-history screen.

**Acceptance:** turning off networking after token acquisition still permits creating a persistent pending payment.

### Phase 3 — transport abstraction and simulator

1. Define `PaymentTransport.send(envelope): Promise<MerchantReceipt>`.
2. Implement `SimulatedTransport` with controllable success, timeout, rejection, and duplicate acknowledgement cases.
3. Add a visible “Demo simulator” mode; do not silently substitute it for Bluetooth.
4. Add unit tests for framing, hash validation, and state transitions.

**Acceptance:** the complete payment story can be demonstrated in a browser with no BLE hardware.

### Phase 4 — Web Bluetooth transport

1. Feature-detect `navigator.bluetooth` and display compatibility guidance when absent.
2. From a Connect button, request a device filtered by the Paron GATT service UUID.
3. Connect to the GATT server, discover characteristics, subscribe to receipt notifications, and write frames sequentially.
4. Verify receipt transaction ID and payload hash before changing state to `ACCEPTED_OFFLINE`.
5. Handle disconnects, timeouts, cancellation, and duplicate notifications idempotently.

**Acceptance:** on Android Chrome, a customer PWA sends an offer to the BLE endpoint and shows a verified merchant receipt in airplane mode with Bluetooth enabled.

### Phase 5 — reconnect and settlement UI

1. Detect restored connectivity via online events plus a lightweight API health check.
2. Upload `QUEUED_FOR_SYNC` transactions in batches through sync-service.
3. Mark submission only after the API returns `202 Accepted`; retain the transaction for later outcome polling.
4. Refresh statuses and map server states including `SETTLED`, `REJECTED`, `FAILED`, and `HELD_FOR_REVIEW` to clear customer wording.
5. Add manual “Sync now” control alongside automatic best-effort sync.

**Acceptance:** a BLE-accepted payment remains queued offline, submits exactly once when online, and displays its eventual settlement state.

### Phase 6 — demo hardening

1. Test reloads during every transaction state and confirm durable recovery.
2. Test duplicate receipt, duplicated sync request, expired token, amount over limit, BLE disconnection, and server rejection.
3. Add in-app presentation instructions: obtain token online, enable airplane mode and Bluetooth, connect, pay, then reconnect and sync.
4. Host the demo via HTTPS and test installation on the demo Android handset.

**Acceptance:** the documented 3–5 minute demo runs repeatedly without manual database repair.

## 7. Security requirements and non-goals

- Never log or display full raw JWTs in UI, analytics, or error reporting.
- Do not expose the existing HS512 secret to merchant apps or BLE devices. Symmetric signing is incompatible with offline third-party verification.
- Bind the offer to `merchantId`, amount, timestamp, transaction ID, and payload hash.
- Limit token validity, amount, merchant exposure, and customer/device velocity. These limit loss; they do not prevent an offline double spend conclusively.
- Treat browser IndexedDB as durable storage, not a hardware-backed secure element. This is sufficient for a controlled demo, not production money.
- Do not label `ACCEPTED_OFFLINE` as settled or final.

## 8. Test matrix

| Scenario | Expected outcome |
| --- | --- |
| First install, then offline reload | app shell and saved wallet load |
| Airplane mode with Bluetooth enabled | BLE payment can complete |
| No Web Bluetooth support | guided fallback to simulator |
| BLE transfer interruption | immutable `SEND_FAILED`/`CREATED` transaction remains for retry |
| Same acknowledgement twice | one receipt and one local transaction state change |
| Repeat sync request | existing server idempotency protects one settlement |
| Token expired or amount too high | payment blocked locally and server rejects if submitted |
| Sync returns held/rejected | transaction remains visible with unambiguous non-settlement state |

## 9. Recommended first milestone

Implement Phases 1–3 first. It produces an installable, offline-capable PWA that demonstrates token acquisition, local transaction creation, receipt flow, and later sync using the simulator. Then add the BLE peripheral and Phase 4 without changing screens or business logic.
