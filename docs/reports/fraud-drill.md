# Fraud Drill Report

**Mode:** offline model artifact
**Scenarios:** 8  |  **Passed:** 8  |  **Failed:** 0
**Elapsed:** 1496 ms

| Scenario | Expected | Actual | Score | Result | Notes |
|---|---|---|---|---|---|
| NORMAL_PAYMENT | APPROVE | APPROVE | 0.1087 | PASS | Genuine small payment; nothing suspicious. |
| REPLAYED_TOKEN | REJECT | REJECT | 1.0 | PASS | Same offline token spent again from another device — the signature & reuse rules should refuse it outright. |
| OVERSIZED_AMOUNT | REJECT | REJECT | 1.0 | PASS | Amount at 94% of the reserved cap with huge merchant deviation — a tampered receipt / overspend attempt. |
| TAMPERED_RECEIPT | REJECT | REJECT | 1.0 | PASS | Amount bumped well above the merchant normal after signing; crypto re-verification catches the forged canonical string too. |
| UNREGISTERED_DEVICE | HOLD | HOLD | 0.1082 | PASS | New device with zero history: fraud-service's low-signal guard (FraudScoringService) downgrades any would-be APPROVE to HOLD_FOR_REVIEW — a brand-new device never auto-approves. |
| DUPLICATE_PAYLOAD | REJECT | REJECT | 1.0 | PASS | Identical payload seen before (replayed submission) — idempotency and duplicate rules refuse it. |
| VELOCITY_SPIKE | REJECT | REJECT | 1.0 | PASS | Dozens of payments in a few minutes — classic mule velocity. |
| LONG_OFFLINE_SYNC | REJECT | REJECT | 1.0 | PASS | Payment stayed offline past the token's working life and surfaced near expiry. The model scores this strongly (>= 0.75) and rejects — stricter than a mere hold, which is the safe call for long-offline. |

## Decision Policy (mirrors fraud-service)

- **any rule hit** -> REJECT (hard rule gate)
- model confidence < 0.6 -> HOLD (low signal / model fallback)
- model score < 0.35 -> APPROVE
- model score >= 0.75 -> REJECT
- otherwise -> HOLD (review band)

Every scenario is a deterministic replay — the same input always yields the same three-state decision, and the loud attacks never slip through the rules while subtle ones are held for review, never auto-approved.
