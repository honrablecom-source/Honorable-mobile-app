# C147 — Editable solvable maze worksheet

One distinct local creation outcome: Layouts → Maze worksheet generates a reproducible puzzle and an optional separate answer key. Registered at source141/500 after verification. **1 new source workflow;0 fixes;0 scaffolding.** This is excluded from the pinned138-workflow native build.

## User behavior

Enter a title, seed,2–16rows/columns and16–64pixel cell size. Preview a maze with one entrance at the top-left cell, one exit at the bottom-right cell and exactly one route between any two cells. The same trimmed seed and grid reproduce the same passages; title, cell size and answer-key visibility do not change them. The seed is for reproducible artwork, not cryptographic randomness.

Review before inserting. Walls, labels and optional dashed gray solution become one new group of editable vector paths/text beside existing artwork. The solution repeats the same maze below the blank puzzle. Ungrouping allows individual changes; editing walls can change solvability. Title/seed text is retained with wrapping rather than dropped. Original objects and embedded photo bytes remain unchanged. OneUndo removes the whole new group; Redo/reload and portableJSON/SVG retain it.

The complete worksheet must fit the500-object/draft budget together with existing work. Capacity or local-storage failure refuses publication. Source snapshots stick invalid after an edit even if Undo later restores the old scene. No image/model upload or network generation is used.

## Implementation and bounded work

`maze.mjs` uses a local seeded PRNG and iterative depth-first traversal to carve a spanning tree over at most256cells. Reciprocal wall masks define the graph; independent route validation requires every cell connected, exactly `cells-1` interior passages and only the two specified outside openings. BFS recovers the unique solution. Collinear wall segments are coalesced into editable lines; a separate dashed polyline passes through the solution cell centers and the entrance/exit boundaries.

`MazeTool.jsx` previews the actual plan as SVG, invalidates it on settings changes and checks captured drawing ownership on Apply. Editor integration captures a sticky source snapshot, recomputes/compares the approved plan, validates whole-draft capacity and uses the existing durable-before-render publication helper. Quota failure leaves the original scene untouched; successful publication is one history step. No asynchronous worker is needed for this bounded≤256cell graph traversal. No dependencies were added.

## Verified evidence

- 10 focused cases pass. All225rectangular grid sizes from2×2through16×16 are independently checked with union-find/BFS for connectivity, acyclicity, reciprocal boundaries, exactly two exterior openings and unique solution. Separate tests reconstruct every closed edge from vector runs, compare both answer/blank wall copies, check route centers, Unicode wrapping/maximum geometry/object bounds, seed independence/caller immutability and malformed graph/input refusal.
- All 612drawing cases and70Node product/tooling cases pass without skips. Full logs, current manifest and source/test/runner hashes are frozen under `.context/noble-fast-web/milestones/C147/`.
- Actual browser reconstructs the graph from rendered SVG wall polylines without importing generator/solver code, independently solves it, and checks the separate answer path/copy. The4×5preview has20cells/19passages/12wall runs and a12cell solution. The16×16preview has256cells/255passages/122wall runs and a77cell solution; full puzzle plus answer inserts255editable objects.
- Inserted wall/route coordinates and neutral stroke colors match the reviewed preview exactly. Seed reproducibility/change, answer-only toggle, invalid/empty settings, source object/photo bytes, one group, oneUndo/Redo/reload/portableJSON/SVG, actual edit→Undo stale refusal, storage quota and490existing-object capacity refusal pass.
- 320/390/844px layouts fit horizontally with44px action and answer-checkbox-label targets. No page/CSP/external-request errors. The retained production-schedule layout browser regression also passes against the current shared editor/layout navigation; its receipt/log/bundle proof is saved under `retained-schedule/`.

Production-input SHA256: `942f5acc8d59e9549dd3f50bdb256de44bb219052f8e82da5f9347bf511d5973`.

## Remaining verification

Browser/Node source evidence only. Actual Android WebView, font rendering/editing, accessibility/TalkBack, lifecycle/memory/performance, native image/PDF/export and printed-puzzle usability remain unverified. Later editing, scaling or printing can affect geometry/legibility. This is not a fresh native APK or device/store acceptance claim. Source implementation, full app builds, artifact identity and real-device behavior remain separate gates.

Pinned138drawing assets are not refreshed for this feature. Main continues its isolated138native integration and separately claimed next-batch work; this source outcome is excluded from those138artifacts. No search/ranking/holdout/benchmark, credits/catalog/server-ledger or personal-media upload changes. Goal500 remains active.

All271tracked138generated-file hashes and the pinned manifest `9a7dff9a2c8195a8c69ebd2231f968e22f5081d4b35da2cdedf15710c30c519a` were independently rechecked unchanged before registration.
