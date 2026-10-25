# Noble app executables

Android APK and any future signed iOS IPA are tracked with Git LFS. Install Git LFS, then run `git lfs pull` after cloning.

Only verified artifacts belong here. Read `release.json` for the exact source commit, SHA256 and acceptance scope. Internal Android candidates use the public debug certificate and are not store releases.


## Ready APKs — earlier branding snapshot

These two verified APKs are from source `4b34fbc` and **do not contain Authoring20**. The twenty new tools are pushed in source (`ca9d4ec`); their full build and final ABI delivery are still running. Read [apk-report.json](apk-report.json) for exact sizes, hashes and architecture coverage.

- [Noble-all-abis-internal.apk](Noble-all-abis-internal.apk): self-contained internal test build, public debug signature, no Metro needed.
- [Noble-all-abis-debug.apk](Noble-all-abis-debug.apk): developer build requiring Metro, public debug signature.

Both include ARM64, ARMv7, x86 and x86-64. Neither is device-accepted or store-ready. The existing Honorable APK below is retained as a historical Creative20 artifact. Final Authoring20 APKs will replace/update the Noble set only after validation.

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
