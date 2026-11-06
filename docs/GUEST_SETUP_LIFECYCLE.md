# Guest canvas setup screen lifetime — November 6

0 new features; 1 lifecycle fix; 0 scaffolding. Source register85/500, batch47 beyond38 remains unchanged. Broad native/APK builds remain deferred until approximately138 source workflows.

Guest canvas setup operations now require the current mounted screen before reading/saving/deleting metadata or accepting a saved-setup review. Unmount marks the instance inactive and clears the review token. Delayed native replies are ignored before JSON parsing/state updates; errors and busy feedback also require the active instance. The pending flag still releases in finally. Choosing a canvas and review cancellation respect screen lifetime. Existing dimensions/background validation, review snapshots, exact replacement/deletion admission, current-screen retries, canvas creation and native workspace-hook guards remain.

Two new actual AccountGate/guest-screen cases reproduce failures against the prior production source: a retained confirmed Delete calls native storage after guest unmount, and a retained Refresh reads storage after unmount. Both require no new native operation after closing. Native methods are mocked, so this proves JavaScript admission rather than filesystem durability or Android lifecycle behavior. An already started native operation can still finish; this change does not roll it back or claim cancellation.

Final command results and tested hashes are recorded locally in `.context/guest-setup-lifecycle/verification.json`, with retained before and full logs. No new dependency, personal-media upload, search ranking, credit or native editor change. Physical-device/background/rotation, native storage and full app/APK validation remain pending.

Final validation: all218Jest tests/21suites pass, including the2new regressions; typecheck exits0 and lint exits0 with38warnings. Existing save/delete review, retry and creation cases remain in the full suite.
