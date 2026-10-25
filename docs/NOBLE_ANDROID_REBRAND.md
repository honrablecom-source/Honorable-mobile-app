# Noble Android branding and APK matrix

Source baseline: `44930012fc953afa8ad2a86fd38c100348f000ef` on `main`.
Codespace starting HEAD: `9431194d13df3c8a5482d49221fc195b6c531095`.

The checkout was fetched and reset to authoritative GitHub main before editing. No unpushed commits existed. Local tracked/untracked work was archived and every file SHA-256 verified in `/tmp/noble-sync-backup/local-work.tar`, with a verified manifest and local-only files alongside it. Codespace configuration and ignored local media/configuration were retained. This recovery directory is local and must be copied before destroying the Codespace.

Noble replaces visible branding in the React Native app, integrated native editors, drawing workspace, standalone Android UI, launcher label, export labels and current README headings. Internal names, package IDs, API headers, authentication keys and storage identifiers remain compatible. Seran remains unchanged. Historical reports and historical screenshots retain their original names.

Both Android modules use a black background with a white vector N, adaptive/round launchers for API 26+, monochrome for API 33+, and five legacy PNG densities. Resources live under each module's `app/src/main/res/`, in `drawable/ic_noble_*`, `mipmap-anydpi-v26`, `mipmap-anydpi-v33`, and `mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}`. The actual consumer APK is `mobile-react-native/android/app`, package `com.honorablemobile`. Standalone package `app.honorable` remains unchanged too.

SDK versions remain compile 37, target 36, minimum 24 for React Native; standalone remains 35/35/26. No native libraries or features were removed.

Use the existing Android build workflow. Normal debug/internal artifacts retain their existing paths. The additional `-PnobleAbiSplits=true -PreactNativeArchitectures=arm64-v8a,armeabi-v7a,x86_64` build creates the requested debug and release matrix. `scripts/verify-noble-apks.py` verifies actual APK metadata, icons, exact ABI coverage, matching native-library coverage, ONNX/Hermes/React Native libraries and local search assets before uploading `Noble-android-apks`. Its `apk-report.json` records exact filenames, bytes, SHA-256, signing, source commit and ABIs. Debug APKs require Metro; the existing internal APK bundles JavaScript and uses the public debug key. Release APKs bundle JavaScript and remain explicitly unsigned without the existing private release-signing environment configuration. No signing secrets were created.

Local verification: TypeScript passed; 11 Jest suites/60 tests passed; lint zero errors/37 warnings; server/Studio 57 tests passed outside the restricted sandbox; SDK/release/account tooling tests passed. Local Android Gradle compilation could not provision Java 17. Full compilation and packaging evidence must come from Actions. No device/emulator was available for installation acceptance. Newly added user-facing features: 0; this is branding and packaging work.

## October 25 resumed verification

Resumed at clean source commit `b4924d977da21293b3ec50f687dd2d080f843007`.
Actions run: https://github.com/honrablecom-source/Honorable-mobile-app/actions/runs/40229448912.
The current run has passed dependency installation, build-tool tests, React Native tests,
account/Studio tests, TypeScript, lint and standalone Android JVM tests. The full
React Native developer/internal APK build has passed, including native JVM tests
and Android instrumentation compilation. CI package/signature/alignment and bundled
asset verification passed; both artifacts and the release identity were uploaded.
The ABI matrix compiled successfully; inspection caught an unexpected x86 ABI in the universal debug APK, so matrix upload was skipped. No instrumentation ran on a device.

The Codespace workspace disk was full. The generated local Gradle 9.4.1 cache was
preserved at `/tmp/noble-preserved-gradle-9.4.1` and symlinked from its original
`/home/codespace/.gradle/caches/9.4.1` location, freeing 264 MB. No source or recovery
archive was deleted. Use `/tmp` for downloaded artifacts. Like the recovery backup,
these local temporary files do not survive Codespace destruction.

Downloaded APKs independently checked for SHA-256, complete ZIP CRC integrity,
model/tokenizer sizes and ONNX/Hermes/React Native libraries for every packaged ABI:

| APK | Bytes | SHA-256 |
| --- | ---: | --- |
| internal | 317466436 | `c8b54305b12c9f82c3f111003d9c03ddb40ffbceab839f7e41e502c8f255a2ef` |
| debug | 454026017 | `21550b65a0e336ba9968490ee5626c1a47a61e615e884195035a448cf220037e` |

Both normal unsplit APKs contain arm64-v8a, armeabi-v7a, x86 and x86_64. The requested
opt-in matrix excludes x86 and is checked separately. Internal JavaScript is
3,787,524 bytes; model 24,281,512 bytes; tokenizer 3,642,073 bytes. The downloaded
internal APK matches both the size and hash in CI's release manifest. Downloads
are preserved under `/tmp/noble-40229448912/`. Internal uses PUBLIC_DEBUG_KEY,
signInConfigured=false, deviceVerified=false and storeReady=false.

### Matrix correction

The complete log confirms 15 build-tool tests, 60 RN tests / 11 suites, 57
server/Studio tests, TypeScript/lint and standalone native tests passed. Full
React Native developer/internal build passed in 24m48s; matrix assembly passed
in 5m02s. The run failed specifically because `app-universal-debug.apk` included
x86 alongside the requested three ABIs. No verified matrix artifact was uploaded.

Installed React Native `NdkConfiguratorUtils.kt` explicitly skips ndk.abiFilters
when ABI splits are enabled. Dependency AAR x86 libraries therefore survive into
the universal APK. Added a JNI packaging exclusion for `**/x86/**`, scoped to
`nobleAbiSplits=true`; ordinary developer/internal packaging stays as tested.
The exact-ABI verifier remains strict. Fresh CI verification is required before
claiming the matrix fixed. No new user-facing features; one packaging fix under
verification, zero device-tested additions.

Android JNI exclusion reference:
https://developer.android.com/reference/tools/gradle-api/8.13/com/android/build/api/dsl/JniLibsPackaging
