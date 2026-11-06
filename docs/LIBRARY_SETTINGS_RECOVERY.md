# Recover blocked library access — November 6

0 new features, 1 core access/usability fix, 0 scaffolding. Existing permission
setup is improved; source workflows remain 83, batch progress 45 beyond 38.

After an explicit access attempt while the library gate remains denied, a
recovery panel offers Open device settings and Check access again. The panel is
hidden on the initial setup screen. Its guidance is conditional: settings are an
option if the permission prompt no longer appears, not a claim that every denial
is permanent. Existing single-file editors and Studio remain available.

Opening settings never grants permissions itself. Check access again invokes
the existing readonly library reload; it does not show another permission prompt,
start indexing or unlock search. The existing permission/index readiness gate
still decides whether main navigation can render. Foreground revalidation from
the prior milestone also remains in place.

The recovery panel admits only one operation at a time, disables its actions
while pending, clears old failure feedback on retry and reports actionable
settings-opening failures. Unmount invalidates the owner, so a late failure or
retained action cannot affect Studio or open another settings screen.

Final evidence under .context/library-settings-help:

- All 188 Jest tests in 18 suites pass, including five new actual Gate cases for
  explicit settings/check callbacks, same-tick admission, failure/retry, unmounted
  ownership and unchanged readiness gating. Typecheck exits 0. Lint exits 0 with
  0 errors and 38 warnings.
- The actual-component RN Web harness uses a synthetic Linking boundary. It
  checks initial hiding, an explicit settings failure then retry, one explicit
  access reload, retained Studio access and gated search, with no automatic
  permission or indexing callbacks. Recovery layout passes 320/390/844px overflow
  checks; the 390px recovery and error screenshots were visually reviewed.
- The normal C084 Studio source-browser regression also passes. Both browser
  modes report zero page errors and external requests. No application dependency
  or lockfile changes are introduced.

Browser reproduction, using the existing preview dependencies and headless shell:

```sh
NOBLE_STUDIO_SETTINGS_PREVIEW=1 NOBLE_STUDIO_PREVIEW_OUTPUT=/tmp/noble-settings-preview PLAYWRIGHT_BROWSERS_PATH=/tmp/noble-studio-browser-cache node Honorable-mobile-app-main/mobile-react-native/workspace-preview/studio-source-preview.mjs
```

The tests mock the settings handoff and native status; they do not execute real
Android Settings, permission dialogs, AppState delivery, providers or devices.
Native search/index/ranking, platform permission lists, billing and upload policy
are unchanged. No full native/Gradle/APK build or packaged drawing asset refresh
ran, respecting the owner's batch-build cadence.
