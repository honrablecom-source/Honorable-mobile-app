# iOS project

Native SwiftUI sources are configured through `project.yml`. On macOS, install
XcodeGen and run `xcodegen generate` in this directory, then build with:

`xcodebuild -project Honorable.xcodeproj -scheme Honorable -sdk iphonesimulator CODE_SIGNING_ALLOWED=NO build`

No signing identity, certificate, or App Store Connect credential is included.
