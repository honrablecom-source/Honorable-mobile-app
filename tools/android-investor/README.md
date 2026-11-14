# Physical Android guest smoke

Prepared, not executed. This uses a real Firebase Test Lab physical device, never an emulator or VM. Owner approval is required before accepting Firebase's terms and uploading the published APK. Reuse the existing Honrable project, verify Spark/free and its remaining physical quota, choose exactly one physical device and one five-minute run. Stop if any payment, billing activation, paid-plan upgrade or virtual device is required. Do not create another Cloud project.

The script cold-launches the actual standalone app, chooses Edit without an account and requires the real guest studio heading. Strict mode prevents Robo from silently skipping an unavailable action; terminate prevents unrelated automatic exploration after the script. The script's configuration prefix is Firebase's documented Robo format, not a standalone JSON document. Disable automatic Google test-account login. No private media, Google login, credit grant, purchase or search belongs in this smoke run.

Use executable/Noble-core-ai-internal.apk, Android1.0.4/build5/source36e5ca58ffb8d6e64ad3881034943cac0b38caad. Expected SHA25653593a819cf64b0f85c9d650f698882c110522934b42422f2d22bc61977c37b2, size440449713bytes. Verify the artifact before upload; don't rebuild or substitute a differently configured APK silently. Upload guest-smoke.robo.json as the Robo script alongside it.

Retain the matrix/run ID, actual physical model and OS, script outcome, screenshots/video, crashes/ANRs and logs in ignored evidence. Redact credentials/identifiers before reporting. A green install or generic Robo result is insufficient: inspect that the guest action and studio assertion actually executed. A failure is evidence to investigate, not permission to weaken the assertion.

This smoke does not establish image/video inference, export destinations, storage recovery, thermal/large-media performance, Google account sign-in, trusted search completion or store readiness. Those need their own reproducible acceptance flows.

References: [Robo script format](https://firebase.google.com/docs/test-lab/android/robo-scripts-reference), [uploading a Robo script](https://firebase.google.com/docs/test-lab/android/run-robo-scripts), [physical-device Spark quota](https://firebase.google.com/docs/test-lab/usage-quotas-pricing).
