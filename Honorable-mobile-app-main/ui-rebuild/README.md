# Honorable interface rebuild

The memory is the interface: black canvas, warm neutral text, ordinary controls, photographs as the principal source of color. This change is limited to presentation and its navigation/interaction wiring. Catalog, model execution, search ranking, credits, billing, identity and completion verification are unchanged.

## Design source and active surfaces

- `product/design-tokens.json` owns semantic colors, spacing, radii, typography, motion and media-grid spacing. `scripts/generate-design.cjs` generates RN tokens, NativeWind colors, browser colors and the Compose palette.
- The React Native app uses platform sans-serif, Lucide and the shared tokens. The shared Android photo editor retains its existing pixel and export operations with a larger canvas and contextual controls.
- The working browser client loads `design-tokens.css`, `honorable.css`, `auth-ui.css` and `studio.css`. These have separate roles: tokens, shell/screens, authentication, and creative workspaces. No new override stylesheet was added. Superseded exact-selector declarations were removed and the files formatted for review.
- The separate Compose entry point remains buildable. Its bubble background, animated orb, floating capsule navigation, heavy result cards and obsolete private demo screens were replaced or removed. Its account controller and search view model calls remain intact. It now consumes the shared palette and generated Lucide vectors.
- Current visual previews come from the working browser client, not the retired static mockup renderer. They do not certify native rendering or device behavior.

## Removed or retired presentation systems

Deleted from the web shell and static route allowlist: `android-ui.css`, `honorable-parity.css`, `monochrome.css`, `phone.css`, `web-shell.css`. None were imported by the active index.

Removed obsolete phone/Android web renderers, duplicate account/pass renderers, Material icon paths, native blue/glass aliases, search shadows, glass Settings and Storage styling. `HonorableGlassCard` was replaced by `HonorableSection`, with its call sites updated.

The prior static preview renderer and its CSS are isolated in `ui-previews/legacy/`; they are retained as historical source, not imported, built or served by the working app. The old gallery entry links to the current captures. Old screenshot files remain historical evidence. The internal Developer Console retains its operational interface; this sprint rebuilds the customer app and creative workspaces.

## Screen decisions

Home starts with the memory prompt and adjacent model choice; credit balances stay quiet. Results have a large leading image and supporting media. Memories keeps scannable library geometry; the RN library retains actual month grouping. The viewer has hideable chrome and supported actions. No sample media is presented as the user's library in an empty state.

Studio starts from a photo and distinguishes recent work, photo creation and advanced workspaces. Available capabilities retain their entitlement and foundation labels. Pass shows all existing 20 default products, with two featured choices and compact access to the rest; Show All still exposes all 31. Usage uses balances, progress, model activity and readable history. Account uses simple rows. Sign-in stays connected to the existing identity flow; unavailable configuration is visible.

Photo editing uses a central canvas, contextual tool rail, a single selected adjustment, values and immediate comparison. Desktop allocates separate project, canvas and inspector regions. Video shows real source playback with a time ruler and existing project tracks; export remains unavailable. Nodes use their own graph area and remain inspection-only foundation. Code has files, line numbers, syntax display, file selection, output and preview; Python/Java execution stays disabled.

## Review and evidence

Open `previews/index.html` for all 27 requested views. Each image is marked DESIGN PREVIEW / FIXTURE MEDIA. Photos are existing repository fixtures. Search results and example graph/script content are explicitly preview data. No preview is evidence of search quality, a successful video search or production customers.

Screens were inspected side by side, with full-size inspection of the mobile photo/adjustment, Studio, video, node and desktop photo/code workspaces. The correction pass addressed image readiness, one-at-a-time adjustments, available sign-in presentation, Studio's source-photo emphasis, isolated node space, visible playhead time and video-fixture seeking. Capture waits for real decoded frames rather than painting fake video results.

## Validation

- TypeScript and static web build pass.
- Browser product smoke covers the five tabs, 20/31 Pass catalog, Usage, Home model/search controls, locked V3, result → viewer → editor, hideable chrome, adjustments, real changed pixels, undo/redo, Save As Copy download, Studio entitlement presentation, disabled script execution and desktop/mobile overflow checks.
- Auth browser regression passes, including account continuity, offline sign-out and secure session restoration.
- Beta browser regression also passes after the Settings and renderer consolidation: invited sign-in, consented attachments, feedback and dashboard operations remain connected.
- React Native behavior suite: 12 tests across six suites pass.
- No benchmarks, holdout work, large indexing runs, ranking changes or repeated unrelated server suites were run locally.
- [Android CI run 35291214790](https://github.com/honrablecom-source/Honorable-mobile-app/actions/runs/35291214790) passed the Compose Kotlin compilation, including the shared native photo editor. APK assembly was still running at handoff. Physical-device visual review has not been performed; browser captures do not certify native rendering. Machine-readable evidence is in `validation.json`.

## Run

From the repository root:

```sh
npm run beta:dev
# http://localhost:4174/admin for beta operations; use the existing invited test account flow.
node Honorable-mobile-app-main/scripts/capture-product-previews.cjs
```

Capture uses a temporary isolated account store and a localhost fixture server. It sends no invitations and changes no production accounts.
