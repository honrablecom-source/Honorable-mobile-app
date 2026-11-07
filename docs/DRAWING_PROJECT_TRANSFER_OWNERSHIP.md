# Drawing project transfer ownership

November 7: **0 new user-facing features; 1 correctness fix; 0 app scaffolding. Source 94/500, batch 56 unchanged by this fix.**

Project save/open now consumes a matching picker result once. Duplicate acceptance cannot enqueue a second read/write, and a late cancellation cannot clear the already accepted transfer. Results received before foreground retain only operation metadata in Activity memory and wait for resume before provider work. The normal pause caused by the system picker keeps the unanswered picker alive. No destination/source URI or project payload was added to saved-instance state; the existing interruption-label behavior remains.

Once a transfer starts, pause invalidates its exact ownership. Old queued workers return before provider access and cannot release later project/image/print/SVG work. Active provider access checks ownership at boundaries; cancellation is cooperative and cannot undo an already completed write. An interrupted save tells the user to inspect the chosen destination for a possible incomplete file. Read results retain the import gate until the matching chunk consumer releases them; wrong IDs do not release it.

Upload admission/chunks now require foreground ownership. Pause cancels an incomplete upload, and finish atomically detaches the exact upload under the shared lock before opening the picker. Stale upload failure/release cannot clear a replacement. The source draft, project JSON semantics, explicit destination chooser, UTF8/file-size limits, recovery format and saved-project shelves are unchanged.

The destination path validates the full strict UTF8 bytes before opening a potentially truncating provider stream. The writer checks cancellation/interruption before each bounded8192-byte write, before flush and after flush. Stream ownership stays with the caller; native use closes it on completion/failure. Write/flush errors propagate with no automatic retry or claim of successful save. No rollback, provider durability or delivery guarantee is introduced.

## Evidence

Three unchanged actual Activity regression families fail before: paused metadata queues work, duplicate results queue twice, and a late cancellation releases the active gate. Original73 retained tests pass. Final98 focused host tests pass:15 new Activity/Intent/ownership families,10 new JDK writer/file families and73 retained drawing/reset/image/print/SVG cases. Eight current drawing production sources compile against Android API34 with retained surrounding photo references; this is not the full configured SDK37 module.

Constructor-free Activity tests use synthetic content URIs, a recording executor, an explicit current UI-thread field and a Context resolver-access probe. They exercise private result/phase/identity transitions, exact upload/read release and actual queued cancelled workers. Invalid saved text is rejected without accessing the resolver. No synthetic URI is opened. Writer tests execute strict byte preservation, exact budget, bounded writes, pre-write/between-write/pre-flush cancellation, thread interruption, write/flush failures and a real temporary-file UTF8 roundtrip.

Existing actual Excalidraw project/recovery browser regressions pass: project download/upload, explicit staged Open/Insert with embedded photo and Undo, malformed-file/source preservation, read/save chunk protocols, stale/cancel/error handling, exact opaque recovery copies, reload and320/390/844px widths, with zero page/external errors. Screenshots at320px project review and844px recovery were inspected. The fixtures only gained isolated evidence-output options; product JavaScript was unchanged and current input hashes match the retained bundle. These browser native boundaries are simulated.

Evidence: `.context/drawing-project-ownership/verification.json`, before/final compile/JVM logs, current source hashes, project/recovery browser logs and artifact directories. Earlier completed Node and drawing-JS checks belong to their own checkpoints; they were not recounted as newly executed for this native-only change. No dependencies, benchmark/search ranking/credit/ledger, personal-media uploads or full native/APK build changed.

## Remaining acceptance

No actual Android picker/super.onActivityResult/onResume ordering, WebView chunk bridge, provider/FD/truncation, partial-write recovery, process death, storage durability, UI/accessibility or device execution. At the approximately138-workflow build boundary, test normal picker cancellation/return, background-before-result deferral, pause during read/write, provider failure/slow output, retry and recreation with a saved drawing. Inspect partial destinations without claiming rollback. Regenerate and package the complete current app/assets together; source/API/browser/JDK evidence does not establish device or store readiness.
