# Noble internal test artifacts

## Android — includes the new AI tools

- [ARM64-only tester APK](Noble-core-ai-arm64-internal.apk) —172107540bytes ([identity/checks](Noble-core-ai-arm64-internal.manifest.json)).
- [Universal tester APK](Noble-core-ai-internal.apk) —369191461bytes; ARM64/ARMv7/x86/x86_64 ([identity/checks](Noble-core-ai-internal.manifest.json)).

Both are self-contained Noble1.0.3/build4testers and start without Metro. They include neural depth→editable2.5D/parallax, MI-GAN object erasing, the mobile Three.js workspace and GIF export, alongside the existing photo/video tools and native search engine. Phone behavior/model quality/export still needs acceptance.

The full app/source85a48506ee28db7840b2aa5b4652fcdbe0942e6a passed Android CI. These APKs explicitly derive from that build plus the exact-version JNI16KB backport at3c6727e125da7429d438745db8e60c0f5b3efc2c. The ARM64 copy removes other ABI library entries. Existing retained code, models, resources and inference-engine bytes are identical; only the declared JNI bridges and license notices change. Both retain the original public debug tester certificate and pass signature/ZIP/nativeELF16KB/model/drawing checks. This is not a claim of whole-app recompilation after the backport. A canonical full rebuild is also running separately.

**Sign-in configuration is incomplete.** Local guest AI editing is available; account sign-in/credit-backed search requires the owner's approved HTTPS API/public Google OAuth configuration. The manifest reports combined configuration presence only, not which value is missing. No endpoint is guessed and no search/credit guard is bypassed.

These use a deliberately public tester key, are not store releases and have not been tested on a physical device or16KB-mode device. [Native repair provenance and limits](../docs/ORT_JNI_16KB.md). All11old tracked APKs and the obsolete local frozen138APK were removed; old artifact history remains in Git.

## Verified iOS build formats

- [Simulator ZIP](Noble-iOS-simulator.zip) with [Mac receipt](Noble-iOS-simulator.receipt.json): x86_64/arm64 iOS15.1+simulator for a compatible Mac.
- [Unsigned device XCArchive ZIP](Noble-iOS-device-xcarchive-unsigned.zip) with [Mac receipt](Noble-iOS-device.receipt.json): ARM64 archive requiring private Apple signing before iPhone installation. This is not a signed IPA.

Both formats passed Mac run43523751252/source1c8397b190e3bcf72d3d1a118ef40e1464c4c7b8 and independent ZIP/executable/JS/metadata/Mach-O receipt checks. **These are iOS shell previews; native Noble search/editing/AI modules are absent on iOS.** They do not establish feature parity, phone behavior or store readiness. No private signing material is included.

Exact file sizes/hashes/source identities are in [release.json](release.json), [Android report](apk-report.json) and each receipt. See [acceptance scope](../docs/RELEASE_FINISH_PLAN.md). Never place credentials, private signing keys or personal media here.
