# Noble mobile creative studio — November 4, 2026

Owner direction: a phone-first creative studio spanning photo editing, video editing, photo creation and video creation, inspired by Blender's breadth. Target approximately 500 useful capabilities over staged releases. Work solo. Billing implementation and marketing execution are deferred. Kickstarter is a later candidate; do not launch or promise delivery before the finished product has acceptance evidence.

## What the target means

500 is a planning budget, not an implemented count or a commitment to ship filler. Audit existing tools before assigning new feature IDs. A feature must deliver a distinct end-to-end user result with editable saved state, undo where relevant, matching preview/export, recovery, clear errors and source protection. Presets, buttons, parameters, tests and platform copies are not separate features. Combine or remove weak proposals. Each approved feature needs a source reference and validation record.

## Proposed allocation — all uncommitted scope

| Area | Capability families | Planning slots |
| --- | --- | ---: |
| Photo editing | Geometry and repair; color and finishing; masks and selections; layers and compositing; retouch and restoration | 5 × 25 = 125 |
| Video editing | Timeline editing; motion and keyframes; compositing and effects; sound editing; captions and delivery | 5 × 25 = 125 |
| Photo creation | Canvas and layout; drawing and brushes; typography and vector shapes; reusable design systems; procedural imagery | 5 × 25 = 125 |
| Video creation | Storyboards and scenes; still-to-motion; motion graphics; animation and reusable scenes; procedural video assembly | 5 × 25 = 125 |
| Total | Twenty families to decompose and assess | 500 |

These are target slots, not 500 specified or completed features. Full 3D modeling/rigging and generative inference need separate feasibility decisions. Do not pretend that exposing prompts implements generation.

## Build cadence

Owner instruction November 4: implement the planned feature work before building the next APK. Do not build an APK per feature. Continue focused tests, typechecking, lint and relevant compilation during implementation; defer full APK packaging until the planned feature work is complete or the owner changes this instruction.

## Delivery sequence

1. Baseline and usability: preserve dirty work, audit existing features, remove blocked entry flows, establish full test/build evidence and define reference phones. Begin with the existing canvas creation path.
2. Project foundation: bounded versioned document state for layers/scenes, undo transactions, crash recovery, asset provenance, offline persistence, render contracts and migration. Reuse existing photo/video state rather than replace it blindly. Prove one vertical slice before expanding schemas.
3. Photo creation and editing: editable text/shape layers, layer ordering and opacity, masks, transforms and reusable compositions. Design source-preserving preview/export before adding controls.
4. Video creation and editing: scene documents, still-image clips, text animation, parameter keyframes, soundtrack envelopes and reusable motion recipes. Every preview must correspond to export behavior.
5. Differentiating workflows: subject-following edits, editable storyboard-to-timeline, reusable cross-media looks, constraint-based multi-format composition and local procedural animation. These are proposed experiments; promote only after runtime, licensing, quality and device-performance evidence.
6. Completion gate: end-to-end real-device testing, lifecycle/media-provider checks, memory/thermal/export performance, accessibility, licensing, privacy review, stable artifact identity and private release signing. Android and iOS require separate acceptance; decide launch platform scope from evidence.
7. Marketing preparation after acceptance: evidence-led demos, positioning, example projects, a landing page and community feedback. Before any Kickstarter launch, research its current software/project eligibility and rules, build a costed delivery plan, reward feasibility, support commitments and an honest risk statement. No live campaign, outreach or publishing is authorized by this plan.

## First proposed implementation batch

| ID | User outcome | Status |
| --- | --- | --- |
| C001 | Search canvas formats by name or pixel dimensions, then open the existing editor | Implemented in source; checks recorded in CREATIVE_500_WORKLOG.md |
| C002 | Choose custom bounded canvas dimensions | Implemented in source; 64–4096 per edge, 4,194,304-pixel cap; native/device validation pending |
| C003 | Choose initial transparent or solid background | Implemented in source for custom canvases; PNG alpha integration tests added but not executed |
| C004 | Save and reopen named canvas setup presets | Implemented in source; 20 private local setups, replace/load/delete; native/device validation pending |
| C005 | Create a composition with editable shape objects | Native source implemented; immutable bounded model, shared raster path, API/JVM checks; app/device validation pending |
| C006 | Add multiple independently editable text objects | Native source implemented using shared StaticLayout render; API/JVM checks; pixel/device validation pending |
| C007 | Reorder, hide and lock composition objects | Proposed; depends on C005/C006 |
| C008 | Recover a layered composition after process restart | Native created-image project source path implemented; durable PNG and versioned editable state, guest shelf; app/process/device validation pending |

C001 extends an existing uncommitted twelve-format canvas implementation; those formats are not twelve new features. First batch does not imply a full app build or device acceptance. Do not upload personal media or queries. Preserve Seran ranking and all existing credit/ledger rules.

## November 4 continuation

Owner confirms the public product name is **Noble**. Historical repository paths, package IDs and classes keep their identifiers for compatibility. Continue solo and defer full APK packaging until the planned implementation pass is complete.

Audit: `creation-web/editor.jsx` and `scene.mjs` already provide editable shapes, multiple text objects and bounded draft recovery through the drawing workspace. Do not count these again as C005/C006/C008. The remaining native photo composition scope still needs a canonical editable layer model and preview/export integration; C007 object hide/lock/order needs source and runtime audit. None of these is newly complete in this continuation. The 500 target remains unfinished.

## Composition continuation implemented in source

| ID | Distinct result | Evidence |
| --- | --- | --- |
| C007 | Hide/show objects with saved visibility and existing object order/lock controls | Local editor/browser checked; native bundle/device pending |
| C009 | Save/open multiple named editable compositions | Same |
| C010 | Transfer an editable composition as bounded Noble JSON | Same |
| C011 | Rename a saved composition | Same |
| C012 | Make an independent editable copy of a saved composition | Same |
| C013 | Undo a saved-project operation without changing the current drawing | Same |
| C014 | Produce scalable vector SVG from visible composition objects | Same |
| C015 | Generate a grid of independently editable composition copies | Same |
| C016 | Generate a ring of independently editable composition copies | Same |
| C017 | Reuse selected composition parts across local drawings | Same |
| C018 | Replace one composition color while protecting locked/hidden objects | Same |

Original C007 native-composition dependency is only partially satisfied through the drawing workspace; it does not establish native raster layers. Existing library arrangement, lock/order, shapes/text and single-draft recovery are not new counts. Full implementation details and limitations are in CREATIVE_500_WORKLOG.md.

## Native refinement source additions

| ID | User result | Status |
| --- | --- | --- |
| C019 | Sort whole timeline clips by source time or playback duration | Source/JVM checked; native UI/device pending |
| C020 | Remove exact duplicate clips, retaining different edits | Same |
| C021 | Join compatible source-contiguous cuts | Same |
| C022 | Extend caption display holds safely into gaps | Same |
| C023 | Change caption case without changing timing | Same |
| C024 | Read a timeline duration/caption/audio-settings report | Same; no media analysis claim |
| C025 | Save and reopen local editable photo edits for the same existing original | Source/codec/access/API checked; native runtime pending |

C008 remains incomplete for private cache-generated canvases. See the work log for source access, ownership, storage, checksum and validation boundaries. All current additions remain separate from the deferred full-app build.

C008 continuation supersedes the earlier cache-source limitation: guest-created images now have a dedicated durable source/project store and browser in source. C025 remains the existing-original edit-save workflow. Both await full native app/runtime validation. CREATIVE_500_REGISTER.json tracks the current 25 source outcomes; it is not a completed 500-feature claim.

| ID | Typography result | Status |
| --- | --- | --- |
| C026 | Curve independent photo text along a circular path | Source, JVM and Android API checked; actual raster/device pending |
| C027 | Style independent photo text with fonts, tracking, alignment, outlines and shadows | Same; one appearance workflow, not one feature per parameter |

Next creation slice: local selected-photos-to-editable-video workflow using the existing Media3 engine. Keep the original images read-only, bound generated assets, and preserve existing video project/source validation rather than introducing a fake prompt generator.

C028–C032 now have source implementations: real local photo-to-video preparation, editable data charts, UTC-correct month calendars, storyboard planning sheets and graduated shape blends. See the register/work log for evidence and runtime limitations. Source total 32/500; full app/device acceptance and the remaining 468 target slots are unfinished.

C033 local photo-effect regions is source-implemented with session format 10 and shared raster/kernel paths; JVM/API and synthetic kernel pixel checks pass, native runtime pending. Expansion total 33/500. The new port 4177 mobile UI review target is development scaffolding and not an additional native feature.

C034 reusable named local region recipes is source-implemented; catalog/JVM/API checks pass, full native/device execution pending. Source expansion now 34/500. Owner requires ongoing preview UI parity; actual RN source is shared and Kotlin UI mirrors are source-derived, with Android pixel verification still pending.

C035 targeted hue-range color adjustment is source-implemented with session11/preset4 compatibility and shared native kernel. A checked browser mirror also previews/exports this effect; 12,288 Kotlin sample pixels match exactly, but Android bitmap/UI parity remains unverified. Expansion count 35/500; 465 target slots unfinished.
