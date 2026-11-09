# C105 — Import timestamped private video notes

**1 newly added source workflow;0 separate fixes;0 scaffolding.** Shared source count103/500, batch65 beyond38, after C104's77fe472 publication. Implemented in native source and API-compiled with focused host evidence. Full APK/native integration and real-device acceptance remain unverified.

Open Video inspector → Import moment notes. Paste up to100 original-source timestamp/label rows:

```text
1.250 | Opening take
8.500 | Close-up
```

Times use source seconds with a decimal point and up to three decimals, before playback speed changes. They may be anywhere within the selected clip's original source video. Labels follow the existing80-character/240-byte Unicode rules. Blank rows/CRLF and trimmed surrounding whitespace are accepted; the first pipe separates the timestamp and label, so later pipes remain label text. The existing seconds parser bounds timestamps to2,147,483.647 seconds. No speech/media analysis, file parser, upload or external service is used.

Review lists every bookmark in source-time order and the current merged total. Back restores the pasted input. Invalid syntax, out-of-source times, duplicate timestamps within the list or existing catalog, invalid labels and the combined100-note limit refuse the proposal together. Existing notes are never silently overwritten. Normal corrections remain in the input form without writing. The editor and confirmation share exact visible/current-stage/full project/source/duration/range/private-session/generation ownership, with input cleanup on dismissal and one-use guarded Save.

The additive `VideoMomentNoteStore.addAll` reads the latest catalog, merges/validates and performs one existing guarded write under the same store lock. Unrelated additions remain; a concurrent duplicate or capacity change refuses before writing. Existing constructor and add/rename/remove/load APIs, storage key, codec and read/write implementation remain. Bookmarks are private source notes across edits/projects, not captions or rendered/exported media. Project Undo does not undo note imports. Originals, timeline/music and project history remain unchanged. A post-write verification error asks the user to reopen notes and check saved state rather than promising rollback.

Novelty audit compared C042's single-note creation/rename/delete/jump and C058's highlights from existing notes. Neither ingests a pasted timestamped production log into the private catalog. This counts as one workflow; row syntax, limits, review and Back do not add separate counts.

## Evidence and limits

589 focused host tests pass:16 new plus573 retained. New cases cover sorted exact millisecond parsing, whitespace/pipes/Unicode and invalid rows, duplicate/source/size bounds, immutable snapshots, existing-note preservation, capacity refusal, codec roundtrip, original/additive store APIs, actual production latest catalog read and refused guard before any write, constructor-free Activity approval/one-use/stale refusal, project/history preservation, Back-stage semantics and paused entry before storage/widgets.

Current Activity/store/parser/tests compiled against pinned API34 references and the hash-verified retained graph.27 source checks verify native entry reachability, capture/choice ordering, both tracked stages, input/Back cleanup, full guards around prevalidation, complete review, one-use guarded Save, locked latest-read merge, unchanged old store bytes except the additive method, existing codec/action lease/project/draft, no timeline mutation and truthful error/Undo guidance.

A shared disk-full condition left the original test stdout empty even though its compilation and test subprocess returned0. That result was not counted. The source/dependency hashes and produced class entries were verified unchanged, then the589-test suite reran with private-tmp output and passed. Source/store checks reran after space recovery. Evidence: `.context/noble-fast-moment-note-import/verification.json`, `compile.log`, `tests-after-enospc.log`, `wiring.json`, `exit.json`; the failed initial runner output is retained.

Host store tests seed a synthetic catalog and execute Android reference `AtomicFile` read/load plus merge and a throwing guard, proving refusal before opening output. They do not execute successful AtomicFile writes, crash durability, private-owner/provider IO or Android storage behavior. Activity fixtures allocate constructor-free metadata and substitute write callbacks. Actual Android input/review/Back/automatic dismissal/cleanup/accessibility, physical lifecycle, device behavior and configured SDK37/full module/Gradle/native/APK remain unexecuted. No production readiness claim. No dependencies, personal media uploads, benchmark, ranking or ledger changes; broad build stays around138 source workflows.
