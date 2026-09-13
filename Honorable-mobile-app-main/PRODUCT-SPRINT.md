# Product architecture sprint

The product now uses Home, Memories, Studio, Pass and Usage. Account is reached from the Home profile button. The pass catalog is one-time purchases; Studio is a separate monthly entitlement with no production price or AI quota assigned.

## Source of truth and migration

- `product/catalog.json` owns all 31 pass IDs/reference USD prices, FAST/VIDEO/TURBO costs and `HONORABLE_STUDIO_MONTHLY`.
- Run `node scripts/generate-product.cjs` after catalog/project contract changes. It creates the TypeScript catalog, Android asset and browser project module. The server loads the source directly. Reference USD prices must be replaced with store-localized prices when billing is integrated.
- Existing ledger `balance` remains purchased, non-expiring credits. Historical amounts, identities, purchases and Ultra entries remain intact. Retired pass IDs are no longer sold; their existing receipts and credits are preserved. Migration is additive/lazy. Invalid JSON fails closed instead of silently resetting the ledger.
- `FREE_MONTHLY_MEMORY_CREDITS` is a server environment variable, default 15. UTC calendar-month cycles reset lazily without rollover. Clients read the total and reset date from the account API.
- A monthly installation pool is assigned to its first eligible account. A second account on that installation receives no second grant, even if the first has unspent credits. The first account can return to its remaining pool. Account-cycle grant records also prevent repeating grants across installations. Purchased balances never move between accounts.
- Android stores a separate random installation ID encrypted by the existing Keystore store, outside backups; sign-out only clears the account session. The browser ID in local storage is development-only, resettable and **not production-grade anti-abuse**. Stronger eligibility still needs Play Integrity/attestation. No invasive fingerprinting is used. Reinstalls and factory resets are not solved by this mechanism.

## Search accounting

`POST /v1/search/start` takes a model and unique request ID; authenticated ownership and cost are resolved by the server. `POST /v1/search/complete` closes that request with SUCCESS, FAILED or CANCELLED. Only SUCCESS spends credits, free first then purchased; terminal states and deductions are idempotent. Opening/reopening/editing a result never calls a debit endpoint. TURBO is unavailable.

Development mode allows completion claims for local integration testing. In production, SUCCESS requires an injected `searchCompletionVerifier`; without it the server responds `SEARCH_COMPLETION_VERIFICATION_REQUIRED`. The old direct debit route is development-only. This is a production launch blocker, intentionally not a pretend secure completion mechanism. A trusted native/local-search proof protocol must be designed without uploading media or trusting arbitrary client claims.

`POST /dev/studio {active: boolean}` grants/removes a **test entitlement only**. All `/dev/` routes are unavailable in production. Studio cancellation does not touch purchased credits. Studio AI allowance fields exist with null/unconfigured values, separate from search credits.

## Editors and project state

All 13 attached references were reviewed: six Photoshop examples establish canvas/tool/inspector/layer separation, four Blender examples establish scene/properties and node workspaces, and three Premiere/video examples establish preview above a multitrack timeline. The Honorable translation uses dark canvas, large touch controls, contextual workspace navigation, a phone bottom inspector and a wider-screen side inspector. No reference imagery, logos or proprietary icons are shipped.

Browser editing uses real Canvas pixels: center crop, rotation, brightness, contrast, saturation, exposure, monochrome, undo/redo/reset, before/after, image-layer visibility/opacity and PNG download as a new copy. Preview is limited to 1280 pixels; export rerenders from the loaded source. The original is never written. Project edits are saved to local browser project storage but automatic restoration/recovery is not implemented yet.

Android's shared `PhotoEditorActivity` is wired from both Compose and React Native result viewers. It uses local bitmap/color-matrix processing on a worker, reduced-resolution previews (smaller on low-RAM devices) and the system document picker for copy export. Native exports are capped at 8192 pixels; tiled/full-resolution export, EXIF/device orientation validation and physical-device memory testing remain open. Native video editing is not implemented; the browser has a timestamp-preserving preview and timeline foundation.

`product/studio/project.js` defines a versioned deterministic project with preserved source references, layers, edit stack, timeline tracks/clips/markers/keyframes/effect attachments, node connections and virtual project files. The browser exposes those structures. Timeline operations support data-level add, trim, split, move and markers; multi-track rendering/export is not implemented. Nodes have a data foundation but no execution engine.

The virtual explorer supports create script/folder, move/rename, duplicate, delete with confirmation, open and path search. It never exposes unrestricted host/Android paths. Python/Java editors save text, show syntax and line numbers, and expose safe API definitions. Neither language executes. Run reports EDITOR_ONLY. There is no eval, interpreter, compiler, shell, filesystem/network permission or account-token bridge. Entitlement checks remain outside script text; no cloud upload adapter exists. Visual changes and saved script files serialize in the same project representation, but scripts cannot yet mutate visual state by executing.

Pose rigging is PARTIAL: no person segmentation or meaningful mesh deformation backend exists. Generative Fill, expansion, advanced animation, relighting, tracking and 3D operations remain Coming Soon. Native adaptive layers/timeline/project/code workspaces need implementation; browser support must not be marketed as native parity.

## Validation and remaining launch work

Focused server and browser tests cover balances, grant limits, idempotency, costs, catalog expansion, test entitlement, true pixel changes, undo/redo, copy download and editor-only scripting. Browser search transport is a deterministic test fixture; that test is not evidence of actual Seran retrieval quality. No protected holdout, ranking retuning, TinyCLIP/VLM change or advertising work was performed.

Android SDK is absent in this Codespace. JDK 25 failed during Gradle startup; the existing JDK 21 reached the SDK check. Use the existing `.github/workflows/android-build.yml` to compile Compose and build the React Native APK. No SDK/toolchain was installed. Device validation and verified real-media demo runs remain required.
