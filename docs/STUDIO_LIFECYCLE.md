# Studio screen lifetime — November 6

0 new features; 1 lifecycle fix; 0 scaffolding. Source register remains 84 workflows, batch 46 beyond 38.

Studio now checks its existing active-screen ref before starting library-source edits, local workspace launches or source paging. A delayed library-editor rejection after Studio unmounts cannot show an unrelated alert on the next screen; completion also avoids setting opening state after unmount. Pending guards still release in finally. This does not cancel an editor already launched or claim native activity cancellation. The existing useOpenWorkspace hook continues to own picker access/generation handling.

Three new actual Studio-screen tests cover delayed rejection after unmount, retained source/import handlers after unmount, and visible current-screen error with exact-source retry. Against the prior production source, the first two fail: an alert appears after leaving and the retained source callback launches the old source. The current-screen retry case already passes. With the fix all 209 Jest tests in 21 suites pass, typecheck exits 0 and lint exits 0 with 38 warnings. Before/full logs and tested hashes are in local `.context/studio-lifecycle/verification.json` and adjacent logs.

Tests mock the native editor/workspace and library boundaries. No physical-device activity, picker, foreground/background or permission acceptance is claimed. No native/APK build, new dependency, personal upload, search-ranking or credit change. Next broad native build remains deferred until approximately 138 source workflows.
