# Honorable staged React Native migration

Status: Phase 0 audit and Phase 1 foundation only (2026-08-25). The existing Android application is the behavioral, visual, and search source of truth. Nothing in `android-app/`, `ios-app/`, the test lab, model assets, ranking, OCR, video, authentication, or billing was changed during this phase.

## Source-control safety

- Migration branch requested: `react-native-migration` (created in the outer repository).
- Important baseline issue: the outer Git commit tracks `Honorable-mobile-app-main.zip`; the extracted `Honorable-mobile-app-main/` tree is wholly untracked. Therefore the working Android source is not protected by normal file-level Git history, and no migration commit is made in this phase. Before committing migration work, import and verify the extracted baseline without losing its provenance.
- Rollback for this phase: continue building `android-app/`; it has no dependency on `mobile-react-native/`. The new directory can be excluded from a build without touching the native app. Do not delete the Android UI until every cutover gate in the mission is satisfied.
- Feature flag: `mobile-react-native/src/config/migrationFlags.ts` defaults `useReactNativeUi` and `searchBridgeEnabled` to `false`. This is an architectural marker until a native build-time entry-point flag is added. The independent Android app remains the actual fallback.

## Repository map before migration

| Concern | Current implementation and status |
|---|---|
| Android app/UI/navigation | `android-app/app/src/main/java/app/honorable/MainActivity.kt`: one Compose activity, `HonorableShell`, five-tab `MainTab` (Home, Memories, Terms, Activity, Settings), overlays for viewer/privacy/Plus, shared glass/theme composables. Functional source of truth. |
| Product contracts | `ProductArchitecture.kt`: screens, Free/Plus preview plan state, account/subscription interfaces, verified entitlement and integrity boundaries. Google credentials and billing stores are explicitly unconfigured; no production integration is present. |
| SearchCore | `search/SearchCore.kt`: platform-neutral media/query/evidence/result contracts, `QueryParser`, `QueryRefiner`, `SearchRanker`, confidence decision, local vector index, OCR normalization/debug tools. Filename is identity/display only and has no ranking weight. |
| SearchPipeline | `search/SearchPipeline.kt`: cached semantic query encoding, two-stage vector candidates plus hybrid ranking, color evidence, representative video frame selection. |
| SearchEvaluation | `search/SearchEvaluation.kt` and `search-evaluation/`: recall/MRR harness and example dataset. |
| Vision enrichment/VLM | `search/VisionEnrichment.kt`: model-neutral service, priority queue, complexity/smart confidence gates; disabled by default. Linux adapter provides cached local Ollama/Moondream behavior. |
| Production Memories | `search/ProductionMemorySearch.kt`: Android `MediaStore` discovery, incremental synchronization, thumbnail loading, ML Kit text/image labeling, representative video decoding, DB updates, `MemoriesViewModel`, permission and search states. |
| TinyCLIP/ONNX/tokenizer | `AndroidTinyClipEmbeddingService.kt`; ONNX Runtime Android; bundled `model_int8.onnx` and `tokenizer.json`; hash checks; 224px CLIP preprocessing; 512-dimensional normalized embeddings. Test lab uses the same assets through `tinyclip_bridge.py`. |
| OCR | Android ML Kit in `AndroidMediaIndexer`; Linux Tesseract adapter; normalized by shared Kotlin semantics. No iOS Vision OCR implementation yet. |
| Media | Android `MediaStore.Images` and `MediaStore.Video`; stable content URIs and modified times. Linux is deliberately restricted to `test-media/`. No React Native filesystem scanning. |
| Index/database | `LocalMediaDatabase.kt`: SQLiteOpenHelper schema v5 for media records and timestamped video frames; additive migration policy, embedding compatibility metadata, incremental modification/deletion handling. |
| Video | Android `MediaMetadataRetriever`, five candidate positions, representative frame selector, OCR/labels/embedding/colors and best timestamps. Linux uses FFmpeg with shared selection/ranking. |
| Background indexing | Current indexing is coroutine/view-model driven; no durable Android WorkManager/service integration was found. This remains a future native requirement. |
| Authentication | Android UI-safe `AccountService` boundary is unconfigured. No Credential Manager implementation/configuration was found. iOS has Keychain storage primitives, but Sign in with Apple/Google is not implemented. |
| Billing/entitlements/quotas | Android interfaces and verified-boundary comments exist, but Google Play Billing and real product loading/purchases are not implemented. Current native enum exposes Free/Plus only and preview UI contains hard-coded preview prices; this conflicts with the mission's five-tier target and must be resolved as a separate product/data migration without inventing prices. No normalized quota implementation was found. |
| Settings/Activity/Home/Memories/Plans | Compose screens live in `MainActivity.kt`. “Plans” is currently a Plus preview overlay, not a five-tier store-backed plan screen. |
| iOS | `ios-app/`: separate early SwiftUI shell, protocol stubs, Keychain helper, unavailable TinyCLIP placeholder, XcodeGen project and XCTest. Photos, Vision, AVFoundation search, StoreKit 2, and Sign in with Apple are not implemented. It is preserved, not replaced. |
| Tests/test lab | Android JUnit tests cover search, integration contracts, and security. `:test-lab` compiles shared Kotlin sources and tests parity, filename independence, discovery, confidence, and vision enrichment. `linux-demo.sh` exposes verify/index/start. |
| CI | Existing Android build/release verification on Ubuntu and native iOS compile/TestFlight workflows on macOS are preserved. `react-native-checks.yml` adds isolated JS/TypeScript checks. |

`SearchPlan` is represented by `SearchQuery`; no class named `SearchPlan` was found. The `QueryParser` is present. No Room usage was found despite Room dependencies; the production index uses `SQLiteOpenHelper`.

## Foundation created

`mobile-react-native/` is an official bare React Native 0.87.0 TypeScript project with independent Android and iOS native targets. React Navigation provides a typed native-stack and bottom-tab shell. The UI foundation has reusable tokens plus screen, glass-card, button, and loading/empty/error state components. Home, Memories, Activity, Settings, and nested Plans are deliberately marked `UI_ONLY`; there is no mock or production search path.

The shell preserves the dark navy, blue/cyan/lilac glass language, round surfaces, high contrast, safe areas, minimum button size, basic accessibility semantics, Android Back/native stack behavior, and iOS native-stack gestures. Animation is limited to native navigation and pressed feedback; Reanimated is deferred until a real interaction requires it.

## Intended boundary (not implemented yet)

```text
React Native TypeScript screens
  -> typed HonorableSearchModule DTOs (IDs/URIs/metadata, paginated)
    -> existing Kotlin search/index contracts on Android
    -> future equivalent native/shared adapters on iOS
```

The eventual bridge may expose `search`, `cancelSearch`, `getSearchStatus`, `getResultDetails`, index status/progress/refresh, normalized account state, and normalized trusted entitlements. It must not expose tensors, full-resolution buffers, tokens, secrets, or ranking controls. JS contribution to ordering/confidence remains 0%. Native responses remain ordered and the list only renders that order.

Before an Android search route is called complete, add old-path-versus-bridge parity tests for Top 1, ordering, semantic/final scores, confidence, timestamp, and query interpretation, including the existing beach/tennis/shoes fixtures where available. Status must progress honestly from `UI_ONLY` to `NATIVE_CONNECTED` to `PARITY_VERIFIED`.

## Privacy and network audit

The new React Native shell adds no application network endpoint and sends no user/account/media data. npm dependency download is build tooling, not an app runtime call. Current core Memories analysis is local. Future account, entitlement verification, integrity, and store calls must be recorded here with endpoint, purpose, data, authentication, and whether required before implementation. Media uploads must never be introduced as a migration shortcut.

App Store privacy and Google Play Data Safety declarations cannot yet be finalized: account/billing integrations are unconfigured. Local media analysis must be distinguished from future account/billing network data.

## External configuration and platform reality

- Android future configuration: `HONORABLE_GOOGLE_WEB_CLIENT_ID`, Play product IDs, entitlement backend URL, and release signing variables; none may be hard-coded.
- iOS future configuration: Apple Developer membership, bundle/App IDs, Photos descriptions, Sign in with Apple capability, StoreKit products, App Store Connect app, certificates, and provisioning profiles.
- Linux/Codespaces can run TypeScript, Jest, lint, Android Gradle tasks where SDK/tooling permits, and shared test-lab verification. Final iOS simulator/device, signing, archive, TestFlight, and App Store validation require macOS/Xcode.

## Phase status and next gate

- Phase 0 audit: complete, with baseline-versioning blocker documented.
- Phase 1 shell: complete at source/check level; device boot remains to be exercised on configured Android/macOS environments.
- Phase 2+: not started. SearchCore, TinyCLIP, OCR, MediaStore, index semantics, video ranking, auth, billing, and existing iOS sources remain unchanged.

Verification in this Codespace:

- React Native: `npm run typecheck`, `npm run lint`, and Jest all pass (1 suite/1 test).
- Full Android `./gradlew test`: blocked before tests because no Android SDK path/install is available in this Codespace. The initial default JDK 25 was also incompatible; rerunning with installed JDK 21 reached the SDK check.
- Linux/test-lab `:test-lab:test`: compiled and ran 19 tests; 18 passed and `FilenameIndependenceTest` failed because `tinyclip_bridge.py` could not import the pre-existing external Python dependency `numpy`, producing a null embedding. No test or engine source was changed to hide the environment failure.
- iOS native build: intentionally not run on Linux; macOS/Xcode is required.
- Dependency install reported nine high-severity npm audit findings. They require a deliberate dependency review; no unsafe forced audit rewrite was applied.

Review this foundation before migrating Home/navigation presentation. Before any commit, first establish a trusted Git baseline for the extracted existing app. Every later phase must state rollback, tests, and one of `UI_ONLY`, `MOCKED`, `NATIVE_CONNECTED`, or `PARITY_VERIFIED`.
