# Noble Android1.0.5 / build6 — startup repair candidate

0new user-facing features;1Android shrinker compatibility fix.

The physical Firebase Test Lab Pixel5/API30 run exposed a startup crash in1.0.4: WorkManager's Room-generated WorkDatabase_Impl remained named in the APK but its required no-argument constructor was missing. Explicit Room constructor retention repairs this reflection boundary without disabling R8 for the app. A new DEX inspection gate requires the public executable constructor in actual internal/debug/release/ABI APKs before verified publication. Class-name presence alone is insufficient.

This candidate keeps the four existing image-studio AI graphs, full image/video engines, original-preserving workflows and local native search implementation. It does not change ranking or credit policy. Existing approved Google audience/API build settings may now be present; configured strings do not establish working Google sign-in or trusted search completion. The deployed account service deliberately rejects production search start while trusted completion is unavailable.

Candidate status: full Android build, actual APK inspection and repeat physical-device startup acceptance must be verified before promotion. Public debug signing is internal testing only. No store readiness, successful live Google/Android login, inference/export/storage/thermal hardware acceptance or iOS parity is claimed. The old1.0.4 tester is a known failing startup baseline and must not be presented as investor-ready.
