# Paron PayPal AI Hackathon demo

Paron is a **PayPal Sandbox-funded offline wallet**, not UPI or a bank-transfer product. The supported demo flow is:

1. Authenticate through the API gateway and fund the user's Paron wallet with PayPal Sandbox.
2. The funding service verifies the PayPal capture and credits the ledger once using the funding-attempt ID.
3. Request a bounded offline token (use Rs. 500 for the demo).
4. Make a signed Rs. 150 offline receipt with the customer PWA and merchant simulator.
5. Reconnect and sync. The server verifies the receipt, evaluates risk, and applies deterministic reservation limits before settlement.
6. Submit a competing receipt with a new device transaction ID. The first reservation settlement that commits wins; any claim above the remaining allowance is rejected or held with evidence.

## Truthful UI state

The PWA must show a local merchant acknowledgement as `ACCEPTED_OFFLINE`, not settled. Only a returned sync status of `SETTLED` is final. `REJECTED`, `FAILED`, and `HELD_FOR_REVIEW` remain visible in transaction history.

## Required configuration

Set `PAYPAL_CLIENT_ID`, `PAYPAL_CLIENT_SECRET`, `PAYPAL_BASE_URL`, database variables, Kafka, Redis, `OFFLINE_TOKEN_SIGNING_SECRET`, and the gateway's session public key. The browser sends an existing Bearer session token to the gateway. There is intentionally no browser-created user identity or OTP fallback.

## Deliberate exclusions

UPI, bank transfer, a phone OTP provider, and real-world offline-payment authorization are not implemented. AI can identify and explain suspicious receipts after reconnection; it cannot retroactively prevent a merchant from handing over goods while offline.
