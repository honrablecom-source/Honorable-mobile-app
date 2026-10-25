# Noble app executables

Android APK and any future signed iOS IPA are tracked with Git LFS. Install Git LFS, then run `git lfs pull` after cloning.

Only verified artifacts belong here. Read `release.json` for the exact source commit, SHA256 and acceptance scope. Internal Android candidates use the public debug certificate and are not store releases.


## Ready APKs — earlier branding snapshot

All ten Noble APKs below are verified earlier branding snapshots and **do not contain Authoring20**. Normal APKs are from `4b34fbc`; the ABI matrix is from `6659e1e`. The twenty new tools are pushed in source (`ca9d4ec`); their full build and final ABI delivery are still running. Read [apk-report.json](apk-report.json) for exact sizes, hashes and architecture coverage.

- [Noble-all-abis-internal.apk](Noble-all-abis-internal.apk): self-contained internal test build, public debug signature, no Metro needed.
- [Noble-all-abis-debug.apk](Noble-all-abis-debug.apk): developer build requiring Metro, public debug signature.

The two normal APKs include ARM64, ARMv7, x86 and x86-64. The matrix uses the three requested ABIs below. Neither is device-accepted or store-ready. The existing Honorable APK below is retained as a historical Creative20 artifact. Final Authoring20 APKs will replace/update the Noble set only after validation.

### Verified ABI matrix

| Architecture | Debug — requires Metro | Release — unsigned |
| --- | --- | --- |
| Universal (ARM64, ARMv7, x86-64) | [APK](Noble-universal-debug.apk) | [APK](Noble-universal-release-unsigned.apk) |
| ARM64 | [APK](Noble-arm64-v8a-debug.apk) | [APK](Noble-arm64-v8a-release-unsigned.apk) |
| ARMv7 | [APK](Noble-armeabi-v7a-debug.apk) | [APK](Noble-armeabi-v7a-release-unsigned.apk) |
| x86-64 | [APK](Noble-x86_64-debug.apk) | [APK](Noble-x86_64-release-unsigned.apk) |

Unsigned release APKs require private signing before installation/distribution.

## iOS status

No signed iPhone IPA is available. Native Honorable iOS search/editing modules are unfinished. Windows cannot run Xcode. The repository workflow can compile an unsigned simulator shell on macOS; a simulator app is not an installable iPhone release.

On a Mac, from `Honorable-mobile-app-main/mobile-react-native/`:

```sh
npm ci --no-audit --no-fund
bundle install
cd ios
bundle exec pod install
cd ..
xcodebuild -workspace ios/HonorableMobile.xcworkspace -scheme HonorableMobile -configuration Release -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' -derivedDataPath build/ios-simulator CODE_SIGNING_ALLOWED=NO build
```

The simulator output is `build/ios-simulator/Build/Products/Release-iphonesimulator/HonorableMobile.app`. A phone IPA additionally needs an Apple Developer team, a signing certificate/provisioning profile and an archive/export configuration. Do not place signing credentials in Git.

The preceding release source b9e135caf57cadc404b632f8e4ba68cbce94d817 passed the [unsigned iOS simulator workflow](https://github.com/honrablecom-source/Honorable-mobile-app/actions/runs/40129418431). The current creative-color tools are Android native; that simulator result does not establish their availability on iOS.
