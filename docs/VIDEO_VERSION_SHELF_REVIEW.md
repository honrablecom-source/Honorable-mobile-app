# Saved-version Activity review ownership

0 new user-facing features;1 correctness fix;0 scaffolding. Source103/500, batch65 unchanged. Focused host/API34 checks pass; actual Android shelf/worker/storage/media/device and full APK acceptance remain unverified.

The version controller already checks private storage ownership, current records, original documents and read grants on its worker. Its Activity admission callback compared closeState/access mode without exact shelf identity or the full imported-source URI/duration/private-owner/generation snapshot. This allowed the Activity boundary to miss source-map changes or restored-value stale reviews.

Entry now captures the shared full foreground project/source/duration/range/private-session/generation review before preparing the range snapshot or constructing the shelf. Admission, busy changes and UI messages are scoped to the exact current shelf. The existing controller releases busy before UI validity; that order is preserved so completion is not blocked by its own job.

Apply returns immediately at the UI callback for a foreign shelf. The actual application helper rejects a foreign owner before consuming anything; its own owner is consumed before full validation and canonical model application. Replaced, failed or completed approvals cannot revive after Undo. The captured primary source is used for the existing source reconciliation. `applySavedDraft` remains byte-identical: it commits the project/catalog/range, handles preview failure separately and keeps imports for Undo. Existing worker/controller/store/encryption, current-catalog checks and all permission/media validation callbacks are unchanged.

## Evidence and limits

620 focused host tests pass:8 new+612 retained. New cases invoke real Activity admission/application with constructor-free identity tokens: canonical version and one Undo/history, imported source merge/retention across Undo, full foreground/source/duration/range/generation/session/recovery/busy/imported URI/imported-duration/project identity refusal, foreign owner isolation, busy release compatibility, source reconciliation conflict refusal, codec/model Undo/Redo, and blocked entry before range widgets/worker/permissions.

The canonical application fixtures deliberately have no preview widgets. Actual `applySavedDraft` commits model/catalog/range state and catches its unavailable preview, returning false while preserving successful application. This verifies its existing separation in metadata fixtures; it does not reproduce a physical-device failure or execute the Activity status view.

Current Activity and tests compile against pinned API34 references and the hash-verified retained graph.19 source checks verify entry order, captured mode/primary, exact full admission and scoped callbacks, consumption before validation, canonical reconcile/application, existing preview outcome, owner assignment before open, byte-identical permission/media callback block and unchanged controller/store/version/project/draft implementations. Evidence: `.context/noble-fast-version-shelf-review/verification.json`, `compile.log`, `tests.log`, `wiring.json`, `exit.json`, `before-version-entry.kt.txt`.

Fixtures do not construct real shelf dialogs or run executor-to-UI scheduling, private-owner/Keystore/AtomicFile/provider/read-grant/media inspection, actual preview/accessibility, physical lifecycle or device behavior. Those remain integration/device checks. No configured SDK37/full module/Gradle/native/APK build or readiness claim. No dependencies, personal media uploads, benchmark, ranking or ledger changes. Broad build remains approximately138 source workflows.
