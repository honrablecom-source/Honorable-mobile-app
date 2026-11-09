# Reset approval ownership and consumption

0 new user-facing features;1 correctness fix;0 scaffolding. Source103/500, batch65 unchanged. Focused host/API34 evidence passes; actual Android UI/device and full APK acceptance remain unverified.

Reset already checked the full project/source/session snapshot and tracked dialog visibility. Its callback did not consume approval or require the current inspector owner. In metadata fixtures, a still-visible tracked approval could reset again after Undo restored the same snapshot, repeat an already-original no-op, or act while a successor owned the inspector.

Reset now uses the shared inspector boundary and exact current dialog before mutation. Successful and no-op approval consumes that owner; a foreign old dialog cannot consume a newer owner. The private three-argument reset helper API remains. All reset model/source catalog/subtitle/music/history behavior is unchanged. A no-op reports that no edit was added. Both changed and unchanged successful reset retain the original selection/player/range refresh path; preview failure is reported separately.

## Evidence

The original601-case Activity, hash-verified and compiled before the production edit, was run with the updated11-case reset fixture. Its original8 assertions passed; exactly3 new ownership tests failed: old versus successor owner, still-visible approval after Undo, and repeated no-op approval. Logs and hashes are retained in `.context/noble-fast-reset-once-review/before-proof.json`, `before-tests.log`, `before-compile.log` and `before-activity.kt.txt`.

The final current Activity/reset fixture compiles against pinned API34 references and the hash-verified retained graph.604 focused host tests pass:3 new plus601 retained. All8 original reset assertion bodies remain byte-identical; their dialog fixture adds the current inspector owner. Existing cases still cover blocked entry, approved reset/one Undo/Redo, subtitle/music history retention, dismissed/untracked/replaced reviews, full snapshot changes, foreign projects, no-op history and source catalogs. The new cases directly check the missing consumption/current-owner contract.

18 source checks verify the helper API, exact/current shared commit, no-op and preview messages, original refresh/disclosure, tracked inspector, preserved original8 tests and unchanged project/draft/review model files. An initial604-case run passed; a final rerun after preserving no-op refresh also passed604 with current hashes. Initial artifact/logs are retained separately. Evidence: `.context/noble-fast-reset-once-review/verification.json`, `tests.log`, `compile.log`, `wiring.json`, `exit.json`.

The dialog/view/Activity fixtures allocate metadata without Android constructors and execute the actual approval/project functions. They do not reproduce actual native dialog construction/automatic dismissal, simultaneous-window interaction, refresh/accessibility, physical lifecycle or rendering/device behavior. Those remain integration/device checks; no physical-device bug or readiness claim is made. No configured SDK37/full module/Gradle/native/APK build, dependencies, uploads, benchmark, ranking or ledger changes. Broad build remains approximately138 source workflows.
