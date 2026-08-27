# Paron Merchant Receiver

This small Android app is the BLE merchant endpoint for the two-phone Paron demo. Install it on the merchant Android phone, set the merchant ID to match the PWA, and tap **Start Bluetooth receiver** before switching to airplane mode (then manually turn Bluetooth back on).

Open this directory in Android Studio and allow Gradle sync. The app requires Android 8.0+; Android 12+ asks for Nearby devices permission.

It implements the GATT service documented in `docs/pwa-implementation-plan.md`. It stores the latest payment receipt in the app UI only; production merchant persistence and token signature validation must be added before treating acceptance as final payment.
