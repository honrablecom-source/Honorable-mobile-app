# Noble full image and video editors

Android source packages the full pinned PhotoCraft and FilmCraft browser engines under `android/app/src/main/assets/creative-editors/`. Guest editing exposes **Advanced studios · preview** only when the independent native module verifies each bundle's exact bytes. These are two experimental manual source workflows, not50 AI features or completed APK/iOS/device delivery. Native search and the credit ledger are unchanged.

The studios use neutral mobile chrome with canvas/panel task controls, explicitly selected local imports and separate exported copies. Original media is not overwritten or uploaded. Sharing does not confirm saving; save/export before closing. Local recovery/imported copies may survive sign-out. Camera/microphone access is unsupported. Desktop-only threaded operations, optional image HEIC/HEIF and broader codecs/large projects have not been accepted on phones. No iOS host is implemented here.

`upstreams.json` pins official repositories/revisions, lockfile profiles, compiler overrides and bindgen versions. `patches/` adapts the real upstream code: Noble branding, neutral/touch layouts, serial WASM H.264 encoding, private local image requests and unconfirmed-save handling. Source remains MIT OR Apache-2.0 with separate dependency/font notices. Restricted ArtCraft graphics are excluded. The image release disables fat LTO; engine code is optimized, while the very large UI crates use opt-level0/codegen-units256. This is not a mobile performance claim.

Requires Rust1.95.0 with wasm32-unknown-unknown, the matching wasm-bindgen CLI on PATH, and Python3.11+ for notices. Build into fresh directories outside the repo; existing checkouts/outputs are never overwritten:

```
node full-editors/build.mjs image /tmp/noble-image-build
node full-editors/build.mjs video /tmp/noble-video-build
python3 full-editors/collect-notices.py /tmp/noble-image-build/source photocraft-web /tmp/noble-image-notices
python3 full-editors/collect-notices.py /tmp/noble-video-build/source filmcraft-web /tmp/noble-video-notices
node full-editors/package.mjs image /tmp/noble-image-build/source /tmp/noble-image-build/web /tmp/noble-image-notices /tmp/noble-image-package
node full-editors/package.mjs video /tmp/noble-video-build/source /tmp/noble-video-build/web /tmp/noble-video-notices /tmp/noble-video-package
```

The notice collector reads public source/cache files and fails for missing license evidence. Pinned root-license fallbacks match each crate's original repository/revision and exact byte digests. Bundles include Rust standard-library and original font/dependency notices. Packaging checks the full source revision/reviewed patch, stores the original WASM in deterministic gzip form and emits local runtime pages and records every asset's size/SHA256 in `bundle.json`. The native host decodes bounded streams at the unchanged `.wasm` URLs; native tests verify the decoded sizes/SHA256 against the original compiler outputs. This reduces packager memory without modifying either engine. The96MiB executable-file cap is separate from128MiB imports/64MiB exports.

Validation (browser tests use the existing `../ui-previews` Playwright dependency and generated fixtures only):

```
node --test full-editors/host-bridge.test.cjs
node full-editors/browser-image.test.cjs --out /tmp/noble-image-test
node full-editors/browser-video.test.cjs --out /tmp/noble-video-test
python3 full-editors/verify-apk.py /tmp/full-react-native.apk
```

Image acceptance checks PNG edit/alpha/exact Undo and editable layered-project pixel round-trip. Video acceptance checks cut/Undo/Redo, project reopening, real H.264 encoding, MP4 decoding and exact mobile export transport. Both use the production CSP and390px viewport. Android policy/share tests also hash the actual packaged binaries. Component/browser/API compilation does not establish a whole-app build, physical-device behavior or store readiness. See `docs/FULL_EDITOR_MOBILE_ROADMAP.md` at the outer repository root for verified evidence and outstanding work.

Android99421da compiled but exhausted the build daemon heap during APK packaging. The gzip packaging fix and4GiB build-only heap are awaiting a combined native retry. The new read-only follow-up CI checks actual internal/release APK contents after a successful Android run. iOS99421da simulator/device archive structures were verified, but remain guest-shell formats without these native studio hosts.

## November14 — Full-editor APK filename defect reproduced and repaired in source

**0new user-facing features;1packaging compatibility fix.** Android run43563878266/source b8aa8be completed successfully, but its follow-up43567125592 failed. Independently downloaded the actual internal APK and reproduced the exact error: Android packaging expanded/renamed editor_bg.wasm.gz to editor_bg.wasm, while bundle receipts and native integrity checks still required the compressed name. Thus build success did not establish usable studios.

The repair retains identical compressed compiler bytes under editor_bg.wasm.gzip, updates receipts/host/packaging/browser routing, and verifies the original decoded compiler hashes. Four APK-gate regression cases pass using real bundled assets (valid multidex, renamed runtime rejection, tamper rejection, missing native-host rejection); all5existing export-bridge cases pass. The main Android build now blocks internal APK publication on the full-editor checker; the follow-up exposes checker stderr. Current repaired native build/APK, physical-device testing and iOS editor host remain pending. Existing b8aa8be APKs must not be described as working full-editor delivery.

## November14 — Fresh full-editor tester verified; final publication

**0new user-facing features;1real Android asset-packaging fix and1verification-checker correction.** Full Android run43614544689/source3f3b506 completed successfully, including JavaScript/React Native/native tests, typecheck/lint, internal APK signing/alignment/content checks and the inspected ABI/release matrix. Independent full-editor workflow43617585467 also passed. The actual422660503-byte universal internal APK matches CI SHA25695c7e080f165f3aa5c45f95bddc9b0e68876c35bafac32130e1d4f7f8dde07f2; ZIP integrity, every exact studio asset, both decoded compiler hashes and the retained module/activity descriptors were independently verified. The unchanged APK replaces executable/Noble-core-ai-internal.apk; its manifest/report/release README name the exact successful source/run. Removed the obsolete ARM64-only tester/receipt; the fresh universal supports ARM64/ARMv7/x86/x86_64. This is a real whole-app build, not a renamed old APK or backport.

Both real engines also pass isolated Playwright headless-browser editing/project/export tests with generated fixtures and no external requests/page exceptions. Four APK-gate regressions and5existing export-bridge cases pass. Owner's no-Android-VM/emulator preference is recorded in AGENTS.md. Robolectric/browser/static checks do not establish physical Android WebView/GPU/thermal/large-media behavior. The studios remain experimental; full-studio AI connections, production account configuration/service acceptance and iOS native editor/search/AI host parity remain unfinished. Store/private signing and device acceptance are not claimed.

Newer source99421da/Mac43559775184 iOS simulator and unsigned device archive files/receipts were refreshed and independently matched to Mac receipts without running either artifact. See executable/release.json and docs/FULL_EDITOR_MOBILE_ROADMAP.md for exact identities and limits. No physical phone or Android emulator/VM was used for acceptance.
