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

Ten focused Node cases pass on Linux, covering actual byte-layout fixtures, thin/universal architectures, platform spoofing, truncated/overlapping tables, executable path escape, absent JS, linked executables, provisioning profiles and archive identity. This validates the verifier, not an iOS build.

## Readiness boundary

The latest prior simulator workflow observed during this audit was [run40226461026](https://github.com/honrablecom-source/Honorable-mobile-app/actions/runs/40226461026), successful at historical source `1d374f6f3ae1779f87b3a3449112babd46d7f8a6`. No downloadable artifact was listed by its artifact API during this audit. It does not validate the current working tree or the new device-archive path.

This Linux workspace has no Xcode. A new successful macOS run and downloaded/verified current artifacts are still required before claiming fresh iOS packages. The current RN iOS target does not implement Android's native media search, photo/video editing or on-device AI modules. A successful shell archive cannot substitute for those ports or for real-device acceptance. The UI must continue to describe unavailable native tools honestly.

Count: zero new user-facing iOS features and zero claimed iOS runtime fixes. This is one packaging/verification improvement, with ten passing verifier tests. Native iOS parity, current macOS build evidence, private signing and device testing remain unfinished.

## First current-source macOS attempt

Run [43516346381](https://github.com/honrablecom-source/Honorable-mobile-app/actions/runs/43516346381) at `b125035f1e222959421df2ca46d64e03fee848be` stopped before CocoaPods/native compilation: seven verifier tests passed and two fixture-backed cases failed. macOS canonicalizes `/var` temporary paths to `/private/var`; the injected test plist reader compared a noncanonical fixture path against the production verifier's canonical path. The fixture now canonicalizes its application directory and a filesystem-alias regression checks that valid app/archive identity still passes. Production validation and rejection assertions are unchanged. This is one test-fixture portability fix, not a native iOS app fix or build-success claim; a fresh macOS run is required.

## Native integration work required for a usable iOS app

The current native contract lives in `mobile-react-native/src/native/HonorableNative.ts`: the RN screens call `HonorableSearchModule`, whose implementation exists only on Android. Port capabilities in that existing contract; keep unsupported actions unavailable until they work. A successful React Native shell build is only the packaging foundation.

1. **On-device search first.** Implement PhotoKit limited-library authorization, incremental indexing/cancellation, durable local media IDs and the real TinyCLIP tokenizer/image/text embedding/index path. Reuse the existing model assets and algorithm behavior, validating public/synthetic input/output fixtures against Android without inspecting Seran holdout or changing Android ranking. Preserve request ownership and no charge on failed/cancelled searches.
2. **Reuse actual AI weights/runtime.** [ONNX Runtime's official Objective-C/Swift API](https://onnxruntime.ai/docs/get-started/with-obj-c.html) provides an iOS route for the existing licensed TinyCLIP, Depth Anything, MI-GAN and upscale assets. Match input normalization, orientation, pixel formats, output interpretation and Stop/ownership behavior before exposing each capability. Benchmark/validate on an iPhone; Android/host inference success is separate evidence. This avoids replacing these models with heuristic lookalikes or a remote upload service.
3. **Reuse the creative workspace.** A WKWebView host can bundle the same Excalidraw/Three/GIF assets, with bounded local media/GLB/GIF/SVG/image bridges and explicit share completion. Implement source/account namespaces and revocation, or retain an explicitly disclosed guest-copy boundary. Do not silently move account photos into shared guest storage. Existing Android JavaScript-interface methods require a real iOS adapter; simply displaying HTML does not implement those methods.
4. **Finish core photo/video editing and copies.** Preserve originals, draft/Undo/lifecycle ownership, deliberate photo/video selection, orientation/color handling and actual save-copy export. Reuse the RN UI and appropriate platform media facilities while validating each native editor route. The legacy Swift protocols and unavailable embedding service are not working implementations. Advanced tools should remain hidden or unavailable until their real pipeline exists.
5. **Account, credits and signed release.** Implement the account boundary/Keychain and applicable Apple purchase/receipt paths against the existing authoritative catalog/server ledger, preserving V3 locked/unchargeable, V1/V2 costs1/3,15free monthly, free-first deductions, no rollover, nonexpiring purchases and no failed/cancelled search charge. Then verify native workflows on a device and sign/export with private Apple credentials configured outside the repository. The unsigned device archive does not establish these acceptance points.

This is an implementation handoff, not a claim that these ports, signing steps or iPhone tests are done. It adds zero user-facing features and does not reinstate the cancelled500-feature target.
