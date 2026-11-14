# AI expansion requested November 14

The owner requested **50 additional AI capabilities**, followed by source publication and fresh Android/iOS formats. This is a new request; it does not restore the historical 500-feature target. Existing tools, style choices, parameters, ordinary filters, platform copies and packaging fixes are not new AI capabilities.

## Acceptance

Count a capability only after a reachable user workflow uses an actual licensed model, produces a useful editable/exportable result, preserves originals and has focused runtime/integration evidence. Keep source, browser/host, native build and physical-device evidence separate. Do not label a current build as containing later source. No private-media/query upload or new service endpoint is authorized by a general feature request.

## Current batch

| ID | Distinct outcome | Status |
|---|---|---|
| A01 | Repaint a local photo/3D texture with a genuine neural style model, review before applying, retain model Undo and export the resulting artwork | Experimental source/browser verified: real bundled WASM + complete React editor workflow; native build/device acceptance pending |
| A02 | Remove luminance noise using learned DnCNN inference, retain transparency/color differences, review before applying or adding a separate copy | Experimental source/browser verified: real bundled tiled WASM, generated-noise improvement, approval/Undo/actual-inference Stop; native build/device acceptance pending |

**New source/browser-verified capabilities: 2/50.** The remaining 48 are unimplemented, not placeholder buttons. Select subsequent outcomes after checking the existing source inventory and their model/runtime/license feasibility. Prioritize photo restoration/colorization, editable semantic masks, depth-aware compositing, video motion/rotoscoping, animation rigs and material creation. Treat multiple outputs of one integration conservatively rather than counting presets toward 50.

A01 reuses ONNX Model Zoo fast-neural-style Mosaic, immutable source `4c46cd00fbdb7cd30b6c1c17ab54f2e1f4f7b177`, 6,728,029 bytes, SHA256 `fa646dedade881243f8d5a2ceb7de2b93675b21fc24f7482894ac4851a9a0a47`, BSD-3-Clause, with MIT ONNX Runtime Web 1.20.1. The actual model requires `[1,3,224,224]` RGB floats in 0–255, despite the upstream README's general dynamic-size description. Prototype first attempted 192 and was rejected; production must respect the actual graph. Expected output is artistic reinterpretation, not restoration or arbitrary prompt generation. No search runtime, ranking or billing change is needed.

A01 is reachable in Drawing → Model → Neural painted texture after adding a photo texture or opening the existing AI depth relief. It preserves aspect and alpha at ≤224px, repeats edge padding for the fixed graph, and requires fresh review approval. Strength changes invalidate that approval. Apply creates exactly one model Undo entry; the separate-image action uses existing drawing ownership/capacity/source checks and Drawing Undo. No auto-save/upload and no ordinary-filter fallback. Stop, close, timeout and background terminate/discard worker output; a one-call worker releases its WASM heap. Model bytes are verified both during asset generation and before inference. Drawing source manifests now cover the model/provenance inputs as well as runtime assets/notices.

Evidence: 837 creation-web Node cases passed, plus focused asset-manifest tests. Real bundled Chromium integration checked a generated 160×96 RGBA texture, 45,757 changed RGB channel values with exact alpha retention, a fresh approval gate, strength invalidation, exact model Undo, no auto-save, real worker Stop during a held model request, corrupted-weight rejection, a separate drawing image and 320px layout. No external requests/page errors. First generated-image inference/review was 2.4s on the host browser, not a phone performance claim. No Android/Apple native inference runtime was changed. The standalone browser is a test surface; current iOS native integration is still absent.

A02 reuses MIT KAIR DnCNN25 published merged-BN weights from immutable source `fc1732f4a4514e42ce15e5b3a1e18c828af47a1e`. PTH SHA256 `0451a70de9b672ae037270498fbb1c17a1c1c4403785df586ff65df5b858e5b0`; no training. The supplied, reproducible strict-weight-loader converter exports a 2,226,850-byte ONNX17 graph SHA256 `5dc5803550a81466bb3edc011cfb1b37ba082ea6fa396d466a9055aec298be37`. Converter/weights/toolchain/model pins and license ship with source. Actual PyTorch/ORT maximum difference is 0.000001669 on the generated fixture. No arbitrary PTH/ONNX files are accepted by the app.

Drawing → Model → Neural noise cleanup processes up to 256px using fixed128 tiles and 18px halos, larger than the 17-convolution receptive radius. Only tile interiors commit, covering every output pixel exactly once. The neural luminance delta retains original color differences until clipping; alpha is copied exactly. This is a Gaussian-noise-trained cleanup, not restoration of lost details, universal camera denoising or a manual median filter. Strength/review/cancellation/Undo/source protection reuse A01. A real browser 160×96 generated sigma25 fixture improved 8-bit luminance MSE from610.36 to24.19 and took4.85s on the host. Full output, fresh approval, exact model Undo and Stop while actual tiled WASM inference ran were verified without external requests or page errors. General image quality, mobile speed/thermal/RAM and device acceptance are unverified.

Combined checkpoint: **842 creation-web cases and10 source/asset-manifest cases pass.** All280 generated drawing files match their current source/input/asset hashes. The actual production editor, both neural graphs and WASM/loader bytes equal the browser-tested bundle. The complete two-model browser workflow was repeated against production assets; the existing10-model/GLB browser gates plus actual AI-depth import/parallax/re-export also pass. Concurrent browser runs took3.9/9.0s for style/cleanup, illustrating why these host figures are not phone performance guarantees. No whole-app/APK build was run per tool; fresh combined batch artifacts remain required.

Latest owner steering additionally requests reuse of an open-source Adobe-style editor core, adapted for mobile and AI. PhotoCraft by ArtCraft is a likely candidate under review; its official source is MIT OR Apache-2.0 and has Rust/WebAssembly engine crates. The owner was asked for the project name/link. No PhotoCraft code is integrated or counted yet. Keep its manual commands distinct from genuinely neural capabilities and avoid restricted upstream branding.

## Release gates

- Coordinate through the local board, preserve the other chat's active release paths and use one publisher.
- Keep the existing exact-version Android JNI repair and 16 KB alignment checks.
- Generate new APKs only after the new source batch is integrated; never relabel existing core-AI testers as the new batch.
- Current iOS build formats are shell previews without native search/editor/AI parity. New iOS functionality needs real integration and Mac build evidence; a signed IPA additionally requires private owner signing.
- Account-backed search remains configuration-incomplete until approved public account API/OAuth configuration is supplied. Preserve native on-device search and the server credit ledger.
- No physical device is currently available. Do not claim zero bugs, model quality/performance on phones, device acceptance or store readiness from automated checks.

Implementation evidence is kept under ignored `.context/noble-fast-ai50/`; only reproducible source, licenses, model pins and honest acceptance notes belong in Git.
