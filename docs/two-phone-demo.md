# Two Android phone demo guide

## What has been implemented

| Component | Location | Role |
| --- | --- | --- |
| Customer wallet PWA | `pwa/` | Stores a token, creates an offline payment, transfers it by BLE, queues it, and syncs it later. |
| Merchant Android app | `merchant-android/` | Advertises a BLE service, receives a payment envelope, and returns an offline acceptance receipt. |
| Shared protocol | `paron-ble-v1` | Frames JSON payment offers over the GATT service UUID defined in both apps. |

The PWA and merchant app use the same BLE UUIDs. Do not change them in only one project.

## Prerequisites

- Two Android phones with Bluetooth Low Energy support.
- **Customer phone:** current Chrome for Android.
- **Merchant phone:** Android 8+ and Android Studio/ADB available to install the companion app.
- The customer PWA must be hosted at an HTTPS address. Web Bluetooth will not work from ordinary HTTP on a phone.
- For the first end-to-end server demo, the Paron API gateway and its dependencies must be reachable while requesting a token and later while syncing. The middle payment step needs no network.

## Install the merchant companion

1. Open `merchant-android/` in Android Studio.
2. Let Android Studio download the Android Gradle Plugin and Android SDK requested by the project, then run the `app` configuration on the merchant phone.
3. Grant the **Nearby devices** permission when Android asks.
4. Open **Paron Merchant Receiver**, leave the merchant ID as `merchant-demo-001`, and tap **Start Bluetooth receiver**.
5. Confirm its status says it is advertising.

The project cannot be compiled in this repository environment yet because the Android SDK and Gradle executable are not installed here. Android Studio supplies both during its first sync.

## Host and install the PWA

1. Deploy the contents of `pwa/public/` through an HTTPS static host, keeping the folder structure unchanged.
2. On the customer Android phone, open the deployed URL in Chrome and choose **Install app** from Chrome's menu.
3. In the installed wallet, enter the deployed API gateway URL and a customer ID.
4. While online, choose **Request token online**. If the backend is not running yet, choose **Create demo token** to demonstrate the BLE and UI flow only.

The service worker caches the app shell after its first successful load, so launch it once while online before relying on airplane mode.

## Live demo script

1. On the merchant phone, start the receiver.
2. On the customer PWA, turn off **Use simulator**, then tap **Connect merchant** and select the merchant phone from Chrome's chooser.
3. Keep the merchant IDs the same, choose an amount no greater than the token limit, and keep the phones nearby.
4. Enable airplane mode on both phones, then manually turn Bluetooth back on. The merchant receiver may need to be restarted after Bluetooth is re-enabled.
5. On the customer phone, tap **Pay offline**.
6. The merchant app shows the amount and a `Pending server settlement` receipt. The customer PWA shows `ACCEPTED_OFFLINE`.
7. Disable airplane mode. On the customer PWA tap **Sync now**. The PWA submits the transaction to `POST /api/v1/sync/{userId}` and changes the local state to `SUBMITTED` after the server returns `202`.

## Simulator run

Leave **Use simulator** enabled and choose **Connect merchant**. This exercises the exact same local transaction, receipt, offline queue, and sync screens without the merchant Android app or BLE hardware. The UI always labels it as a simulator.

## Known demo limits

- Merchant acceptance only confirms that the nearby device received a complete payment envelope. It is **not** settlement.
- The current merchant app performs structural validation, merchant ID matching, and amount checks. It does not yet verify a token signature.
- The existing server token uses HS512. Do not distribute its shared signing secret to merchant phones. Before claiming offline cryptographic token verification, migrate issuance to an asymmetric signature (Ed25519 or ES256), and place the public key in the merchant app.
- A browser's IndexedDB is adequate for a controlled demo but is not a secure element. A real payment product needs device-bound secure storage, fraud/exposure controls, and reconciliation handling for offline double spending.
