# Local AI in the full image studio

November 14 source/browser milestone: **one new AI capability (general-object cutout), one new inference model, and two integrations of existing AI tools.** Mask correction controls support the cutout workflow and are not separate features. Existing person-only ML Kit cutout is not counted again. These changes are experimental source/browser verified; a fresh native build is required. The previously published source3f3b506 APK does not contain this later integration.

## Reachable workflow

Guest editing → Advanced studios · preview → image studio → **AI photo tools**. Choose a local still PNG/JPEG (up to12MiB/16megapixels, dimensions checked before decode). The open layered document stays intact. Working copies are metadata-free and resized to the stated tool limit. No model/media/query is uploaded, no account service or credit policy changes, and no arbitrary model URL is accepted.

- **Object cutout:** U2NETP predicts the salient foreground, including non-human objects. The320×320 model mask is bilinearly fitted to a working photo up to1024px and multiplied by existing alpha. RGB is preserved in the pixel operation. This is an estimate, not semantic labels or guaranteed hair/glass matting. A public cup-and-saucer fixture produced a useful cutout with some edge fringes. Erase/Restore brushes let users correct the alpha mask; eight alpha-only Undo snapshots bound history to8MiB at1024px. Brush changes revoke preview approval.
- **Neural noise cleanup:** the already bundled learned DnCNN model is now reachable directly from the full image studio, with its existing256px/luminance/halo-tile limits. This is not a new model or general lost-detail recovery.
- **Neural painting:** the already bundled Mosaic model is now reachable here, at224px. Its artistic reinterpretation is not restoration or text-to-image generation.

Every result requires explicit review approval. New source/tool, mask edits, Stop, close, timeout and simulated backgrounding invalidate approval and pending results. Inference runs in one owned worker; termination discards late output and releases the WASM heap. Preview tools intercept their own keys/wheel so editor shortcuts do not change the underlying layered project. Opening an approved PNG uses the full engine's existing import path to create a separate document; the source remains open. Export uses the existing bounded native share transport. Opening a share sheet does not confirm saving. No original file is overwritten.

## Model, runtime and licenses

The new ONNX model is the official rembg release asset85857953 (`u2netp.onnx`,4574861bytes). SHA256: `309c8469258dda742793dce0ebea8e6dd393174f89934733ecc8b14c76f4ddd8`; MD5 matches the pinned upstream adapter's `8e83ca70e441ab06c318d82300c84806`. [U2NET source](https://github.com/xuebinqin/U-2-Net/tree/ac7e1c817ecab7c7dff5ce6b1abba61cd213ff29) is Apache-2.0; [rembg adapter/distribution](https://github.com/danielgatis/rembg/tree/991927b77667946dab59c50df31f7d4640e80457) is MIT. Both full notices ship with the existing Mosaic BSD, DnCNN MIT and ONNX Runtime MIT notices in `AI-NOTICES.txt`. Source/distribution/normalization/limits are pinned in `full-editors/ai-assets/provenance.json`.

ONNX Runtime Web remains the already pinned1.20.1 single-thread WASM runtime. Every model's exact size/SHA256 is checked during packaging and before inference. The native immutable-bundle receipt covers every runtime/model/UI byte. `ai-build.json` additionally pins the relevant source/templates/license inputs using canonical UTF-8/LF text hashes; `verify-ai-bundle.mjs` refuses stale source/assets before the Android build. Binary/runtime receipts preserve exact bytes across Git checkouts. No native JNI/search runtime, ranking, Seran holdout or ledger is changed.

Rebuild the AI wrapper around a verified existing full image bundle into a fresh directory:

```sh
node full-editors/build-ai.mjs android/app/src/main/assets/creative-editors/image /fresh/image-output
node full-editors/verify-ai-bundle.mjs /fresh/image-output
```

The normal full-engine `package.mjs` recipe also includes the AI package. It uses the pinned creation-web esbuild/ONNX dependencies; `NOBLE_EDITOR_NODE_MODULES` may point at an isolated dependency installation. Existing source bundles are validated before staging; compiler outputs are not rebuilt or silently re-pinned.

## Verified evidence and limitations

Six new processing/mask host cases pass, including RGB/source/alpha invariants, dark-image normalization, malformed/flat/non-finite rejection, edge clipping and exact alpha Undo. Five retained export-bridge cases and four actual-asset APK-gate regressions also pass. The original full image editor's PNG/edit/Undo/two-layer project round-trip remains passing.

The real full engine and all three real graphs passed a phone-width390px headless Chromium workflow against the **production** bundle and CSP. The CC0 coffee photo by Rachel Michetti, courtesy of Pikolo Espresso Bar, is from [scikit-image's documented public dataset](https://scikit-image.org/docs/0.25.x/api/skimage.data.html#skimage.data.coffee); it remains in ignored test evidence, not the application. Its600×400 cutout had123908 alpha<32pixels and112435 alpha>224pixels; output and screenshot were inspected. The test performs a real mask brush stroke, verifies approval revocation and exact Undo, checks original layered-document preservation, exact native PNG transport and separate engine document creation. A generated256×256 Gaussian-noise fixture improved luminance MSE from621.21to1.60; Mosaic changed149936RGBchannel values. These fixtures do not establish general photographic quality. Stop during actual tiled inference, tool-change approval invalidation and simulated background cancellation pass with no page exceptions or external requests.

Evidence is retained under ignored `.context/ai-studio/`. Native compilation/package integration, real Android WebView/GPU/thermal/large-photo acceptance and iOS hosting remain separate. The owner has no physical phone and requests no Android VM/emulator. This milestone is not an investor-readiness or store-readiness claim.
