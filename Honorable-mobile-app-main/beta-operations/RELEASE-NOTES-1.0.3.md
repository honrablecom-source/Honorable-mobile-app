# Noble 1.0.3 / build 4 — core AI internal candidate

Status: verified self-contained internal testers are available in executable/. Their exact receipts identify the successful full base-app build85a4850 and JNI-only16KB repair3c6727e. They are explicitly derived packages; a canonical whole-app rebuild after the repair is still running. Previous APKs are retired. Physical-device/model-quality/export/store acceptance remains unverified. Account configuration is incomplete and iOS native search/editing/AI is absent. [Current artifact identities and scope](../../executable/release.json).

## New creative capabilities

- **AI depth animation:** bundled Apache2 Depth Anything V2 Small estimates relative depth from the complete edited photo. Review its image/depth preview before opening a separate textured2.5Dscene in the MIT Three.js workspace. Adjust depth, materials and camera animation; export GLB. This is a surface inferred from one photo, with possible distorted edges, not complete geometry or generated unseen sides.
- **AI object eraser:** paint a mask or select a precise rectangle, run the official bundled MIT MI-GAN pipeline locally, review full before/after images and open a separate PNG copy. Only selected working-image pixels change; the current complete edit is fitted within1024pixels and every unpainted pixel stays exact. Generated textures can contain seams, repeats or wrong shapes; they do not recover hidden original content.
- **Mobile model workspace:** create bounded primitives, apply local image textures, adjust materials, orbit/zoom, animate a turntable, undo, save locally, add a PNG preview and export real GLB models. Ordinary modeling controls are distinct from neural depth inference.
- **Shareable GIF animation:** MIT gifenc encodes the current model/depth motion in a cancellable local worker, up to384pixels/10fps/1–6seconds/8MiB. GIF has no audio and can band photo colors. Review the result; original photos/current edits and the saved scene remain intact.

Account-owned photos require separate explicit consent before making shared guest-workspace copies, which remain accessible after sign-out/account switching. No personal media or query is uploaded by these integrations. Model weights, complete redistribution notices and generated workspace assets are bundled in source; the release build checks their exact packaged bytes.

## Core app and AI tools

The white/black phone Studio now exposes Photo AI and Video AI import flows directly. Existing Android editing includes crop/color/text/composition, undo/recovery and save-copy export, plus reviewed person/background, neural upscale, OCR text/layout/privacy and subject-asset tools. Existing video tools include timeline/trim/speed/music/titles, recovery/export, local speech captions, Spanish caption translation, visible-text recovery and sound-caption suggestions. Optional public-model downloads require consent; private media/transcripts/queries stay local. Recognition and generated suggestions can be wrong and require review.

Native on-device photo search/ranking and original-media protection remain unchanged. V3 stays locked/unchargeable; V1/V2 costs1/3,15free monthly credits, free-first deductions, no free rollover and nonexpiring purchases retain existing server-ledger authority. Failed/cancelled searches are not charged. Account sign-in depends on the configured HTTPS account service; artifact metadata reports configuration presence, not a successful phone login.

## Validation and distribution scope

React Native531cases/typecheck/lint0errors and creation831cases pass. Whole photo/drawing AndroidAPI37 source compiles; exact production neural models run on licensed public/generated host fixtures. Eraser/depth/native drawing/browser/GLB/GIF/Stop/ownership checks pass. Full AGP validation found desktop-codec, static JDK-class and default-application fixture problems; their focused repairs preserve all original behavior assertions. The final combined whole-app build, package/signature/alignment/ABI/model checks and physical-device acceptance remain pending.

Internal Android builds use the public debug signing certificate and are not store releases; developer debug APKs require Metro. Inspect each artifact's source hash, ABI, signing and bundled-code metadata. Never infer current APK contents from a source commit alone.

The published iOS simulator ZIP is a separate verified shell preview. Native iOS search/editing/AI modules are absent. Xcode produced an unsigned device archive, but archive metadata verification failed; a tested parser repair is rerunning on Mac CI. No verified device archive or signed IPA is available yet. Private Apple signing, phone tests and store readiness are separate work.

Required phone acceptance includes cold startup without Metro, authorized gallery/index/search/cancellation, account/session/credit behavior, original-preserving photo/video export and recovery, actual model quality/memory/thermal/cancellation behavior, touch/accessibility and3D/GIF sharing. No expensive search benchmark or Seran holdout was used for build validation.
