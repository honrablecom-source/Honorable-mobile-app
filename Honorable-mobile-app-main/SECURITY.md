# Honorable security model

Honorable is proprietary, privacy-first mobile software. Client binaries and
bundled models can be extracted and studied; no client-side control makes reverse
engineering impossible. Security reports should not include user media or secrets.

## Trust boundaries

- Memories and Terms processing stays on device and does not require attestation.
- Mobile clients never receive marketing database, email, platform, or signing credentials.
- Store entitlements must be verified from store evidence (or a bounded verified cache).
- Play Integrity and App Attest are risk signals for sensitive server-backed actions
  only, and their tokens/assertions require verification by a trusted server.
- Release signing keys and R8 mapping files are private operational material.

## Concise threat model

| Attack | Risk | Implemented mitigation |
|---|---|---|
| Download/decompile APK | High | R8 optimization, shrinking, and identifier obfuscation |
| Copy UI | High | Reduced release metadata; technical controls cannot prevent visual imitation |
| Extract search logic | High | R8 obfuscates surrounding implementation; private media remains local |
| Extract TinyCLIP | High | Pinned SHA-256 detects replacement/corruption; extraction remains possible |
| Patch premium checks | High | Verified-entitlement boundary; no local preference is authoritative |
| Redistribute patched APK | High | Official signing plus prepared server-verified integrity signals |
| Steal backend/signing secrets | Medium | Secrets excluded from clients/repository and supplied by CI environment |

## Remaining requirements

Production distribution must use Google Play App Signing and Apple-managed signing.
A local/CI Android release is unsigned unless all four `HONORABLE_RELEASE_*`
signing environment variables are supplied; key material and passwords are never
read from committed Gradle properties.
A purpose-built backend is required before Play Integrity, App Attest, or server-side
purchase verification can authorize high-value server actions.
