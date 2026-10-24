# Dependency review — October 11, 2026

October 18 continuation: refreshed `npm audit --omit=dev` against the current RN lockfile. Counts remain **0 critical, 25 high, 5 moderate** affected package entries. No forced fixes, framework downgrade or lockfile changes were made. Full RN lint now passes with 34 warnings / zero errors; standalone Compose regression passes 57 tests. Complete ARM64 internal builds have passed; current exact-artifact evidence is in HONORABLE_CURRENT_STATUS.md. These findings remain an unresolved security-review gate, not production clearance.

Local person-cutout increment: added the pinned bundled `com.google.mlkit:segmentation-selfie:16.0.0-beta6` dependency to both Android targets, following [Google's implementation reference](https://developers.google.com/ml-kit/vision/selfie-segmentation/android). This beta SDK targets people; it is not a general object/generative editing engine. The vendor model is `selfiesegmentation_mlkit-256x256-2021_01_19-v1215.f16.tflite`, 249,024 bytes, SHA-256 `8d13b7fae74af625c641226813616a2117bd6bca19eb3b75574621fc08557f27`. Verify that exact model reaches each APK. The npm audit does not assess this native dependency; model quality, hardware compatibility, licensing/security disposition and phone behavior remain acceptance work. No media upload or paid inference provider was introduced.

The compatible dependency update was installed locally and passed TypeScript, ESLint (zero errors), and all 12 React Native tests. The full Android build remains in progress.

Production npm audit after the update: **0 critical, 25 high, 5 moderate** affected package entries. This is not a claim of production security clearance. Full development-tool audit also has additional findings.

## Changes

- Override shell-quote to 1.12.0 to address the critical command-injection advisory [GHSA-pqg4-j6r4-53mv](https://github.com/advisories/GHSA-pqg4-j6r4-53mv).
- Apply compatible updates to brace-expansion, js-yaml, compression, joi, qs, source-map-js, React Navigation core, NativeWind/CSS interop and Worklets through the lockfile.
- Constrain Reanimated to ~4.6.0 and Worklets to ~0.12.1 to preserve the compatible minor pairing. The installed Worklets patch is 0.12.2. No forced peer dependency resolution was used.

## Remaining advisory roots

| Package | Advisory | Current limitation |
| --- | --- | --- |
| braces | [Nested pattern stack exhaustion](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm) | Audit marks versions through 3.0.3 affected; dependency chains include Metro, CLI and Tailwind. |
| image-size | [JXL/HEIF loop](https://github.com/advisories/GHSA-5p2g-fcmc-qvqq), [ICNS loop](https://github.com/advisories/GHSA-w3rx-r6r6-pgpr) | Audit marks versions through 2.0.2 affected; a compatible repair was not supplied by the completed audit-fix operation. |
| postcss-selector-parser | [Quadratic selector parsing](https://github.com/advisories/GHSA-rj75-hqrm-r3gf) | Used through NativeWind/Tailwind build tooling; patched major compatibility requires review. |

Many high-severity package entries are propagation through these build/development dependencies. Reachability in the shipped app has **not** been proven or dismissed. React Native 0.87.0, Reanimated and CLI must not be blindly downgraded to old versions suggested by npm audit: those suggestions cross framework compatibility boundaries.

Before public distribution: review patched upstream versions, repair compatible chains, verify production bundle reachability, and rerun Android packaging and the behavior checks. Do not use npm audit fix --force as a release shortcut.
