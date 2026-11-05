# C077 — Local native inspector tool search

**1 new experimental source workflow; 0 separate fixes/scaffolding.** One shared workflow serves the native photo and video inspectors; editor copies, result counts and Clear are not separate features. No full app/APK build or device acceptance is claimed.

The photo/video inspector now offers Find an editing tool, searching only the tool names and categories already supplied by that inspector. Multiple words must all match the combined category/name. Case, accents, compatibility forms and punctuation are normalized. Matching keeps the original category order and original action indices, including duplicate labels. The displayed tool names and registered callbacks remain unchanged.

The query is limited to 80 characters. A polite live result count, Clear and no-results guidance support discovery. Input uses a Done keyboard action and suppresses automatic keyboard display on opening; the Done handler asks the input method to hide. Filtering returns the results to the top of the scroll area. The shared white/black bottom inspector and existing 48 dp/80 dp controls remain. Actual keyboard/layout behavior is not yet tested on Android.

## Implementation and boundaries

`InspectorToolSearch` is a pure bounded-query matcher using the platform's Unicode normalization and root-locale case folding. It returns section/tool indices, never commands or generated actions. `NativeInspectorPanel` snapshots the supplied catalogue and maps results back to the same Tool objects. The caller's existing access-mode filtering remains authoritative; excluded tools are never added by search. Closing/finishing activities are guarded before invoking a selected action. The public Tool/show calling contract used by both current editors is preserved.

This searches the current inspector catalogue, not every action elsewhere in the app, personal media, captions, files or the web. No query history, logging, persistence, network request, new library or media access is introduced. Native on-device media search/ranking, AI availability, credits and server ledger are unchanged.

## Verification

- **Eight Kotlin host tests pass:** all-tools/empty query and original order; category plus multi-word name matching; Unicode compatibility/accents/punctuation/Japanese; Turkish default locale; duplicate labels/indices/source preservation; restricted/absent catalogues; unknown/punctuation-only queries; query limit and repeated terms.
- Actual current `NativeInspectorPanel`, `InspectorToolSearch`, unchanged `EditorChrome` and the tests compile against the verified Android API34 compile surface. The panel's old source was also compiled for comparison. All existing public methods and the Tool API remain compatible. An additional Kotlin compiler-synthetic TextWatcher bridge is recorded, not treated as a new app API or source defect.
- The test runner reports `OK (8 tests)`. Complete compile/test logs, downloaded dependency hashes, before/after calling-contract declarations and source hashes are under `.context/noble-fast-inspector-search/verification.json` and linked files.
- Framework/JUnit/Hamcrest jars were restored into this task's own `/tmp` directory and SHA-256 matched prior recorded artifacts. No shared dependency/build outputs were modified. Compilation reports the Android deprecation warning for the resize soft-input flag; it does not fail compilation.

The first ABI harness expected identical public listings and rejected Kotlin's additive synthetic bridge. It now verifies that existing declarations remain unchanged and every additional public member has `ACC_SYNTHETIC`. No production change was required for that harness correction.

No actual Android Dialog/EditText/IME/TalkBack/touch/screen-size or click-navigation execution; no complete photo/video activity integration compile, whole-app/Gradle/APK build or production-readiness claim. The API34 host compile and pure matching tests do not prove device UI behavior. Broad native integration and device checks remain for the owner's later build batch.
