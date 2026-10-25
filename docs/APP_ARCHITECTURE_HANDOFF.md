# Honorable: app map for the next engineer

Updated October 24, 2026. This describes the existing product, not a replacement architecture. Read `AGENTS.md`, `SESSION_HANDOFF.md` and `OWNER_TASK_2026-10-24.md` first. The owner requires solo development, no computer-use tools, and batched feature integration before a full compile.

## Product and repository

The authoritative repository is `honrablecom-source/Honorable-mobile-app`. Publish reviewed changes to `main` without rewriting history. The app lives in `Honorable-mobile-app-main/`. The outer `.github/workflows/` owns CI. Adjacent ZIP extractions are not authoritative. Keep the Honorable name; the pasted Noble ad is not a confirmed rebrand.

The product combines a local media library, Seran natural-language search, original-media viewing, native photo/video editing and offline creation. Real generative image/video creation and photo rigging are not working capabilities. Do not market them as implemented.

## Entry and navigation

`mobile-react-native/App.tsx` mounts SafeAreaProvider, MemoryPassProvider and AccountGate. Signed-in content then mounts LibraryProvider, SearchModeProvider, LibraryGate and the React Navigation container. AccountGate also offers a separate guest editing path. Guest editing must not mount account/search/paid navigation.

| Surface | Source | Purpose |
| --- | --- | --- |
| Authentication and guest entry | `mobile-react-native/src/auth/AccountGate.tsx` | Session boundary and offline entry |
| Home | `src/screens/HomeScreen.tsx` | Search input, library status and editor entry cards |
| Library | `src/screens/MemoriesScreen.tsx` | Local media browsing and matching results |
| Studio | `src/screens/StudioScreen.tsx` | Open an existing photo or video for native editing |
| Guest studio | `src/screens/GuestEditingScreen.tsx` | Local photo/video editing, blank image creation and drawing |
| Media viewer | `src/components/MediaViewer.tsx` | Inspect original media and enter editing |
| Account | `src/screens/SettingsScreen.tsx` | Account, privacy and settings entry |
| Other stack routes | `src/navigation/AppNavigator.tsx` | Pass, plans, usage, models, feedback, storage/activity, legal and consent screens |

Paths beginning `src/` in this table are relative to `mobile-react-native/`. The four main tabs are Home, Memories (labelled Library), Studio and Account. Native editor windows are Android activities, not additional React Navigation tabs.

## Native boundary and local media

`mobile-react-native/src/native/HonorableNative.ts` defines the typed JavaScript/native boundary. `src/native/openWorkspace.ts` owns selection, account/guest routing and a shared busy guard. Extend this boundary rather than adding a parallel bridge. `mobile-react-native/android/app/src/main/java/com/honorablemobile/HonorableNativePackage.kt` contains the Android React methods.

Preserve the native/shared Seran implementation and bundled model/tokenizer. Do not rebuild search in JavaScript or inspect the protected benchmark holdout. A two-word query can be accepted without proving that arbitrary two-word descriptions reliably identify every memory. Evaluate retrieval on authorized development examples, then on a real device.

Media selection uses user-granted content URIs. Original files remain unchanged. Never infer a permanent permission from a URI string. Missing or revoked access must produce a clear recoverable error.

## Photo editor

Shared Android source is in `android-app/app/src/main/java/app/honorable/editor/`; the React Native Android module includes this source directory.

- `PhotoEditorActivity.kt`: tools, inspector, undo/redo, lifecycle recovery, preview scheduling and copy-export selection.
- `PhotoEdit.kt`: immutable scalar edit state. Keep new edits in this model so undo, compare and export use the same state.
- `PhotoRasterRenderer.kt`: the shared preview/export render path, including geometry, color, person masks, finishes, composition and captions.
- `PhotoSessionCodec.kt`: bounded versioned state for edit/history/pending-export recovery. Read earlier versions; reject invalid or unknown data.
- `PhotoPresets.kt` and `PhotoPresetStore.kt`: local adjustment looks, separated by guest/account/server namespace. Do not copy framing, text, source identity or person masks between photos.
- `SafePhotoCopy.kt`: encode privately, compare source/destination file identities before truncation, then verify output bytes and dimensions.
- `ImportedPhotoOriginals.kt`: new bounded provenance list for future multi-source creation handoff. Imported originals must also remain protected from destination aliases.
- `PhotoCreative.kt` and `PhotoCreativeControls.kt`: eight deterministic local color tools. These are not generative AI.
- `PhotoEffects.kt` and `PhotoEffectsControls.kt`: twelve additional local effects, including bounded spatial processing with one scratch array at most. Effect state is carried by session v6 and preset v3; older formats remain readable.

The raster pipeline applies geometry/tone, finish pixels, creative color, effects, person background, composition and finally caption text. `PhotoComposition.kt`, `PhotoCompositionRenderer.kt` and `PhotoCompositionControls.kt` add ten Authoring20 composition tools. Pixel sampling is source-coordinate based, preserves existing alpha through transforms and masks, and keeps presentation within the decoded pixel budget. The renderer operates on its private edited raster, never the imported bitmap. Composition is part of `PhotoEdit`, so preview, export, compare and undo share state.

Session decoding supports versions 1–7; writing chooses the minimum required version. Composition uses v7. Activity recovery calls `encodeForRecovery` to retain current/pending edits and nearest history within 64 KiB. Strict codec encoding still rejects oversize packets. Adjustment preset v3 remains unchanged and deliberately retains the target photo's composition; it never transfers another photo's watermark or framing. See AUTHORING20_READINESS.md for validation scope.

The twelve-effect stage follows creative color and precedes background replacement/text. It preserves the alpha at each output position, including cutout edges. It stores no source media or paths. A disabled effect can still retain its chosen duotone colors in saved state. The earlier creative-only encoding remains supported.

## Video editor and creation

`mobile-react-native/android/app/src/main/java/com/honorablemobile/HonorableVideoEditorActivity.kt` owns the Android video workspace. Reuse its project state, clip sequence, preview/export pipeline, captions, audio and inspector. Read existing `*READINESS.md` checkpoints before adding overlapping tools. A drawn timeline in an HTML preview is not proof of arbitrary compositing tracks or exported video effects.

`VideoBatchTools.kt` holds eight existing pure timeline/caption proposals; `VideoAuthoringTools.kt` adds ten more, exposed through the same eighteen-entry batch/inspector menu. New recipes produce clip ranges, speeds, volume gains, titles and captions supported by the existing renderer. They do not introduce a parallel project format. The activity checks a current project/account snapshot before review and again before `applyDraft`; validation precedes mutation, soundtrack is retained, and the whole operation is one undo step.

Manual beat-grid cuts do not analyze audio. Word captions estimate timing inside existing cues. Speed ramps use four discrete supported speeds. Timecodes refer to original source time. Caption ducking splits only the selected clip and scales its original audio; existing clip fades must be disabled first. These are local authoring workflows, not new inference engines.

`HonorableDrawingActivity.kt` hosts the existing bundled Excalidraw integration under an isolated local HTTPS origin. `DrawingPolicy.kt` restricts asset paths and PNG handoff size. File/network access is blocked. Created PNGs use the private creation FileProvider, then enter the existing photo editor. Keep this working integration intact.

miniPaint remains an external candidate, not an app dependency. Its main MIT license does not automatically cover all vendored code. A removed GPL notifier, other notice gaps, mobile integration, lifecycle, bounded drafts and protected copy output must all be resolved before shipping it.

## Account, credits and operations

`dev-server/src/server.js` hosts the existing local account API. `auth.js`, `sessions.js`, `ledger.js` and `search-completion.js` own authentication/session/ledger and completion behavior. `dev-server/src/admin/` and `src/beta/` provide existing operations infrastructure. Reuse these before creating duplicate admin sites. Related front ends live in `developer-console/`, `beta-operations/` and `dev-server/admin-public/`.

`product/catalog.json` defines product modes and pass reference prices. Preserve V1=1, V2=3, V3=8 but locked/unchargeable; 15 monthly free credits; free-first spending; no free rollover; nonexpiring purchases; no improper failed/cancelled charge. The server remains authoritative. Never grant a production entitlement or balance from a local `111111` test code.

The Google client configuration exists, but the hosted account API is unset/deferred. This prevents claiming end-to-end production sign-in. Billing and paid deployment are deferred. Existing free guest on-device editing remains authorized; do not silently impose new subscription restrictions while adding local tools.

## Builds and evidence

The historical Creative20 artifact is `executable/Honorable-Android-1.0.3.apk`, stored through Git LFS. Noble normal debug/internal APKs include four ABIs; the opt-in distribution matrix creates debug/release APKs for ARM64, ARMv7, x86-64 and universal (those three ABIs). Release APKs are unsigned without private signing; debug needs Metro, internal bundles JavaScript with the public debug key.

Packaging run 40235549888 passed before Authoring20. The complete twenty-feature source batch was committed in `78c92ba`, followed by the frame/reflection layout correction and a passing 117-case combined API/JVM check. Its final integrated build is pending. Owner requests all ten new APK files in `executable/`; final publication must update release metadata and hashes for the actual feature-bearing source. A later source change does not automatically update an existing APK. Do not relabel an earlier artifact as containing the new tools.

Use the existing pinned Node 22, Java 17, Android SDK and constrained Gradle settings recorded in the task's build scripts. Shared JVM tests, RN-native JVM tests, TypeScript, Jest, lint, full APK assembly and independent APK verification are separate checks. Android integration tests can compile on this PC but have not run on a phone. Do not claim compilation as device acceptance.

`.github/workflows/ios-build.yml` builds the simulator shell on macOS and uploads a ZIP on success. Windows cannot produce a signed iPhone IPA. Native iOS editor parity, signing and physical acceptance remain open; see `executable/README.md` for Mac commands.

`docs/ui/Honorable-current-preview.html` is an interface review artifact. RN-derived screens and native-workspace illustrations are labelled. It is not the installed app or device evidence. The illustrated product guide is `docs/releases/Honorable-Creative-Guide.docx`; keep it aligned with verified functionality.

## Next delivery gates

Finish and validate the current integrated batch, then compile/package once. Publish only verified source and matching release metadata. Extend the feature inventory with actual working tools rather than buttons, preset variants or imported module counts. Refresh preview and the illustrated guide after packaging. Remaining large gates are creation-engine integration, real generation/rigging, hosted account/payment operation, two-word retrieval validation, physical Android acceptance, native iOS parity and store signing.
