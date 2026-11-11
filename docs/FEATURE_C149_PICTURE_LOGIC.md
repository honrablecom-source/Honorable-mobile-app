# C149 — Picture-logic worksheet with verified unique solution

One distinct user workflow: Layouts → Picture logic turns a supplied pixel pattern into numbered row/column clues, a blank editable grid and an optional separate answer key. Registered at source142/500 after final verification. **1 new source workflow;0 separate fixes;0 scaffolding.** Later checkout source is excluded from the pinned138-workflow native build.

## User outcome and rules

Enter a rectangular5–15row by5–15column pattern using `#` for filled cells and `.` for empty cells. Include both kinds. Numbers describe consecutive filled runs with at least one empty cell between runs;0means a wholly empty line. A local verifier must establish exactly one solution, reproducing the complete supplied pattern, before a worksheet can be inserted. Ambiguous patterns, malformed input and proofs that exceed the work/deadline limit are refused without a partial puzzle.

Review the actual clues and optional answer, then insert one group of editable text/grid lines/filled answer cells beside existing work. Existing objects and embedded photo bytes are retained. Answer artwork is separate below the blank puzzle; disabling it retains only the puzzle/clues. Later edits can change solvability; verification applies to the original generated worksheet. OneUndo removes the group; Redo/reload and portableJSON/SVG preserve it. Title is1–48visible characters, cell size20–48pixels, and the whole new group must fit the existing500-object/draft budget.

This is a different outcome from the C147maze: it derives number clues from user-supplied artwork and proves a unique cell assignment, rather than creating an entrance-to-exit route.

## Solver and lifecycle

`nonogram.mjs` enumerates each line's legal run placements. Constraint propagation filters row/column domains against assigned cells and adds cells agreed by every remaining pattern. Exhaustive binary-cell branching then explores both siblings before accepting a sole solution; it stops after a second solution or refuses at10000search nodes. A single found solution followed by budget exhaustion is not reported unique. Inputs are at most15cells per line; no random generator, external model or uploaded data is used.

`nonogram-worker.mjs` runs the proof in a fresh owned local Blob worker. `nonogram-task.mjs` checks canonical source pattern, complete binary grid, both clue sets, dimensions, multiplicity and bounded node count before publishing. Foreign/stale/partial messages cannot consume an operation. Success/error/abort/transport/deadline terminate the worker and revoke its URL; the normal task deadline is10seconds. Component revision/hidden/unmount/Stop and sticky drawing ownership prevent late results from publishing.

`PictureLogicTool.jsx` renders the exact worksheet plan. Shared reviewed-worksheet insertion rebuilds/compares that plan, verifies current source and whole-draft capacity, persists before renderer mutation and captures one history operation. Quota refusal preserves the old scene. The C147maze uses the same helper and is covered by a retained browser regression. No dependencies or native permissions were added.

## Evidence

- 19new focused cases:11core/solver/layout and8worker protocol. Every legal line domain through10cells matches independent binary enumeration. Every2×2,2×3,3×2and3×3clue class across656binary grids matches complete brute-force solution multiplicity; contradictory/ambiguous/budget/cancellation/mismatched-proof paths are checked. Known5×5and15×15unique patterns reproduce exactly; zero clues, answer placement, Unicode wrapping, geometry/object bounds, caller immutability and deterministic behavior pass.
- All631drawing tests and70Node product/tooling tests pass without skips. Final corrected product/style source is used for the631case run.
- Actual browser independently reads rendered row/column clues and enumerates whole-row assignments with column-prefix checks, instead of using production propagation/cell branching. A5×5pattern,5×6pattern with an empty column and15×15cross have exactly one matching solution. All grid lines and answer cells match the supplied pattern; actual inserted vectors, numbers and dimensions match the reviewed preview.
- Ambiguous diagonal and invalid pattern refusals, answer-choice invalidation, real worker Stop/hidden termination, an actual late proof after edit→Undo refusal, original photo/geometry, quota/500-object capacity, one group/Undo/Redo/reload/portableJSON/SVG and320/390/844px44pxcontrols pass. The15×15cross inserts157editable objects including its separate answer.
- Initial recovery test found zero-width answer strokes restored as2pixels by the existing renderer. The new workflow now uses stable1pixelstrokes matching the grid, and full semantic recovery assertions pass unchanged. Initial failure is retained in `.context/noble-fast-web/milestones/C149-initial-recovery-failure/`; this correction completes the new feature and adds no separately counted fix.

Immutable final corrected feature proof: `.context/noble-fast-web/milestones/C149/final/`; earlier proof remains alongside it. Production-input SHA256: `ca56eb437f97732ebd9cf12b7fa5721d07853ec2825e17159064f0fc3d7dd50f`. Retained maze shared-helper browser regression passes against the same production source; its complete receipt/log/manifest is saved under `retained-maze/`.

## Limits

Source/browser verification only. Actual Android WebView, font/editing/clipboard/print behavior, TalkBack/accessibility, lifecycle/mobile memory/performance, native export, device/store acceptance and printed-puzzle usability are unverified. Exhaustive search is bounded, so some unique puzzles may be refused when proof cannot finish; no partial or approximate uniqueness is offered. Output retains editable artwork, not a continuously enforced solver certificate after edits.

No per-feature full-app/Gradle/APK build or tracked138asset refresh. Search ranking, holdout/benchmark, credits/catalog/server ledger and personal-media uploads are untouched. Main's pinned138integration and separate source work remain outside this outcome. Goal500 remains active.

Pinned138manifest and all271tracked generated-file hashes were rechecked unchanged before registration. Final rectangular test additions changed only verification code; product input bytes remain identical to the corrected passing source above.
