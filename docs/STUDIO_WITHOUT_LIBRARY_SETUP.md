# Studio remains available during library setup — November 5

0 new features, 1 core access/usability fix, 0 scaffolding. Existing Studio tools
are reused; they are not counted again. Source workflows remain 83, batch progress
45 beyond the baseline of 38.

Signed-in users previously needed library setup to reach Studio's existing
drawing, blank-image, photo-sequence and saved-project routes. Single-file editing
was available at the gate, but the wider creative workspace was hidden. Open
Studio is now offered while library status loads, when permission is denied, and
when the index is empty or being prepared. It renders the actual Studio component
without changing library permissions or starting indexing.

Library setup in the Studio header returns to the gate. Hardware Back closes the
inspector first and then returns to setup. The ordinary navigated Studio does not
install this special Back behavior. Main navigation/search children remain gated
by the existing readiness checks; opening Studio does not render them. If the
library becomes ready while Studio is open, users remain in Studio until returning.
Leaving invalidates its picker hook, so a late selection cannot open an editor on
the returned setup screen. Existing native authorization and guest-creation
boundaries remain in force.

Eight new tests mount the actual LibraryGate and Studio together with native
mocks. They cover loading/denied/empty/indexing/error setup, all five existing
creator/project callbacks, absence of permission/index calls, gated main content,
return navigation, inspector/hardware Back, readiness transitions and a late
picker result. All 176 Jest tests in 18 suites pass, typecheck exits 0, and lint
exits 0 with 0 errors and 38 warnings.

The expanded actual-component React Native Web harness checks Gate → Studio →
setup at 320/390/844px, existing creation callbacks, no search-shell admission,
and no permission/index requests. The original C084 source-browser regression
also passes. Both report zero page errors and external requests. Setup and Studio
390px screenshots were visually reviewed. Evidence and source/test hashes live
under .context/gate-studio-access.

No library setup bypass is added to search, no native search/index/ranking or
credit behavior changes, and no personal media or queries are uploaded. Native
permission/provider/editor launching, actual hardware gestures, IME/accessibility
and device/lifecycle acceptance remain unverified. No full native/Gradle/APK build
or packaged drawing asset refresh ran, preserving the owner's batch cadence.

Browser reproduction uses the existing preview dependencies and headless shell:

```sh
NOBLE_STUDIO_GATE_PREVIEW=1 NOBLE_STUDIO_PREVIEW_OUTPUT=/tmp/noble-gate-preview PLAYWRIGHT_BROWSERS_PATH=/tmp/noble-studio-browser-cache node Honorable-mobile-app-main/mobile-react-native/workspace-preview/studio-source-preview.mjs
```
