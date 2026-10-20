# Honorable feature completion register

Updated October 20, 2026. Continue the existing application and design. No feature is considered fully ready until its real user flow and output pass acceptance. Compilation, JVM tests and attractive previews are separate evidence.

| Capability | Current implementation | Remaining acceptance/work |
| --- | --- | --- |
| App-wide mobile UI | Shared workspace chrome across routed RN screens and native photo/video/playback; white/light dialog theme and native insets; local checks and internal APK pass | Owner visual approval, native screenshots and device/touch/large-text validation; iOS build proof remains open |
| Home/library/search/results/viewer | Existing RN presentation and native Seran integration | Physical permissions, usability/accessibility, grounded retrieval and performance |
| Workspace selection | Typed local Photo/Video pickers and existing editors | Physical cancel/denial/provider behavior |
| Basic photo editing | Crop/rotate, brightness/contrast/saturation/exposure, monochrome, undo/redo/reset/compare | Phone pixels/export/orientation, lifecycle and memory checks |
| Photo lifecycle recovery | Bounded scalar edits, undo/redo, compare/tool and pending-picker settings; account/API/source isolation; masks recomputed locally | JVM coverage and combined internal APK pass; physical rotation/process/picker validation pending; not persistent photo projects |
| Photo text recognition | Bundled on-device OCR, review and explicit copy | Real recognition/error/offline acceptance |
| Scene-aware photo enhancement / looks | On-device labels plus sampled luminance, RGB curves, bounded exposure/color; Auto/Cinematic/Portrait/Vivid/Mono; stale results rejected | Physical model/quality/performance acceptance |
| Person cutout | Local person segmentation, source-aligned soft matte, transparent PNG path and undo/compare; combined ARM64 build verified; no device inference proof | Real person/no-person/edge quality, transforms and saved-alpha validation on a phone |
| Photo export safety | New URI/inode refusal before truncation, private PNG encoding and saved-copy hash/dimension checks | Real providers, aliases, cancellation/failure and original-byte integrity |
| Generic subject selection/masks | Not implemented beyond person cutout | Object-aware selection, refinement and reliable output |
| Generative fill / expansion | Planned | Actual generation engine, accepted hardware/privacy/cost model and output validation |
| Content-aware move/patch / relight | Planned | Real pixel-processing engines and controls |
| RGB curves / local exposure | Implemented graph/dialog, source-aligned radial exposure, history/lifecycle, common preview/export; JVM/build checks pass | Physical raster/alpha/placement/quality acceptance |
| RAW / advanced filter engine | Planned beyond the above curves and classifier-guided looks | Supported decoders, processing and render/export parity |
| Rig/pose manipulation | Incomplete foundation/aspiration | Genuine segmentation/keypoints/deformation; no fake skeleton-only claim |
| Layers / smart layers / compositing | Web project foundation; native advanced engine unfinished | Canonical persisted state, masks/blending and matching renders |
| Video Editor V1 | Multi-source trim/split/delete/reorder, speed/volume, titles/fade edges, composition preview and copy export | Actual phone rendering, order/speed/audio/orientation and original integrity |
| Video drafts | Encrypted account/API/root-source-scoped save/restore; v1/v2 readable and v3 persists titles/fades/soundtrack; restore undoable | Keystore/files/lifecycle/account switching on a phone; no old-session undo-history persistence |
| Video scene suggestion | Experimental sampled-frame local label analysis | Grounded quality, cancellation and preview/export acceptance |
| Multiple video sources | Implemented with durable document grants, per-clip routing and compatible drafts | Physical provider/codec/audio/aspect and lifecycle acceptance |
| Project soundtrack | One looping audio sequence, gain/removal, undo, drafts and original-file protection | Physical loop boundaries, mix/sync/output; richer audio timeline still incomplete |
| General multitrack editing | Incomplete beyond a video sequence plus one soundtrack | Track UI, arbitrary audio/video overlays, scheduling and compositing |
| Basic clip titles and visual fades | Implemented with local wrapped overlay and output-time fade-through-black, shared preview/export and history/drafts | Physical GPU/text/fade acceptance, richer title styling/timing |
| Overlapping crossfades/keyframes | Planned | Actual overlap scheduling/render engine and visual controls |
| AI-assisted video rough-cut creation | Existing-clip assembly using bounded sampled local labels, 15/30/60s targets, review/cancel/undo; source/speed/music retained | Physical inference quality, rendered output and cancellation/lifecycle acceptance; no speech/beat/generative model |
| AI captions / advanced AI video | Planned | Real inference and timing, editable results, output/performance/privacy validation |
| Tracking / stabilization / video 3D | Planned | Real tracking/render engines and supported hardware |
| Nodes / Code workspace | Web foundations; processing/script execution unavailable | Canonical project/render engine and robust sandbox before execution |
| Real Google identity | Local production-server browser sign-in/session/logout verified | Hosted account service and actual Android sign-in |
| Account/security/credits | Hardened verifier/sessions/parser, authoritative ledger and preserved prices | Trusted native completion authority, production operations and unresolved dependency findings |
| Store billing / distribution | Production integration unfinished; debug-key internal candidates only | Store setup, trusted purchases, private signing, policies and device acceptance |
| Search quality / private beta | Historical metrics are not current acceptance | Measured development-set quality/performance and consented pilot; protected holdout stays untouched |
| iOS | Roadmap | Native parity, macOS/Xcode build and device acceptance |

The owner has no Android phone and has deferred billing/hosting. Those external gates remain open. No personal media/queries are uploaded by this work. The existing V3 lock, prices, allowance and server ledger authority are preserved. See HONORABLE_CURRENT_STATUS.md for exact tests, source commits and artifact hashes; do not promote a roadmap row to working solely because its controls exist.

## UI-first checkpoint — October 19

Home hierarchy, compact workspace actions, direct Studio pickers and navigation token alignment implemented locally. TypeScript and 25 RN tests passed; lint has 0 errors / 29 warnings. No new UI APK, visual acceptance or iOS build. Full creative scope and order: HONORABLE_UI_AND_CREATIVE_ROADMAP.md. Completed/next notes: HONORABLE_PROGRESS_2026-10-19.md.

## Functional editing checkpoint

Selection-bounded/stale-safe scene suggestions, Apply trim, edited clip duplication, direct mute and actual clip-selection strip are implemented. 65 Compose + 16 RN native JVM tests and the combined full ARM64 build pass; APK independently checked. Person inference, media output and native visual quality still need device acceptance. See HONORABLE_EDITING_CHECKPOINT.md.

## Multi-source milestone

Multiple imported document videos now share a real timeline; source IDs survive clip operations, preview/export/scene inference, encrypted draft restore and bounded lifecycle recovery. Added 7 JVM regressions (23 RN native total); 67 Node checks and combined APK verification pass. URI ordering and secondary-original hard-link protection instrumentation compiles only. Mixed-media output and physical lifecycle/permission validation remain open. Titles/transitions, tracks, advanced generation/rigging and iOS remain incomplete.
