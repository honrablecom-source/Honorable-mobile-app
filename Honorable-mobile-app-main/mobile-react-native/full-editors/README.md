# Noble full image and video studios

Android packages the real pinned PhotoCraft and FilmCraft Rust/WASM engines. Open Guest editing → Advanced studios · preview. The source3f3b506 internal APK passed full Android build43614544689 and the5-APK editor gate43617585467. These are experimental phone workspaces, not Photoshop/Premiere parity or physical-device/store acceptance. iOS currently has no native host for these studios.

The current source adds **AI photo tools** to the full image studio: a new local U2NETP general-object cutout with mask correction/Undo, plus integrations of the existing DnCNN cleanup and Mosaic painting models. Review, cancellation, original preservation, separate document creation and native PNG transport pass real production-bundle browser tests. Three-tool source0f0cc5a passed full Android43621271622 and five-APK gate43625813335. The current1.0.4 candidate adds Apache-2.0 learned image-adaptive color with adjustable strength and an explicit read-only current-document snapshot; its own native build is pending. The published3f3b506 APK does not contain these later integrations. See outer-repo docs/AI_STUDIO_INTEGRATION.md for exact model/source/license pins, workflow limits and evidence.

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
