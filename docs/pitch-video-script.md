# Paron — 5-Minute Pitch Video Script

**Track:** Razorpay AI Buildathon — AI Risk Manager
**Submission:** this script + the 5-min video + the repo (which actually runs)

---

## 0. Cold open (0:00–0:15)

> "Your customer's phone is in airplane mode. They still need to pay. You accept the payment.
> You just took on the risk of a settlement that happens hours later — and by then, the receipt
> might not even be real."

*(Screen: a phone in airplane mode passes ₹150 to a merchant phone over Bluetooth; the merchant's balance ticks up while nothing has settled yet.)*

---

## 1. Problem (0:15–0:45)

> "Delayed and offline settlement has an uncomfortable gap: between **offline acceptance** and
> **online settlement**, a transaction is just a blob of bytes. It can be replayed, forged,
> overspent, or pushed through from a device that has no business spending that token.
> The merchant is the one exposed — and a payments platform inherits that exposure.
>
> Track Two asked for a risk manager with *measured precision and recall*. So here's the plan:
> make the money **bounded by construction**, and make the settlement decision **defence-only AI** —
> it can only ever say three things."

*(Big on-screen words: APPROVE · HOLD · REJECT.)*

---

## 2. System walkthrough (0:45–2:15) — "signed receipts + hard money guards"

> "Three phases. **Phase one, online:** the customer requests an offline limit; the platform
> *reserves* that money in a ledger and issues a capped, expiring JWT spending token. There is
> no new money at the point of sale — it's already been set aside. *(walk through the reserve)*

> **Phase two, offline:** the customer pays the merchant over Bluetooth in airplane mode.
> Here's the heart of it — every single payment is **signed on-device** with an ECDSA P-256 key,
> over a canonical string: transaction id, token, merchant, amount, timestamp, device id.
> The merchant POS refuses anything above the displayed cap. The signing key never leaves the phone.

> **Phase three, reconnect:** the device pushes its queue of receipts. Before anything settles,
> sync-service re-verifies **every signature** — the amount the merchant saw is in the bytes that
> were signed — then token spend-state is checked cumulatively, and the risk manager looks at it.
> Three outcomes only:
> - a **hard rule hit** — replayed token, duplicate payload, velocity breach, amount anomaly,
>   temporal impossibility — is a hard reject. No model score overrides a rule.
> - if no rule fired, the **calibrated model** scores it. Below threshold: approve. Way above: reject.
>   In between, or if confidence is low, or if the model is down: **hold for review**, no money moves.
> - and here's the guard that matters: a brand-new device with zero history **never auto-approves**."

> "And when two receipts contradict each other — the merchant says ₹200, the customer's device says
> it already paid — there's an **AI Judge**: it re-verifies both signatures, checks the authoritative
> token spend state, and hands down a deterministic, auditable verdict: forged receipt, double spend,
> single payment, multiple legitimate. The LLM, if present, only writes the summary. It never decides."

---

## 3. Live batch demo (2:15–3:45)

> "Let's prove the repo actually runs — a real batch through the real stack."

*(Live demo, captured from the running services; no slides. Suggested script while it runs:)*

> "Here's a merchant's collected-balance screen climbing as receipts land. *(merchant ledger card)*
> Now we throw three **forged receipts** at the risk gate:
> - a **replayed token** — same token, second device → rejected by the reuse rule;
> - a **tampered amount** — ₹150 signed, ₹1,500 submitted → signature re-verification fails it;
> - and an **unregistered device** pushing a clean-looking ₹50 → **held**, not approved,
>   because zero-history devices go to review, thanks to the low-signal guard.
>
> Watch the fraud drill replay all eight scenarios and come back **8/8 green** —
> the same input always yields the same decision."

*(Show `python fraud_drill.py` output: 8/8 PASSED.)*

---

## 4. Metrics (3:45–4:45) — "measured, not vibes"

> "Track Two demanded measured numbers, so here they are — computed on a **held-out synthetic set**,
> regenerated from a one-command pipeline in the repo:

> *(show the table)*
>
> | Metric | AI model | Rules baseline | Delta |
> |---|---|---|---|
> | Precision | 0.978 | 0.602 | +0.38 |
> | Recall | 0.568 | 0.508 | +0.06 |
> | F1 | 0.718 | 0.551 | +0.17 |
> | PR-AUC | 0.709 | 0.445 | **+0.26** |
> | FP cost at ₹500/FP | ₹2,500 | ₹67,000 | **−₹64,500** |

> "But the real number is the **production 3-state decision**: on the same held-out set,
> the full rules + model + low-signal-guard path keeps **64% of fraud from ever being
> auto-approved** — they're rejected or held. 81% of legitimate payments still flow
> straight through to APPROVE. That's the risk manager working the way a risk manager
> actually works: the loud attacks get hard-rejected by rules, the subtle ones get held
> for a human, and the AI never auto-approves something it has no history to justify.
> And the whole report is checked into `docs/reports/` so you can reproduce it yourself."

> *(show the 3-state table: fraud caught 0.642 / legit approved 0.809 / false-hold 0.106 / false-reject 0.084)*

---

## 5. Failure & audit trace (4:45–5:30) — "what broke at 2 AM"

> "A risk manager is only as good as the failure it was built by. Three real ones:
>
> One — a **double-settle bug** where the fraud service scored every transaction twice —
> once gating settlement, once in a Kafka consumer nobody read. Two scores per receipt is how
> silent risk slips in. Deleted the second path, made the decision single.
>
> Two — **incremental settlement**: a refund settled a full reservation instead of only the spent
> delta, leaking reserved money. Fixed with a delta-based settle and regression tests.
>
> Three — this is my favorite — the **fraud drill we built to prove the AI layer** immediately
> found that brand-new devices *auto-approved*. A perfect low-score, full-confidence approval,
> and no history behind it. That's the exact gap the drill exists to catch. It's fixed with a
> low-signal guard and two tests. The check-in is the evidence: `docs/reports/fraud-drill.md`.
>
> Offline payments are trust, deferred. Paron bounds the money by construction and makes every
> settlement decision auditable, deterministic, and defence-only."

*(End card: repo URL · track · "synthetic data only" · admission that labels are synthetic.)*

---

## Staging notes

- Total runtime: ~5:30. Trim Q2 walkthrough if needed — guard Q2 detail (low-signal) behind one line.
- Record batch demo live (clean services, fresh Redis) so the numbers on screen match the log output.
- Keep the "three words" hook (APPROVE · HOLD · REJECT) on screen throughout for narrative stickiness.
- Final card must include the honest limitations line: **"labels are synthetic; real data requires recalibration"**.