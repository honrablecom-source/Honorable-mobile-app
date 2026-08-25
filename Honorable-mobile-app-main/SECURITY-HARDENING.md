# Release security hardening

No mobile application can make shipped code or model assets impossible to copy,
inspect, or patch. Obfuscation raises reverse-engineering cost; it is not a trust
boundary. Honorable therefore keeps secrets and premium authority out of the JS
bundle and treats official signing plus verified store/server evidence as the
meaningful boundaries.

## Implemented

- Existing Android and React Native Android release variants use R8 optimization,
  shrinking, and identifier obfuscation. Debug logging calls are stripped where
  safe; JNI and React Native bridge entry points are narrowly preserved.
- React Native release builds no longer use the template's public debug signing
  key. Bundle/install/publish release tasks fail unless all four external
  `HONORABLE_RELEASE_*` signing variables are supplied.
- Debug builds retain the template debug key so development is not broken.
- Android backup and device-transfer extraction are disabled for the React Native
  app, including its local index, preferences, and derived media evidence.
- TinyCLIP model/tokenizer hashes remain validated at runtime. Model files are not
  encrypted with a client-side key because such a key would be extractable and
  could destabilize offline inference.
- Entitlements remain native/server-authoritative; unverified state is downgraded
  to Free. No JS value can grant premium access.
- A repository check rejects recognized private keys/tokens, debug-signed release
  configuration, and disabled release minification. CI also blocks critical npm
  advisories while the documented Metro high-severity chain awaits a compatible
  upstream fix.

## Operational requirements

- Use Google Play App Signing and Apple-managed distribution signing. Restrict
  signing credentials to protected CI environments and never expose them to pull
  requests or logs.
- Protect `main` and release branches with required reviews and passing checks.
- Keep R8 mapping files private and access-controlled; they reverse obfuscation.
- Verify purchases and high-value server actions on a trusted backend before they
  become authoritative. Play Integrity/App Attest are signals, not local unlocks.
- Rotate any credential immediately if a future scan finds it in Git history;
  deleting the latest copy alone is insufficient.
