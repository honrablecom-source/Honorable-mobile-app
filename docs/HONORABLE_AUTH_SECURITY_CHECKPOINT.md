# AUTH-01 Google ID token verifier checkpoint

Date: October 18, 2026. This checkpoint covers only [auth.js](../Honorable-mobile-app-main/dev-server/src/auth.js) and [google-auth-hardening.test.js](../Honorable-mobile-app-main/dev-server/test/google-auth-hardening.test.js). The existing Google and session tests were left unchanged. No app APK, server deployment, account configuration, ledger, or native code was changed.

## Behavior

- Google ID tokens still require a valid RS256 signature, the configured exact audience, either documented Google issuer, a nonempty stable `sub`, and an unexpired numeric `exp`. The returned `sub` and downstream `GOOGLE:` identity mapping are unchanged.
- JWT segments must be bounded, canonical base64url JSON objects; malformed headers, claims and signatures produce controlled 401 failures. Optional identity fields are type checked before reaching account or beta flows.
- JWKS accepts only usable RSA public verification keys of at least 2048 bits, with signing use/RS256 eligibility. Malformed provider responses fail as `GOOGLE_KEYS_UNAVAILABLE` (503). An expired cache is never used after a refresh failure.
- Provider `Cache-Control: max-age` and `Age` determine lifetime, capped at one hour. `no-store`/`no-cache` produce zero lifetime; a missing max-age has a bounded 60-second fallback. Unknown `kid` causes one shared refresh and a 15-second miss cooldown; failed refreshes have a 5-second retry backoff. Concurrent refresh requests share one fetch. Key fetch and JSON parsing are bounded by a 5-second default timeout (10-second maximum configuration) with abort signaling.

## Evidence

- Baseline `node --test test/google-auth.test.js test/session.test.js`: 6/6 pass.
- Final `node --test test/*.test.js`: 45/45 pass. New crypto-signed cases cover key rotation and expiry, concurrent unknown keys, no-store miss bounds, provider failure/backoff, timeout even when transport ignores abort, malformed token/JWKS data, and cache lifetime directives.
- `node --check` passed for the changed source and new test. No real Google token or user credential was used.

## Compatibility and remaining gates

The bounded cache can reject a newly rotated key for up to 15 seconds after an unrelated unknown-key miss, or return 503 during a Google key endpoint outage. It deliberately fails closed rather than accepting stale keys. Tokens with malformed optional claim types or RSA keys below 2048 bits now reject; normal Google RS256 ID tokens and existing subject mapping remain supported.

Google's [Android backend verification guidance](https://developers.google.com/identity/sign-in/android/backend-auth) requires checking signature, audience, issuer and expiry, and using the public-key response's `Cache-Control` header. This local signed-fixture result does not establish live Google integration. A configured HTTPS account service, real Google sign-in, and physical Android acceptance remain open; no phone is available, and M2 remains blocked.

## AUTH-02 production HTTP boundary test

The new [production-auth-flow.test.js](../Honorable-mobile-app-main/dev-server/test/production-auth-flow.test.js) uses `createServer` in production mode with the real `GoogleIdTokenVerifier`, locally generated RSA-signed synthetic ID tokens and a synthetic JWKS HTTP response. It uses only a temporary test ledger; no real token, account configuration or production verifier bypass is involved. The targeted test run passed **2/2**.

The HTTP cases show that a Google subject maps to an opaque stable account across profile changes; another signed subject cannot read or claim that account through forged body/header IDs or a reused search request ID. Wrong-audience, tampered and expired credentials reject. Native refresh rotates credentials; replay of the prior refresh token rejects; logout revokes its presented access and refresh tokens. Production `/dev` auth and purchases stay closed. Client-claimed search `SUCCESS` without a trusted completion verifier returns 503 and leaves the 15 free credits untouched.

**Historical review finding (before the director's fix):** At the HTTP route, replaying an old refresh token returned 401 before the session store's replay-revocation branch ran. The rotated session therefore remained valid after that replay. The initial AUTH-02 test asserted replay rejection and logout revocation but did not establish replay-triggered session revocation. No production code was changed in the initial AUTH-02 test ticket.

## AUTH-02 refresh replay resolution

The director resolved the HTTP replay finding at the route and session-store boundary. `PersistentSessions.refresh(token, authorize = () => {})` now performs replay detection and revocation before looking up a valid native session, checks beta access through the authorization callback, then rotates the session. The HTTP refresh route calls `sessions.refresh(token, id => beta.assertAccess(id))` without a preliminary lookup. A replay of an old refresh token revokes the rotated access and refresh credentials; beta denial does not rotate them. Existing direct callers of `refresh(token)` retain their behavior.

Independent review inspected that change and ran the focused Google authentication, hardening, production HTTP flow, session, and beta tests: **26/26 passed**. No production or test code was changed during that review. The director subsequently confirmed the full Node/product/build-tooling regression **72/72 passed**, plus browser authentication and product raster-export integration passed after the final server changes. These are separate from live Google sign-in and physical Android acceptance, which remain unverified. The Account API URL remains unlocated; no deployment is claimed. The verified M1 APK remains unchanged, and this server/test/browser-harness checkpoint did not rebuild it.
