# Noble Android branding and APK matrix

Source baseline: `44930012fc953afa8ad2a86fd38c100348f000ef` on `main`.
Codespace starting HEAD: `9431194d13df3c8a5482d49221fc195b6c531095`.

The checkout was fetched and reset to authoritative GitHub main before editing. No unpushed commits existed. Local tracked/untracked work was archived and every file SHA-256 verified in `/tmp/noble-sync-backup/local-work.tar`, with a verified manifest and local-only files alongside it. Codespace configuration and ignored local media/configuration were retained. This recovery directory is local and must be copied before destroying the Codespace.

Noble replaces visible branding in the React Native app, integrated native editors, drawing workspace, standalone Android UI, launcher label, export labels and current README headings. Internal names, package IDs, API headers, authentication keys and storage identifiers remain compatible. Seran remains unchanged. Historical reports and historical screenshots retain their original names.

Both Android modules use a black background with a white vector N, adaptive/round launchers for API 26+, monochrome for API 33+, and five legacy PNG densities. Resources live under each module's `app/src/main/res/`, in `drawable/ic_noble_*`, `mipmap-anydpi-v26`, `mipmap-anydpi-v33`, and `mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}`. The actual consumer APK is `mobile-react-native/android/app`, package `com.honorablemobile`. Standalone package `app.honorable` remains unchanged too.

SDK versions remain compile 37, target 36, minimum 24 for React Native; standalone remains 35/35/26. No native libraries or features were removed.

Use the existing Android build workflow. Normal debug/internal artifacts retain their existing paths. The additional `-PnobleAbiSplits=true -PreactNativeArchitectures=arm64-v8a,armeabi-v7a,x86_64` build creates the requested debug and release matrix. `scripts/verify-noble-apks.py` verifies actual APK metadata, icons, exact ABI coverage, matching native-library coverage, ONNX/Hermes/React Native libraries and local search assets before uploading `Noble-android-apks`. Its `apk-report.json` records exact filenames, bytes, SHA-256, signing, source commit and ABIs. Debug APKs require Metro; the existing internal APK bundles JavaScript and uses the public debug key. Release APKs bundle JavaScript and remain explicitly unsigned without the existing private release-signing environment configuration. No signing secrets were created.

Local verification: TypeScript passed; 11 Jest suites/60 tests passed; lint zero errors/37 warnings; server/Studio 57 tests passed outside the restricted sandbox; SDK/release/account tooling tests passed. Local Android Gradle compilation could not provision Java 17. Full compilation and packaging evidence must come from Actions. No device/emulator was available for installation acceptance. Newly added user-facing features: 0; this is branding and packaging work.
