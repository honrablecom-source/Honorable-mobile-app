# Noble offline drawing

Excalidraw 0.18.1 (MIT) is embedded as a local touch drawing editor. Noble provides white/black chrome, bounded local draft save/restore, offline asset delivery, and PNG transfer into the existing Android photo editor. This is manual drawing, not AI image generation or a replacement for the native photo/video engines.

At the next broad native build checkpoint, regenerate with Node 22: `npm ci`, `npm test`, `npm run build` from this folder. This writes the Android drawing assets and a source/output fingerprint manifest. Android CI is configured to regenerate these assets before native compilation and verify their packaged bytes before uploads; that workflow change has not yet been executed. Local Gradle alone does not regenerate them. Package versions and integrity hashes are locked. Third-party code and font notices are retained in THIRD_PARTY and the generated bundle. Preserve the roughly100-new-feature cadence for full native/APK builds.

From mobile-react-native, verify generated assets with `node ../scripts/drawing-assets.cjs creation-web android/app/src/main/assets/drawing`. After the later APK build, run `python3 ../scripts/verify-drawing-apk.py --assets android/app/src/main/assets/drawing --scan android/app/build/outputs/apk`. These checks bind captured source inputs and asset bytes; they do not establish signing, Android runtime behavior or device acceptance. The current safeguard was validated using temporary browser assets and synthetic ZIP fixtures only; committed Android assets have not been refreshed in this milestone.

The Android activity is private and guest-only. Only bundled assets from its synthetic HTTPS origin are served; other requests and navigation are blocked. WebView file/content access, remote collaboration, external frames and outbound connections are disabled. An explicit local photo picker returns only a bounded in-memory copy through the narrow native bridge; source URIs are never exposed to the page. No account data or media is uploaded.

One editable drawing draft is stored in WebView local storage under the private app origin, without cloud backup. Tap Save draft before leaving. Drafts are bounded to 500 supported objects and 1 MiB; images are rasterized to at most 2048 pixels per edge. Native PNG validation applies independent bounds before opening the photo editor. PNG creation does not overwrite an imported original.

Device/WebView drawing, font, touch, lifecycle and PNG acceptance remain separate from compilation and unit tests. iOS integration is not implemented.

Local editor preview: `npm run preview` (http://127.0.0.1:4176), or run workspace task **Noble: live editor preview** and open that URL in VS Code Simple Browser. Source changes recompile into ignored `.context/noble-editor-preview/`, leaving Android's bundled assets and APK unchanged. `npm run test:preview` checks the running preview using isolated Chromium and synthetic scenes. Install the UI-preview Playwright browser/dependencies if unavailable. Preview screenshots and outputs are retained under `.context/noble-editor-review/`; this is not Android/WebView/device validation.

Composition tools now include object visibility, saved project and component shelves, project JSON transfer, SVG output, grid/ring repeats and global color replacement. Existing renderer alignment, distribution, text, shapes, locking, stacking and drawing Undo are reused. Bounded scenes keep hidden opacity/lock metadata, and exports omit invisible objects. Project/component shelves each hold up to 12 entries and 2 MiB; quota failures preserve existing saved state. Starting a new native drawing clears the current draft, while named shelves remain local.

The build applies a small original transform to the pinned MIT renderer's font fallback, replacing the upstream CDN fallback URL with the private local asset origin. Upstream notices and font licenses remain bundled. Existing unbundled upstream JS may still contain URL strings; browser acceptance checks exercise actual runtime requests.

## Local QR artwork and focused review

Layouts now offers one selected tool form at a time; switching tools retains current field values. QR artwork accepts a short link/message (up to96 UTF-8 bytes) and optional60-character caption. The pinned MIT `qrcode-generator`2.0.4 encodes it locally; no URL is opened and no payload is uploaded. A four-module white border and coalesced black rectangles form a grouped editable result. Existing originals,500-object validation, draft/project limits and one-step insertion Undo remain authoritative.

`jsqr`1.4.0 is a test-only Apache2 dependency, not imported by the drawing app. Tests rasterize generated rectangles and independently decode them; real-browser review also decodes the actual PNG and SVG exports. The encoder's MIT notice lives in THIRD_PARTY/QR-CODE-GENERATOR-LICENSE.txt and is copied by the existing generic notice bundling step.

Focused checks: `npm test`; with the private drawing preview running, `node test/qr-preview.cjs`, `node test/palette-preview.cjs`, `node test/dependencies-preview.cjs`, and `NOBLE_PREVIEW_URL=http://127.0.0.1:4177/drawing/ node test/layouts-preview.cjs`. Browser checks use isolated synthetic scenes. They do not establish Android WebView/device acceptance or produce an APK. Preserve the owner's roughly100-feature cadence for full native/APK builds.

## Embedded local photos (C044; Android integration pending)

Add photo places a local PNG/JPEG copy with text and shapes. Copies are re-encoded to strip source metadata, fitted within1024 pixels per edge and192KiB each, and identified by SHA-256 using pinned MIT @noble/hashes2.4.0. Keep up to8 distinct copies per session including Undo history; saving and reopening releases unused history. The existing500-object/1MiB draft and2MiB shelf budgets still apply, so some combinations fit fewer photos. Browser source input is capped at12MiB/16-megapixels. Android uses the existing bounded, orientation-aware decoder after explicit picker selection. Originals are never edited or persisted by this import path.

Vector-only drafts remain v1; embedded-photo scenes use v2. Named projects, reusable components and portable JSON include only referenced copies, without original names/URIs/account state. Portable project JSON therefore contains image content: share it only intentionally. Imports verify bytes, dimensions, digest and actual decoding before changing the scene. Quota failure preserves the prior draft. PNG and SVG export render embedded copies; SVG includes image data rather than links. One-step Undo, cross-project Undo and reload retain photos.

`node test/photo-preview.cjs` exercises real browser PNG/JPEG decoding, persistence, named/portable/component recovery, Undo, quota failure, native callback protocol simulation and actual exported pixels with synthetic fixtures. Android picker/decoder integration and its two authored instrumentation cases remain uncompiled/unexecuted until the broad native batch. Browser acceptance does not establish WebView/device behavior. No new bundled Android assets or APK have been generated for this change.

## Editable photo contact sheets (C048)

Arrange → Photo contact sheet fits2–8 existing visible unlocked photos into square cells while keeping displayed proportions, crops, flips and embedded bytes. Choose all eligible photos or explicitly selected photos; order follows the scene back-to-front, then left-to-right/down. Other artwork stays in place. Use1–4 columns,64–1024 cell size and0–256 gap. Grouped, rotated, bound or framed photos are refused before changes. Each photo stays independent; one Undo restores the layout. This arranges existing images rather than creating copies.

`node test/photo-sheet-preview.cjs` exercises the actual browser form, invalid-input preservation, exact geometry, unchanged asset bytes/hidden photo/other artwork, Undo/Redo, reload/portable recovery and real exported PNG colors at phone widths. Pure model cases are included in `npm test`. Android WebView/device acceptance remains pending.

Panel navigation: opening/switching focuses the heading and resets scroll. Tab reaches Close; Escape/Close returns to the matching toolbar control. Form values survive panel switches. A160ms entry fade/movement and120ms toolbar transition follow the system reduced-motion setting. `node test/panel-preview.cjs` checks actual keyboard focus, value retention, scroll reset, computed normal/reduced animation styles and phone widths. Android WebView/TalkBack acceptance remains pending.

## Replace a composed photo (C050)

Objects → Replace photo selects a new bounded local copy, fits its aspect inside the previous box and retains center/ID/order/rotation/flip/opacity. The old crop is reset. Show/unlock/ungroup and detach bindings first. Source originals stay untouched. One Undo restores the previous image/crop; old bytes remain in session history under the existing8-copy cap. Quota/invalid input is refused before renderer changes. The post-import view reserves room around controls.

`node test/photo-replace-preview.cjs` checks actual chooser, protected target, invalid/quota preservation, geometry/identity, old-image Undo, recovery/portable/SVG and PNG pixels using synthetic fixtures. Native picker callback checks are browser simulations; Android WebView/device acceptance remains pending.

Arrange now shows one chosen tool form: Align & space, Photo sheet, Shape blend, Grid repeat, Ring repeat or Recolor. Input values, checkbox state and the chosen tool survive switching/closing while this editor stays open; this is session UI state, not a saved project setting. Generic alignment is the default. `node test/arrange-picker-preview.cjs` checks actual form isolation, retained values and align/grid/ring/recolor with Undo. Existing photo-sheet/layouts/panel browser checks navigate through the picker. This is a usability refinement, not six new features.

Saved project/component replacement now asks for inline review when a matching name already exists. Keep saved version cancels; a different name keeps both. Confirmation refuses changed names/scenes/selections or externally changed shelves. Failed storage writes preserve the prior shelf and allow retry. Unchanged saves are no-ops; successful replacement retains existing session-only shelf Undo. `node test/save-review-preview.cjs` checks these actual browser flows. This is save-safety usability, not a new feature/schema.

## Editable tables (C052)

Layouts → Table accepts plain tabs or | separators with a header row,2–6 columns/2–10 rows,up to64 Unicode codepoints per cell and4096 input characters. Cells become ordinary rectangles with bound editable labels; first-row shading and wrapping reserve space. Empty cells are allowed,ragged/control-bearing rows are refused,and formulas/URLs stay literal text. One Undo removes insertion;draft/portable recovery and PNG/SVG reuse existing paths. Maximum planned table expands to121 objects under the existing500-object document guard.

`node test/table-preview.cjs` checks actual renderer bindings/wrapping/recovery/export with synthetic text. This Linux container originally lacked CJK/emoji fonts; optional preview-only fonts are extracted under `/tmp/noble-preview-fonts`. Use `FONTCONFIG_FILE=/tmp/noble-preview-fonts/fonts.conf node test/table-preview.cjs` when that verified local setup exists. Its hash/config record lives in ignored `.context/table-layout/preview-fonts.json`. These are not bundled app fonts;Android typography/device acceptance remains pending.

## Find artwork (C053)

Objects → Find artwork filters this drawing’s text/type with an optional All/Text/Photos/Shapes-lines-frames filter. Literal matching ignores case,collapses whitespace and normalizes Unicode;queries are limited to100 characters and stay transient. Original action numbers and front-to-back order are stable. Find on canvas changes the view without editing/unlocking artwork or adding an Undo step;hidden objects must be shown first. Locked objects can be located.

`node test/object-finder-preview.cjs` checks actual filters,unchanged artwork/Undo,hidden/locked handling,reload/query nonpersistence and screenshot pixels showing a distant object in view. Use the optional isolated FONTCONFIG_FILE described above for CJK/emoji preview coverage. Pure helper cases run in `npm test`. No native media-search/ranking/network/billing path is involved;Android WebView/device acceptance remains pending.

## Text-color review (C054)

Objects → Review text colors compares assigned visible-text colors/opacity against a chosen opaque hex background. It uses [W3C G18 sRGB luminance](https://www.w3.org/WAI/WCAG22/Techniques/general/G18) and an unrounded4.5:1 normal-text reference. It does not sample actual artwork backgrounds or certify accessibility;photos,shapes,patterns,fonts,output size and effects need manual review. Hidden text is excluded,locked text included,and Find on canvas leaves artwork/Undo unchanged. Filter/settings state is session-only.

`node test/text-contrast-preview.cjs` checks actual reports,independent canvas alpha compositing,keyboard help,invalid inputs,nonmutation,settings/reload behavior and phone widths. Numeric and preservation cases run with `npm test`. Android typography/WebView/device acceptance remains pending. No dependencies or network service are used by this review.

Prepared SVG is tied to the current document content. Artwork changes or failed preparation clear the copyable output;view-only navigation retains valid output. Asynchronous preparation rechecks its canonical source before publishing. `node test/svg-recovery-preview.cjs` reproduces the old stale-output failure and verifies corrected error/edit/Undo recovery with synthetic scenes. Source/media/history formats are unchanged;Android WebView acceptance remains pending.

`node test/live-object-list-preview.cjs` exercises actual double-click canvas text editing,live Objects labels,prepared-SVG invalidation/current edited SVG,Undo/Redo,finder and reload. It verified current behavior without application changes. The canvas editor is the built-in browser renderer;this is not Android-native/device execution.

## Insert portable artwork (C057)

Projects → Advanced transfer → Insert project artwork adds a portable JSON composition beside the visible drawing,with fresh element/group/binding/frame IDs and the same embedded photo copies. Movable objects are selected;hidden/locked states stay protected. Import project JSON remains the replace/open route. Both support drawing Undo. Missing/invalid graph references,frame cycles,budgets and quota failure refuse insertion before publication. Existing named shelves and originals remain untouched.

`node test/merge-project-preview.cjs` checks actual diagram/grouped-QR/photo insertion,repeats,original preservation,Undo/recovery/portable/SVG/PNG pixels and failure preservation. Pure merge/graph cases run in `npm test`. The current portable schema and media limits are reused;Android WebView/device acceptance remains pending.

Reusable components now support grouped/connected/frame selections. Bound labels travel with containers,selected frames include their descendants,and external connections/frame membership are detached in the copy. Copying a child without its frame does not retain the outside frame’s clipping context. Hidden/locked children and immutable photo hashes stay unchanged. Insertion remaps graph identities,selects movable copies and returns to the canvas with one-step Undo.

`node test/component-graph-preview.cjs` checks actual groupedQR/diagram capture and independent repeat/recovery/export;`test/component-graph.test.mjs` covers selected subgraphs,frame children,hidden images and invalid selection cases. Updated general/photo browser tests cover return-to-canvas navigation. Frame touch and group dragging on Android remain unverified. This improves existing components,not the feature count.

Successful automatic/lifecycle/preflight draft saves stay quiet so they do not erase tool errors or progress. Explicit Save draft confirms success;all persistence failures remain visible. Canvas controls track initialized API readiness independently of save-message updates. `node test/save-feedback-preview.cjs` checks real timer writes/latest text,lifecycle-hook return values,feedback retention,quota failure/recovery and initial controls. Android lifecycle execution remains unverified.

## Editable starter designs (C060; Android integration pending)

Layouts → Starter design offers a quote card, announcement or simple flyer as one local design workflow. Personalize the headline (1–60 Unicode codepoints),optional details (up to180 across3 lines) and optional sign-off (up to60). Inserts a grouped640×960 white card,black typography and neutral accents. Text stays bound to editable shapes;Ungroup permits individual arrangement. Existing drawing Undo,Projects saves,portable JSON,SVG and Use image reuse their current bounded validation. This is deterministic local layout,not AI generation;no content is uploaded. The beginner guide points here.

Focused validation:`npm test`;with the local preview running,`node test/starters-preview.cjs`. Browser checks cover all3 styles,invalid-input preservation,form retention,phone widths,group/binding identity,Undo/Redo,saved recovery,exports and decoded PNG pixels. Maximal wide-Unicode text fits its fixed blocks;this does not certify glyph coverage for every font/platform. Actual Android WebView/touch/text editing/group dragging/accessibility/native handoff remain untested. No full native/APK build or Android assets refresh occurred for this source feature. Regenerate drawing assets at the later broad build checkpoint.

## Use selected artwork as an image (C061; Android acceptance pending)

Select objects on the canvas,then Projects → Image copies → Use selected artwork. Only that selection opens through the existing photo-editor PNG route. White is the default;Use transparent image background preserves empty canvas areas. Attached labels and selected-frame descendants travel with the selection;hidden objects are excluded andconnections to unselected objects are detached in a render-only copy. The whole editable drawing anddraft remain. Header Use image still includes the whole visible drawing. Empty selection refuses export;it never silently falls back to the whole drawing. Existing2048-pixel edge/8MiB PNG/native validation andoriginal-media protections remain.

Focused checks:`npm test`;`node test/selection-image-preview.cjs` with the local preview running. Models cover labels,frames,external bindings,hidden descendants andsanitized referenced photo files. Browser pixels distinguish selected grouped card/label from an outside card,confirm whole export andexact draft/reload preservation. Actual Android WebView/touch/native PNG handoff/provenance remain untested;no full native/APK build or Android assets refresh occurred. Preserve the broad native build cadence.

## PNG canvas background

Projects → Image copies → Use transparent image background affects both Use image and Use selected artwork until the workspace is reopened. White remains the default. Transparency removes empty canvas areas;it does not remove backgrounds from photos or filled shapes. Choose PNG in the photo editor to retain transparency. Image controls are grouped under Image copies. SVG retains its existing white background,andeditable source/JSON is unchanged. This refines existing export options rather than adding a registered feature.

`node test/transparent-image-preview.cjs` checks actual decoded PNG alpha andunchanged opaque fill pixels,whole/selected exports,panel retention,reload default,exact draft preservation andphone widths. Native Android alpha/decode/photo-editor/export acceptance remains pending the later broad build checkpoint. No full native/APK build or Android drawing asset refresh occurred for this refinement.

## Editable project files (C062; Android device acceptance pending)

Projects → Project files → Save project file keeps an editable `.noble.json` document. Choose project file stages a valid file without changing the drawing;Open chosen project replaces it with existing Undo,whileInsert chosen artwork adds it beside existing work. Embedded photo copies travel with the file. Explicitly choose where to keep it. Manual JSON transfer remains available. File export validates the current drawing directly andcan provide a recovery copy even when local draft writes fail;it does not close the canvas.

Android uses ACTION_CREATE_DOCUMENT/ACTION_OPEN_DOCUMENT with OPENABLE andEXTRA_LOCAL_ONLY,temporary content-URI access andno new storage permission. Native transport is strict UTF-8,bounded to2MiB+4096 bytes,andcrosses the bridge in at most32768 UTF-16-character chunks without splitting surrogate pairs. JS performs existing full project/photo validation;Open/Insert retain actual bitmap verification before commit. Native callbacks carry request IDs andsmall metadata;chosen file text is read in chunks andexplicitly released. Cancellation/error/stale replies preserve current work. Failed destination writes may leave an incomplete file andreport that failure. Browser review uses real local upload/download instead of native providers. No app-owned network transfer is added.

102 JS model tests,4 pure JVM transport cases and5 actual drawing-source API checks pass. `node test/project-files-preview.cjs` verifies actual browser files,embedded-photo Open/Insert/Undo,invalid-file preservation,native-style chunk/cancel/stale protocol,quota recovery andphone widths. Evidence `.context/project-files/verification.json`. Android provider/URI grants/lifecycle/recreation/real write/read acceptance remain unexecuted. No full Gradle/app/APK build or Android asset refresh occurred;regenerate bundled drawing assets at the later broad checkpoint. API reference:[Android document files](https://developer.android.com/training/data-storage/shared/documents-files).

## Focused Projects views

Projects shows one task at a time. Saved designs contains named saves,Open/Manage/Delete andshelf Undo. Project files contains Save/Choose andstaged explicit Open/Insert;Project name for file edits the same current name as Saved designs. Image copies contains white/transparent PNG andselection export. Reusable parts contains component saves/insertion/Undo. Advanced transfer contains manual JSON andSVG. Fields retain their values across views andpanel close/reopen. Choosing a different view dismisses an unfinished replacement review without changing saved work. Chosen files route to Project files automatically.

The active task is marked;switching focuses its heading. Sections enter with a short fade/slide,disabled for reduced motion. `node test/project-views-preview.cjs` checks visibility,state retention,shared naming,review cancellation,focus,reduced motion,phone/landscape overflow andsource preservation.21 existing browser scripts use `test/project-navigation.cjs` toclick the actual switcher;456 original assertions remain unchanged.22 browser suites pass;after the final file-name field addition,the3 affected suites pass again. Android WebView/font-scale/touch/TalkBack acceptance remains pending;no native/APK build or Android asset refresh occurred.

## Editable selection files

Project files → Save selected artwork only creates an editable file from the current canvas selection. Whole-drawing save remains the default,andreopening the workspace resets it. Attached labels,selected-frame contents,hidden children andtheir photo copies are retained. Unselected artwork is omitted;the complete current drawing stays unchanged. Empty selection refuses save rather than falling back to the whole drawing. This refines C062's file options,not an additional registered feature.

`node test/selection-file-preview.cjs` verifies a real downloaded grouped card/label/photo file,staged reimport,independent Insert identities/bindings,Undo,source preservation,whole-mode restoration andreload defaults. Models cover selected-frame closure,hidden/locked photo state andsource-URI/name stripping. Existing file transport andgeneric editor suites pass. Android provider/bridge/selection/touch/group movement acceptance remains pending;no native/APK build or Android asset refresh occurred.

## Project graph checks before Open

Portable imports/file staging andsaved-project Open now run the existing object-reference/frame-cycle validator before renderer restoration. Missing object references,wrong frame/container types andframe cycles refuse opening whilekeeping the current drawing andstored shelf intact. Corrupt saved entries are not automatically deleted. Complete bound projects still Open withUndo;actual bound-label deletion/export/reimport is covered. `node test/open-graph-preview.cjs` reproduces the old unsafe replacement andchecks corrected paths.104 model tests andfile/photo/generic/selected-file browser regressions pass. This does not certify every graph property or cached-draft resumption;Android acceptance remains pending the broad build checkpoint.

## Unsafe cached draft preservation

Automatic draft resume now validates object references andframe cycles before passing data to the renderer. Unsafe graphs enter the existing preserved-draft state:the stored raw is not cleared or normalized by autosave/manual Save/lifecycle hook. Project-file save refuses the unresolved canvas rather than downloading a misleading blank file. Ordinary valid-canvas file export can still recover a copy when local draft writes fail. This is preservation,not a repair/recovery-export tool;starting a new drawing remains an explicit native confirmation that replaces the old draft.

`node test/cached-graph-preview.cjs` reproduces the old automatic rewrite andchecks missing/cyclic raw byte preservation,zero current-draft writes,JS save hook,reload andno blank file download. Valid project/photo/component andnormal bound-label deletion/reopen regressions pass. Android WebView storage,onPause,process death andnative confirmation remain untested;no native/APK build or Android assets refresh occurred.


## Preserved draft recovery copy (C067)

When startup cannot safely open a stored draft, Projects → Project files opens with **Keep recovery copy**. Choose where to keep the archive before starting fresh. It preserves the exact stored text, including any photo data/metadata, inside a `noble-drawing-recovery` v1 JSON envelope. The stored original stays unchanged. This archive is for later repair; it does not repair/reopen automatically and cannot be imported as an ordinary editable project. Keep it private as you would the original drawing. The escaped archive must fit2MiB+4096 bytes; refusal never truncates or clears the original.

107 model tests and `test/recovery-file-preview.cjs`, `test/cached-graph-preview.cjs`, `test/project-files-preview.cjs` pass. Actual browser download and simulated native save/cancel/error/chunk replies are covered. Android SAF/providers/lifecycle/destination bytes/device acceptance remain pending; native source unchanged, no full APK build or packaged Android asset refresh. Evidence `.context/recovery-file/verification.json`. This supersedes the preceding preservation-only milestone's absence of raw export.


## First drawing guidance

An empty canvas offers **Start a design**, **Choose a photo** and **Explore canvas**. Design opens the existing editable starter form. Choose a photo invokes the local chooser. Explore dismisses tips for this session and focuses the canvas. Artwork,open panels and unreadable preserved drafts hide the prompt;recovery is still available through Project files. No preference is persisted. The prompt uses44px actions andhonors reduced motion. `test/first-design-preview.cjs` checks phone/landscape,focus/dismissal,chooser,personalized starter/save/actual PNG/reload andpreserved-draft separation. Starter/recovery/general editor browser regressions pass. This is a usability refinement,not an additional registered feature. Android WebView/touch/TalkBack/device testing remains pending;no full APK build orpackaged asset refresh. Evidence `.context/first-design/verification.json`.


## Starter design preview

The starter form now shows a read-only image of the current design before insertion. It uses the same local `starterPlan`, renderer conversion and SVG export as the actual artwork. After a short typing pause it updates for style/headline/details/sign-off; invalid input shows the existing validation message and removes the old preview. Editing or closing releases preview image URLs and cancels pending preview publication. Nothing is inserted until **Insert editable starter design**. The preview does not read or change the current drawing, draft or projects.

`test/starter-preview-review.cjs` checks all3 rendered styles,loaded images,rapid latest-input updates,invalid fields,literal script-looking text,unchanged source,blob cleanup,phone bounds and actual insertion/save/PNG/reload. Existing starter andfirst-design browser suites also pass. No new feature count;1 usability refinement. Native/WebView/device acceptance remains pending;no full APK build orpackaged Android asset refresh. Evidence `.context/starter-preview-review/verification.json`.

### Editable barcode labels (C069)

Layouts → Barcode label converts up to 80 printable ASCII characters into a grouped Code 128 label with a white quiet border and optional caption. Bars remain editable rectangles, with one-step Undo, local project recovery and PNG/SVG export. This is one workflow, not one feature per parameter. It does not create EAN/UPC product codes. Keep bar/border proportions and confirm a scan before printing. No identifier is uploaded.

Uses pinned [JsBarcode 3.12.3](https://github.com/lindell/JsBarcode), with its MIT notice in `THIRD_PARTY/jsbarcode-MIT-LICENSE.txt` and offline bundles. Test-only ZXing independently decodes the generated rectangles and actual PNG/SVG exports. Run `node --test test/barcode.test.mjs` and `node test/barcode-preview.cjs` against an isolated preview. All 113 model tests and the barcode and existing QR browser suites pass. See [C069 evidence](../../../docs/FEATURE_C069_BARCODE.md). Android WebView/SAF/device/printing acceptance remains pending; no new native/APK build.

### Suggested photo framing (C070)

Objects → Suggest crop analyzes a full embedded photo copy locally using pinned MIT Smartcrop 2.0.5. Choose a format and review the proposed photo-only crop before Apply; Keep current framing leaves the drawing unchanged. Applying preserves source bytes, center, rotation, flip and opacity, fits within the current photo box, and uses the existing Undo/recovery/export pipeline. It is a heuristic suggestion rather than a face-recognition or crop-quality promise.

The source copy must be at least 32 px on each side and satisfy the existing embedded-photo limits. Show, unlock and ungroup it first. Projects and SVG retain the full embedded photo; PNG is flattened visible artwork. The runtime MIT notice is bundled in `THIRD_PARTY/SMARTCROP-LICENSE.txt`.

All 118 model families (5new), `test/photo-framing-preview.cjs` and the existing photo-replacement browser regression pass. See [C070 evidence](../../../docs/FEATURE_C070_PHOTO_FRAMING.md). No Android asset refresh, full native/APK build or device acceptance.

### Four-corner photo straightening (C072)

Objects → Straighten photo marks a flat document's corners and previews a locally corrected PNG before inserting a separate copy. Tap corners or enter percentages, then choose an aspect ratio when physical proportions matter. This uses the full source before existing crop/rotation/flip/opacity; the original remains. Keep original only cancels. The copy is flattened, bounded to 1024 px and subject to existing project storage limits. Undo, recovery and exports use the photo pipeline.

Pinned MIT perspective-transform 1.1.3 is adapted to Noble's white/black review panel, with both upstream and bundled Numeric Javascript notices retained. No media is uploaded. See [C072 evidence](../../../docs/FEATURE_C072_PHOTO_RECTIFY.md). Source and isolated browser checks only; no Android asset refresh, full app/APK build or device acceptance.

### Editable weighted word cloud (C074)

Layouts → Word cloud accepts 2–24 unique `word or phrase | weight` rows, with whole-number weights 1–100. Preview locally arranges black text with larger weights at larger sizes. Every word must fit before Insert is allowed. Insert creates grouped ordinary editable text; Undo removes it and Close panel cancels. Ungroup to move or style individual words; later text edits can change spacing. Existing artwork, saved recovery and JSON/SVG/PNG exports are retained. Nothing is uploaded.

Pinned BSD d3-cloud1.2.9 uses Noble's rectangle-mask adapter to reserve editable-text spacing; its notice and the d3-dispatch ISC notice are bundled. See [C074 verification and limits](../../../docs/FEATURE_C074_WORD_CLOUD.md). Source and browser checks only; no Android asset refresh/full app/APK/device acceptance.

### Connected-color transparency (C075)

Objects → Remove connected color uses a tapped source pixel and RGB tolerance to remove one touching background area into a separate flattened PNG copy. Disconnected matching islands remain. Review the checkerboard preview before Insert, or Keep original only to cancel. Original artwork stays unchanged; Undo, recovery and exports use the existing photo pipeline. The full source is used before object crop/flip/rotation/opacity. Hard edges may retain halos. This is manual color selection, with no upload or person recognition.

For a transparent export of the new selected copy: Projects → Image copies → Use transparent image background → Use selected artwork; keep PNG in the photo editor. Pinned MIT magic-wand-tool1.1.7 and its notice are bundled. See [C075 evidence and limits](../../../docs/FEATURE_C075_CONNECTED_COLOR.md). No Android asset refresh/full app/APK/device acceptance.

### Editable connected-color outlines (C076)

Objects → Trace connected outline traces a tapped source-color region into grouped, closed, unfilled black line paths, including inner contours. Preview before Insert, or Keep original only to cancel. The full embedded source is used before crop/flip/rotation/opacity; originals stay unchanged. Paths approximate pixel centers and simplification can change details. Ungroup to edit each path. Undo, recovery, editable JSON, vector SVG and PNG use existing workspace tools.

Reuses the pinned MIT magic-wand-tool1.1.7 and shared C075 selector/review panel; no new dependency. Complex, tiny or thin traces are refused as a whole. See [C076 verification and limits](../../../docs/FEATURE_C076_PHOTO_TRACE.md). Local processing only; no Android asset refresh/full app/APK/device acceptance.

The drawing workspace has its own `tsconfig.json` so restored native dependencies cannot change browser compilation. Native strict-mode emission breaks the pinned legacy Numeric shim used by perspective-transform; the scoped browser configuration preserves its working runtime semantics. A regression reproduces the native-settings failure and checks a real transform with the browser settings. Native TypeScript configuration is unchanged.
