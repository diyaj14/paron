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
- Debug-only OTP `123456` and clearly marked sample wallet/payment data.
- Debug-only local offline payment recording as `PENDING_VERIFICATION`.
- UPI processing, production authentication, offline-token authorization, and reconciliation fail closed until a real provider and secure backend are connected.

The supplied PDF does not include visual designs for onboarding/OTP, wallet, or transaction history. Those screens follow the supplied app palette and established component language without claiming a reference-perfect match.

Original reference files are in `references/`.
