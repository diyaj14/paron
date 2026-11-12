# Two-device Android QR demo

This is the judge walkthrough for the native Android app. Use the **Paron wallet app on both devices** for QR payment. `merchant-android/` is a separate Bluetooth receiver and merchant-balance viewer; it does not scan QR codes.

## Demo accounts

Sign in with different demo phone numbers so the video shows separate payer and receiver devices:

| Device | Demo phone | OTP |
| --- | --- | --- |
| Payer (emulator) | `9876543210` | `123456` |
| Receiver (physical phone) | `9876543211` | `123456` |

The auth service accepts the configured demo OTP for any ten-digit phone number. For local use, `.env` should set `DEMO_AUTH_PASSWORD=123456`; for Render, set the same variable on auth-service. These are demo credentials, not phone verification or production authentication.

## Before recording

1. Start the local backend using [run-guide.md](run-guide.md), or ensure the Render services are awake and healthy.
2. Run `OfflinePay-Kotlin-Wallet-App/native/offlinepay-android/` on the emulator and physical phone from Android Studio.
3. Configure the API gateway URL on both installs. For local services, the emulator uses `http://10.0.2.2:8080`; the physical phone uses `http://<computer-LAN-IP>:8080` while on the same Wi-Fi. For Render, use `https://paron-api.onrender.com` on both.
4. Sign in with the device-specific phone and OTP above. On the payer, reserve an amount while online and confirm that the offline balance appears. If the wallet balance is already sufficient, PayPal Sandbox top-up is not required for this payment demonstration.

## Record the payment

1. On the payer/emulator, open **Pay Offline**, choose an amount no larger than the reserved balance, and create the offline payment. The success screen displays the signed payment QR.
2. Disconnect both devices from the internet. Keep the camera available on the receiving phone.
3. On the receiving phone, open **Pay with UPI → Scan QR Code** and scan the payer's QR. The receiver verifies the receipt locally and shows it as received/pending sync; this local acknowledgement is not yet settlement.
4. Restore connectivity on the receiving phone. It may sync automatically; if a **Sync** action is shown, tap it.
5. To show the merchant ledger result, open `merchant-android/` on the receiving phone, set Merchant ID to `merchant_canteen` (the payer app's default), enter the reachable API gateway URL, and tap **Register & check balance (online)**. The collected balance should reflect settled transactions for that merchant ID.

The sync worker assigns the settled transaction to the payer identified by the token. Because the receiver wallet currently polls sync history under the receiver's account, its local receipt can remain marked pending even after the merchant ledger is credited. Use the merchant balance shown above as the settlement evidence in the recording.

## What the demo verifies

- The payer token caps offline spending and is issued before connectivity is lost.
- The receipt is signed on the payer device and checked by the receiving app.
- On sync, backend services validate the token and receipt, apply fraud/risk checks, debit the payer's reserved balance, and credit the merchant ledger.
- PayPal Sandbox funding is an optional online wallet top-up. It is not used to authorize the QR transfer while the devices are offline.

If token reservation reports `ACTIVE_RESERVATION_EXISTS`, deploy the latest ledger-service build with stale-reservation recovery, then reserve again. Do not clear reservation rows directly: release must return only unspent funds and preserve settled amounts. Wait for the payer to show its offline balance before recording.
