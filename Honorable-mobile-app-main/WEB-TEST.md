# Honorable browser testing

From the workspace root, run `npm run dev:test` and open http://localhost:4174 (or forward port 4174 privately in Codespaces). The existing Android-style shell is a browser simulation. Its media/search routes go only to the local Kotlin test-lab adapter on 4175. Initial Gradle startup can take a minute; the shell retries while it starts.

Usage → Import local test media accepts selection and drag/drop. Files are copied to `android-app/test-lab/web-test-shell/storage`, or `HONORABLE_TEST_MEDIA_ROOT`, and refreshed through the existing shared indexer. Supported browser-test inputs: JPEG, PNG, WebP, MP4, MOV, M4V, WebM, MKV (browser playback depends on codecs). No media is sent to the account service or Google. Search ranking and confidence come from the Kotlin core; below-threshold candidates remain inspectable and are labeled accordingly.

First launch shows Welcome inside the phone. Google Sign-In restores the same server account and credits on subsequent launches. Settings → Account includes Sign out. A persistent HttpOnly/Secure/SameSite cookie replaces the old sessionStorage bearer token. See [AUTH-EXPERIENCE.md](AUTH-EXPERIENCE.md) for session lifetime, offline limits and security details.

For development testing only, the “Use test account” control sits outside the phone. It is only available with the bundled development service. Pass testing remains simulated; no payment is collected.

`HONORABLE_ACCOUNT_API_URL` selects the account service. When unset, pointing to localhost:8787, or pointing to this Codespace's forwarded 8787 origin, the launcher starts the bundled development ledger on loopback and uses it directly. Other account URLs are proxied server-side; development token and purchase endpoints are blocked for them. Persistent session records remain server-side. Only `HONORABLE_GOOGLE_WEB_CLIENT_ID` (a public client identifier) is provided to the browser. The bundled server uses the same configured client ID to verify Google signature, issuer, audience, expiry and subject, and resolves account ownership by Google subject, never email.

Google Cloud: in the existing Web application OAuth client identified by `HONORABLE_GOOGLE_WEB_CLIENT_ID`, add `http://localhost`, `http://localhost:4174`, and the actual forwarded HTTPS port-4174 origin to Authorized JavaScript origins. No redirect URI is needed for the GIS credential callback. See https://developers.google.com/identity/gsi/web/guides/get-google-api-clientid . Android Credential Manager is not used by the browser.

Focused verification: `npm run build:web`, `npm run typecheck`, `npm run test:web` (launcher running; uses the existing ui-previews Playwright install), and `node --test Honorable-mobile-app-main/dev-server/test/*.test.js`. UI testing creates a development account, simulated purchase and imported test photo. Static build output is under `android-app/test-lab/build/web-shell`; no personal media or secrets are copied into it.

## Android screen presentation

The primary browser presentation is now `android-ui.js` / `android-ui.css`, ported from `android-app/app/src/main/java/app/honorable/MainActivity.kt`, the documented Android source of truth. It uses its Home, Memories (landing/focus/searching/results), Terms, Activity and Settings hierarchy, five-tab dock, layout dimensions, and copy. Roboto and official Material Rounded SVG assets are bundled locally with their licenses. The prompt PNGs are byte-identical to Android resources. The user-requested neutral black/white palette is retained.

Models, Pass and Usage & import are browser testing tools above the phone, and retain the existing account and shared search adapters. Activity shows actual searches from this browser session rather than Android's hard-coded sample events. Terms retains its screen layout but explicitly reports that its analysis adapter is unavailable; it does not fabricate analysis. Native rendering/font metrics and browser rendering are separate implementations; pixel identity has not been verified against an Android screenshot.

UI checks: `node Honorable-mobile-app-main/scripts/test-android-ui.cjs` compares source-backed dimensions, text and navigation and checks account/import access; `node Honorable-mobile-app-main/scripts/test-web-scroll.cjs` tests real wheel/touch scrolling and fixed navigation at three sizes. Neither runs search benchmarks.
