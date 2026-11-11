# C175 — Photo to portable ASCII artwork

One new source workflow, registered at164/500: Photo tools → ASCII art creates exact selectable plain text and a reviewed editable monospace drawing group from a local photo. One separate SVG font-export fix; zero scaffolding counted. Controls, ramps, characters and platform copies are not additional features. Existing sprite animation preview was audited and not counted again.

## User result and sampling

Use a full embedded8–1024pixel source, before drawing crop/rotation/flip/opacity. Choose8–80columns,4–40rows, never larger than source, and2–16distinct printable ASCII characters in dark-to-light order. Leading/trailing spaces are literal entries. Optional reversal maps255minusmean into the ramp. This uses supplied character order; glyph ink coverage is not font-calibrated.

Integer rectangle-overlap weights cover exact fractional source-cell areas. Alpha composites over white before encoded luminance54/183/19 averaging, then cell means round0–255. Equal-width tone bins select ramp characters. Hidden transparent colors cannot create dark marks. The source is privately captured; async processing yields each output row and checks current ownership through the final yield. Decode/sampling has an overall30second deadline and cleans listeners/timers/handlers/canvas on completion/cancel/error.

The text field preserves every character, trailing space and newline: maximum80×40output is3239characters including39newlines. Select ASCII text selects the exact output for the device copy action, without automatic clipboard access. Square versus narrow character-cell geometry depends on font; default row count approximates the provided monospace font. Other fonts/apps may change spacing. A pure-white default result remains selectable all-space text, with invisible-art insertion disabled.

## Editable insertion and recovery

Drawing output is a title, white plate, one or two bounded monospace text blocks and note, all in one group beside the unchanged source. Maximum4–5objects. Existing per-object2000character text/originalText limits stay intact:80columnmax uses24+16rowblocks, each≤2000characters. Row-range/grid/ramp/reversal metadata survives recovery. Joining block texts with one newline reconstructs the entire plain text; no truncation or smaller maxgrid.

Rebuild-and-compare admission/current-source guards, sticky edit→Undo invalidation, existing draft/object/storage limits and atomic publication preserve artwork. One Undo removes the fullgroup. Settings, Stop, hidden-page, Escape and unmount discard/cancel review. Inserted bytes/text survive Undo/Redo/reload and JSON/SVG. Text edits or font substitutions can change appearance.

## Separate SVG fix

Excalidraw0.18.1's font subsetter attempts dynamicJavaScript while initializing WOFF2 processing. StrictCSP blocks it; existing code catches and embeds the complete originalfont. A build-time adapter, guarded against a missing/changed/duplicated pinned marker, selects that same fullfont encoder directly. No CSPweakening, dependency upgrade, vendor/node_modules mutation or changed scene textlimit. Fullfonts can be larger than subsets; this preserves the renderer's existing strict-CSP fallback behavior. ActualSVGcontains byteexactcompleteCascadiaWOFF2 and no CSPerrors.

## Evidence and limits

10focused tests pass:7ASCIItests plus3executablefont-path/driftguards. ASCIIproof covers9independentexpanded-raster fractionalarea grids/mixedRGBalpha;every256tone over3ramps/bothpolarities; spaces/newlines/blanktext; privateproof/input/progress/first-finalcancel; everymax1024source80×40character/rowblock; invalidinputs/forgedproof. All762drawing and70Node server/project/configuration tests pass. Isolatedbundle/manifest pass.

Actualbrowser independently checks every default/fractional11×7/reversed character andSVGrow, exact selection, sourcebytes/geometry, grouping/textmetadata, UndoRedo/reload/JSONSVG/quota/500capacity/staleedit→Undo, all-space review/insertion refusal, realasyncStopHiddenEscape, max3239characters/2blocks/originalTextlength/reconstructedtext/reload,320/390/844pixel44pixelactions, fulloriginalfontbytes and no page/CSP/externalerrors. Retainedstitch browser regression also passes against the final currentbundle; logs/receipt retained with C175 proof.

Two initialfailures are retained: maxsingle-text hit the existing2000character guard; after rowchunking passed, strictbrowser caught legacyfontsubsetterCSPerror. Finalproof is `.context/noble-fast-web/milestones/C175/`; sourceSHA256 `8a6ac48243b84b7b5d681f835f4930f3d617da2e5c46d84270cb3eb2b2b5dd45`.

All271frozen138drawingassets match bytes/SHA; manifestSHA256 `9a7dff9a2c8195a8c69ebd2231f968e22f5081d4b35da2cdedf15710c30c519a`. LaterASCII/fixsource excluded from thatnativebuild. No new fullnative/APKbuild or physicaldevice/WebView/provider/fontportability/accessibility/memory/performance/store acceptance. No uploads/search/ranking/benchmark/holdout/credits/catalog/ledger changes. Goal500remains active.
