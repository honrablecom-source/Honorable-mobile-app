# Honorable Developer Console

Private internal console at `/admin`, using the existing account process and authoritative ledger. No consumer navigation entry. No advertising work or search/model/credit/pricing changes.

## Run locally

From the repository root:

```sh
npm run admin:setup
npm run admin:dev
```

Open **http://localhost:4174/admin**. Setup asks for an OWNER identity and a password of at least 16 characters in the terminal (hidden input). It creates `dev-server/data/admin-users.json` with mode 0600 and a salted scrypt hash; the data directory is gitignored. No default password is provided. Set `HONORABLE_ADMIN_CONFIG` for another config path. Do not run a second account process against the same ledger file. Stop an existing `dev:test` process before starting `admin:dev`.

The console is disabled when admin configuration is absent or invalid. Both the bundled gateway and account server bind to loopback by default; admin routes also reject non-loopback connections. Use a private localhost tunnel for remote development. Do not publish the gateway or place `/admin` behind a public forwarding proxy: a proxy's loopback connection is not proof that its original caller is internal.

## Authentication and roles

Admin credentials and sessions are separate from consumer Google/account authentication. Server-side checks enforce OWNER, ADMIN and VIEWER. OWNER and ADMIN can inspect support email, restore existing verified ledger state, invalidate consumer sessions, inspect audit history and export aggregates. VIEWER has read-only analytics and opaque account details, without email or operational/export access. There is no arbitrary credit grant or account deletion action.

Additional identities are provisioned in the protected config file by an operator, using `passwordHash()` from `dev-server/src/admin/auth.js`; each entry has `username`, `role`, explicit `environments`, and the generated `password` hash object. Restart to reload identities and revoke admin sessions. Setup refuses to overwrite an existing config. Never put passwords in shell arguments or source control.

Sessions use random opaque tokens, server-side hashed storage, an eight-hour expiry and HttpOnly/Secure/SameSite=Strict host cookies. Writes require matching Origin and a custom CSRF header. Login attempts are rate limited. Production writes require HTTPS and explicit production authorization. Production deployment still requires a private network boundary, TLS termination and operator-managed identities; external SSO/MFA is not integrated. Each environment runs with its own ledger/config; switching the selector cannot grant another environment's access.

Sensitive operations append actor, action, opaque target, time and constrained reason to a hash-chained append-only audit file. The UI cannot edit audit history. Integrity/I/O failure blocks sensitive operations. This detects alteration under normal application operation; external immutable storage is needed for protection against a privileged host attacker rewriting the entire file.

## Data and definitions

Accounts, balances, 31 pass products, free grants and subscriptions come from the existing ledger. Development/test purchases never become real revenue. Unknown legacy provenance remains unclassified. Legacy accounts without a registration date are counted where their environment is known, but are excluded from dated growth/cohort calculations; unknown environments are excluded and disclosed.

DAU is the current UTC calendar day; WAU/MAU are rolling seven/thirty-day windows. Activity means authenticated session activity, search, result opening or editor interaction, not polling alone. Returning means activity on more than one observed day. Growth supports daily/weekly/monthly buckets and 24h/7d/30d/90d/all ranges. Current entitlements determine the four segments. Retention measures exact UTC D1/D7/D30 returns only for mature cohorts observed since collection began; absent history is unavailable. The funnel reports observed user intersections, not a claim of strict event ordering.

Search outcomes and credits derive from server jobs/ledger, with replay-safe accounting. Latency uses trusted engine timing where supplied, otherwise server transaction duration. Photo/video classification describes trusted completed result types, not the contents or intent of every query. Browser editor/tool/workspace and coming-soon interest events are client-reported. Interest is never labeled feature execution. Detailed event-based reports cover retained raw events; daily activity/cohort aggregates persist longer. “All” does not recover deleted raw detail.

Telemetry accepts only central event/metadata allowlists, authenticated account assignment and server-assigned environment/time. It rejects raw queries, filenames, paths, images, videos, code, arbitrary strings, tokens and credentials. Support email comes only from legitimately stored account profiles and is withheld from VIEWER. CSV exports contain aggregates, not account email or private content. Client metadata is useful operational context, not financial authority.

## Storage and failure behavior

`AnalyticsStore` exposes record/snapshot/flush/close separately from the ledger, allowing a later database adapter. Defaults: 30 days of detailed events (maximum 50,000), 400 days of daily aggregates, 90 days of hashed deduplication keys. Configure `HONORABLE_ANALYTICS_RAW_DAYS` (1–90) and `HONORABLE_ANALYTICS_AGGREGATE_DAYS` (30–730). Daily aggregates retain opaque account sets for retention calculations. Authoritative ledger and audit records are not removed by analytics retention.

The lightweight store is for one account process; move storage/aggregation and audit to appropriate durable services before scaling to multiple writers. Writes are deferred and atomic; failures disable collection without interrupting product operations. Browser events batch at 20, queue at most 60, time out after three seconds and drop failed batches. No indefinite retries. Set browser localStorage `honorable-analytics-disabled` to `1` to disable optional interaction events.

## Coverage and remaining integrations

Working: Overview, Users, Search, Memory Pass, Studio, Editor, Retention, Errors, System and Audit; date/environment controls, directory filters/details, safe support actions, aggregate CSV, near-real-time activity, server event and browser interaction collection.

Partial by design of the current product: native editor/platform/version instrumentation, native crashes, release/outdated-version inventory, index-request telemetry, production billing revenue, production identity deployment, and historical data before collection. System health probes account/ledger/analytics and, through the bundled gateway, search/web services; it does not certify physical-device or production health. Existing production native search still requires its trusted completion verifier integration. No native UI or compiled input was changed in this console sprint.

## Validation

```sh
npm run test:product
npm run test:admin:web
npm run test:auth:web
npm run test:product:web
npm run build:web
```

31 server/product tests pass, including admin login/CSRF/roles/environment isolation, DAU/WAU/MAU and segments, ledger/model/pass metrics, deduplication, privacy rejection, retention bounds, audit integrity and analytics-outage resilience. Browser tests cover actual editor interactions and all ten admin pages, support detail, 31 products, CSV and logout. Search in `test:admin:web` is explicitly a test fixture; screenshots in `previews/` show isolated DEVELOPMENT activity, not production customers.

An additional live gateway validation passed with the real shared Kotlin engine and predeclared local beach query: authenticated search, trusted typed completion, actual monochrome edit/save-as-copy, browser telemetry flush, separate admin login through the real gateway, and aggregate checks for success, photo completion, save and WEB_TEST platform. `previews/real-development-overview.png` captures that run. Its temporary test credentials were kept outside the repository; they are not operator credentials.
