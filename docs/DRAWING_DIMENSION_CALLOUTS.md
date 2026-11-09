# Editable dimension callouts — C104

Arrange → Dimension callouts captures selected artwork and adds reviewed width/height annotations around its axis-aligned drawing bounds. Read the selection, enter a known width and label unit, preview the actual artwork, then insert. Four extension lines, two double-ended arrows and two text labels form one new editable group. Original artwork and attached labels remain unchanged. One Undo removes the group; Redo, reload, portable projects, SVG and PNG retain it.

This is one new source outcome. Units, decimal precision and distance controls belong to that workflow. Existing canvas-size controls and selection handles do not create persistent exported dimension annotations.

## Calibration and lifetime

The owner supplies the known width. Height follows the captured bounding rectangle's width-to-height ratio. Changing a unit label does not convert numbers. This is not physical measurement inferred from a photo, and exported/printed physical scale is not guaranteed. Rotated artwork uses its axis-aligned bounds rather than its unrotated local box. The labels are static snapshots: moving/resizing artwork later does not update them. Ungroup to edit labels and arrows.

Capture includes attached text through the existing component-graph closure, with up to 50 visible objects outside frames. It keeps only referenced photo copies for the preview. Selection-only changes do not retarget an existing capture. Source edits invalidate its lifetime permanently, even if Undo restores identical geometry. A preserved unreadable draft cannot be replaced through this tool.

The pure plan bounds coordinates and dimensions, known width, calibrated height, supported units, precision, offset and object budget. Labels that round to zero are refused. Eight objects must fit the existing 500-object cap. The renderer measures actual text before centering labels, then renders those same elements with the captured artwork. Source buffers and geometry are not mutated for preview. Cancel, hidden page, unmount, settings changes or stale async PNG completion discard old proposals and revoke their preview URLs.

Insertion validates the complete merged draft and uses the existing durable scene-publication path, including storage quota checks before renderer mutation. A failure never inserts only part of the dimension group. Existing source-file handling, ranking, credits/ledger and upload behavior are unchanged. No new package was added.

## Verified November 9

- **314 drawing tests pass**, including 13 new pure cases for calibration/ratio, exact arrows/extensions, unit labels, rounding/no-zero labels, negative/fractional bounds, value and object budgets, coordinate limits, neutral editable text, source immutability and centering from measured text.
- **75 required Node/asset tests pass**: dev-server, studio projects, SDK configuration, release manifests and drawing assets. Total 389 focused tests in this slice.
- **Actual editor browser acceptance passes** with synthetic red artwork and an attached label. The preview preserves the actual colored artwork; calibrated labels read W 50 cm / H 25 cm. Inserted labels center using actual renderer dimensions, both arrows retain their heads, all eight objects share one independent group, and source geometry/style/bindings/text are preserved. Rotated artwork separately proves the axis-aligned ratio and placement. A cropped, flipped, rotated and translucent embedded-photo case also retains its exact source bytes and placement while displaying the actual photo in the preview.
- A held actual PNG encoder completes after Cancel without inserting anything. Hidden-page invalidation and source edit followed by Undo refuse old proposals. Quota failure supports retry. Object-count and near-1 MiB encoded-draft limits refuse the complete group atomically. One Undo/Redo, reload, portable JSON, SVG label text and PNG source colors/callout ink pass. Three phone/landscape sizes fit horizontally with 44px insertion controls. No external requests, CSP violations or page errors occur.
- Retained shape-combine and photo-tool disclosure browser suites pass on the final isolated bundle. Its exact asset manifest binds 86 source files and 271 generated asset files; existing package versions and lock are unchanged.

Evidence is in `.context/dimension-callouts/`: `core.log`, `all-drawing.log`, `node-assets.log`, `browser.log`, `photo-ui-regression.log`, `boolean-regression.log`, `verification.json` and retained synthetic outputs. The initial browser fixture used the wrong saved-draft schema version; the app preserved/refused it correctly. The corrected shape-only version 1 fixture passes. No product fix is counted for that test correction.

## Pending acceptance

No actual Android WebView, touch/keyboard/TalkBack, Activity lifecycle, native image receiver/provider, device performance, full React Native/native/Gradle/APK build, tracked-asset refresh, private signing or store acceptance was executed for C104. Browser PNG handoff is a fixture. Parent React Native TypeScript config is unavailable; the web build warning is not mobile typecheck/lint/Jest acceptance. Keep the approximately 138-source-outcome broad build checkpoint. Source 102/500 and batch 64 beyond 38 are accounting totals, not production-readiness claims.
