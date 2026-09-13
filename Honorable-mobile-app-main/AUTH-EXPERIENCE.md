# First-launch and persistent account experience

`npm run dev:test` from the workspace root starts the existing shell, local Kotlin search adapter and bundled development account server when the configured API refers to this machine. Open http://localhost:4174 or its private Codespaces forwarded URL.

The launch screen stays visible while the Honorable account session is checked. No session means Welcome → Continue with Google. A valid returning session restores the account, entitlements and credits before Home. Settings includes profile presentation, credits, subscription status and Sign out. Media is never sent with authentication.

## Session authority and storage

Google Identity Services is used only in the browser. Both Android entry points use the existing `GetGoogleIdOption` / Credential Manager flow, shared in `GoogleAccountSignIn.kt`. The server verifies Google's signature, issuer, audience, expiry and subject. Only the verified subject selects an opaque Honorable account; names and email are presentation fields.

The account server now uses cryptographically random, revocable sessions. The ledger and identity map are preserved. The adjacent `<ledger-file>.sessions` file contains only SHA-256 token hashes and session metadata, is atomically written with owner-only permissions, and must live on persistent private server storage. Existing stateless sessions require one fresh sign-in during this migration.

Browser sessions use `__Host-honorable` with HttpOnly, Secure, SameSite=Strict, Path=/ and a 30-day Max-Age. Token values never enter browser JSON, sessionStorage or localStorage. Same-origin writes require an explicit web header and an allowlisted Origin. Only the Honorable cookie is forwarded by the gateway. No JavaScript bearer-token fallback exists. HTTPS is required remotely; Chromium allows secure cookies on localhost.

Android keeps native access and refresh credentials plus its account cache in an AES-GCM encrypted AtomicFile under `noBackupFilesDir`; the encryption key is in Android Keystore. Cache entries are bound to the configured account API. Native account methods return account presentation data to React Native, not persistent credentials. Access tokens last 15 minutes; refresh tokens rotate and expire after 30 days. Reusing an old refresh token revokes that session family.

Temporary outages can use a previously verified account snapshot for at most 24 hours, never beyond session expiry. This is labeled offline and cannot authorize purchases or credit deductions. Invalid/revoked sessions clear the cache. The browser's cached profile/balance contains no token or account identifier and is not server authority. This permits local functionality in an already loaded shell or during account-service outages; it does not install an offline PWA/service worker.

Sign out clears local account state and revokes the current server session when reachable. Android clears its encrypted session even offline. Browser offline sign-out records a non-secret pending-logout marker, clears profile state immediately, and blocks silent restoration until the HttpOnly cookie can be cleared on reconnection. Neither platform deletes the server account, credits, media, or index. An unreachable server cannot immediately revoke its own record; local logout still prevents that device from restoring it automatically.

## Configuration and final device checks

- Set `HONORABLE_GOOGLE_WEB_CLIENT_ID` and HTTPS `HONORABLE_ACCOUNT_API_URL` for the Android build and the account service. No Google client secret is used.
- For a separately deployed account service, set `HONORABLE_WEB_ORIGINS` to the exact comma-separated browser origins. The bundled launcher supplies localhost and the current Codespaces origin automatically.
- In the existing Google Web OAuth client, authorize `http://localhost`, `http://localhost:4174`, and the actual forwarded HTTPS origin. The GIS callback needs no redirect URI.
- Android OAuth registration must cover the installed package and its signing SHA-1. The existing Android build workflow reports the APK certificate fingerprint. Register the actual release package/certificate for production; do not substitute the web client for Android package registration.
- Run the Android build workflow and test Google account selection, process-kill/relaunch restoration, airplane-mode fallback, sign out and same-account credits on an Android device. This workspace has no Android SDK, so APK compilation/device execution was not verified locally.

## Focused checks

- `npm run build:web` and `npm run typecheck`
- `npm run test:auth:account`: account, Google verification, cookie persistence/CSRF, rotation, replay rejection, expiry and ledger retention.
- `npm run test:auth:web`: browser startup, no Welcome flash, close/reopen and server restart, HttpOnly persistence, offline logout, expiry and same-account credits. Uses signed Google test fixtures through the real verifier; only the Google account picker and key download are substituted inside the test.
- `npm --prefix Honorable-mobile-app-main/mobile-react-native test -- --runInBand AccountStartup.test.tsx`
- From `android-app`: `./test-lab.sh :test-lab:test --tests app.honorable.auth.SessionPolicyTest --console=plain`

No Seran benchmarks, holdout tests, Play Billing work, ranking changes or unrelated cleanup are part of this change.
