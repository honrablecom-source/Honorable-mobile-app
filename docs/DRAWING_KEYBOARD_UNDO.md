# Drawing keyboard Undo

November 8 — **0 new features; 1 keyboard history correctness fix.** SVG import exposed a general canvas problem: on a fresh drawing, ArrowRight moved a rectangle from x=40 to x=41, but Undo left it at x=41. The same result reproduced without opening SVG import or using the object locator. Before evidence is retained in `.context/svg-import/editor-keyboard-undo-failure.log` and the two keyboard diagnostic JSON files.

The pinned Excalidraw 0.18.1 arrow-nudge branch mutates positions without requesting a store history increment. A narrow build hook inserts the same capture request already used by the library's other edit paths before that branch mutates elements. It matches one exact branch in the pinned production module and fails the build if the upstream text changes. Installed dependency bytes, movement/binding logic, modifier handling and MIT notices remain intact.

The actual browser regression now passes first nudge Undo/Redo, successive separately rendered keys, the library's existing five-unit Shift step and reload. The fresh-canvas test opens neither SVG import nor the object locator. The SVG workflow also passes rejection after an actual keyboard edit and subsequent Undo. Shape-combine, owned-photo and finishing-guide browser regressions pass against the same final bundle.

Two test-oracle mistakes were corrected separately: clicking the header Save button between successive keys moved focus out of the editor, so that check now uses the existing native save hook while keeping editor focus; the Shift expectation was changed from an assumed ten units to the pinned constant's actual five units. Neither correction changes movement behavior or counts as an app fix.

The maintained regression is `creation-web/test/drawing-keyboard-history-preview.cjs`; the build hook is in `creation-web/build.mjs`. Source/browser proof does not establish native WebView, hardware keyboard, APK or device acceptance. Tracked native assets are unbuilt until the owner's batch boundary.
