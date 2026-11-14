# Full image and video editor integration

The owner clarified November14: integrate the **image editor and video editor**, meaning PhotoCraft and FilmCraft, adapted for mobile. Do not expand into the rest of the ArtCraft suite. This supersedes treating the small raster-processing subset as the requested full editor. The separate50-new-AI request remains unfinished at2 verified experimental source/browser workflows; manual editor controls, command counts and platform ports do not increase that AI count.

## Scope and verified baseline

| Component | Pinned source | Actual evidence | Still required |
|---|---|---|---|
| Existing Noble raster lab | PhotoCraft five-library subset, commit9f76056 | Published; real bundled WASM/layers/effects/neural/Undo/browser checks | Full native package/device acceptance |
| Full PhotoCraft image engine | storytold/photocraft `f143ce58498f201a8dfc9c07f5e24147c6a22bee` | Full engine checks successfully for wasm32 using its committed lockfile and Rust1.95; upstream dead-code warnings retained | Full editor WebAssembly binary, touch UI, image/project import, real edited image/project export, app integration |
| Full FilmCraft video editor | storytold/filmcraft `d1e64ecfcc5e6f21e30f571e6fe3edbe86fdcafa` | Official source/license/web architecture inspected; full web-crate check passed after replacing restricted branding | Actual decode/timeline/playback/encode proof, phone controls/file/export integration |

Both upstream projects license their code MIT OR Apache-2.0. Preserve their complete notices and separate font/asset licenses. Modified Noble builds must exclude restricted ArtCraft branding. Reuse the real Rust engines and platform-appropriate functionality; do not copy proprietary Adobe/Photopea source or mistake an upstream menu entry for a working feature.

## Implementation path

1. Build and exercise the real full image and video engines before exposing app entry points. Test with generated/public fixtures only; retain logs and compare actual rendered/exported results.
2. Adapt the mobile workspace: white/black chrome, usable touch targets, canvas/timeline navigation and compact panels rather than desktop sidebars consuming the phone. Retain access to upstream editing tools through clear task menus.
3. Add bounded, explicitly user-selected local file access and genuine save/share paths. Keep original files and existing Noble projects unchanged. Preserve editable project formats where supported; make unsupported operations explicit.
4. Keep all media local. Bundled assets may load only from the private local app origin. No hosted editor, private-media upload, query upload, telemetry endpoint or automatic public sharing is part of this request.
5. Connect Noble's actual local AI to useful editing outcomes with source ownership, cancellation, review, undo and editable/exportable results. Do not count duplicated AI entry points or manual filters as new neural capabilities.
6. Integrate both editors alongside existing native photo search and current editors, preserving search ranking, permissions and the server credit ledger. Cross-platform code compilation does not establish iOS app availability.
7. Produce fresh combined Android and iOS build formats, verify real packaged code/assets/signatures/source identities, and publish them with honest device/signing/platform limitations. Existing artifacts do not contain later source.

## Known upstream platform limits

FilmCraft's official web architecture runs its engines cooperatively on the UI thread. Desktop operations that require threads (including certain previews, proxies and mask tracking) currently report unsupported on the web. Software render/export, WebCodecs acceleration and codec support must be verified in the actual mobile runtime. OPFS recovery and user-selected media retention need app lifecycle/privacy review; a browser capability does not prove a phone capability.

The full editors remain unfinished integrations. No full Photoshop/Premiere parity, zero-bug, physical-device, production-account or store-ready claim is justified. Account-backed search still needs the approved production account deployment identified by the owner; do not guess or reconfigure its endpoint. Private store signing is a separate external requirement.

Read-only prototype evidence lives in ignored `.context/noble-fast-full-editor/`. Product source scopes will be claimed before edits. The separate Noble Chat keeps its existing source and release claims; do not stage its dirty handoff notes.

## November14 mobile host checkpoint (unfinished integration)

Android private local-origin host and independent `NobleCreative` capability module are implemented in source. Only hash-verified packaged editor assets can enable an entry; no full bundles are packaged yet, so these entries remain hidden. User-selected imports are bounded copies; exports stream to private cache and request a share sheet. A share handoff does not confirm saving. Native file access, remote browsing and unrequested media uploads are excluded. Microphone/camera requests currently remain unsupported.

Verified: five native host classes compile against actual AndroidAPI37 and React Native APIs; four RobolectricSDK34 policy/export tests pass; five React entry/lifecycle tests pass; five JavaScript export/cancellation tests pass; mobile TypeScript check passes. These are component/source checks, not a full Android/iOS app build or physical-device proof. New user-facing features at this checkpoint: **0**; two full editor integrations remain unfinished, and the separate AI expansion remains **2/50**.

The full PhotoCraft release compilation received SIGTERM while compiling large engine/UI crates. This is a failed build, not evidence of broken Rust typing or a successful editor binary. A retry lowers only those crates' compiler optimization/codegen settings; logs and actual exit receipts remain in the ignored evidence directory. FilmCraft's initial full release compilation likewise received SIGTERM in its large UI crate. Both full editors now retry with UI-only opt-level0 and256codegen units, retaining engine optimization; phone panel patches compile through their UI crates, while final web binary linking/runtime acceptance remain pending. The pinned recipe `mobile-react-native/full-editors/build.mjs` records source revisions, lockfile builds, brand/theme patches and exact bindgen versions. It builds engines only; phone layout, functional imports and real edited exports must pass before packaging enables either entry.
