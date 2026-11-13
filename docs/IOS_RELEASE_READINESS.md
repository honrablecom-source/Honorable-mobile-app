# iOS release evidence and remaining work

The owner cancelled the feature-count target and asked for a finished core app with fresh Android and iOS packages. The repository's React Native iOS project is the current build target. The older `ios-app/` SwiftUI project also contains unavailable embedding and service scaffolding; it is not an implemented native search/editor engine to copy into the current app.

## Package paths

The updated `.github/workflows/ios-build.yml` prepares two separate current-source outputs on macOS:

- `Noble-iOS-simulator-unsigned`: simulator `.app` ZIP and a verification receipt.
- `Noble-iOS-device-xcarchive-unsigned`: an unsigned arm64 device `.xcarchive` ZIP and a verification receipt.

The workflow pins Xcode16.4, which is listed on the [official macos-15 runner image](https://github.com/actions/runner-images/blob/main/images/macos/macos-15-Readme.md). The installed/pinned React Native package's `scripts/cocoapods/helpers.rb` requires iOS15.1 and Xcode16.1; the existing application deployment target already matches, so this work does not change the Xcode project deployment target or raise its requirement without evidence.

Both builds reuse the exact committed JavaScript lockfile, install their CocoaPods dependencies, bundle the Release JavaScript and retain build logs/receipts. Ruby dependencies remain governed by the existing Gemfile; no new frozen Ruby/CocoaPods lockfile or fully reproducible pod-resolution claim is made.

The device archive uses the ordinary application archive action with code signing disabled. It is not a signed IPA and cannot be advertised as an installable App Store/TestFlight release. A private Apple signing identity, provisioning, developer account setup and appropriate export are separate steps. No signing keys, profiles, tokens or account credentials are added to this repository or workflow.

## Artifact verification

`Honorable-mobile-app-main/scripts/verify-ios-artifact.cjs` checks the actual application before packaging. It requires the Noble bundle identifier, a nonempty regular executable and bundled JavaScript, expected built plist platform, compatible 64-bit executable architecture and the actual Mach-O `LC_BUILD_VERSION` device/simulator platform. It validates every slice of a universal executable, rejects malformed/overlapping slices, checks an archive's exact contained application path, and refuses an unexpected provisioning profile. Receipts record the source commit and executable/JS SHA-256 hashes while explicitly keeping native feature parity, device testing and store readiness unverified.

The Mach-O constants and layout were checked against Apple's published [loader header](https://github.com/apple-oss-distributions/xnu/blob/main/EXTERNAL_HEADERS/mach-o/loader.h) and [CPU definitions](https://github.com/apple-oss-distributions/xnu/blob/main/osfmk/mach/machine.h). The verifier is original project code; no Apple header implementation is copied or bundled.

Nine focused Node cases pass on Linux, covering actual byte-layout fixtures, thin/universal architectures, platform spoofing, truncated/overlapping tables, executable path escape, absent JS, linked executables, provisioning profiles and archive identity. This validates the verifier, not an iOS build.

## Readiness boundary

The latest prior simulator workflow observed during this audit was [run40226461026](https://github.com/honrablecom-source/Honorable-mobile-app/actions/runs/40226461026), successful at historical source `1d374f6f3ae1779f87b3a3449112babd46d7f8a6`. No downloadable artifact was listed by its artifact API during this audit. It does not validate the current working tree or the new device-archive path.

This Linux workspace has no Xcode. A new successful macOS run and downloaded/verified current artifacts are still required before claiming fresh iOS packages. The current RN iOS target does not implement Android's native media search, photo/video editing or on-device AI modules. A successful shell archive cannot substitute for those ports or for real-device acceptance. The UI must continue to describe unavailable native tools honestly.

Count: zero new user-facing iOS features and zero claimed iOS runtime fixes. This is one packaging/verification improvement, with nine passing verifier tests. Native iOS parity, current macOS build evidence, private signing and device testing remain unfinished.
