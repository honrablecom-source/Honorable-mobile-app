# Creative workspace release integration

November 13 owner direction: finish a focused AI creative app; the historical 500-workflow goal is cancelled. This work reuses MIT Three.js 0.187.0 and its GLTFExporter/GLTFLoader/OrbitControls alongside the existing offline Excalidraw workspace. The pinned package, lockfile and full MIT notice are included; no CDN, hosted generator or private-media upload is introduced.

## What is implemented

One new creative workspace in source: Drawing → Model. It supports a bounded object, material color/roughness/metalness, a local drawing-photo texture, orbit/zoom, turntable animation, model-edit undo, explicit local draft saving, PNG preview insertion into the drawing and real animated GLB export. Native Android texture selection uses the existing Add photo flow and a visible/unlocked drawing-photo chooser; a browser file input is not presented as a functioning Android picker.

The same workspace consumes the new native AI depth flow being implemented by the main chat. Three.js does not perform neural inference. An incoming local depth GLB is reviewed before opening; it does not automatically replace a saved model. Its existing geometry and embedded photo become an editable 2.5D relief with depth-strength/material controls, gentle parallax playback, explicit saving and animated GLB re-export. This estimates one visible surface, not unseen geometry, measured depth, complete object reconstruction or rendered video export. The ordinary primitive tools are not labeled AI.

The app's on-device search, model ranking, search history and billing/credit authority are unchanged by these files. The full photo/video AI integration, native build and release artifacts are owned by the main chat.

## Fixed native contract

Photo launches the non-exported `com.honorablemobile.HonorableDrawingActivity` with `EditorAccessMode.EXTRA=GUEST_BASIC` and `noble.depth.glb.path`. The path must resolve to a regular file whose canonical parent is exactly `cacheDir/noble-depth-handoff`; input is at most 3 MiB. The main producer uses an embedded PNG at up to 512 pixels and a 65×65 relative-depth grid. No large media extras or external content URI are accepted by this handoff.

`getDepthModelLength`, `readDepthModelChunk(offset)` and `releaseDepthModel` transfer bounded base64 to the trusted bundled page. Release clears the native RAM copy. The temporary file survives screen recreation; explicitly finishing the activity deletes it. Cache eviction or process interruption can still require regenerating an unsaved relief. Users must explicitly save a model to retain their edits.

Input validation requires GLB v2, embedded buffer/PNG/JPEG resources, ordinary bounded meshes (at most 20,000 vertices) and finite geometry. Remote resource URIs, compressed extension loaders, animations/skins in the incoming depth file and excessive geometry are rejected. Missing expected textures fail rather than silently yielding a textureless success. Blob connections are allowed solely for local embedded-image decoding under the existing CSP; external network loads remain blocked.

Output uses the real Three.js GLTFExporter and a separate Android FileProvider share bridge. It checks the GLB header/chunk layout, transfers bounded chunks (output up to 8 MiB), writes/verifies the exact file and invalidates unfinished operations on pause/close. Opening a share sheet is not proof that a destination saved the model. The GLB contains the intended photo texture; share only material the user intends to include.

## Storage and account boundary

Drawing remains guest-only and uses shared local WebView storage, including `noble-model-studio-v1`. Saved models and drawing copies can remain after logout and are not protected by an account namespace. There is no account-isolation claim. Main must obtain fresh, unchecked, explicit consent that describes this shared-device copy before allowing an account-owned photo into this guest workspace. Incoming data stays in memory until review and explicit save; Add preview to drawing intentionally creates a shared local drawing copy. A storage-full save preserves the previous saved model and reports the failure.

## Verified evidence and limits

- 825 creation-web Node cases pass, including the 10 focused model/import cases. These use the actual Three.js exporter, loader and animation mixer, test byte-exact chunk transport, relative geometry/parallax re-export, remote-resource rejection and malformed/nonfinite input.
- The isolated current drawing bundle compiles. Bundled notices include `THREE-MIT.txt`.
- The Chromium test starts with synthetic artwork, a 390×844 viewport, actual WebGL rendering, local texture/draft restore/undo, animated GLB download, a synthetic native chunk bridge, drawing insertion, and a synthetic 65×65 embedded-PNG native depth handoff → real GLTFLoader → reviewed opening → depth edit → saved draft → animated GLB export. Both 320px and 390px layouts pass without horizontal overflow. The final browser run also consumed the actual native-produced 977,288-byte depth GLB from the public NASA astronaut fixture (SHA-256 `59b0ed9f1cb31d66f68a0ce882664062d4fb69660eda5d4f1490b0f70284f219`), decoded its embedded texture, edited/saved the relief, re-exported animated GLB and verified that orbit controls change the rendered image. No external requests or page errors occurred. Neural inference was performed separately by the main chat using the native production model/mesh code; this browser test does not establish phone inference speed or depth quality.
- Five Android Robolectric SDK34 policy cases pass (13.172 seconds) for binary transport/header/budgets, cancellation/partial-file cleanup and private handoff paths. The production transport and all 10 current drawing Activity/helper sources also compile against Android API37 (external photo/AndroidX APIs supplied from the existing validated dependency jars; this is an isolated compilation, not a whole-app build). Actual WebView/Android share behavior, whole-app integration and fresh APK packaging still need the main release validation.
- Evidence is retained under ignored `.context/noble-fast-release/`; none of its tool caches or synthetic binary fixtures should be committed as a release artifact.

Count: one newly implemented source workspace, still experimental until native/device validation. The AI inference flow is joint integration work and is not separately counted here. Integration defects corrected during development are not additional features. No iOS parity, signed IPA, current APK, phone-quality or store-ready claim is made by this note.
