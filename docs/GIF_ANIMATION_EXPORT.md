# Local GIF animation export

The owner wants a finished mobile AI creative tool rather than a feature-count project. Animated GLB preserves an editable 3D asset, but a GIF is a practical additional sharing format for the actual AI-depth relief and ordinary model turntables.

The Model workspace now renders one complete loop from its existing Three.js animation into a local GIF. It captures the view/background framed at export start, up to 384 pixels per side, 10 fps and 1–6 whole seconds. It does not upscale the preview or include audio. Model edits, saved drafts and the initial preview pose stay unchanged. GIF palette colors can show banding; this is a small looping image, not a full-quality video or additional neural video generation. Users can still export GLB to retain editable geometry/materials/animation.

## Reused implementation

- MIT `gifenc` 1.0.3 encodes and quantizes in a bundled dedicated worker. It is pinned in the lockfile and its full MIT notice is copied into bundled notices. Its [upstream documentation](https://github.com/mattdesl/gifenc) describes its 256-color palette and lack of dithering; no proprietary/cloud encoder is used.
- MIT `omggif` 1.0.10 is a test-only independent decoder, not a shipped media engine. Tests verify actual frame pixels, count, loop metadata and timing.
- The existing Three.js mixer, scene and camera render each actual frame; a frame buffer is transferred to the worker one at a time. No artificial still-image motion is substituted for the depth geometry. Workers are terminated on Stop, background or close; incomplete output is never handed to a destination.

The Android bridge shares the existing mutual-exclusion gate with GLB, SVG, printing and other export/import operations. It receives bounded base64 chunks, validates GIF blocks, dimensions, frame count/delays/trailer and the 8 MiB cap, writes and byte-verifies a separate private-cache `.gif`, then opens the explicit Android share chooser. Stale IDs cannot release newer ownership. Pending work stops on pause/close. Opening the chooser is not proof a destination saved the file. No original media file, query, search ranking, credit cost or ledger path is changed.

## Validation

- Six focused Node cases pass using the real gifenc codec and independent omggif decoder, including exact moving-frame pixels, loop/timing, native chunk equality, malformed inputs and worker cancellation/late replies.
- All 831 creation-web Node cases pass; this includes those six new cases.
- An actual Chromium/WebGL/worker run consumes the 977,288-byte native-produced public NASA depth GLB, exports a 364×280 ten-frame GIF (202,049 bytes in that run), independently decodes real parallax motion/timing/looping, verifies unchanged saved model/preview pose, deterministically Stops after partial work with no download, and exercises the GIF native chunk callback protocol. No page errors or external requests occurred. The synthetic native bridge checks JavaScript transport; it does not prove an Android share destination saved a file.
- The current drawing Activity/helpers and test sources compile against API37. Fifty SDK34 Robolectric cases pass in the latest completed focused run, including GIF file policy/admission and prior GLB/SVG/print/reset regressions. The new file-policy fixture explicitly uses `android.app.Application`; this avoids unrelated React Native application startup in pure codec/file tests. Full AGP/app validation remains separate and may reveal additional harness or integration issues.
- The generated-color encoder prototype produced 384×280, 40 frames, 811,323 bytes in 5.78 seconds on this host; this is not phone performance or a general photo-quality benchmark. Browser evidence and generated/public fixtures stay under ignored `.context/noble-fast-gif/`.

Count: one new source sharing capability, experimental until the fresh whole-app build and phone testing. It does not count additional AI inference, presets, limits, tests or packaging as features. No iOS GIF/native-editor parity, signed IPA, store-readiness or real-device claim is made.
