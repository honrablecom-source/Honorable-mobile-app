# Noble native preview with Appetize

Owner explicitly authorizes using Appetize to test Noble. Work solo and use SDK/API-based test automation; no computer-use tools or subagents were authorized. Keep the prior no-per-feature-APK-build instruction. Uploading a new build is separate from editing browser preview source.

Prepared candidate: `executable/Noble-x86_64-debug.apk`, 167,333,841 bytes, real ZIP/APK containing x86_64 native libraries. Exact SHA-256 and preflight status are recorded in ignored `.context/appetize-candidate.json`. This is an older existing APK. It does **not** contain the current 35-feature expansion's latest unbuilt source changes. Do not present a baseline Appetize session as evidence for C034/C035 or other additions absent from that artifact.

Access checked: no Appetize connector is available and none of the supported Appetize API-token environment names is configured. Only presence was checked; no secret values were printed. A pending user question requests either an existing Appetize app link or secure workspace configuration of APPETIZE_API_TOKEN. Tokens must stay out of chat, source files, logs and commits.

Appetize upload, session execution and Android behavior are **not yet performed**. No new APK build, account signup, subscription purchase or paid device session has been started.

Once access is available, record the uploaded build ID/link and exact artifact hash, then use synthetic media to exercise the artifact's actual available flows:

1. Launch and inspect welcome/guest entry at phone dimensions; capture actual native screenshots.
2. Select a synthetic photo, edit it, compare, Undo/Redo and export a new copy; inspect output dimensions/pixels and verify original bytes remain unchanged.
3. Select a synthetic video, preview a trim, edit available title/caption/audio controls and export a copy; inspect actual output metadata and playback.
4. Close/reopen and recreate activities where supported; check saved-state recovery, unavailable-source errors and cancellation.
5. Collect relevant device/app errors without capturing credentials or personal data. Bind each result to its tested APK version.

After a future batch APK containing current source exists, separately verify the new source workflows, including regional recipe persistence/namespace isolation and selective-color preview/export/session recovery. Browser-mirror evidence and native API compilation are not substitutes for running that APK. Cloud emulator testing remains distinct from physical-device hardware, memory/thermal and media-codec acceptance.

Official references:

- [Android APK/APKS upload](https://docs.appetize.io/platform/app-management/uploading-apps/android)
- [REST API authentication](https://docs.appetize.io/rest-api)
- [SDK test automation and sessions](https://docs.appetize.io/javascript-sdk)
- [Emulator/hardware limitations](https://support.appetize.io/does-appetize.io-use-emulators-or-physical-devices)

0 app features added by this testing preparation; source expansion remains 35/500. Test preparation is development scaffolding, not an app feature or a test-pass claim.
