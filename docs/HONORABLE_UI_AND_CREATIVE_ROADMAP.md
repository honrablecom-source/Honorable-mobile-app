# Latest visual direction — Review 04

The owner rejected earlier dark and PC-style versions. White-majority mobile UI, black outlines/typography/controls, neutral gray only; retain media colors. Combine conversational input, compact Adobe-style tools and Blender-inspired canvas/workspace organization. Earlier palette references below are historical and superseded. This design remains under owner review.

# Honorable UI and creative feature plan

Updated October 19, 2026. Owner priority: UI first, inexpensive foundations next, research-heavy capabilities later. This updates the implementation order in historical handoffs. Continue the existing app; preserve local Seran, originals and credit rules; follow the latest white-majority outline design. Android and iOS are product targets; neither platform is marked ready from shared UI alone.

## UI direction

ChatGPT inspiration: quiet typography, one clear input, minimal navigation and contextual actions. Blender inspiration: a large canvas, explicit workspace modes, compact tools and an inspector that follows selection. Do not copy desktop density onto a phone.

Use existing tokens: canvas #000000, surface #080808, raised #101010, selected #171717, border #242424, text #FFFFFF / #BDBAB4 / #94918C, occasional mint #A9D8C5. No decorative gradients, glow, sparkle icons, oversized feature tiles, fake sample memories or marketing dashboards. Reuse the established icon family. Prefer 10px corners, 16px mobile gutters, readable type and 44px minimum action targets. Preserve dynamic type, contrast, screen-reader labels, keyboard dismissal and safe areas.

## Screen structure

| Area | Primary content and action | Secondary content |
| --- | --- | --- |
| Home | Find a moment; natural-language Seran search; actual memory thumbnails | Compact model/cost selector, recent searches, small Photo/Video workspace actions; balances in Usage |
| Memories | Real media grid, filters and viewer | Permissions, empty states, metadata, edit as copy |
| Studio | Direct Photo/Video import; later recent projects backed by real persistence | Create/Animate modes appear as their engines become usable |
| Account | Identity, privacy, storage, usage and Memory Pass | Existing pricing and entitlement rules preserved |
| Photo editor | Canvas, compare, undo/redo, Save copy | Contextual Crop / Adjust / Mask sheets; advanced inspector on demand |
| Video editor | Preview, transport, real clip timeline and Export | Selected-clip tools; later tracks, audio and keyframes |
| Create | Output type, prompt, references, settings, explicit Generate | Cost/processing location before submit; jobs, cancellation, versions |
| Animate | Canvas with selected subject and motion controls | Rig handles, keyframes, paths and camera; each enabled only with working support |
| Advanced Studio | Canvas, Layers / Nodes / Code / 3D workspace switch | Project explorer and contextual inspector |

Mobile: one main work surface; tools in bottom sheets, one inspector at a time. Tablets/landscape: canvas with optional inspector and timeline. Larger screens: resizable explorer, canvas and inspector. Existing Pass/Usage routes remain functional during migration; consolidate into Account only after navigation regression checks. No empty new tabs now.

Search and the creative assistant are separate contexts: Seran retrieves memories; the future assistant proposes edits to a selected project. Never quietly substitute generated media for a retrieved memory.

## Interaction contracts

- Import cancellation leaves the current workspace unchanged. Empty galleries show a clear import/permission action.
- Processing exposes progress and cancellation where supported. Failure preserves the original and editable project.
- Preview and exported output must use the same operations. An attractive timeline or graph is not a renderer.
- Each AI result is an editable version, with compare, undo and explicit acceptance. Preserve references and original assets.
- Assistant instructions become bounded project operations with a preview/diff and confirmation for uploads or paid generation. No secret arbitrary code execution.
- Do not upload personal media or queries without explicit authorization. Generation integration must disclose local/remote processing and price before submission. Hosting/billing remain deferred by the owner.
- Do not claim cross-platform parity until each native flow is built and tested. Do not paywall baseline performance or silently change established credits.

## Complete capability map

Status vocabulary: implemented = code exists; tested = named checks passed; device accepted = real media/device flow passed; planned = engine not delivered. None of the planned rows is completed by adding controls.

| Workstream | Scope | Current evidence | Next deliverable |
| --- | --- | --- | --- |
| Seran memories | Description, semantic/object/scene/activity/color/OCR/metadata clues; video frames, moments and hybrid ranking | Native engine and RN flows exist; V2 reliability not accepted; V3 remains locked | Real development-set relevance/performance and permissions tests; protect holdout |
| Basic photo | Import, crop/rotate, exposure/brightness/contrast/saturation, monochrome, undo/redo/reset/compare, copy export | Existing implementation; person cutout and safer export built locally | Visual/device acceptance and edit-state recovery |
| Pro photo | Curves, filters, RAW, local adjustments, smart masks/layers, blending, nondestructive projects | Incomplete/planned | Canonical operations and project save/reopen matching preview/export |
| AI photo manipulation | Person/object selection, refine mask, background replacement, move/patch/remove, fill, expansion, relight, clothing/color, shadows/reflections, perspective insertion, restoration/upscale | Person cutout beta only; remainder planned | Mask refinement and selection acceptance before reconstruction engines |
| Photo generation | Prompt, image/reference, sketch and photo transformations; versions, seed/settings where supported | Planned | Provider-independent job contract and actual generation adapter after cost/privacy decisions |
| Video V1 | Playback/seek/scrub, trim/split/delete/reorder, speed/volume/mute, undo/redo/reset, drafts and export | Media3 code and builds; encrypted drafts; device proof pending | Selection-safe scene analysis and phone export acceptance |
| Pro video | Multiple sources, video/audio tracks, proxies, projects, background export, transitions, titles, audio mixing, color and effects | Planned beyond single-source V1 | Multi-source project schema then real track compositing/export |
| Video AI editing | Tracking, rotoscoping, tracked masks/effects, stabilization, camera tracking/reframe, remove/replace/inpaint, relight, scene recomposition/restyling/motion changes | Experimental scene suggestion only | One bounded tracking/mask operation with rendered-output tests |
| Video generation | Text/photo/video inputs, first/last frames, sketches/storyboards, character/scene references, shot sequencing, camera direction, extension/continuation | Planned | Job and storyboard model, then validated adapter; durations depend on actual engine |
| Puppet Studio / human rigs | Detect body, editable skeleton/joints, 2D deformation, pose controls, clothing/face preservation, multiple subjects, depth/3D estimation, hidden-area reconstruction | Planned; person cutout is a foundation, not rigging | Manual 2D mesh/rig proof with honest limitations before automatic realistic reconstruction |
| Object/animal rigs | Select and separate objects; movable parts; limbs/wings/mechanical parts; contact shadows, hidden surfaces | Planned | Reuse rig/selection model; evaluate each subject class separately |
| Animation | Person/face/eyes/hair/clothes/nature/text/object/3D movement; paths, keyframes, curves, camera, parallax, first/end poses, controllable regions | Planned | Deterministic transform keyframes and export before generative motion |
| Motion transfer | Reference video to character, expressions/hands, intensity, smoothing, foot contact/balance, proportions and retargeting | Planned/research | Consented references, pose sequence and retargeting evaluation |
| Audio AI | Captions, lip-sync, voice/narration, sound effects/music, timing and mix | Planned | Editable captions first; consent and actual audio/video export validation |
| Quality tools | Interpolation and photo/video upscaling/restoration | Planned | Measured output and time/memory/hardware limits |
| Advanced workspace | Layers/nodes, masks/compositing, keyframes/curves, geometry/transforms/booleans, tracked 3D, project filesystem/explorer | Web foundations; native engines unfinished | Versioned canonical project and renderer, then one real operation per workspace |
| Code | Structured project view, Python/Java editing and visual-data synchronization | Web foundations; execution unavailable | Validated project edits; sandbox required before script execution |
| Creative assistant | Make this photo cinematic; make a 30-second trailer from five clips; explain/preview/apply/undo project operations | Planned | Deterministic intent-to-operation preview first; generation and destructive/export actions explicit |
| Terms explanation | Good/watch-out/important/money/cancel/privacy/rights/surprising clauses with grounded citations | Planned separate capability | Placement and document-grounded output; distinguish facts and interpretation |
| Accounts/release | Identity, sessions, privacy, account credits, store verification and signing | Local browser Google auth verified; hosted/native/billing incomplete | Owner-deferred hosting; trustworthy native account flow and device/store acceptance |
| iOS | Shared RN presentation/project contracts; native media/search/edit/export/account parity | Not built or validated | Audit adapters and permissions; implement equivalents; macOS/Xcode and real iPhone checks |

## Delivery order: smallest useful work first

1. **UI pass A (this checkpoint):** simplify Home, compact workspace buttons, remove extra Studio selection step and promotional tiles, align navigation theme with existing tokens. Typecheck/lint/Jest; no new APK or device claim.
2. **UI pass B:** visually review real Home/Studio/native editors, unify contextual editor controls and empty/loading/error/accessibility states. Reuse native editors; do not replace engines with mockups.
3. **Small functional fixes:** finish cutout artifact verification and review; selected-clip scene suggestions with stale-result protection; persistent photo edit recovery. Rebuild only after a meaningful batch.
4. **Project foundation:** versioned portable project schema, imported-asset references, save/reopen, layers/transforms and deterministic keyframes with matching renders. Keep platform adapters separate from portable state.
5. **Editing expansion:** multi-source/video tracks, audio/text/transitions, manual masks and refinement. Introduce proxy/background jobs when measured workloads require them.
6. **Creation foundation:** prompt/reference/storyboard and job state/cancel/version contracts. Integrate real image generation before video generation once privacy, hosting and operating budget are approved; no pretend Generate action.
7. **Puppet proof:** 2D pose/mesh deformation, depth/parallax and keyframe export. Then reconstruction/inpainting, motion transfer and temporal consistency research. Preserve identity and consent for face/voice/reference work.
8. **Advanced:** video tracking/inpainting, character consistency, 3D and node compositing, safe script execution. Release capabilities individually when accepted.
9. **Platform delivery:** keep shared UI portable throughout; schedule native iOS adapters after project contracts stabilize, then require macOS/Xcode and physical iPhone testing. Android testing continues in parallel when hardware is available.

No promised completion date or API/model purchase is implied. Easy UI work is separate from research-heavy animation and generation.

## Acceptance checklist per milestone

A milestone note lists files/behavior changed, test/build evidence, current artifact and Git status, platform limits and the single next task. Check import, cancellation, original integrity, undo, save/reopen and preview/export parity for the affected capability. Generated content additionally needs consented references, observable cost/failure/cancel behavior and quality evaluation. Android and iOS each need their own permission, lifecycle, storage, performance and device evidence.

## Review 02 visual correction

The owner rejected the first minimal-list pass. Updated direction adds a composed search surface and a genuine Studio selection workspace rather than sparse rows. Four native top-level tabs are now implemented: Home, Memories, Studio, Account. Pass/Usage remain available in Account through existing stack routes. Studio uses selected real library media, separate Photo/Video modes, preview, assets and source details with editor handoff. The wider-screen prototype sidebar is a design reference; desktop application support is not implied. See October 19 progress note for source/check evidence and explicit prototype boundaries.

## Review 03 overrides — owner decision

Mobile comes first. UI palette is black, white and neutral grayscale; remove mint, warm gray and other UI accents. Media remains in its original colors. Shared authoritative palette and generated consumers are updated. Desktop sidebar/inspector examples are superseded as the primary direction. Studio uses a single canvas, touch-sized mode selector, swipeable memories, pinned edit action and bottom-sheet source details. Prototype at the existing review URL is phone-width on large displays. Android/iOS actual build and device evidence remain separate from the prototype.
