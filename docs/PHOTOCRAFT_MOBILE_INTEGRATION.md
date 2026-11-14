# PhotoCraft core in Noble's mobile raster lab

**One new experimental manual workspace; no additional AI capability counted at this milestone.** The requested AI batch remains2/50 (neural painting and learned noise cleanup). This workspace reuses those actual neural tools, not renamed manual filters. Physical-device/full native-build acceptance is pending.

## Reused open-source core

The working candidate for the owner's Adobe-remake request is official [PhotoCraft](https://github.com/storytold/photocraft), immutable revision `f143ce58498f201a8dfc9c07f5e24147c6a22bee`. Five production Rust libraries—geometry, color-management foundations, color/blend mathematics, raster infrastructure and imaging algorithms—are vendored under creation-web/photocraft-core. Source/data bytes are unchanged and pinned per file. Only Cargo workspace/development manifests are adapted; original manifests are retained. This is a processing-core subset, not the entire PhotoCraft engine, application or a Photoshop replacement.

MIT OR Apache-2.0 licenses and required notices ship. Upstream BSD-3-Clause face data/code and the original CC0 synthetic ICC profile retain attribution; face detection and CMYK editing are not exposed. No proprietary Adobe/Photopea code, desktop UI, logos, branded artwork, fonts, account services or network services are copied.

The real core compiles with Rust1.95.0 and wasm-bindgen0.2.106 to approximately1.2MB of bundled WebAssembly. `npm run build:photocraft` uses the committed Cargo lockfile and records source/runtime hashes and tool versions. Ordinary drawing builds verify those hashes before copying the reviewed runtime. The app loads only bundled local JS/WASM; no remote editor or model endpoint is added. Narrow local Git attributes preserve pinned bytes on Windows. See the core README and UPSTREAM.json for exact scope/pins.

## Phone workflow

Drawing → Model → add a photo texture or open an existing AI depth relief → **Raster lab (PhotoCraft core)**. Work with up to four layers, duplicate/remove/reorder, choose visibility/opacity and five real core blend modes. Twelve bounded manual effects use the actual Rust algorithms, including edge extraction, high pass, blur, sharpening, emboss and twirl. These controls are choices inside one workspace, not twelve new AI tools.

Neural paint/cleanup operate on the selected layer through the already licensed local Mosaic/DnCNN models. Every neural result requires fresh review before layer insertion. The full composition then needs separate fresh approval before being used as a model texture or added as a separate drawing image. Model Undo restores the previous texture; the lab has its own bounded temporary Undo. Originals and the existing model remain unchanged until explicit use. Late closed/cancelled neural work cannot commit through the asynchronous core compositor. Stop/background/close invalidate owned work and discard partial output.

Working copies are8-bit sRGB at≤512px per side and≤3MiB per composition; neural limits remain224/256px. Incoming additional photo layers are metadata-stripped through the existing validated importer and fitted centrally without copying drawing crop/rotation. Internal valid working PNGs above the drawing-import192KiB budget are supported. Output is flattened; no PSD import/export, high-bit-depth editing, editable raster-project persistence, full PhotoCraft feature parity or automatic save is claimed. Drawing/model storage remains shared after logout, as disclosed in the UI.

## Verified evidence

- Four native Rust adapter tests pass: invalid/NaN/bounds failures, actual inversion/alpha/source retention, real alpha compositing, and repeated-edge blur.
- Eight focused Node cases run the actual compiled WASM and every exposed filter/blend, validate project/job/history bounds and verify source/runtime/pin tampering rejection. Combined creation suite:850 cases pass. Source/asset-manifest suite:11 cases pass.
- Actual Chromium/WebAssembly/React integration passes: layer duplication, known multiply pixel `[145,16,4,255]`, opacity, core effects, exact raster Undo, real neural→Rust composition, closed late neural apply rejection, worker Stop, fresh final approval, exact parent Model Undo and a separately owned drawing image.512px valid working PNGs larger than192KiB pass.320px layout has no horizontal overflow; no external requests or page errors.
- A generated-data phone-width screenshot was inspected. Active tools now scroll into view; a valid larger internal PNG no longer inherits the smaller drawing-photo import cap. These are two separate fixes, not new features.

Evidence and full logs are local under ignored `.context/noble-fast-photocraft/`. The upstream full test suite, real-phone RAM/thermal/performance/media acceptance and store readiness are unverified. Current iOS formats still lack native creative/search integration; a portable WASM core alone does not establish an iOS user workflow. New combined native/APK/iOS artifacts remain required after the requested batch; older release files are not relabeled as containing this code.
