# OfflinePay

A native Android wallet app for online and offline payment flows, wallet balances, and transaction history.

## Run & Operate

- `gradle -p native/offlinepay-android :app:assembleDebug` — assemble the Android debug APK (requires Android SDK 35)
- Open `native/offlinepay-android` in Android Studio to run on an emulator or Android device
- `pnpm --filter @workspace/api-server run dev` — run the API server (port 5000)
- `pnpm run typecheck` — full typecheck across all packages
- `pnpm run build` — typecheck + build all packages
- `pnpm --filter @workspace/api-spec run codegen` — regenerate API hooks and Zod schemas from the OpenAPI spec
- `pnpm --filter @workspace/db run push` — push DB schema changes (dev only)
- Required env: `DATABASE_URL` — Postgres connection string

## Stack

- Native Android app: Kotlin, Jetpack Compose, CameraX, and ML Kit
- pnpm workspaces, Node.js 24, TypeScript 5.9
- API: Express 5
- DB: PostgreSQL + Drizzle ORM
- Validation: Zod (`zod/v4`), `drizzle-zod`
- API codegen: Orval (from OpenAPI spec)
- Build: esbuild (CJS bundle)

## Where things live

- `native/offlinepay-android/` — standalone Android Gradle project
- `native/offlinepay-android/references/` — original design PDF, flow image, and implementation brief

## Architecture decisions

- Payments and phone verification fail closed in non-demo builds until real providers and a secure backend are configured.
- Debug-only local payment records are development data, not authorized or settled transactions.
- Screens missing from the design PDF follow its colors and component language; their exact layout is not represented as reference-perfect.

## Product

- Onboarding and phone/OTP flow
- Home, UPI selection, QR scanning, payment details, offline payment, and confirmation
- Online/offline wallet balances, offline reserve, and transaction history

## User preferences

- Preserve the supplied visual reference and flow; do not add unrequested actions or colors.
- Keep real payment and offline-token claims separate from development-mode behavior.

## Gotchas

_Populate as you build — sharp edges, "always run X before Y" rules._

## Pointers

- See the `pnpm-workspace` skill for workspace structure, TypeScript setup, and package details
