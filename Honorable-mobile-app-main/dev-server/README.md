# Honorable development entitlement server

This dependency-free local server proves the Memory Pass flow before real billing. It owns balances, verifies known development products, records an append-only transaction history, makes purchase and debit operations idempotent, restores account state, and rejects V3/Ultra spending. Protected routes derive ownership exclusively from a verified bearer token; client account headers and body fields are ignored.

Run `npm test`, then `npm start`. Android emulators reach it at `http://10.0.2.2:8787`. `npm start` explicitly selects development mode. In production mode `/dev/auth/token` and `/dev/purchases` return 404, while protected APIs remain unavailable until a production identity verifier is configured.

The app charges only after native search succeeds. Failed or cancelled native searches are not charged. A stable request ID is assigned to that search operation before execution, and server idempotency prevents the same operation from being charged twice. If the charge is rejected, results are withheld and the authoritative error/balance is shown.

Google identity requires `HONORABLE_GOOGLE_WEB_CLIENT_ID` on both the Android build and server. Production server sessions additionally require a high-entropy `HONORABLE_SESSION_SECRET`; Android requires `HONORABLE_ACCOUNT_API_URL`. Google ID tokens are verified for RS256 signature against Google's JWKS, issuer, exact audience, expiry, and stable `sub`. The server maps `GOOGLE:<sub>` to an opaque Honorable account UUID; email and display name never select ownership.
