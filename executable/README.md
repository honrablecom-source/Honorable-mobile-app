# Noble release artifacts

The owner requested removal of the old APKs on November13. All11 earlier tracked APKs have been removed from the current checkout; the old frozen138 debug APK was also deleted. Historical hashes in apk-report.json are marked retired and do not identify a downloadable current release.

Fresh current-source Android and iOS artifacts are in progress. No new APK/IPA is available in this directory yet. Only validated artifacts will be listed in release.json after a successful build and package verification.

Android internal tester builds use the public debug certificate and are not store releases. Developer debug APKs requiring Metro are not self-contained tester packages. iOS simulator apps, unsigned device archives and signed iPhone IPAs are separate artifacts; current native iOS search/editing is incomplete and cannot be represented as feature parity by compiling the shell.

See ../docs/RELEASE_FINISH_PLAN.md for the current acceptance plan. Never place private signing keys, credentials or personal media here.
