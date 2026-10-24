# App executables

Android APK and any future signed iOS IPA are tracked with Git LFS. Install Git LFS, then run `git lfs pull` after cloning.

Only verified artifacts belong here. Read `release.json` for the exact source commit, SHA256 and acceptance scope. Internal Android candidates use the public debug certificate and are not store releases.

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
