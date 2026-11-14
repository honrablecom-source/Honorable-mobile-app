# Noble internal test artifacts

## Android — full image and video studios

- [Universal self-contained tester APK](Noble-core-ai-internal.apk) — 440449713 bytes; arm64-v8a, armeabi-v7a, x86, x86_64.
- [Exact source, SHA-256 and checks](Noble-core-ai-internal.manifest.json).

This unchanged APK comes from successful [Android run 43630705712](https://github.com/honrablecom-source/Honorable-mobile-app/actions/runs/43630705712) at source `36e5ca58ffb8d6e64ad3881034943cac0b38caad`. It includes the PhotoCraft image studio and FilmCraft video studio under **Guest editing → Advanced studios · preview**, alongside existing local AI, photo/video tools and native search. It starts without Metro. Compressed runtime names, all editor asset hashes, decoded compiler hashes and retained native components were independently checked in the actual APK. Its SHA-256 matches the CI release identity exactly; ZIP integrity passes. CI verified package/signing and ran the Android host tests. This is a fresh whole-app build, not an APK backport or renamed older artifact.

The studios remain experimental. Both real engines passed generated-fixture editing/project/export browser tests, including exact image Undo/layered reopening and H.264 video export/playback. Robolectric runs Android host tests without an Android VM or emulator. These checks do not establish physical-phone, real Android WebView, GPU/thermal, large-media or store acceptance. Local originals are preserved; sharing does not confirm saving. The image studio includes four local AI tools: object cutout with mask correction, learned adaptive color, DnCNN cleanup and Mosaic painting. Explicit current-document snapshots preserve the layered source. Fresh review is required before separate-document import or PNG transport. The verified visible-control keyboard focus correction is included; phone acceptance remains pending. No iOS full-editor host is available.

Signing uses the deliberately public tester key, not private store signing. Account configuration presence is recorded in the manifest; production sign-in/ledger acceptance remains unverified. The obsolete ARM64-only tester and its receipt were removed rather than offered as this build. The universal tester supports ARM64 too.

## Verified iOS build formats

- [Simulator ZIP](Noble-iOS-simulator.zip) with [Mac receipt](Noble-iOS-simulator.receipt.json): x86_64/arm64 iOS15.1+simulator for a compatible Mac.
- [Unsigned device XCArchive ZIP](Noble-iOS-device-xcarchive-unsigned.zip) with [Mac receipt](Noble-iOS-device.receipt.json): ARM64 archive requiring private Apple signing before iPhone installation. This is not a signed IPA.

Both formats passed Mac run43559775184/source99421da76bb7225d164a48f02a7ae76ab2356eae and independent ZIP/executable/JS/metadata/Mach-O receipt checks. **These are iOS shell previews; native Noble search/editing/AI modules are absent on iOS.** They do not establish feature parity, phone behavior or store readiness. No private signing material is included.

Exact file sizes/hashes/source identities are in [release.json](release.json), [Android report](apk-report.json) and each receipt. See [acceptance scope](../docs/RELEASE_FINISH_PLAN.md). Never place credentials, private signing keys or personal media here.
