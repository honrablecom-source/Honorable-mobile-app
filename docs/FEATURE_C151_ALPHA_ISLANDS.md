# C151 — Separate transparent artwork into individual items

One distinct source workflow: Photo tools → Separate transparent items finds every connected nonzero-alpha island in supplied artwork and extracts a complete labeled set of PNGs. Registered at source143/500 after verification. Noble-main subsequently reconciled the verified entry into all four shared handoff notes. **1 new source workflow;0 separate fixes;0 scaffolding.** Excluded from the pinned138-workflow native build.

## User result

Choose edge-only4-neighbour or edge-and-corner8-neighbour connectivity, preview every item and its source bounds/padding, then insert the complete set as one group or explicitly hand one reviewed PNG to the photo editor. This follows existing transparency, not recognized subjects; an opaque image normally has one island. Exactly2–6islands are required. Blank, single-island or seventh-island results refuse the whole operation, including tiny specks; no visible source pixel is silently dropped.

Each output contains only its component, even where bounding boxes overlap (for example, a ring surrounding a separate dot). Tight bounds are centered into at least8×8pixels with transparent padding when necessary. Decoded RGBA values are copied without scaling; transparent output pixels are canonical black/zero alpha. Each exact PNG must fit192KiB. The full embedded source before canvas crop/rotation/flip/opacity remains separate and unchanged.

Complete-set insertion adds labeled independently editable images as one group. Labels retain tight source origin, dimensions and minimum-size padding. Exact reviewed PNG bytes are retained. OneUndo removes all new items/labels; Redo/reload/portableJSON/SVG retain them. Capacity or storage failure refuses the complete set before renderer mutation. Single-item handoff uses reviewed bytes including disclosed minimum padding; whole-drawing exports can add drawing padding or resample artwork.

This differs from C131equal-grid slicing: component detection derives irregular masks/bounds from alpha, preserving isolated holes/overlapping bounds rather than cutting fixed rectangles. It also differs from C075single-region color removal.

## Implementation and ownership

`photo-islands.mjs` uses bounded iterative flood fill with explicit4/8neighbour adjacency over at most1024square pixels. Discovery is source scan order and stops with refusal at a seventh component. A full-source label map controls copying so other islands are excluded from overlapping boxes. Minimum-size padding and pixel counts are explicit. Only after the complete2–6component graph is known are output buffers created.

The fresh single-use local Blob worker is owned by `photo-islands-task.mjs`. Private source copies survive transfer/caller edits. The client validates every source/output dimension, component index/order, tight bounds, padding, pixel count, exact source RGBA for visible output pixels, transparent canonicalization and whole-source alpha coverage without duplicates/drops. All success/error/abort/deadline/transport paths terminate/revoke resources. Decode, worker and sequential exact PNG encoding share an overall30second deadline and hidden/Stop/current-source guards.

`PhotoIslandsTool.jsx` owns previews/Blob URLs and source/settings revisions. Editor insertion verifies every PNG/dimension before merging the complete file set, checks object/file/draft capacity, persists before renderer mutation and captures one history step. Sticky source invalidation survives edit→Undo. Escape bypasses the owned busy gate to cancel a held second verifier. No new dependency, native permission or upload was added.

## Evidence

- 16 new focused cases:10kernel/plan and6protocol. Independent union-find checks120random alpha masks in both modes, exact every-component RGBA/padding, nested overlapping boxes, diagonal touches, alpha1/128/255/hidden RGB, no discarded visible pixels, blank/opaque/seventh refusal, source guards and1024square grids. Task metadata/view/order/sourceRGBA/count/foreign/abort/deadline/transport rejection/cleanup pass.
- All 647drawing cases and70Node product/tooling cases pass, no skips. Current corrected labels are included in the647case run.
- Actual browser independently union-finds the decoded source and compares **every output RGBA byte** with the corresponding component-only expected buffer. Nested bounds, minimum8padding, scan order,4vs8connectivity and six outputs pass.
- A1024×1024ring yields a28672bytePNG with all4194304decoded output bytes compared; the separate center pixel yields an8×8/114bytePNG with padding3,3. The ring output does not include the center item. Source PNGs and all inserted output bytes remain unchanged.
- Raw exact-item handoff refusal/retry, settings/hidden/Stop/realworker termination, second-encoding cancellation, quota/eight-file-capacity refusal, one grouped labeled set/Undo/Redo/reload/portableJSON/SVG, held second bitmap verifierEscape with no partial publication and actual edit→Undo stale approval refusal pass. A deliberately oversized second encoder blob tests complete-set size refusal; it is synthetic encoder injection, not a claimed naturally oversized source case.
- 320/390/844px layouts fit horizontally and action targets are44px; no page/CSP/external-request errors. Initial browser recovery exposed zero-dimension text labels being removed by renderer restoration. Explicit220×35labels/line-height1.25 and nonoverlapping slots complete the new workflow; the full semantic reload assertion is unchanged. Initial failure is preserved under `.context/noble-fast-web/milestones/C151-initial-label-recovery-failure/`.

Immutable corrected proof: `.context/noble-fast-web/milestones/C151/`. Production-input SHA256: `83a035dbf41afdb280ef15e7db49f49928f8668fb98546846ca2b2968f737917`. All271pinned138generated-file hashes and manifest were rechecked unchanged.

## Limits

Source/browser evidence only. Real user artwork, Android WebView/provider/nativePNG/photo-editor/save-copy, mobile memory/performance, TalkBack/lifecycle/accessibility and physical-device/store behavior remain unverified. Adjacency is literal alpha connectivity: small nonzero-alpha bridges can join visually separate content; no semantic segmentation or automatic speck removal is promised. Exact PNG handoff is browser-simulated; later native editing/export choices may resample or recolor it. Workspace display size can differ from the intrinsic PNG grid.

No per-feature whole-app/Gradle/APK build or tracked138asset refresh. Native search/ranking/holdout/benchmark, credits/catalog/server ledger and personal-media uploads are untouched. Main's other claims/source work and pinned integration remain separate. Goal500 remains active.

Native packaging status is separate: Noble-main confirmed the old138packaging process and `/tmp` toolchain/build copy were lost in a container reset. Prior unit/integration receipts are retained, but no APK success is established. This workflow neither represents nor alters that pinned138artifact.

The shared-note handoff is now reconciled: Noble-main copied the C151 entry/proof hash into SESSION_HANDOFF, WORK_QUEUE, HONORABLE_CURRENT_STATUS and CREATIVE_500_WORKLOG. All four were independently checked after release. The earlier queued entry remains preserved in `.context/noble-fast-web/milestones/C151/PENDING_SHARED_HANDOFF.md` as historical evidence; no pending note write remains. Newer C142/source144 and recovered native-packaging entries are preserved.
