# Noble readiness criteria

Owner instruction November14: keep developing a fully functional, investor-ready app with more genuine AI. Prioritize coherent outcomes and release blockers over feature counts. The current goal is active; it has not been achieved. Android first; iOS follows, as explicitly clarified by the owner. iOS parity is a subsequent milestone rather than a gate for the first Android demo.

| Area | Evidence now | Gate still open |
|---|---|---|
| Android core/editor package | Source0f0cc5a full build43621271622 and5-APK gate43625813335 passed; published tester remains3f3b506 | Combined four-tool1.0.4 candidate needs complete build/package proof |
| Full image/video studios | Real browser edit/project/export pipelines; native host and package checks | Broad media/error/recovery acceptance, phone WebView and performance |
| Useful local AI | Existing search, photo/video/creation AI; new general-object model and full-studio AI workflow source/browser verified | Model quality, resource limits, discoverability and combined native release |
| Search/account integrity | Native engine preserved; ledger/ranking/privacy guards retained | Approved live account API, OAuth production identity, actual sign-in/credit-backed search |
| iOS | Verified simulator and unsigned device shell archive at99421da | Native editor/search/AI host parity; private owner signing and installation |
| Release trust | Exact source/run/hash/signing provenance, immutable models/notices, current internal artifacts | Store signing/release review, privacy/support policy and reproducible product demo |
| Investor demo | Offline/guest creation workflows can be tested without account credentials | Execute [Android demo sequence](ANDROID_INVESTOR_DEMO.md), validate stable current build and platform/quality scope |

The owner authorized inspecting and, if needed, creating Google account/backend configuration through Chrome. The extension twice rejected `console.cloud.google.com`, most recently due to a saved permission/managed policy. Do not circumvent that browser block. Continue independent offline work; account setup requires access to be enabled through the normal connection before inspection/creation can proceed. Never print or commit tokens/secrets or invent a production endpoint.

The owner has no Android phone and prohibits Android VMs/emulators. Use Robolectric host tests, real engine browser tests and package/static checks. Those do not establish physical-device behavior or justify claiming zero bugs. Investor readiness must explicitly state platform, AI-quality, production-account and hardware gaps until they are resolved.
