# Noble full image and video studios

Android packages the real pinned PhotoCraft and FilmCraft Rust/WASM engines. Open Edit without an account → Advanced studios · preview. Corrected source36e5ca5 internal1.0.4/build5 passed full Android43630705712 and five-APK43634836827. Exact packaged assets, decoded engines and native components were independently checked. These remain experimental phone workspaces; physical-device/store acceptance and iOS native hosting are not established.

AI photo tools includes local U2NETP cutout with mask correction/Undo, learned adaptive color with adjustable strength/review, existing DnCNN cleanup and Mosaic painting. Choose a local PNG/JPEG or explicitly capture the current layered document as a read-only working copy. Browser checks exercise all four graphs, source/alpha/approval/cancellation/corrupt-model behavior, separate documents, PNG transport and keyboard focus. Video checks include project/H.264/playback, damaged-project preservation, active Stop, stereo WAV and real AAC in MP4. See outer-repo docs/AI_STUDIO_INTEGRATION.md and INVESTOR_READINESS.md for exact pins and limitations.

## Privacy and limits

Immutable local bundles only; selected local imports and separate exports. No personal media/query upload or arbitrary model endpoint. Original media is not overwritten. Sharing does not confirm saving; save layered projects separately before closing. Local recovery/imported copies may survive sign-out. Camera/microphone capture is unsupported. Desktop-only threaded operations, optional image HEIC/HEIF, broader codecs/large projects, physical Android WebView/GPU/thermal behavior and iOS parity remain unaccepted. Search/ranking/account/credit rules are unchanged.

Executable assets are individually bounded to96MiB; media imports128MiB each/256MiB together and exports64MiB. AI helper PNG/JPEG input is further bounded to12MiB/16MP, with1024px object/color,256px denoise and224px painting working copies. AI model hashes are checked before inference. One owned worker is terminated on cancellation; stale/partial output never auto-commits. Cutout mask Undo uses at most eight alpha snapshots (8MiB at1024px).

## Build and reproduction

upstreams.json pins PhotoCraft f143ce58498f201a8dfc9c07f5e24147c6a22bee and FilmCraft d1e64ecfcc5e6f21e30f571e6fe3edbe86fdcafa, reviewed patches, Rust1.95.0 profiles and wasm-bindgen versions. MIT/Apache and original dependency/font notices ship. Large UI crates use opt-level0/codegen-units256; image fat LTO is disabled. These settings do not establish phone performance.

Use fresh source/target/output directories; the build never overwrites an existing checkout:

```sh
node full-editors/build.mjs image /fresh/noble-image-build
node full-editors/build.mjs video /fresh/noble-video-build
python3 full-editors/collect-notices.py /fresh/noble-image-build/source photocraft-web /fresh/image-notices
python3 full-editors/collect-notices.py /fresh/noble-video-build/source filmcraft-web /fresh/video-notices
node full-editors/package.mjs image /fresh/noble-image-build/source /fresh/noble-image-build/web /fresh/image-notices /fresh/image-package
node full-editors/package.mjs video /fresh/noble-video-build/source /fresh/noble-video-build/web /fresh/video-notices /fresh/video-package
```

The image packager uses the pinned creation-web esbuild/ONNX dependencies to bundle the AI wrapper. NOBLE_EDITOR_NODE_MODULES can select an isolated installation. To update only that wrapper around a verified existing image bundle:

```sh
node full-editors/build-ai.mjs android/app/src/main/assets/creative-editors/image /fresh/image-with-ai
node full-editors/verify-ai-bundle.mjs /fresh/image-with-ai
```

The engine compiler bytes remain exact, stored as editor_bg.wasm.gzip because Android expanded/renamed .gz assets. Native routing serves bounded decoded streams at the unchanged .wasm URL. Receipts cover every packaged byte; ai-build.json additionally ties generated AI assets to canonical UTF-8/LF source/template/license hashes. Stale source, changed model bytes or renamed packaged files refuse validation.

## Tests

```sh
node --test full-editors/ai-object.test.mjs full-editors/ai-mask.test.mjs full-editors/ai-color.test.mjs full-editors/host-bridge.test.cjs
node full-editors/verify-ai-bundle.mjs
python3 full-editors/test_verify_apk.py
python3 full-editors/verify-apk.py /path/to/full-react-native.apk
node full-editors/browser-image.test.cjs --out /fresh/image-test
node full-editors/browser-video.test.cjs --out /fresh/video-test
NOBLE_AI_OBJECT_FIXTURE=/licensed/local/photo.png node full-editors/browser-ai.test.cjs
```

Browser tests use the ../ui-previews Playwright dependency (or a test-only dependency adapter), production CSP and generated/licensed fixtures. The AI test accepts explicit fixture and output paths through NOBLE_AI_OBJECT_FIXTURE/NOBLE_AI_EVIDENCE; NOBLE_IMAGE_ASSETS can target staging. Use only licensed test photos, never private/holdout data. Android policy/share tests hash real bundles and decode original compiler outputs. Robolectric/browser/package checks run without an Android app VM/emulator and never count as physical-device acceptance.

## Review without a phone or Android VM

From mobile-react-native, run `node full-editors/preview.mjs --port 4181`, then open http://127.0.0.1:4181/. The loopback-only Node server verifies every studio asset against its receipt before serving either real engine. It accepts GET/HEAD only, exposes a fixed asset whitelist, rejects other hosts and accepts no media uploads. Local-file selection and downloads happen in the browser; browser recovery/imported copies can persist. Stop with Ctrl+C in its terminal.

This reviews the checkout's actual image/video engines and local image AI, without a mocked account or native bridge. It is not the Android app and does not validate native sharing, account search, Android lifecycle, phone performance or iOS parity. The published APK can use an earlier source. Save exported/project copies and verify them in your chosen destination.
