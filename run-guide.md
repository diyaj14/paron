# 🚀 Paron — Full Stack Run Guide
> Run the native Android app with the Spring Boot backend. The web mockup is not part of the submission flow.

---

## ⚡ Your Stack at a Glance

| Layer | What it is | Where |
|---|---|---|
| **Infrastructure** | Kafka + Redis (Docker) | `docker-compose.yml` |
| **Backend** | 7 Spring Boot services | `api-gateway`, `auth-service`, `token-service`, `ledger-service`, `sync-service`, `fraud-service`, `paypal-funding-service` |
| **Android App** | Kotlin Compose native app | `OfflinePay-Kotlin-Wallet-App/native/offlinepay-android/` |

---

## STEP 1 — Start Infrastructure (Docker)

> **Open a terminal in Antigravity** → `Ctrl+`` ` (backtick) or Terminal panel

```powershell
Set-Location -LiteralPath "C:\Users\Diya Joshy\paron-1"
docker compose up -d
```

Wait ~30 seconds, then verify both are healthy:
```powershell
docker compose ps
```
You should see **`healthy`** for both `offlinepay-kafka` and `offlinepay-redis`.

> [!IMPORTANT]
> Don't proceed to Step 2 until both show `healthy`. Kafka takes the longest (~30s).

---

## STEP 2 — Load Environment Variables

In the **same terminal**:
```powershell
Set-Location -LiteralPath "C:\Users\Diya Joshy\paron-1"
. .\set-env.ps1
```

This sets all your `.env` values (Supabase, PayPal keys, JWT secrets) into the current shell session.  
> [!NOTE]
> You must run `. .\set-env.ps1` (with the leading dot) in **every new terminal** before running Java services. Set `PAYPAL_CLIENT_ID` and `PAYPAL_CLIENT_SECRET` from a PayPal Developer Sandbox app; funding uses USD Sandbox amounts and credits the demo INR ledger at `PAYPAL_WALLET_CREDIT_RATE` (₹85/USD by default).

---

## STEP 3 — Build All Backend Services

```powershell
Set-Location -LiteralPath "C:\Users\Diya Joshy\paron-1"
.\mvnw.cmd clean package -DskipTests -q
```

This builds all 7 Spring services into JARs. Takes ~2–3 minutes first run.

> [!TIP]
> After the first build, use `-pl api-gateway` (etc.) to rebuild only one service when you change it.

---

## STEP 4 — Start All Backend Services (Antigravity Terminals)

Run each service in its **own terminal tab** in Antigravity (`+` button in terminal panel):

### Terminal A — API Gateway (port 8080)
```powershell
Set-Location -LiteralPath "C:\Users\Diya Joshy\paron-1"; . .\set-env.ps1
java -jar .\api-gateway\target\api-gateway-1.0-boot.jar --server.port=8080
```
Use the `-boot.jar` file; `api-gateway-1.0.jar` is not the executable Spring Boot archive.

### Terminal B — Token Service (port 8081)
```powershell
Set-Location -LiteralPath "C:\Users\Diya Joshy\paron-1"; . .\set-env.ps1
java -jar token-service\target\token-service-1.0.jar --server.port=8081
```

### Terminal C — Ledger Service (port 8082)
```powershell
Set-Location -LiteralPath "C:\Users\Diya Joshy\paron-1"; . .\set-env.ps1
java -jar ledger-service\target\ledger-service-1.0.jar --server.port=8082
```

### Terminal D — Sync Service (port 8083)
```powershell
Set-Location -LiteralPath "C:\Users\Diya Joshy\paron-1"; . .\set-env.ps1
java -jar sync-service\target\sync-service-1.0.jar --server.port=8083
```

### Terminal E — Fraud Service (port 8084)
```powershell
Set-Location -LiteralPath "C:\Users\Diya Joshy\paron-1"; . .\set-env.ps1
java -jar fraud-service\target\fraud-service-1.0.jar --server.port=8084
```

### Terminal F — Auth Service (port 8086)
```powershell
Set-Location -LiteralPath "C:\Users\Diya Joshy\paron-1"; . .\set-env.ps1
java -jar auth-service\target\auth-service-1.0-boot.jar --server.port=8086
```

### Terminal G — PayPal Funding Service (port 8085)
```powershell
Set-Location -LiteralPath "C:\Users\Diya Joshy\paron-1"; . .\set-env.ps1
java -jar paypal-funding-service\target\paypal-funding-service-1.0.jar --server.port=8085
```

> [!TIP]
> Or just run `.\start-services.ps1` — it opens service windows automatically, including auth-service on port 8086.

---

## STEP 5 — Skip the Web Mockup

No PWA or browser frontend is needed. The Android app is the user-facing product and starts PayPal Sandbox checkout from Wallet.

> [!WARNING]
> The `vite.config.ts` **requires** `PORT` and `BASE_PATH` env vars or it throws an error. Always set them before `pnpm dev`.

---

## STEP 6 — Open Android App in Android Studio

1. Open **Android Studio**
2. Choose **"Open"** → navigate to:
   ```
   C:\Users\Diya Joshy\paron-1\OfflinePay-Kotlin-Wallet-App\native\offlinepay-android
   ```
3. Wait for Gradle sync to complete (bottom status bar)
4. In the **Build Variants** panel (View → Tool Windows → Build Variants), confirm:
   - Module: `app`
   - Active Build Variant: **`debug`** ← this enables `DEMO_MODE = true`

5. **Run the app** (▶ button or `Shift+F10`):
   - On **emulator**: Create one via AVD Manager → API 30+ recommended
   - On **physical device**: Enable USB Debugging in Developer Options

> [!NOTE]
> The debug OTP is **`123456`**. Android sends OTP verification and profile setup through the API gateway. Profiles are saved in `wallet_user_profiles`; balances and reservations are stored by ledger-service; offline tokens are stored by token-service; offline receipts are queued and settled by sync-service. Debug balances are seeded test funds, not real deposits.

---

## STEP 7 — Verify Backend Integration (Health Checks)

Open a new terminal and run these checks:

```powershell
# 1. API Gateway health
Invoke-RestMethod http://localhost:8080/actuator/health

# 2. Token service health
Invoke-RestMethod http://localhost:8081/actuator/health

# 3. Ledger service health
Invoke-RestMethod http://localhost:8082/actuator/health

# 4. Sync service health
Invoke-RestMethod http://localhost:8083/actuator/health

# 5. Fraud service health
Invoke-RestMethod http://localhost:8084/actuator/health

# 6. PayPal funding service health
Invoke-RestMethod http://localhost:8085/actuator/health
```

Each should return `{ "status": "UP" }`.

Then test a real API flow using `testing-guide.http` in Antigravity's **REST Client**:
```
Open: C:\Users\Diya Joshy\paron-1\testing-guide.http
```
Click "Send Request" on each block in order (auth → reserve → pay → sync).

---

## STEP 8 — Android ↔ Backend Connection

The Android app sends backend requests through the API gateway on port 8080. Configure that gateway URL in Wallet → Backend Server Connection:

1. Find your machine's local IP:
   ```powershell
   ipconfig | Select-String "IPv4"
   ```
   e.g., `192.168.1.5`

2. Enter `http://10.0.2.2:8080` on an emulator or `http://<laptop-LAN-IP>:8080` on a physical phone, then tap **Apply Server URL**.

3. Add `INTERNET` permission to [`AndroidManifest.xml`](file:///c:/Users/Diya%20Joshy/paron-1/OfflinePay-Kotlin-Wallet-App/native/offlinepay-android/app/src/main/AndroidManifest.xml) if not already there:
   ```xml
   <uses-permission android:name="android.permission.INTERNET" />
   ```

4. For emulator, use `10.0.2.2` instead of `localhost` in all HTTP calls.

---

## 🔢 Port Reference

| Service | Port | URL |
|---|---|---|
| API Gateway | 8080 | http://localhost:8080 |
| Token Service | 8081 | http://localhost:8081 |
| Ledger Service | 8082 | http://localhost:8082 |
| Sync Service | 8083 | http://localhost:8083 |
| Fraud Service | 8084 | http://localhost:8084 |
| Auth Service | 8086 | http://localhost:8086 |
| Web Frontend | 5173 | http://localhost:5173 |
| Kafka | 9092 | localhost:9092 |
| Redis | 6379 | localhost:6379 |

---

## ⚠️ Common Issues

| Problem | Fix |
|---|---|
| `PORT env var required` when running Vite | Set `$env:PORT = "5173"` and `$env:BASE_PATH = "/"` before `pnpm dev` |
| Kafka/Redis not healthy | Wait longer, check `docker compose logs kafka` |
| Java services crash on startup | Run `. .\set-env.ps1` first in the same terminal |
| Android app can't reach backend | Use `10.0.2.2:8080` on emulator, LAN IP on physical device |
| Gradle sync fails in Android Studio | File → Invalidate Caches → Invalidate and Restart |
| `DEMO_MODE` is false | Make sure Build Variant is set to `debug` not `release` |
