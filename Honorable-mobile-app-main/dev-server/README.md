# Honorable development entitlement server

This dependency-free local server proves the Memory Pass flow before real billing. It owns balances, verifies known development products, records an append-only transaction history, makes purchase and debit operations idempotent, restores account state, and rejects V3/Ultra spending. Protected routes derive ownership exclusively from a verified Honorable session; client account headers and body fields are ignored.

Run `npm test`, then `npm start`. Android requires an HTTPS account endpoint; use a configured HTTPS deployment or forwarding origin. `npm start` explicitly selects development mode. In production mode `/dev/auth/token` and `/dev/purchases` return 404, while protected APIs remain unavailable until a production identity verifier is configured.

The app charges only after native search succeeds. Failed or cancelled native searches are not charged. A stable request ID is assigned to that search operation before execution, and server idempotency prevents the same operation from being charged twice. If the charge is rejected, results are withheld and the authoritative error/balance is shown.

Google identity requires `HONORABLE_GOOGLE_WEB_CLIENT_ID` on both the Android build and server. Sessions are opaque, revocable, and persisted as hashes next to the ledger; Android requires `HONORABLE_ACCOUNT_API_URL`. Google ID tokens are verified for RS256 signature against Google's JWKS, issuer, exact audience, expiry, and stable `sub`. The server maps `GOOGLE:<sub>` to an opaque Honorable account UUID; email and display name never select ownership.


See [AUTH-EXPERIENCE.md](../AUTH-EXPERIENCE.md) for HttpOnly browser cookies, native refresh rotation, secure Android storage, required origins, and focused session tests. Production web clients require `HONORABLE_WEB_ORIGINS`; development endpoints stay disabled in production mode.
