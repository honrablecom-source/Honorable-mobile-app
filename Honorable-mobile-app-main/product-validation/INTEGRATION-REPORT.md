# Product integration evidence — 2026-09-13

Gate: **YELLOW for the verified local web demo; Android production readiness remains blocked.** Do not promote this to GREEN or represent the web editor as Android device execution.

## Verified

- Real engine: shared Kotlin `HybridSearchEngine` via the existing Linux adapter, TinyCLIP active. No JavaScript ranking, benchmark run, holdout changes or ranking tuning. 13 actual media files indexed once (11 photos, 2 videos), 28.8 seconds; 0 skipped.
- Photo integration: **9/11 predefined top-1 checks passed**. Folded-jeans query chose another asset; bright-room query returned no result. All cases remain in `results.json`.
- Demo A: “sunset over a sandy beach” → `photo-04.jpg`. Real result; top three and measured latency recorded in `results.json`.
- Demo C: that actual result opened in the web pixel editor, monochrome changed pixels, undo/redo and before/after worked, a new PNG was downloaded, and original SHA-256 stayed unchanged. `edited-copy.png` and both hashes are recorded.
- Browser operations: crop, rotate, brightness, contrast, saturation, exposure, reset, layer visibility/opacity/lock. Desktop panels do not overlap (220 / 935 / 285 px at 1440 px). See `editor-checks.json`.
- Account/Google verifier, opaque subject identity, session rotation/restore/logout/isolation, monthly grants/no rollover, free-first cross-balance spend, 31 catalog entries/default 20, purchased persistence, idempotency and Studio foundations: 24 focused Node tests pass.
- React Native account startup tests: 2 pass. TypeScript and web build pass. Browser auth lifecycle and product/editor integration checks pass.
- Live gateway rejects forged SUCCESS, cancellation costs zero, failed real searches cost zero, completed transaction replay charges once. V3 rejects both at the account/gateway and the Linux HTTP boundary; no fallback to V2.

## Completion trust boundary

The local gateway verifies the authenticated account, creates a stable ledger job, calls the real local Kotlin engine, creates an in-process receipt bound to account/request/model, validates that receipt at the account server and debits before releasing results. Proofs never go to browser code. Cancellation aborts the request. Completed results can be reopened without creating another search.

This verifier is **local-development only**. It is not remotely attested native execution. Production account completion without a provider fails closed with `SEARCH_COMPLETION_VERIFICATION_REQUIRED`. The Android client currently cannot supply a trusted production proof. Device attestation/production completion is a real launch blocker, not a successful check. A stopped gateway loses its short-lived result cache; an already completed ID is rejected for rerun rather than charged again.

## Not verified / not ready

- **Demo B NOT READY:** both declared V2 park-video queries returned no result. No independently distinguishable action moment was annotated for these similar sample videos. No video/moment advertising claim is supported.
- Android Google sign-in and physical-device editing/session restore have not been executed here. Follow `DEVICE-CHECKLIST.md` once configured.
- Real billing remains disconnected. Every purchase/Studio grant in this evidence is explicitly development-only.
- Native photo rig: NOT READY. Segmentation/deformation, Generative Fill, expansion, rig animation, camera tracking and 3D rendering remain unavailable.
- Local JVM indexing has no Android ML Kit labels and local Ollama vision was unavailable. These are disclosed platform differences, not hidden fallback results.

## Studio capability states

| Capability | Web | Android |
|---|---|---|
| Basic photo adjustment/export | AVAILABLE, pixel-tested | AVAILABLE implementation; device test required |
| Layer visibility/opacity | AVAILABLE, pixel-tested | FOUNDATION / no native layer drawer |
| Project tree / serialization | AVAILABLE | Web workspace only |
| Python / Java text editing | AVAILABLE (Studio entitlement) | Web workspace only |
| Python / Java execution | COMING SOON, disabled | COMING SOON |
| Video sequence / Nodes | FOUNDATION, Studio locked without entitlement | Not implemented |
| Pose/rig | COMING SOON / partial data foundation | NOT READY |
| Advanced AI / tracking / 3D | COMING SOON | COMING SOON |

The native tab root uses React Native screens, not the old Compose UI. Its photo editor still launches the existing Android raster activity; no browser transforms substitute for native processing. UI tokens are centralized, and the browser loads the new stylesheet instead of the five legacy CSS layers. The older native editor presentation has not been redesigned during this scope-frozen integration sprint.

## Privacy and security scope

Media and query text stay in the local gateway/Kotlin process; the account API receives account and ledger metadata, not media. No new credential logging or hardcoded production tokens was introduced. Native credentials remain in encrypted Android Keystore-backed storage and are excluded from backups; account requests require HTTPS. Development entitlement/purchase routes are absent in production. Resettable installation IDs are anti-abuse limits, not device attestation. Standard public debug signing material is not a production key. No production-secret values are included in this evidence.

## Reproduce

1. `node scripts/prepare-product-validation.cjs` verifies/copies the 13 manifest sources without reading benchmark directories.
2. Set `HONORABLE_TEST_MEDIA_ROOT` to the absolute `product-validation/media` directory, `HONORABLE_LEDGER_PATH` to an isolated temporary file, `HONORABLE_WEB_PORT=4184`, and `HONORABLE_SEARCH_PORT=4185`; run `node scripts/web-test.cjs`.
3. Run `node scripts/validate-real-product.cjs` and `node scripts/validate-editor-operations.cjs`.

The main web demo requires `VERIFIED_LOCAL_ENGINE`. `TEST_FIXTURE` is only configured by isolated UI test servers. The 22 images in `ui-preview-gallery` are layout fixtures; real results live in this directory. Screenshots retain their web/development context and are technical evidence, not proof of Google sign-in, billing, native rendering or cleared advertising media rights.

## Android build

Native build source: `d2df2d346f2da1aef05384c28b1f4681e5a47925`.
Actions run: https://github.com/honrablecom-source/Honorable-mobile-app/actions/runs/34786070365
Status at report creation: APK assembly running; auth compilation and TypeScript passed. Final build/configuration/signing evidence is in `android-build.json` once available. Later validation-only changes do not alter native compiled inputs.
