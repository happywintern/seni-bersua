# SuCash KMP

Kotlin Multiplatform project with 3 modules:

- `composeApp`: Android POS app (Compose Multiplatform)
- `shared`: shared SQLDelight DB, domain models, repositories, DTOs
- `server`: Ktor backend

## Android App Screens

- `Mobile First Setup`: first-run local initialization for outlet info, owner + cashier local PIN, pricing defaults, starter categories, and optional opening cash session
- `Orders`: local cached incoming orders with status workflow `Accept -> Done` (Selesai tab)
- `New Order`: walk-in order builder (optional table token, menu pick, cart)
- `Checkout`: checkout for the current draft order with payment option `Tunai` or `QRIS`
- `Menu`: group management, item CRUD, search/filter, temporary bundles (placeholder)
- `Recap`: today/week/month filters, metrics, chart, payment-method breakdown, top/slow movers
- `Settings`: receipt settings (store info, logo URIs, footer), sync, and shortcuts
- `Cash Flow` (subpage): shift-aware cash monitoring (opening cash, net cash sales, cash in/out, estimated cash position)
- `Stock` (subpage): low-stock alerts, manual adjustment, threshold, movement history
- `Cash Closing` (subpage): open shift, cash in/out, close shift with expected-vs-counted variance
- `Receipt Preview` (route): `receiptPreview/{transaksiId}`

## Build and Run

Prerequisites:

- JDK 17+ configured (`JAVA_HOME` + `java` in PATH)
- Android SDK installed and configured in Android Studio

Build commands:

```bash
./gradlew clean
./gradlew :shared:compileKotlinAndroid
./gradlew :shared:allTests
./gradlew :composeApp:assembleDebug
./gradlew :server:build
```

Project `.env`:

1. Root `.env` is now supported automatically by server runtime.
2. Template file: `.env.example`
3. Copy and edit locally:

```bash
cp .env.example .env
```

Main keys:

- `SUCASH_SERVER_HOST` (default `0.0.0.0`)
- `SUCASH_SERVER_PORT` (default `8080`)
- `SUCASH_SERVER_DB_PATH` (default `data/sucash-server.db`)
- `SUCASH_MEDIA_UPLOAD_DIR` (default `data/uploads`, for product image file storage)
- `TURSO_DATABASE_URL` (when set, server uses Turso/libSQL as primary DB)
- `TURSO_AUTH_TOKEN` (required for protected Turso DB, keep secret)
- `SUCASH_MIGRATE_LOCAL_SQLITE_TO_TURSO` (optional one-time bootstrap copy; `true/false`)
- `SUCASH_MIGRATION_SOURCE_DB_PATH` (optional source sqlite path for bootstrap copy)
- `SUCASH_API_SESSION_TTL_SECONDS` (session bearer TTL; default `86400`)
- `SUCASH_API_PAIRING_TTL_SECONDS` (pairing code TTL; use `0` for one-time lifetime until redeemed)
- `SUCASH_ALLOW_LOCAL_PAIRING_BOOTSTRAP` (allow owner pairing bootstrap from local/private network; default `true`)
- `SUCASH_ALLOW_LEGACY_TOKEN_AUTH` (allow legacy shared-secret bearer fallback; default `false`)
- `SUCASH_REQUIRE_EXPLICIT_OUTLET_ID` (require explicit outlet on API request; default `true`)
- `SUCASH_RESERVATION_RATE_LIMIT_ENABLED` (default `true`)
- `SUCASH_RESERVATION_RATE_LIMIT_MAX_REQUESTS` (default `5`)
- `SUCASH_RESERVATION_RATE_LIMIT_WINDOW_SECONDS` (default `60`)

Run server:

```bash
./gradlew :server:run
```

## Production Docker (VPS)

Container ports are set inside requested range `1000-1010`:

- Server API: `1000`
- Webapp: `1001`

Build and run:

```bash
docker compose build
docker compose up -d
```

Open:

- `http://<vps-ip>:1000` (Ktor server + API)
- `http://<vps-ip>:1001/web/` (React webapp)

Useful commands:

```bash
docker compose logs -f server
docker compose logs -f webapp
docker compose down
```

Notes:

- `docker-compose.yml` loads root `.env` automatically into `server` via `env_file`.
- DB + uploaded media persist in Docker volume `sucash-server-data` (`/data/sucash-server.db` and `/data/uploads`).
- Webapp `/api/*` is reverse-proxied by Nginx to `server:1000`, so browser CORS is not required.

If you see:

`JAVA_HOME is not set and no 'java' command could be found in your PATH`

set `JAVA_HOME` first, then rerun Gradle.

React webapp (Vite) source:

- Location: `webapp/`
- Build target: `webapp/dist` (served by Ktor at `/web/*`)

Build React webapp:

```bash
cd webapp
npm install
npm run build
cd ..
./gradlew :server:run
```

Optional custom dist path:

```bash
export SUCASH_WEBAPP_DIST=/absolute/path/to/webapp/dist
./gradlew :server:run
```

React dev mode (with `/api` proxy to local Ktor):

```bash
./gradlew :server:run
# in another terminal
cd webapp
npm run dev
```

Webapp outlet/server settings:

1. Open `http://localhost:8080/web/admin`
2. Set:
   - `API Base URL` (example: `http://localhost:8080`)
   - `Outlet / Store ID` (example: `default`, `main-branch`, etc)
3. Click `Save Settings`
4. Optional: click `Test Product Sync`

After this:

- `http://localhost:8080/web/menu` pulls products from server outlet (`GET /api/menu?outlet=...`)
- homepage menu cards also follow server product catalog for selected outlet
- `http://localhost:8080/web/reservasi` submits reservation to selected outlet (not hardcoded `default`)

Optional server DB path (defaults to `data/sucash-server.db`):

```bash
export SUCASH_SERVER_DB_PATH=/absolute/path/to/sucash-server.db
./gradlew :server:run
```

Use Turso as server database:

```bash
export TURSO_DATABASE_URL=libsql://your-db-name-organization.turso.io
export TURSO_AUTH_TOKEN=your_token
./gradlew :server:run
```

Optional one-time bootstrap (copy existing local SQLite data into empty Turso DB):

```bash
export TURSO_DATABASE_URL=libsql://your-db-name-organization.turso.io
export TURSO_AUTH_TOKEN=your_token
export SUCASH_MIGRATE_LOCAL_SQLITE_TO_TURSO=true
export SUCASH_MIGRATION_SOURCE_DB_PATH=/absolute/path/to/old/sucash-server.db
./gradlew :server:run
```

After successful bootstrap, set `SUCASH_MIGRATE_LOCAL_SQLITE_TO_TURSO=false` again.

Server config precedence:

1. Environment variable (shell/CI)
2. Root `.env`
3. Hardcoded default

## Web Flow (Server)

After `:server:run`, open:

- `http://localhost:8080/` for company/profile + seeded customer UUID links
- `http://localhost:8080/dashboard` for cashier order monitor + `Accept -> Done` actions + recap summary filters (Today/Week/Month)
- `http://localhost:8080/admin` for server admin onboarding (outlet provisioning + pairing code generation)
- `http://localhost:8080/t/{customerUuid}` for customer checkout page
- `http://localhost:8080/scan/{customerUuid}` as barcode-scan redirect target
- subdomain-style host is supported as redirect to table route (example host header: `{customerUuid}.localhost` -> `/t/{customerUuid}`)
- `http://localhost:8080/web/reservasi` for whole-cafe reservation form (stored in `cafe_reservation` table)
- `http://localhost:8080/web/admin` for web-side outlet/server settings (API base URL + outlet selector for `/web/menu` and `/web/reservasi`)

API endpoints used by the React web UI:

- `GET /api/menu?outlet=...`
- `POST /api/menu/upsert`
- `POST /api/media/menu-image/upload` (multipart file upload, returns `image_url`)
- `POST /api/menu/{id}/delete?outlet=...`
- `GET /api/customers`
- `GET /api/customers/{uuid}`
- `GET /api/orders?status=NEW,ACCEPTED,PREPARING,SERVED&outlet=...`
- `POST /api/orders`
- `POST /api/reservations`
- `GET /api/reservations?status=PENDING,CONFIRMED&outlet=...`
- `POST /api/reservations/{id}/status`
- `POST /api/orders/{id}/status`
- `POST /api/sync/transactions/batch`
- `GET /api/recap/daily?date=YYYY-MM-DD&outlet=...`
- `GET /api/recap/summary?range=TODAY|WEEK|MONTH&date=YYYY-MM-DD&outlet=...`
- `POST /api/admin/reset-all?outlet=...`
- `POST /api/auth/pairing/create`
- `POST /api/auth/pairing/redeem`
- `POST /api/auth/session/refresh`
- `POST /api/auth/session/logout`
- `GET /api/outlets`
- `POST /api/outlets/upsert`
- `POST /api/outlets/{id}/status`

Reservation endpoint protection:

- `POST /api/reservations` is IP-rate-limited (fixed window).
- When limit is exceeded, API returns `429 Too Many Requests` with `Retry-After` header.

Protected APIs (write + sensitive reads like orders/recap/reservations) now use **server session bearer token** auth:

- `Authorization: Bearer <token>`
- Token is issued by server after pairing (`/api/auth/pairing/*`).
- Token outlet + role must match requested outlet and endpoint permission.
- Legacy shared-secret bearer is disabled by default (`SUCASH_ALLOW_LEGACY_TOKEN_AUTH=false`).
- Outlet is explicit by default (`SUCASH_REQUIRE_EXPLICIT_OUTLET_ID=true`), so client should always send `outlet_id` (body) or `outlet` (query).

API response envelope (all `/api/*` endpoints):

```json
{
  "data": {},
  "message": "OK",
  "error": null
}
```

POST request envelope (recommended for web/mobile clients):

```json
{
  "data": {},
  "message": "request context",
  "error": null
}
```

## Mobile <-> Server

## Mobile-First Setup Flow

On a fresh tablet install, SuCash now starts in local-first mode:

1. Open the app on Android.
2. Complete `Setup Wizard`.
3. Choose setup path:
   - `Local Only` (offline-first now, connect server later)
   - `Connect to Server` (save server base URL during setup)
4. The app stores outlet identity, owner/cashier local account defaults, receipt config, pricing defaults, and optional opening cash session locally.
5. Server pairing is optional and can be done later from `Settings`.
6. After setup, app is locked by default and requires login using `Owner PIN` or `Cashier PIN`.
7. `Settings -> Device Access -> Lock App (Logout)` returns the app to login screen.

What this means:

- No server is required to start using the POS.
- Menu, modifiers, transactions, recap, and cashflow work locally first.
- Sync buttons stay disabled until `Server Base URL` is explicitly configured in `Settings`.

From Android app:

1. Open `Settings`
2. Set `Server Base URL` only when you want to pair a server
   - emulator: `http://10.0.2.2:8080`
   - physical device: `http://<your-pc-lan-ip>:8080`
3. Save settings
4. Set `Outlet ID` (recommended; defaults to `default` if left blank)
5. Generate pairing code from `http://localhost:8080/admin`.
   - First-time setup: owner token can be empty if request comes from local/private network and no active owner session exists yet.
   - After first owner session exists: owner bearer token is required.
6. Login again, then run `Pair Active User to Server` in Settings.
7. If cashier pairing is blocked, ask owner to provide a pairing code.
8. Open `Orders` screen and tap `Pull Orders`

Status updates in mobile (`Accept`, `Done`) are sent to server and reflected in web dashboard.
`New Order` also auto-pulls latest menu from server on open, and has a `Sync Menu` button for manual refresh.

Menu sync:

1. Open `Menu` screen
2. App auto-pulls menu from server on first open
3. Local item save/delete auto-pushes to server (if network/server available)
4. You can still use `Pull Menu` / `Push Menu` buttons for manual retry

Transaction sync:

1. Open `Orders` -> `New Walk-in Order` -> `Checkout`
2. App enqueues transaction to local outbox
3. App immediately tries to flush pending outbox to `POST /api/sync/transactions/batch`
4. `GET /api/recap/daily` is updated for dashboard recap

Cashier payment options:

- `Tunai`: cashier enters paid amount, change is calculated.
- `QRIS`: payment is treated as exact total (paid = grand total, change = 0).
- selected method is stored in payment record and included in sync payload.

Readable timestamp format:

- mobile screens now use `dd-MM-yy HH:mm` in notifications, orders timeline, transaction history, receipt preview, stock history, and cashflow history.

Stock engine (Sprint 2 foundation):

1. Checkout now auto-decrements local stock for each sold item (`SALE` movement).
2. Local stock tables in shared DB:
   - `stock_item_balance`
   - `stock_ledger`
   - `stock_threshold`
3. Server sync ingestion now mirrors stock decrement and writes server stock ledger for new synced transactions.
4. Low-stock and stock-history read hooks are available in shared `StockRepository`.
5. Current default policy allows negative stock; setting key prepared: `allow_negative_stock`.

Cash closing flow:

1. Shift lifecycle tables in shared DB:
   - `cash_session`
   - `cash_movement`
2. Mobile flow:
   - open shift with opening cash
   - record cash in/out
   - close shift with counted cash
3. App computes expected cash and variance at close.

Arus Kas summary logic:

1. `Total Pemasukan` = sum of all payment methods in selected range.
2. `Penjualan Tunai Bersih` = cash payments minus cancelled/refunded cash transactions.
3. `Posisi Kas Estimasi` = `Opening Cash + Cash Sales Net + Manual Cash In - Manual Cash Out`.
4. Weekly filter uses rolling last 7 calendar days (anchor day inclusive).

Website order confirmation:

1. On `/t/{customerUuid}`, customer can pick payment confirmation:
   - `Leave Blank`
   - `Pay at Cashier`
2. Value is stored on server and shown in dashboard/mobile order detail notes.

Outlet scoping:

1. Sync/menu/order/recap APIs are now outlet-aware.
2. Mobile uses `Settings -> Outlet ID`; if empty, app falls back to `default`.
3. For web, pass outlet in query string (example: `/dashboard?outlet=main`) and API calls should include `outlet`.

Manual full sync:

1. Open `Settings`
2. Save `Server Base URL` first
3. Use `Manual Sync` section:
   - `Sync All Now`
   - or individual `Pull Orders`, `Pull Menu`, `Push Menu`, `Flush Transaksi`
4. Reset actions are separated:
   - `Reset Local Data` (clears local data and returns device to setup flow)
   - `Reset Server Data` (calls `/api/admin/reset-all` for selected outlet)

## Notes

- QR scanner is Android (`QrScannerActivity`).
- Receipt screen now supports share + print actions (Android share intent + print manager).
- Bundle/promo recommendations are applied as auto-discount during checkout when date window + item conditions match.
