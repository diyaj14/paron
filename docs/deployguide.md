# Deploy Paron for free

This guide deploys Paron's backend as free Render Web Services and prepares the Android app for installation. Render can host the backend APIs, but it cannot run an Android app on a phone. Build the APK with Android Studio and publish it separately, for example as a GitHub Release.

This setup is for a hackathon demo. It is not a reliable production deployment.

## Read this before deploying

- Do not create Render Private Services. They are paid. Use **Web Service → Docker → Free** for each Render process.
- Free Render Web Services are public. Render free services cannot receive private-network requests, so service-to-service calls in this setup use each service's public HTTPS URL.
- Free services sleep after 15 minutes without inbound traffic and can take about a minute to wake. A multi-service call can wake several services, so the first app action after idling may take a while.
- Render includes 750 free instance hours per workspace per month, shared across free services. Render can suspend free services if that allowance is used up.
- Free web services have 512 MB RAM. Spring Boot startup can be slow or run out of memory on this plan.
- Because downstream services have public URLs and several backend routes are not protected when called directly, use only synthetic/demo data and PayPal Sandbox. Do not use real customer data or real payment credentials.
- Reuse the existing Supabase database and any already provisioned free Redis/Kafka services. Do not create a Render Free PostgreSQL database for this demo: Render Free PostgreSQL expires after 30 days.
- Supabase Free projects may pause after a week with too little database activity. Check the Supabase dashboard if database calls stop working.
- The Render free plan and provider free tiers have limits. Check your account's usage page and provider dashboards before deploying.

Render's documented free limits: [free services and datastores](https://render.com/docs/free). Supabase inactivity behavior: [free project pausing](https://supabase.com/kb/faqs/platform/free-project-pausing).

## Architecture deployed by this guide

The customer uses the native Android app. The app calls the public Render API gateway. The gateway and backend services call one another using public HTTPS addresses because free Render services cannot receive private-network traffic.

| Component | Render type | Render plan |
|---|---|---|
| risk-model-service | Web Service, Docker | Free |
| ledger-service | Web Service, Docker | Free |
| token-service | Web Service, Docker | Free |
| auth-service | Web Service, Docker | Free |
| fraud-service | Web Service, Docker | Free |
| sync-service | Web Service, Docker | Free |
| paypal-funding-service | Web Service, Docker | Free |
| api-gateway | Web Service, Docker | Free |

The PWA is not used in this app flow and does not need to be deployed.

## Before you begin

1. Push the latest source to the GitHub repository and branch you will connect to Render. The repository needs Dockerfile.render, risk-model-service/Dockerfile, and the model artifacts.
2. Confirm .env, private keys, Android local.properties, and other secrets are not committed. The root .gitignore excludes .env and PEM files. Never paste the complete .env into a public repo or chat.
3. Have these external services ready:
   - A Supabase PostgreSQL project with the Paron tables already initialized.
   - A Redis-compatible endpoint and credentials.
   - A Kafka endpoint and credentials.
   - PayPal Developer Sandbox client ID, client secret, and buyer account.
4. If your current local .env already has working Supabase, Redis, Kafka, and PayPal Sandbox values, reuse them by entering only the needed values into the appropriate Render service. Do not copy its localhost service URLs.

### If you do not have the external providers yet

- **Supabase:** use the existing Paron database if possible. The repository does not provide a complete SQL migration bundle, and most services do not create their own tables. A newly created, empty Supabase database will not be enough. If your existing database is unavailable, stop here and initialize the schema before deploying.
- **Redis:** create a Redis database in a provider dashboard that offers a free tier. In Upstash, create a Redis database and copy its TCP host, port, password, and TLS setting. Do not use its REST URL as REDIS_HOST.
- **Kafka:** create a Kafka cluster with a provider that currently offers a free plan, then copy its bootstrap and SASL connection values. The local Docker Compose Kafka cannot be reached by Render.
- **PayPal:** in PayPal Developer Dashboard, create a Sandbox app and Sandbox buyer account. Use only the Sandbox app credentials here.

Check each provider's current quota and billing page before creating resources. A free Redis/Kafka account is external to Render and follows that provider's own limits.

The Spring services build their PostgreSQL connection string with host port 6543 and SSL, so SUPABASE_HOST must be the pooler hostname only, without https:// or a port. The app services use ddl-auto: none for most tables; deploying against a blank database will not initialize all required Paron tables.

## Render naming and URLs

Use these names if available in your Render account. Render service names must be unique; if one is taken, choose another and use the actual URL shown on its service page.

| Render service name | Public URL pattern |
|---|---|
| paron-risk-model | https://paron-risk-model.onrender.com |
| paron-ledger | https://paron-ledger.onrender.com |
| paron-token | https://paron-token.onrender.com |
| paron-auth | https://paron-auth.onrender.com |
| paron-fraud | https://paron-fraud.onrender.com |
| paron-sync | https://paron-sync.onrender.com |
| paron-paypal-funding | https://paron-paypal-funding.onrender.com |
| paron-api | https://paron-api.onrender.com |

If Render assigns a different URL, use the actual URL from the service dashboard everywhere below. Keep every service in the same Render region. Singapore is suitable for the Android demo in India.

## Create the risk model service

Create this first so the fraud service can point to it.

1. In Render, select **New → Web Service** and connect your GitHub repository and deployment branch.
2. Set **Language** to **Docker**.
3. Set **Name** to paron-risk-model, select your common region, and select the **Free** instance type.
4. Set **Dockerfile Path** to risk-model-service/Dockerfile.
5. Set **Docker Context** to the repository root (`.`). The Dockerfile copies files from `risk-model-service/`.
6. Add this environment variable:
   - MODEL_DIR = /app/artifacts/approved/lr-calibrated-v1.0.0
7. Set the HTTP health check path to /healthz, if the form offers it.
8. Select **Deploy Web Service**.
9. Wait for the deployment to become live. Open the service's public URL with /healthz, for example https://paron-risk-model.onrender.com/healthz. It should return a successful response.

The model Dockerfile copies its model files into /app/artifacts; the MODEL_DIR value points the service to the versioned model included in the repository.

## Create the Java services

Repeat the following steps for each Java service in the table below.

1. In Render select **New → Web Service** and choose the same GitHub repository and branch.
2. Select **Docker** as the language.
3. Set the service name from the table and use the same region as the risk model.
4. Set **Dockerfile Path** to Dockerfile.render.
5. Set **Docker Context** to the repository root, .
6. Add the SERVICE and JAR_FILE values from the table as environment variables. The shared Dockerfile uses these to build and select the correct Spring Boot JAR.
7. Set the instance type to **Free**.
8. Add the service-specific environment variables listed in the next section before deploying.
9. Set the HTTP health check path to /actuator/health, if available.
10. Deploy and wait for the service to become live.

| Service name | SERVICE value | JAR_FILE value |
|---|---|---|
| paron-ledger | ledger-service | ledger-service-1.0 |
| paron-token | token-service | token-service-1.0 |
| paron-auth | auth-service | auth-service-1.0-boot |
| paron-fraud | fraud-service | fraud-service-1.0 |
| paron-sync | sync-service | sync-service-1.0 |
| paron-paypal-funding | paypal-funding-service | paypal-funding-service-1.0 |
| paron-api | api-gateway | api-gateway-1.0-boot |

Create the services in this order: ledger, token, auth, fraud, sync, PayPal funding, and API gateway. The risk model was deployed first. The API gateway is the only URL that should be entered in the Android app.

## Environment variables

Use Render's Environment page to enter variables. Render supports multiline values, which is useful for PEM keys. Keep keys in the service environment only; do not put secrets into render.yaml or source control. You can add the shared database values to an Environment Group and link that group to the six Java services that use Postgres, or enter the values separately on each service.

For Docker services, Render makes service environment variables available as Docker build arguments. The shared Dockerfile uses SERVICE and JAR_FILE during its build; set both before the first deploy. See [Render Docker deployments](https://render.com/docs/docker) and [environment variables](https://render.com/docs/configure-environment-variables).

### Exact environment variables by service

Add these on each service's **Environment** page before its first deploy. Put `SERVICE` and `JAR_FILE` on the Java services as ordinary environment variables too; Render passes them to the Docker build as build arguments. Values shown as `https://...` should use the actual public URL Render assigned to your service. Do not add variables listed under another service.

#### Risk model (`paron-risk-model`)

- `MODEL_DIR` = `/app/artifacts/approved/lr-calibrated-v1.0.0`

Render supplies `PORT` automatically. No database, Redis, or Kafka variables are needed here.

#### Ledger (`paron-ledger`)

- `SERVICE` = `ledger-service`
- `JAR_FILE` = `ledger-service-1.0`
- `SUPABASE_HOST` = Supabase pooler hostname only, no scheme or port
- `SUPABASE_DB` = database name, usually `postgres`
- `SUPABASE_USER` = username shown in the exact Supabase connection string you selected. For the shared transaction pooler on port `6543`, it is typically `postgres.<project-ref>`, not plain `postgres`.
- `SUPABASE_PASSWORD` = Supabase database password

#### Token (`paron-token`)

- `SERVICE` = `token-service`
- `JAR_FILE` = `token-service-1.0`
- `SUPABASE_HOST`, `SUPABASE_DB`, `SUPABASE_USER`, `SUPABASE_PASSWORD` = same Supabase project values as ledger
- `OFFLINE_TOKEN_SIGNING_SECRET` = cryptographically random value of at least 64 bytes; `JwtUtil` uses it directly as the HS512 key. On Windows PowerShell, generate one locally with:

  ```powershell
  $bytes = New-Object byte[] 64
  $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
  $rng.GetBytes($bytes)
  [Convert]::ToBase64String($bytes)
  $rng.Dispose()
  ```

  Copy the printed value into Render without sharing it. Keep this value stable; replacing it invalidates offline tokens signed with the previous key.
- `OFFLINE_TOKEN_ISSUER` = `paron-offline-token`
- `LEDGER_SERVICE_URL` = `https://paron-ledger.onrender.com`

Do not reuse the session JWT key as the offline token signing secret.

#### Auth (`paron-auth`)

- `SERVICE` = `auth-service`
- `JAR_FILE` = `auth-service-1.0-boot`
- `SUPABASE_HOST`, `SUPABASE_DB`, `SUPABASE_USER`, `SUPABASE_PASSWORD` = same Supabase project values as ledger
- `SESSION_JWT_PRIVATE_KEY` = contents of the session private key PEM
- `SESSION_JWT_ISSUER` = `paron-demo-auth`
- `SESSION_JWT_AUDIENCE` = `paron-wallet`
- `DEMO_AUTH_USERNAME` = `demo-customer`
- `DEMO_AUTH_PASSWORD` = demo OTP accepted by the Android sign-in screen (use `123456` only for this demo)
- `DEMO_AUTH_TOKEN_TTL_MINUTES` = `30` (optional; this is the default)

#### Fraud (`paron-fraud`)

- `SERVICE` = `fraud-service`
- `JAR_FILE` = `fraud-service-1.0`
- `SUPABASE_HOST`, `SUPABASE_DB`, `SUPABASE_USER`, `SUPABASE_PASSWORD` = same Supabase project values as ledger
- `REDIS_HOST` = Redis hostname only
- `REDIS_PORT` = Redis port
- `REDIS_PASSWORD` = Redis password/token
- `REDIS_SSL` = `true` when the Redis provider requires TLS
- `MODEL_SERVICE_URL` = `https://paron-risk-model.onrender.com`

#### Sync (`paron-sync`)

- `SERVICE` = `sync-service`
- `JAR_FILE` = `sync-service-1.0`
- `SUPABASE_HOST`, `SUPABASE_DB`, `SUPABASE_USER`, `SUPABASE_PASSWORD` = same Supabase project values as ledger
- `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `REDIS_SSL` = Redis provider host, port, password/token, and TLS setting
- `KAFKA_BOOTSTRAP_SERVERS` = Kafka bootstrap hostname and port
- `KAFKA_SASL_MECHANISM` = mechanism specified by the Kafka provider
- `KAFKA_SECURITY_PROTOCOL` = provider protocol, typically `SASL_SSL`
- `KAFKA_JAAS_CONFIG` = full JAAS login configuration using the Kafka username and password
- `LEDGER_SERVICE_URL` = `https://paron-ledger.onrender.com`
- `TOKEN_SERVICE_URL` = `https://paron-token.onrender.com`
- `FRAUD_SERVICE_URL` = `https://paron-fraud.onrender.com`

The Kafka topic is `offline.transactions`. Use the exact connection mechanism and values shown in the Kafka provider dashboard.

For **Aiven SASL/SCRAM**, use the SASL bootstrap port from Aiven's connection information (SASL and client-certificate authentication can use different ports). Set `KAFKA_SECURITY_PROTOCOL=SASL_SSL`, `KAFKA_SASL_MECHANISM=SCRAM-SHA-256`, and `KAFKA_JAAS_CONFIG` to `org.apache.kafka.common.security.scram.ScramLoginModule required username="<AIVEN_SERVICE_USER>" password="<AIVEN_SERVICE_PASSWORD>";`. In Aiven Quick Connect, download the **CA certificate** for SASL. In the Render sync-service Environment page, add it under **Secret Files** with filename `aiven-ca.pem`, then add:

- `SPRING_KAFKA_SSL_TRUST_STORE_LOCATION` = `file:/etc/secrets/aiven-ca.pem`
- `SPRING_KAFKA_SSL_TRUST_STORE_TYPE` = `PEM`

Do not use Aiven's service certificate or private key for SASL/SCRAM. Render mounts secret files at `/etc/secrets/<filename>`; Kafka supports PEM trust stores. Keep the CA file out of Git.

#### PayPal funding (`paron-paypal-funding`)

- `SERVICE` = `paypal-funding-service`
- `JAR_FILE` = `paypal-funding-service-1.0`
- `SUPABASE_HOST`, `SUPABASE_DB`, `SUPABASE_USER`, `SUPABASE_PASSWORD` = same Supabase project values as ledger
- `LEDGER_SERVICE_URL` = `https://paron-ledger.onrender.com`
- `PAYPAL_CLIENT_ID` = Client ID from a PayPal Developer Sandbox app
- `PAYPAL_CLIENT_SECRET` = Sandbox secret from the same app
- `PAYPAL_BASE_URL` = `https://api-m.sandbox.paypal.com`
- `PAYPAL_CURRENCY` = `USD`
- `PAYPAL_MAXIMUM_AMOUNT` = `500.00`
- `PAYPAL_WALLET_CREDIT_RATE` = `85.00` demo INR credit per USD

Never put the PayPal secret in the Android app or public repository. The conversion rate is a demo ledger rate, not an exchange-rate quote. Use a Sandbox buyer account to approve checkout.

#### API gateway (`paron-api`)

- `SERVICE` = `api-gateway`
- `JAR_FILE` = `api-gateway-1.0-boot`
- `AUTH_SERVICE_URL` = `https://paron-auth.onrender.com`
- `TOKEN_SERVICE_URL` = `https://paron-token.onrender.com`
- `LEDGER_SERVICE_URL` = `https://paron-ledger.onrender.com`
- `SYNC_SERVICE_URL` = `https://paron-sync.onrender.com`
- `FRAUD_SERVICE_URL` = `https://paron-fraud.onrender.com`
- `PAYPAL_FUNDING_SERVICE_URL` = `https://paron-paypal-funding.onrender.com`
- `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `REDIS_SSL` = Redis provider host, port, password/token, and TLS setting
- `JWT_PUBLIC_KEY` = contents of the matching session public key PEM
- `SESSION_JWT_ISSUER` = same value as auth service (`paron-demo-auth`)
- `SESSION_JWT_AUDIENCE` = same value as auth service (`paron-wallet`)

The gateway and risk model do not need Supabase variables. If you generate a new session key pair, paste the matching PEM files into auth and gateway; never commit the private key. The Android app should call only the gateway URL.

Do not create a Render Private Service for Redis. Use your existing free Redis provider if its free tier remains available. Store the Redis, Kafka, Supabase, and PayPal secrets only in Render's Environment settings, not in source control or `render.yaml`.

## Connect the Android app

Render deploys the backend, not the Android application binary. The Android app must be built and installed on an emulator or phone.

1. Open OfflinePay-Kotlin-Wallet-App/native/offlinepay-android in Android Studio.
2. Let Gradle sync and run the app on an emulator or physical Android phone.
3. In the app's backend URL setting, enter the API gateway URL, for example https://paron-api.onrender.com.
4. Sign in with the configured demo OTP.
5. Build a demo APK from Android Studio or with the Gradle wrapper. The generated APK is under app/build/outputs/apk/.
6. Publish the APK as a GitHub Release asset or another free download. Add that download link to the README/Devpost submission. Do not upload signing keys.

You do not need to deploy the PWA or create a Render Static Site for the customer app.

## Verify the full flow

Check each service health URL after deployment:

- Risk model: https://paron-risk-model.onrender.com/healthz
- Java services: the service URL followed by /actuator/health
- Gateway: https://paron-api.onrender.com/actuator/health

Then test from the Android app in this order:

1. Sign in and complete onboarding.
2. Confirm the account/profile and balance load.
3. Reserve an offline token.
4. Start a PayPal Sandbox top-up, approve it with the Sandbox buyer, and confirm the balance updates after return to the app.
5. Make an offline payment, reconnect, and trigger sync.
6. Confirm the backend shows the transaction status and risk decision.

Use test users and synthetic transactions only. Never use production PayPal credentials or real customer data in this free public-service layout.

## Common problems

| Symptom | Check |
|---|---|
| Docker build cannot find Dockerfile or risk model files | Use Dockerfile Path `risk-model-service/Dockerfile` and Docker Context `.`. The root `.dockerignore` must not exclude `risk-model-service/`. |
| Build says SERVICE or JAR_FILE is blank, or Docker tries to copy `/workspace//target/.jar` | In the Render service's **Environment** settings, add `SERVICE` and `JAR_FILE` from the table (for PayPal funding: `paypal-funding-service` and `paypal-funding-service-1.0`). Choose **Save, rebuild, and deploy**. These non-secret values are also Docker build arguments. |
| Service starts then exits with code 137 | The Free 512 MB memory limit may be too small for that Java service. Restarting or tuning cannot guarantee it will fit. |
| App gets 502 or times out after inactivity | A free Render service may be waking up; wait about a minute and retry. A chain of sleeping services can take longer. |
| Datasource URL is missing or Hibernate cannot determine the PostgreSQL dialect | On the failing service, verify `SUPABASE_HOST`, `SUPABASE_DB`, `SUPABASE_USER`, and `SUPABASE_PASSWORD`; for the shared transaction pooler, copy its exact username (typically `postgres.<project-ref>`). If needed, add `SPRING_DATASOURCE_URL` with `jdbc:postgresql://<pooler-host>:6543/<database>?sslmode=require&connect_timeout=90&tcpKeepAlive=true&prepareThreshold=0` and `SPRING_JPA_DATABASE_PLATFORM=org.hibernate.dialect.PostgreSQLDialect`, then save and redeploy. |
| Database connection fails | Check the Supabase pooler host, port, username, password, TLS access, and that the existing schema is initialized. |
| Gateway says public key is missing or invalid | Check the PEM formatting, matching auth private/public keys, issuer, audience, and that Render saved the environment changes. |
| Kafka reports `SSL handshake failed` | Confirm the bootstrap host and port match Aiven's **SASL** connection details, then add the Aiven CA certificate as a Render secret file and set `SPRING_KAFKA_SSL_TRUST_STORE_LOCATION` and `SPRING_KAFKA_SSL_TRUST_STORE_TYPE` as shown above. |
| Kafka authentication or authorization fails after TLS connects | Check the Aiven service username/password, exact JAAS string, SCRAM mechanism, and read/write ACLs for the sync-service user. |
| Redis connection fails | Check host/port/password and whether TLS is required. |
| PayPal top-up fails | Confirm the Sandbox client ID and secret belong to the same Sandbox app; inspect paypal-funding-service logs. |

If any Java service repeatedly exceeds free memory, or the service chain is too slow for the demo, this project cannot run reliably as eight independent free Render services. Do not switch to paid services if your requirement is a $0 deployment; use the local stack for the video/demo instead.

## Official Render references

- [Deploy for Free](https://render.com/docs/free)
- [Web Services](https://render.com/docs/web-services)
- [Docker deployments](https://render.com/docs/docker)
- [Environment variables and secrets](https://render.com/docs/configure-environment-variables)
- [Health checks](https://render.com/docs/health-checks)

