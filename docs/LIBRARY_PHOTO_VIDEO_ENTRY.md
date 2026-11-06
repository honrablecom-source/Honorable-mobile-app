# Library selection to existing photo-video creator

This reuses C028: **0 new user-facing features, 1 creation usability integration, 1 native entry adapter**. The shared register remains87 source workflows,49 beyond the38 baseline. No full native/APK build was run.

Library passes1–20 explicitly chosen photos in their selected order to the existing internal guest creator. Mixed photo/video selections are refused rather than silently dropping videos. Exact URI/type bindings are checked, and sharing, selection and creator launch use a shared pending guard. Errors and false-open results retain picks for retry. The creator opens for reviewing order, holds and format; encoding still requires its existing explicit Create action.

Native entry validates the bounded JSON/URI model, actual image MIME and readability of every original before launching an explicit internal intent with only the selected references and read grants. Restored state, including an empty selection, takes priority over the initial intent so removed photos cannot reappear after recreation. Existing access mode and creation pipeline remain in place. The selection footer stays below the virtualized list.

## Verification

- 258 Jest tests across24 suites pass, including7 new cases (5 screen,2 wrapper). An initial cold focused run passed24/25 with an existing5-second retry test timing out; unchanged warm rerun passed25/25. No timeout increase or virtualized-list replacement.
- 43 actual Android helper/model JVM tests pass:7 new entry tests plus36 retained. These exercise real Intent,ClipData,Bundle and selection objects with simulated MIME/read callbacks.
- 67 required Node tests pass; typecheck passes; lint reports0 errors and38 warnings.
- Current creator compatibility compile passed against actual API34/Media3 common1.11.1 and retained older reference APIs. This is not a full current native module/all-source/API37 compile.
- Actual Library/grid UI through React Native Web passes320x768,390x844 and844x390 selected-photo hold/failure/retry checks, exact ordered originals, mixed-selection refusal, blocked duplicate actions, visible footer and normal viewer reopening. Existing sharing regression mode also passes all three sizes. No page errors, preview alerts or external requests. Screens/results and full logs are retained under `.context/library-photo-video/`.

The browser uses synthetic library/native/navigation boundaries, outer text and zero safe-area insets. A preview-only navigation alias and explicit JSX configuration allow isolated tooling without the mobile dependency tree. It does not render the real native creator or exercise encoding. Correction after publication: the earlier milestone incorrectly described an opaque footer/layering fix. That style was absent from the published source. A fresh controlled browser comparison with and without that proposed style produced zero changed pixels, and badge hit testing found no footer obstruction. The unnecessary style was removed; no layout fix is claimed. Evidence is retained in `.context/library-footer-layering/`.

Full native-module compilation, real provider/descriptors/grant delivery, native lifecycle/rotation, bitmap decode, encode/export/reopen, physical devices and store readiness remain unverified. No guarantee of cancelling an already-started native operation is added. Temporary compiled jars/bundles may have been removed by environment resets; retained logs are evidence of completed checks, not a claim that those artifacts remain present. No personal media was uploaded; no app dependency, permission, search ranking, credits or ledger policy changed. No benchmark or holdout was run.
