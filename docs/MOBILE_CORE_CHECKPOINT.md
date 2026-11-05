# React Native core checks — November 5

0 new features, 0 new fixes, 0 scaffolding. Existing dependency restoration is
maintenance. Source count remains 82 workflows, batch progress 44 beyond 38.

The existing mobile node_modules symlink pointed to a missing temporary target.
The current package.json and package-lock.json were copied into that same external
dependency directory and npm ci restored it, using /tmp for available disk space.
The copied lockfile remains byte-identical to the repository version; neither
manifest, lockfile nor symlink was changed. Installation exited successfully, with
peer-version and deprecation warnings retained in the full log. This checkpoint
does not claim those dependency warnings were resolved.

Commands run from Honorable-mobile-app-main/mobile-react-native:

```sh
npm run typecheck
npm run lint
npm test -- --runInBand --cacheDirectory=/tmp/noble-main-rn-jest-cache
```

All commands exit 0. Typecheck reports no errors. Lint reports 0 errors and 41
warnings, including inline-style warnings. Jest passes all 154 tests in 17 suites,
with no snapshots. Coverage includes guest/account entry, Studio start guidance,
workspace lifecycle and media routing, viewer actions/sharing, reduced motion,
library admission, credit/search presentation and app composition. Full logs,
command metadata and current source/config/test hashes are retained under
.context/mobile-core-check.

These are TypeScript/static checks and React/Jest tests using native mocks. They
do not execute the Android WebView/editor, media providers, native search engine,
playback/export pipeline, gestures, storage/lifecycle or real-device rendering.
No benchmark/holdout, media upload, full native/Gradle/APK build, signing or store
readiness claim follows from these results. Other-chat Kotlin recovery changes
are outside this checkpoint. Packaged drawing assets remain deferred to the
planned broad build; temporary dependencies may need restoring after environment
recreation.
