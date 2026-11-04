# OfflinePay Android

Native Android app source written in Kotlin and Jetpack Compose. Open this directory as a project in Android Studio with Android SDK 35 installed.

## Run

```sh
gradle :app:assembleDebug
```

The Replit workspace has no Android SDK or device emulator, so APK assembly and on-device preview are not available here. The app source is organized as a standalone Gradle project.

## Implemented scope

- Onboarding, phone number, and OTP screens.
- Home, UPI choice, QR scanner, payment details, offline payment, wallet, transaction history, and confirmation.
- Camera-based QR scanning with a mobile-number alternative.
- Debug OTP verification through `POST /api/v1/auth/otp/verify` on the API gateway.
- Profile creation in the backend `wallet_user_profiles` table after OTP verification.
- Ledger account and balance requests through the gateway.
- PayPal Sandbox wallet top-up initiated in the app; the backend creates and verifies/captures the order before crediting the INR demo ledger (default demo conversion ₹85 per USD).
- Offline-token reservation through token-service; the token record is stored by token-service and the funds are reserved by ledger-service.
- Signed offline receipts stored on-device while offline, then submitted to sync-service when connectivity returns.
- Settlement status fetched from sync-service; the app only shows a receipt as settled after the backend reports `SETTLED`.
- Individual receipt merchant IDs and decimal amounts are retained through persistence and sync.

Balances seeded by the debug ledger account endpoint are test funds. The UPI screen does not connect to a payment provider and must not be used for real payments.

All Android API calls use the API gateway base URL. Use `http://10.0.2.2:8080` in an emulator, or the laptop's LAN IP with port `8080` on a physical phone. Start `auth-service` on port `8086` and `paypal-funding-service` on port `8085` along with the gateway, token, ledger, sync, and fraud services. PayPal credentials stay on the backend; use only Sandbox credentials for the demo.

The supplied PDF does not include visual designs for onboarding/OTP, wallet, or transaction history. Those screens follow the supplied app palette and established component language without claiming a reference-perfect match.

Original reference files are in `references/`.
