# Honorable — durable project context

Owner direction (October 11, 2026): continue toward a distribution-ready AI-powered photo and video search/editing application, including useful AI editing features. First establish a working, verifiable app and editing/export baseline.

## Product decisions

- Honorable — Find any memory. “You remember the moment. Honorable finds it.”
- Natural-language search over a personal camera roll, native on-device inference, local metadata/OCR/embeddings and sampled video frames. Do not silently add cloud media processing.
- Cinematic black canvas, large real photos, restrained monochrome controls and a small mint accent. Preserve the existing UI direction.
- Seran FAST/V1 costs 1 credit; VIDEO/V2 costs 3; TURBO/V3 remains locked and unchargeable. Protected benchmark holdout must remain untouched.
- 15 free monthly search credits, free-first deductions, no free rollover; purchased credits do not expire. Failed/cancelled searches and reopening saved results should not charge. Preserve the 31-option Memory Pass catalog and server ledger authority.
- Hybrid monetization: free allowance, Memory Pass purchases and a Studio subscription. Production store billing is unfinished.

## Verified checkout facts and recovered baseline

- Repository: https://github.com/honrablecom-source/Honorable-mobile-app ; default branch main.
- Initial recovered commit: 9431194d13df3c8a5482d49221fc195b6c531095.
- Continue the existing checkout on `codex/android-internal-readiness`. The owner authorized GitHub pushes on October 18; recovery/editor/security source was pushed as `5a6de152bb7af77b2e24eeabf67db0929c7b2e0a`. The newly supplied source ZIP matches the older recovered baseline in the selected files and must not replace newer reviewed work.
- Git root contains Honorable-mobile-app-main/ with the app and supporting server. Root .github/workflows/android-build.yml is the recognized Android workflow.
- React Native 0.87.0; Android package com.honorablemobile; Gradle wrapper 9.4.1. RN declares compileSdk 37, targetSdk 36, Build Tools 37.0.0 and NDK 27.1.12297006. Standalone Compose declares SDK 35 and AGP 8.7.3.
- Native PhotoEditorActivity supports basic local adjustments/transforms, undo/redo and copy export via the Android document picker. This session added on-device Latin-script OCR with review/copy, pixel-budgeted decoding, export cleanup and preview bitmap cleanup. Native compilation and 57 JVM tests passed; device behavior remains unverified.
- Local TinyCLIP model and tokenizer are present. No cloud search service is introduced by this work.
- At recovery, main at 9431194 and react-native-migration at 3f4f5d6 lacked Video Editor V1. The current working branch now includes Media3 video editing/export, project composition preview, Home New Workspace and typed local pickers. Complete ARM64 APK builds and JVM/RN checks pass; physical behavior remains unverified.
- Additional existing native AI assistance includes photo OCR, scene-aware color/exposure adjustment and an experimental sampled-frame video scene suggestion. These are implemented helpers, not proof of complete generative photo/video editing. Subject isolation, generative fill/expansion, relighting and advanced AI video remain roadmap work.
- Genuine Google credentials, browser session restoration and local logout were verified against the local production account code. There is still no accepted hosted HTTPS account API for the Android artifact, and the owner has no Android phone. Billing/hosting is deferred at the owner's request.

## Historical reports requiring recovery/verification

The supplied handoff described Video Editor V1 before it was recovered/integrated here. Current branch source and passing builds now establish packaged trim/split/reorder/speed/audio controls and composition preview/export. The current save path uses the system Save As document picker, not automatic MediaStore insertion. Physical correctness still needs execution. Preserve earlier work and use the latest current-status checkpoint rather than historical ZIP claims.

## Release and feature priorities

1. Maintain passing whole-app builds and self-contained internal APKs with exact source/artifact identity; continue small, reviewed improvements to the existing editors.
2. Verify startup, auth, search/credits and non-destructive photo/video export on physical Android hardware. Resolve native trusted search completion and production billing before commercial release.
3. Extend real editing capabilities incrementally. Explicit encrypted local video draft save/restore is the current engineering increment. Subject-aware edits, stronger search-to-highlight tools and captions remain future work. Existing scene-aware helpers still require phone validation. Model choice, performance and any new cloud/credit cost need explicit evidence and product review.
4. Private beta, signing/deployment/store configuration, privacy and reliability evidence, then public distribution. iOS additionally requires a macOS/Xcode build and its own testing.

See HONORABLE_CURRENT_STATUS.md for the verified session state and next action.

## Local development environment

Portable Git, Node 22.23.3, JDK 17, Android SDK 35/37.0, NDK 27.1 and Gradle caches are in the adjacent `.tools/` folder. No permanent PATH change is needed. React Native CMake failed in the long original Windows path; a temporary H: mapping to the Git root is being used for native builds. Keep shared-source relative paths intact by mapping the entire Git root, not just the React Native folder.

The TinyCLIP tokenizer has a pinned byte digest. Windows autocrlf broke it during checkout; it was restored to the original LF bytes and `.gitattributes` now disables text conversion for the tokenizer and ONNX assets. Do not change integrity pins to accommodate corrupted assets.
