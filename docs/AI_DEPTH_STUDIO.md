# Local AI depth photo and creative workspace integration

This is a release feature, not progress toward the cancelled500-count target. The owner requested substantially more actual AI capabilities and reuse of established open-source projects.

## Working source path

Photo editor → Assist → AI depth animation captures the complete edited preview, fits a512edge working image, runs bundled pretrained Depth Anything V2 Small locally, shows photo/depth previews and requires fresh approval before opening a new editable textured2.5D scene. The Three.js workspace adjusts depth/material/camera/parallax and exports a real animatedGLB; it also saves an explicit local draft or inserts aPNG preview. Relative inferred depth is not metric depth, a complete3D reconstruction or unseen surfaces. No private media/query upload or model training.

Models/runtime/projects reused: official Apache2 Depth Anything V2 Small, onnx-community ONNX conversion, existing ONNX Runtime Android1.20, pinned MIT Three.js0.187.0 and existing offline Excalidraw workspace. Weights are unmodified and bundled: revision4472b7362082ad9968fee890ca0f1e5aca36b93d,27258801bytes, SHA256fcf51f1b230362b28690bb9d1809bf0431f29cad20534e3f589bd7285547f20d. Full provenance/license under native assets/depth. Mobile adapter uses bounded aspect-preserving bilinear resizing, patch multiples14 and ImageNet RGB normalization. This adaptation does not claim exact desktop preprocessor parity.

## Ownership and review

The source image/current edit are never mutated. Snapshot/model/bitmaps/cache hold the parent's worker lease until actual settlement after Stop/pause. Source/session/generation/protected-original state is rechecked. Every unconfirmed/stale/failed handoff deletes its generated temporary copy. The receiving drawing workspace is currently shared local guest storage. Account-owned images require a separate unchecked approval explicitly stating copies remain accessible after sign-out or switching accounts. Incoming geometry stays inRAM until an explicit Save/Addpreview; it never silently replaces a saved scene.

Private handoff contract: internalDrawingActivity, GUEST_BASIC mode after explicit copy approval, noble.depth.glb.path under exact canonical cache/noble-depth-handoff, GLBv2≤3MiB, embedded texture only,65×65 grid/4225vertices/512edgePNG. Drawing validates/chunks the file and preserves it across rotation until explicit finish. External network references are rejected. Blob connections are used solely for local embedded image decoding; no external requests.

## Verified evidence

- Entire current PhotoEditorActivity+three depth helpers compiles against actual AndroidAPI37/existingSDK classpath.
- Five core JVM tests pass0.146s; five SDK34/native-graphics review/ownership/confirmation/Stop/cache tests pass20.059s. Both named previews are verified; the initial fixture counted an unrelated systemDialogicon, corrected without changing product code/deadlines. Initial runtime classpath omitted AndroidApplication, fixed before executing tests; all failures retained.
- Exact Android-compiled core runs real pinned weights on the previously licensed publicNASA512photo. Actual65×65depth/977288BtexturedGLB generated in a2.44s host observation; original pixels untouched. Independent GLB chunk/JSON/bounds/embedded-image checks pass. This is not a phone timing or general depth-accuracy claim.
- Actual output GLB SHA25659b0ed9f1cb31d66f68a0ce882664062d4fb69660eda5d4f1490b0f70284f219 passes the real ThreeGLTFLoader/native-chunk/browser flow at320/390px, including decoded photo texture, depth edits, explicit persistence and actual animated-parallaxGLB re-export. No external requests/page errors. Peer evidence in .context/noble-fast-release.
- Five drawing-native policy tests, wholeDrawingActivity/helper API37 compile and825 drawing Node cases pass.272generated drawing files are source/byte verified. ReactNative current531tests, typecheck and lint0errors also pass;71Node regressions pass.

Not yet verified: current integrated Gradle/APK, real Android permissions/device/model performance/lifecycle/physical UI/media/export receivers, iOS native parity, store/signing. Host/API/browser evidence is not an installable-app success claim. Current release integration build is the next gate, not another feature-count batch. MI-GAN neural object removal is a separate new AI integration still being wired.
