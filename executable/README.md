# Noble release artifacts

The owner requested removal of the old APKs on November13. All11 earlier tracked APKs have been removed from the current checkout; the old frozen138 debug APK was also deleted. Historical hashes in apk-report.json are marked retired and do not identify a downloadable current release.

A fresh **iOS simulator ZIP** is available: [Noble-iOS-simulator.zip](Noble-iOS-simulator.zip), with its [Mac verification receipt](Noble-iOS-simulator.receipt.json). Source878e8f47b7c90457dd77f018ba2b308d515de64c;20259844bytes; SHA256 `9b11b4fe2d71a3b2e2174e8be3b4cbe1a42fbff2efd3eda822f48d993aa4ab21`. ZIP integrity and actual executable/JavaScript bytes match the receipt. Open the included app in an Apple Silicon iOS Simulator on a compatible Mac; this is an unsigned simulator shell with native search/editing/AI absent, not an iPhone IPA.

Fresh Android APKs and the unsigned iOS device archive are still building. No new APK or signed IPA is available here yet. Only validated artifacts are listed in release.json; a source feature or isolated compile does not prove an APK includes it.

Android internal tester builds use the public debug certificate and are not store releases. Developer debug APKs requiring Metro are not self-contained tester packages. iOS simulator apps, unsigned device archives and signed iPhone IPAs are separate artifacts; current native iOS search/editing is incomplete and cannot be represented as feature parity by compiling the shell.

See ../docs/RELEASE_FINISH_PLAN.md for the current acceptance plan. Never place private signing keys, credentials or personal media here.
