# Noble release artifacts

The owner requested removal of the old APKs on November13. All11 earlier tracked APKs and the obsolete local frozen138debug APK were removed. Historical hashes in apk-report.json are retired and do not identify current downloadable APKs.

## Verified iOS build formats

- [Simulator ZIP](Noble-iOS-simulator.zip) and [Mac receipt](Noble-iOS-simulator.receipt.json): 20259845bytes; SHA256 `c8837e1077235c9aa08fc78fa29b6be122d9e095e982416aa2932a9e98e03691`. Universal x86_64/arm64 iOS15.1+simulator build for a compatible Mac.
- [Unsigned device XCArchive ZIP](Noble-iOS-device-xcarchive-unsigned.zip) and [Mac receipt](Noble-iOS-device.receipt.json): 38103155bytes; SHA256 `d2c4e07605d3be2703f0eaa0cc34144d01fb3d663bb1c653b7651bbbbff31c22`. ARM64 device archive requiring private Apple signing before iPhone installation; this is not a signed IPA.

Both formats were built from `1c8397b190e3bcf72d3d1a118ef40e1464c4c7b8` in successful Mac run43523751252. ZIP integrity, real executable/JavaScript hashes, metadata and actual Mach-O platform/architecture independently match the complete Mac receipts. The archive parser handles its real CreationDate metadata.

**These are iOS shell previews. Native Noble search, editing and AI modules are absent on iOS.** Build success does not establish feature parity, physical-device behavior or store readiness. No private signing material is included.

## Android candidate

Final current-source APKs, including neural depth, MI-GAN erasing and GIF export, are still building on run43524556805/source85a4850. No new APK is available here yet. Only validated artifact identities will be listed in release.json.

Internal Android tester APKs use the public debug certificate and are not store releases. Developer debug APKs requiring Metro are separate. Always check each artifact's exact source hash, ABI, signature and bundled-code metadata; never infer APK contents from a source preview.

See [release acceptance](../docs/RELEASE_FINISH_PLAN.md). Never place credentials, private signing keys or personal media here.
