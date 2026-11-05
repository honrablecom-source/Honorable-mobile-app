# Current drawing core browser acceptance — November 5

0 new features, 0 new fixes, 0 scaffolding. This validation milestone follows the
guarded drawing-reset fix; it does not count that fix again. The source register
remains 82 workflows, 44 beyond the batch baseline of 38.

Three existing browser fixtures passed against the current isolated editor bundle:

- Project files: real browser download and chooser upload; review before explicit
  Open/Insert; embedded photo preservation; Undo; malformed input preserving the
  drawing and previous text; phone widths and reopen. Simulated native callbacks
  cover stale/cancel/chunk read-release/save. Export also succeeds when local draft
  writes are rejected, preserving the ability to keep an external copy.
- Recovery: malformed JSON, invalid graph and future drafts route to recovery;
  actual downloaded archive keeps exact original text; no ordinary-import repair
  or draft writes; read, size, destination and chunk failures, cancellation and
  stale callbacks preserve the stored source; reopen and phone widths pass.
- Photo replacement: protected selection refusal, invalid input/quota preservation,
  fitting and stable object identity/order/transforms, other-photo preservation,
  one-step Undo/Redo with original bytes/crop, reload, portable JSON and SVG, and
  actual PNG green-pixel output. Native success/cancel are simulated.

All fixtures check page errors and external requests. Evidence paths were remapped
to .context/core-drawing-acceptance; assertions and application behavior were not
changed. Logs and verification.json record current source/test hashes. The source
manifest verifies all 266 generated files in the isolated /tmp output. Existing
167 Node and 17 native host results are documented separately in
[drawing reset checks](DRAWING_RESET_CHECKPOINT.md).

This does not establish Android WebView/Activity lifecycle, SAF provider behavior,
actual native dialogs, device media/rendering/export performance or store readiness.
No packaged Android assets were refreshed, no native/APK/full build was run, and no
personal media or queries were uploaded. At the planned batch build, synchronize
packaged assets and run device acceptance for the core flows above.
