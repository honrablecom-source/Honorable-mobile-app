# Honorable — durable project context

Owner direction (October 11, 2026): continue toward a distribution-ready AI-powered photo and video search/editing application, including useful AI editing features. First establish a working, verifiable app and editing/export baseline.

## Product decisions

- Honorable — Find any memory. “You remember the moment. Honorable finds it.”
- Natural-language search over a personal camera roll, native on-device inference, local metadata/OCR/embeddings and sampled video frames. Do not silently add cloud media processing.
- Cinematic black canvas, large real photos, restrained monochrome controls and a small mint accent. Preserve the existing UI direction.
- Seran FAST/V1 costs 1 credit; VIDEO/V2 costs 3; TURBO/V3 remains locked and unchargeable. Protected benchmark holdout must remain untouched.
- 15 free monthly search credits, free-first deductions, no free rollover; purchased credits do not expire. Failed/cancelled searches and reopening saved results should not charge. Preserve the 31-option Memory Pass catalog and server ledger authority.
- Hybrid monetization: free allowance, Memory Pass purchases and a Studio subscription. Production store billing is unfinished.

## Verified checkout facts

- Repository: https://github.com/honrablecom-source/Honorable-mobile-app ; default branch main.
- Initial recovered commit: 9431194d13df3c8a5482d49221fc195b6c531095.
- Git root contains Honorable-mobile-app-main/ with the app and supporting server. Root .github/workflows/android-build.yml is the recognized Android workflow.
- React Native 0.87.0; Android package com.honorablemobile; Gradle wrapper 9.4.1. RN declares compileSdk 37, targetSdk 36, Build Tools 37.0.0 and NDK 27.1.12297006. Standalone Compose declares SDK 35 and AGP 8.7.3.
- Native PhotoEditorActivity supports basic local adjustments/transforms, undo/redo and copy export via the Android document picker. This session added on-device Latin-script OCR with review/copy, pixel-budgeted decoding, export cleanup and preview bitmap cleanup. Native compilation and 57 JVM tests passed; device behavior remains unverified.
- Local TinyCLIP model and tokenizer are present. No cloud search service is introduced by this work.
- Main lacks Video Editor V1, Media3 dependencies, native video edit/export integration and Home New Workspace entry described in the handoff. Both public branches were inspected: main at 9431194 and react-native-migration at 3f4f5d6. Neither contains Video Editor V1. Unpushed Codespaces work remains unverified.

## Historical reports requiring recovery/verification

The supplied handoff described a later Video Editor V1 with trim/split/reorder/speed/audio controls and MediaStore export, plus partial tests and a failed whole-app build. Those changes are absent from recovered main. Do not claim them as implemented here or overwrite a newer checkout if found.

## Release and feature priorities

1. Recover any later editor work, obtain a passing whole-app build and a self-contained internal APK with exact commit/hash identity.
2. Verify startup, auth, search/credits and non-destructive photo/video export on physical Android hardware. Resolve native trusted search completion and production billing before commercial release.
3. Add useful, real editing capabilities after the baseline is validated. Candidate directions: local photo enhancement, subject-aware editing, search-to-highlight video clips and captioned edits. These are proposed features, not implemented capabilities. Model choice, performance and any new cloud/credit cost need explicit evidence and product review.
4. Private beta, signing/deployment/store configuration, privacy and reliability evidence, then public distribution. iOS additionally requires a macOS/Xcode build and its own testing.

See HONORABLE_CURRENT_STATUS.md for the verified session state and next action.

## Local development environment

Portable Git, Node 22.23.3, JDK 17, Android SDK 35/37.0, NDK 27.1 and Gradle caches are in the adjacent `.tools/` folder. No permanent PATH change is needed. React Native CMake failed in the long original Windows path; a temporary H: mapping to the Git root is being used for native builds. Keep shared-source relative paths intact by mapping the entire Git root, not just the React Native folder.

The TinyCLIP tokenizer has a pinned byte digest. Windows autocrlf broke it during checkout; it was restored to the original LF bytes and `.gitattributes` now disables text conversion for the tokenizer and ONNX assets. Do not change integrity pins to accommodate corrupted assets.
