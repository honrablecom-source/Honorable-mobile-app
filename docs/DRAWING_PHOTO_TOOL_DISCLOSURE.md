# Drawing photo tools disclosure

The Objects panel now groups each photo's eight editing actions under a closed-by-default **Photo tools** disclosure. This keeps the object list shorter while leaving Find on canvas and object management visible. Opening the disclosure shows three groups:

- Source and framing: Replace photo, Suggest crop, Straighten photo.
- Cut out and trace: Outline cutout, Remove connected color, Trace connected outline.
- Create from this photo: Surface mockup, Photo mosaic.

The existing editor callbacks, source/lifetime checks, Undo behavior, storage paths and media operations remain in use. Locked or hidden photos still have disabled editing actions; opening their disclosure explains that they must be shown and unlocked. Photo numbers remain part of the accessible labels, so repeated controls identify their source.

This is **0 new user-facing features, 0 separate functional fixes, 1 usability improvement and 0 app scaffolding**. The source register stays at 101/500 and batch 63 beyond 38. Existing actions are not recounted as new features.

## Interaction and appearance

Native HTML details/summary supplies disclosure behavior. Summary and editing controls meet the 44px minimum tested target height. The page keeps the white/black/neutral design and original media colors. Group content has a 160ms entrance animation; reduced-motion preference removes it. A visible 2px focus outline supports keyboard use.

Actual browser testing confirms Enter expands the disclosure, Tab reaches the first photo action, Shift+Tab returns to the summary and Space collapses it. Chromium's actual accessibility tree identifies a named, focusable DisclosureTriangle and changes its expanded property from false to true. Playwright's simplified ARIA snapshot exposes only the summary text, so the test checks the underlying accessibility tree directly rather than adding redundant roles/state to native elements. Focus-visible is tested after keyboard navigation, not merely programmatic focus following a mouse click.

Touch emulation opens and closes the disclosure by tapping. Normal and reduced motion, phone widths 320/390 and landscape 844, 44px targets, all eight original routes, locked protection, original geometry/bytes and absence of page/CSP/external-request errors pass. This is browser evidence, not physical touch or TalkBack acceptance.

## Validation status

301 drawing tests and 75 required Node/asset tests pass. The final isolated asset manifest binds 83 source files and 271 generated asset files. The dedicated browser interaction suite passes. All nine retained photo browser suites pass through the actual disclosure: replacement, suggested framing, perspective straightening, connected-color removal, connected-region tracing, editable mosaics, manual outline cutout, surface mockup and owned photo import. Their original pixel/source/Undo/recovery/ownership assertions remain; no assertions were removed. Initial tracing/mosaic harness failures came from two secondary browser pages that still bypassed the disclosure; those paths now use the same real summary navigation.

The shared test helper opens the actual disclosure before an existing action. Test output can be redirected with NOBLE_DRAWING_TEST_OUTPUT so regression captures/service bundles do not overwrite another task's evidence. Original standalone service paths remain defaults when that variable is absent. The test harness supports running only named remaining suites after a failure; it does not infer success from a timeout or missing handle.

Evidence is under .context/photo-object-tools/, with larger synthetic captures and per-suite logs initially under /tmp/noble-photo-tools-checks. The synthetic evidence is retained in evidence.tar.xz with per-file SHA256 records and restore-evidence.py. Final per-suite logs establish nine passes across an initial batch and a targeted rerun of unfinished/failed suites; no pass is inferred from an earlier failure log. No new dependencies, mobile React Native typecheck/lint/Jest, native/WebView/Gradle/APK build, tracked-asset refresh, device accessibility/performance or store acceptance is claimed. The parent React Native TypeScript config remains unavailable. Preserve the owner's approximately 138-source-outcome broad build checkpoint. Search/ranking, holdout, credit/ledger, personal-media upload behavior and peer video/story/REA work are unchanged.
