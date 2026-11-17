# Noble internal test artifacts

## Android — corrected startup, limited physical smoke

- [Universal self-contained1.0.5/build6 tester APK](Noble-core-ai-internal.apk) —440482481bytes; ARM64,ARMv7,x86,x86_64; no Metro required.
- [Exact source, SHA256 and verification scope](Noble-core-ai-internal.manifest.json).

Unchanged CI bytes from source `10ce6970e916f6c4ef113c4eff28392d79520427`, successful [Android43650751564](https://github.com/honrablecom-source/Honorable-mobile-app/actions/runs/43650751564) and [five-APK editor43654784027](https://github.com/honrablecom-source/Honorable-mobile-app/actions/runs/43654784027). SHA256 `9e34dae869dc014fab1703429360152c3db9adc8ca9e18763d7e1f13aad51c46`. Identity/artifact digests, ZIP integrity, public tester signature, executable Room constructor and full studio/model payloads were independently verified. Replaces the known-crashing1.0.4 tester; no APK patching or new build claim.

The owner-uploaded replacement passed [physical Pixel5/Android11 Robo](https://console.firebase.google.com/project/honrable/testlab/histories/bh.d184d3de2c56f9db/matrices/5492285374052445028):5m10s,102actions,34screens. Screenshots show actual Noble launch and local Studio UI; no fatal-exception log matches. The crawl reached its time limit. The strict guest script was not attached, and cloud-uploaded bytes were not independently hash-compared. This is limited startup/navigation evidence, not every-feature, AI inference, save/export, recovery or live-account acceptance.

PhotoCraft/FilmCraft and four local image-studio graphs are packaged: object cutout, learned adaptive color, DnCNN cleanup and Mosaic painting. Actual engine/project/export/inference acceptance exists in host browser tests; these remain experimental on phones until controlled hardware checks pass. Preserve originals and review outputs. Drawing host visibility alone does not prove its engine loaded or saved a document.

The deliberately public debug key is for internal tests only. Configured Google/API strings do not establish successful live Google sign-in or production search completion; the trusted provider remains missing. deviceTested/storeReady remain false for full acceptance. iOS native search/editor/AI parity remains absent.

## Verified iOS build formats

- [Simulator ZIP](Noble-iOS-simulator.zip) with [Mac receipt](Noble-iOS-simulator.receipt.json): x86_64/arm64 iOS15.1+simulator for a compatible Mac.
- [Unsigned device XCArchive ZIP](Noble-iOS-device-xcarchive-unsigned.zip) with [Mac receipt](Noble-iOS-device.receipt.json): ARM64 archive requiring private Apple signing before iPhone installation. This is not a signed IPA.

Both formats passed Mac run43559775184/source99421da76bb7225d164a48f02a7ae76ab2356eae and independent ZIP/executable/JS/metadata/Mach-O receipt checks. **These are iOS shell previews; native Noble search/editing/AI modules are absent on iOS.** They do not establish feature parity, phone behavior or store readiness. No private signing material is included.

Exact file sizes/hashes/source identities are in [release.json](release.json), [Android report](apk-report.json) and each receipt. See [acceptance scope](../docs/RELEASE_FINISH_PLAN.md). Never place credentials, private signing keys or personal media here.
