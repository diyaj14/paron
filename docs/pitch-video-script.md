# Paron — PayPal AI Hackathon Pitch

Use this as the narration and shot list for the submission video. Keep the Android app and backend responses visible during the demo; do not present the browser simulation as a real Bluetooth transfer.

## One-line pitch

**Paron lets people make a small, prepaid phone-to-phone payment when internet access disappears, then verifies and reconciles the receipt when connectivity returns.**

## Slide 1 — The moment the connection drops

**On screen:** A campus canteen queue and a payment screen stuck loading.

**Say:**

> In India, we reach for our phones for everyday payments. But at a campus canteen, in a basement, or on a crowded network, the connection can disappear at exactly the wrong moment. I have been the person searching for someone with a better signal while the queue waits. Paron is built for that interruption.

## Slide 2 — A small offline allowance

**On screen:** Paron wallet and the PayPal Sandbox checkout.

**Say:**

> While online, a customer funds the Paron demo wallet through PayPal Sandbox and sets aside a capped amount for offline use. The backend confirms the PayPal capture before crediting the demo ledger, then issues one active, time-limited offline token. The customer is spending from that reserved allowance, not creating new money at the counter.

## Slide 3 — Pay nearby without internet

**On screen:** Two Android phones; customer sends a payment by Bluetooth or presents a QR code. Show airplane mode if the recording setup supports it.

**Say:**

> When the network is unavailable, the customer creates a payment receipt locally. The app signs the payment details with an ECDSA P-256 device key and transfers the receipt to the merchant over Bluetooth or QR. The merchant gets an offline acknowledgement and stores the receipt. That acknowledgement is provisional; it is not yet a final settlement.

## Slide 4 — Reconnect, verify, settle

**On screen:** Turn connectivity back on, press Sync, and show the backend result plus both wallet/merchant ledger states.

**Say:**

> When the payment is synced after reconnect, Paron sends the queued receipt to the backend. The sync and fraud services check the signature, token limits, duplicates, and transaction patterns. The ledger settles an accepted claim and updates the merchant's demo balance. A suspicious claim can be rejected or held for review. If the backend has not returned a settled result, we keep showing it as pending. Offline payment acceptance cannot guarantee that every claim will later settle.

## Slide 5 — What the AI does (and does not do)

**On screen:** A risk-review example and the model version/health response. Add “synthetic evaluation data” visibly next to any metrics.

**Say:**

> Paron's Python risk service runs a calibrated logistic-regression model trained on generated transaction scenarios. It looks at amount, velocity, token reuse, timing, and available history, and returns a score with reason contributions. Deterministic integrity rules and backend policy remain in control: a model score is not a payment authorization, and unavailable or weak signals can be held for review. Our published metrics are from synthetic holdout data, not real-world fraud outcomes.

> *(Optional metric card, only after the hosted health endpoint confirms v2: On 3,554 eligible records in the synthetic holdout, model PR-AUC was 0.9105, precision 0.8169, and recall 0.786 at the report's threshold. These are simulated-scenario results, not production estimates.)*

## Slide 6 — PayPal and the product

**On screen:** PayPal Sandbox approval followed by the credited test-wallet balance; then the Android APK and service diagram.

**Say:**

> PayPal is the funding entry point in this prototype: the funding service creates a Sandbox order, verifies its completed capture, and then credits Paron's INR demo ledger using a disclosed fixed demo conversion rate. Merchant settlement in this prototype is an internal ledger update, not a PayPal payout. The product is a native Kotlin Android wallet backed by Spring Boot services, PostgreSQL, Redis, Kafka, and a Python FastAPI risk model. Render hosts the demo backend. The browser site is a judge-facing walkthrough; its nearby handoff is simulated.

## Close

**On screen:** Paron logo, public repository, APK release, website, and demo-video links.

**Say:**

> Paron does not claim that offline money is risk-free. It makes the offline amount explicit, signs what the customer spent, and gives the backend a controlled way to verify and reconcile the claim. The result is a payment flow designed to keep moving when the signal does not.

## Capture checklist

- Record a real Android app flow for onboarding, PayPal Sandbox funding, token reservation, offline payment, and post-reconnect sync.
- Before recording, verify PayPal Sandbox order creation and capture succeed from the Java funding service. A standalone credential-check script is not proof that the app's backend integration works.
- Show the payment as pending/accepted offline until the sync response confirms settlement.
- Show that the merchant amount changes only after successful backend settlement; include one held/rejected case if the running environment is stable.
- Confirm `/healthz` reports `lr-calibrated-v2.0.0-scenario` if describing v2 as the hosted model.
- Keep PayPal secrets, private keys, passwords, and real personal data off screen.
- Add captions or narration that distinguish real Android/backend steps from the browser simulation.
- End with the public GitHub repo, MIT license, APK download, deployed website, and video URLs.

## Submission criteria checklist

| Requirement from the rules | Evidence in Paron | Status before submission |
|---|---|---|
| PayPal developer integration using Sandbox | Backend order creation, buyer approval, verified capture, and demo-ledger credit | Implemented in source; **verify the Java funding service succeeds end-to-end**. Recent local logs showed `invalid_client`, despite the separate PowerShell credential check passing. |
| AI tool/model is part of the project | Trained logistic-regression risk model, Python FastAPI scoring service, fraud policy, synthetic evaluation artifacts | Implemented. Label metrics as synthetic and distinguish local v2 artifacts from the hosted model until `/healthz` confirms v2. |
| Project installs and works on its stated platform | Native Android app and published debug APK; backend uses Render services | Android build has succeeded, but submission readiness requires a clean install and a complete live run. Do not rely on the APK existing as proof that remote services work. |
| Video demonstrates the end-to-end project | The sequence above covers funding through offline acceptance and eventual sync | **Still needs recording after PayPal and backend flow are verified.** |
| Problem, audience, and value are clear | Campus canteen/customer story; customers and merchants facing unreliable connectivity | Addressed in this pitch. |
| Public code repository | README links `github.com/diyaj14/paron` | Verify the repository opens in a signed-out/private browser. |
| Approved OSI license | MIT `LICENSE` | Present. |
| README setup, PayPal/AI use, tools and integrations | README has setup, service map, PayPal Sandbox, AI section, and Render mention | Present; replace the website placeholder and add real screenshots/video link. |
| Significant update during the submission period, if the project predates it | Git history shows recent work, including Android, PayPal, and website changes | Confirm the official submission-period start date and cite the relevant commits in the submission. Local commit dates alone do not prove they fall within that period. |
| Sponsor prize rules, if entering one | PayPal and Render are named integrations | Read the separate requirements for the specific sponsor prize; general integration alone does not establish eligibility. |

## Claims to avoid

- Do not say PayPal holds or reserves the offline amount. PayPal Sandbox funds the Paron test wallet; Paron's ledger reserves the allowance.
- Do not call the offline merchant acknowledgement a completed payment or guaranteed settlement.
- Do not say AI prevents fraud or independently approves payments. It supplies a risk score to a policy-controlled review path.
- Do not present synthetic-data precision/recall as production performance.
- Do not say the browser demo performs real Bluetooth or Android hardware signing; its local consumer/merchant handoff is simulated.
- Do not describe an internal merchant-ledger credit as a PayPal payout or bank settlement.
