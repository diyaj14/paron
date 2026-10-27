# Paron / OfflinePay — Dual-Device Demo Run Guide
### (Android Emulator ↔ Physical Android Phone — 100% Offline P2P Transactions)

This guide walks you through demonstrating **100% offline peer-to-peer payments** between two Android devices running the native Kotlin Compose app (**`offlinepay-android`**), completely bypassing the web frontend:
- **Device A (Payer / Customer)**: Android Emulator running in Android Studio on your laptop.
- **Device B (Payee / Merchant)**: Physical Android Phone in your hand.
- **Backend (Settlement & Fraud Assessment)**: Spring Boot microservices on your laptop.

---

## 🏗️ Demo Architecture at a Glance

```
                                  [ 100% OFFLINE AIR-GAPPED HANDOVER ]
   +-------------------------------+                              +-------------------------------+
   |      DEVICE A (Emulator)      |   Visual QR Code / Bluetooth |   DEVICE B (Physical Phone)   |
   |        Customer Wallet        | ---------------------------> |        Merchant Store         |
   |                               | <--------------------------- |                               |
   | • Has Offline Token Reserve   |    ECDSA-Signed Claim JSON   | • Scans Payer's Screen / BT   |
   | • Signs transaction with      |    (Zero internet needed)    | • Verifies ECDSA signature    |
   |   device private key (ECDSA)  |                              | • Stores in local ledger DB   |
   +-------------------------------+                              +-------------------------------+
                   |                                                              |
                   |                [ WHEN CONNECTIVITY RETURNS ]                 |
                   +------------------------------+-------------------------------+
                                                  |
                                                  v  (HTTP POST /api/v1/sync/transactions)
                                   +------------------------------+
                                   |     Spring Boot Backend      |
                                   | • API Gateway   (:8080)      |
                                   | • Sync Service  (:8083)      |
                                   | • Fraud Service (:8084)      |
                                   | • Ledger Service(:8082)      |
                                   +------------------------------+
```

---

## ⚡ Quick Prerequisites

1. **Backend Infrastructure & Services Running on Laptop**:
   Ensure Docker (Kafka + Redis) and your 5 Spring Boot services are running:
   ```powershell
   cd "C:\Users\Diya Joshy\paron-1"
   docker compose ps   # Must show healthy for kafka and redis
   ```
   *(Your 5 Spring Boot services: `api-gateway` on 8080, `token-service` on 8081, `ledger-service` on 8082, `sync-service` on 8083, `fraud-service` on 8084)*.

2. **Laptop Local Wi-Fi IP**:
   Find your laptop's Wi-Fi IP (your physical phone needs this to sync):
   ```powershell
   ipconfig | findstr /i "IPv4"
   # e.g., 192.168.1.6
   ```

---

## 📲 Step 1 — Install & Launch App on Both Devices

The native app is located at:
`C:\Users\Diya Joshy\paron-1\OfflinePay-Kotlin-Wallet-App\native\offlinepay-android`

### Device 1: Launch on the Android Emulator
1. Open Android Studio.
2. Open the project folder `C:\Users\Diya Joshy\paron-1\OfflinePay-Kotlin-Wallet-App\native\offlinepay-android`.
3. In the toolbar device dropdown, select your **Android Virtual Device (Emulator)** (Pixel 6/7 recommended, API 30+).
4. Click **Run (▶)** (or `Shift + F10`).
5. In the app:
   - Log in with any phone number (e.g. `9876543210`) and demo OTP: **`123456`**.
   - Navigate to the **Wallet** tab (bottom bar).
   - Under **Backend Server Connection**, tap **`Emulator (10.0.2.2)`**.
   - Tap **Reserve Offline Balance** (or use the preloaded demo ₹500 balance).

### Device 2: Launch on Your Physical Android Phone
1. Connect your Android phone to the laptop with a USB cable and enable **USB Debugging** (in Developer Options).
   *(Alternatively, build the APK with `.\gradlew.bat assembleDebug` and install `app-debug.apk` directly on your phone).*
2. In Android Studio, select your physical phone in the device dropdown and click **Run (▶)**.
3. In the app on your physical phone:
   - Log in with another number (e.g. `9123456780`) and OTP: **`123456`**.
   - Navigate to the **Wallet** tab.
   - Under **Backend Server Connection**, select **`Wi-Fi (192.168.1.6)`** (or type your laptop's actual IP).
   - Tap **Apply Server URL**.

---

## 🎬 Step 2 — Perform the 100% Offline P2P Payment

You can disconnect both the phone and laptop from the internet during this step to prove it is **completely air-gapped**.

### 🌟 Flow A: Instant Visual QR Code Handover (Recommended & Most Visual)
This is the smoothest method for live presentations because it uses optical transmission:

1. **On Device A (Emulator on Laptop Screen — Payer)**:
   - On the Home screen, tap **Pay**.
   - Select **Enter Mobile Number Instead** (or pick a mock merchant like *Apollo Pharmacy*).
   - Enter Amount: **`₹150`**.
   - Tap **Pay Using Offline Token**.
   - **Result**: The emulator displays the **Offline Payment QR Code** containing the ECDSA cryptographically signed payload.

2. **On Device B (Physical Phone in Hand — Receiver / Merchant)**:
   - On your physical phone, tap **Pay** → **Scan QR Code** (grant camera permission if prompted).
   - Point your physical phone's camera at the emulator screen displaying the QR code.

3. **Instant Offline Verification**:
   - The phone's camera instantly reads the QR code.
   - **`CryptoManager`** extracts the ECDSA signature and verifies the cryptographic digest completely offline.
   - The physical phone vibrates and adds the transaction to its local history ledger:
     `🟢 Received ₹150 • Signed with Token • Pending Sync`.
   - Device A's offline balance drops from ₹500 → ₹350.

---

### 📡 Flow B: Bluetooth P2P Wireless Handover
If you want to demonstrate wireless radio transmission:

1. **Pair Phone with Laptop**:
   - Pair your physical Android phone with your laptop in Windows Bluetooth Settings.
2. **On Device B (Physical Phone — Merchant)**:
   - Ensure Bluetooth is ON.
3. **On Device A (Emulator / Laptop)**:
   - In the payment confirmation screen, tap **Pay via Bluetooth**.
   - Select Device B from the paired devices list.
   - The signed payment offer is sent over Bluetooth RFCOMM socket (`00001101-0000-1000-8000-00805F9B34FB`).
   - Device B verifies the signature and returns an instant cryptographic receipt `ACK|SUCCESS`.

---

## 🔄 Step 3 — Settle & Sync with Spring Boot Backend

Now demonstrate what happens when connectivity returns:

1. Reconnect your devices to Wi-Fi.
2. In the app on either device, go to the **Wallet** tab.
3. Tap **Sync Queued Payments Now**.
4. **Backend Flow in Action**:
   - The app sends `POST /api/v1/sync/transactions` with the signed payload to your **API Gateway** (`:8080`).
   - **Sync Service** (`:8083`) receives the offline transactions and publishes an event to **Kafka**.
   - **Fraud Service** (`:8084`) evaluates the risk score (offline duration, velocity, amount deviation, token reuse).
   - **Ledger Service** (`:8082`) atomically settles the reserved balance and marks the transaction as **`SETTLED`**.
5. The transaction badge in the Android app updates from `Pending Sync` ➔ **`Settled on Bank Ledger`**.

---

## 🔍 Verification Commands (Optional)

Check that transactions were processed through the backend microservices:

```powershell
# Check Sync Service health
Invoke-RestMethod http://localhost:8083/actuator/health

# Check Ledger balances
Invoke-RestMethod http://localhost:8082/actuator/health

# Check Fraud Service health
Invoke-RestMethod http://localhost:8084/actuator/health
```

---

## 🛠️ Demo Day Troubleshooting Cheat Sheet

| Symptom | Cause | Solution |
| :--- | :--- | :--- |
| **Physical phone can't reach backend** | Wrong IP or firewall blocking port 8080 | Check `ipconfig`. In Wallet screen, set `http://<YOUR_LAN_IP>:8080` and tap *Apply Server URL*. |
| **Camera won't scan emulator screen** | Screen glare or low brightness | Increase laptop screen brightness and hold phone ~20 cm away from the emulator QR code. |
| **Emulator says camera unavailable** | Emulator virtual camera not configured | Use Emulator as the **Payer (displays QR)** and Physical Phone as the **Scanner (camera)**. |
| **"Offline balance exhausted"** | Spent all demo tokens | Go to Wallet tab → tap **Reserve Offline Balance** to replenish ₹500. |
| **Reset everything for a fresh demo** | Stale local transactions | On Android: Settings → Apps → OfflinePay → **Clear Storage / Data**, or tap *Clear History* in the app. |
