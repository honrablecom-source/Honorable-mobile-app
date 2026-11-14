# Exact ONNX Runtime1.20 JNI alignment backport

Independent inspection of the fully built source85a4850 internal APK found that its ARM64/x86_64 libonnxruntime4j_jni.so had4096-byte ELF load alignment. Its inference libraries and the other40inspected64-bit native libraries already met16KB alignment. APK ZIP alignment alone did not catch this. [Upstream issue24902](https://github.com/microsoft/onnxruntime/issues/24902) describes the same1.20.0 bridge defect; [official fix24947](https://github.com/microsoft/onnxruntime/pull/24947) adds the16KB linker flag for MODULE libraries.

## Scope

Reuse immutable upstream1.20.0 Java/JNI/include sources at commitc4fb724e810bb496165b9015c77f402727392933. Full MIT terms, original Oracle/Microsoft notices, exact source hashes and the original official Maven AAR SHA256 are retained in third_party/onnxruntime-jni-1.20.0. Windows checkouts preserve source bytes through .gitattributes.

The helper generates JNI headers against the published classes.jar and compares all147actual Java native method descriptors/static flags. It rebuilds only the ARM64/x86_64 JNI bridges using declared NDK27.1.12297006, NNAPI enabled to match the original imports and the official max-page-size16384 linker fix. Every181exported function, ORT import and shared-library dependency must match. The inference libraries, Java classes, consumer rules and32-bit libraries retain their exact bytes. Full license/copyright assets are added separately. No inference-engine upgrade, model change, ranking change, holdout inspection or benchmark is involved.

Both app Gradle projects consume the generated AAR before normal packaging/signing. This preserves existing private release-signing behavior; no private key is part of this backport. Python3 and the declared NDK/JDK are build tools only. Optional developer properties nobleOrtNdkDirectory/noblePythonExecutable select installed tools without committing environment values.

## Verified evidence

- Real original Maven AAR27867924bytes, SHA25607a8f71ef890afed8c6087a56220e6d558a492804276ee2dd7cb7f6262242027.
- Generated147native contracts exactly match its classes.jar; both rebuilt bridges match181exports, original ORT imports/dependencies and16KB ELF segment alignment/congruence.
- Actual Gradle9.4.1 React Native and Gradle8.9 standalone runtime-preparation tasks pass. Both independently generate identical AAR SHA2564aac06aff0f09f28a41cedf22a75dab37642212ddc54c3726e83e85f5beacbca, including full license/notices.
- Seven independent archive/class/ELF/source-pin contracts and six APK model/license/ELF gate cases pass. Real Java compiler fixtures test native descriptors. Final APK gating now verifies native ELF alignment, not only ZIP padding.
- A fully signed prototype derived from the actual CI source85APK passes exact retained-entry byte comparison, model/search/drawing bytes, native ELF/ZIP16KB alignment and the original public debug certificate. Its zero-SHA prototype receipt is local-only and is not a published release identity.

Evidence .context/noble-main-jni16kb and .context/noble-main-release-validation. Initial missing-include/Fp16-classpath setup failures were corrected before the real builds passed. APK guard failures exposed legitimate AGP symbol stripping and automatic v1 signature additions; the production helper reproduces the original NDK stripping exactly and explicitly preserves the base's v2-only signature policy, rather than relaxing byte/file-set checks.

## Current APK derivation

The fresh tester packages reuse the fully compiled/verified source85a48506ee28db7840b2aa5b4652fcdbe0942e6a CI internal APK. The helper verifies its CI manifest/hash and published-core provenance, replaces the two JNI entries, adds exact license/notices, ZIP-aligns and signs with the existing deliberately public tester key. An ARM64-only copy also removes the other ABI library entries. Every retained code/model/resource/engine entry must stay byte-identical. Receipts name both the full base-build source and the repair-source commit. These are explicitly derived artifacts, not a claim that the whole app was recompiled after the native backport.

The Gradle hooks make future normal builds apply the same native repair before signing. Store releases require private signing. Physical-device/JNI/16KB-mode/model quality/thermal/export acceptance remains unverified; static alignment and host build proofs are separate claims.

The base manifest reports combined sign-in configuration incomplete. It does not identify which setting is absent. Account sign-in and credit-backed search require the owner's approved HTTPS API/public OAuth configuration; do not guess an endpoint, embed credentials or bypass the existing ledger. Local guest AI editing remains separate. iOS native search/editing/AI parity remains absent despite its verified build formats.
