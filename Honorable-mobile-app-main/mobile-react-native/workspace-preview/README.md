# Noble interactive mobile workspace preview

This is a development review target, separate from the Android app and release assets. The launch flow imports the real `src/auth/AccountGate.tsx`, `src/screens/GuestEditingScreen.tsx`, design tokens and `useOpenWorkspace` hook. AccountGate uses an explicit guest/welcome-only browser boundary; there is no simulated successful sign-in. The Android platform branch is selected for these source components, while React Native Web internals remain on web. The launch screen is retained while workspaces open, matching native Activity return behavior. The bundler maps React Native primitives to React Native for Web and substitutes a browser-only media boundary. Native app code and native bridge behavior remain intact.

Run with Node 22.11+:

```
cd Honorable-mobile-app-main/mobile-react-native/creation-web
npm ci
cd ../workspace-preview
npm ci
npm start
```

Open http://localhost:4177, or forward **4177** privately in the workspace **Ports** panel. Workspace task: **Noble: mobile app preview**. Source changes compile into ignored `.context/noble-mobile-preview`; refresh the page to load updates. This does not build an APK. Devcontainer/VS Code port labels are configured for future automatic forwarding; current workspace forwarding depends on its Ports panel.

Drawing uses the actual bundled creation-web editor, served under `/drawing/` on the same port. The preview server compiles those assets into ignored `.context/noble-editor-preview` on startup. Its assets are isolated from the Android drawing asset directory. For live drawing-source updates, also run the existing `Noble: live editor preview` task on 4176; both previews read that same isolated drawing output. Optional native model evidence links appear only when their separately generated review pages exist.

Working browser paths:

- Current guest studio, searchable canvas formats, bounded custom canvases, named browser canvas setups.
- Actual drawing editor: editable layouts/charts/calendars/storyboards/blends, objects, Undo, named compositions, JSON/SVG, components/repeats/recoloring; Use image opens the browser photo adapter.
- Local photo selection, browser canvas crop/rotate/mirror/color/text, Undo/Redo, original comparison and PNG download. These browser render operations are separate from Android's raster engine. Maximum 4,194,304 output pixels / 4096px per side.
- Local browser video playback, range playback and preview caption overlay; these do not write an edited MP4.
- Photo sequence selection and slideshow preview; actual H264 preparation and native timeline export require Android.

**What works here** explains these boundaries inside the preview. Native saved image/video projects, on-device AI/search, account services, Media3 encoding and Android lifecycle/provider behavior are unavailable in the browser. Those routes show clear messages. Model review pages are explicitly labeled source/model evidence.

Media is read with browser File/Object URLs and never uploaded. The read-only HTTP server accepts GET/HEAD only. Only canvas setup metadata and actual drawing projects use browser localStorage; imported photo/video bytes are not persisted there. This preview has its own browser setup namespace, separate from native app data. Returning to Studio revokes browser media URLs. Test fixtures are synthetic.

`npm test` runs the isolated Playwright scenario using the repository's existing ui-previews tooling. Logs/screenshots belong under ignored `.context/`. This test is not Android/device acceptance. SafeAreaView has a browser-only View wrapper because the browser frame has no Android system bars. The adapter for Alert reports browser errors visibly. React Native Web aliasing follows its [official setup](https://necolas.github.io/react-native-web/docs/setup/).

## Source UI parity work

Owner requests a one-to-one Noble UI review. The React Native welcome/guest screens and drawing editor are shared source; Kotlin photo/video/creator views cannot execute in a browser. Their browser layouts now follow the actual native header, transport, tray, tool rails and creator fields. Photo toolbar and inspector labels, video toolbar/groups and creator title/help/buttons/formats are extracted directly from Kotlin during preview compilation. Contract checks fail when the expected source structure changes. This is a source-derived native layout mirror, **not proof of pixel-perfect native parity**. Browser buttons/fonts/viewport, media decoding, native dialogs, Android system bars, animation, frame thumbnails and rendering still need comparison against Android. Do not describe this preview as a native emulator or claim the whole app is pixel-identical.

Photo/video controls that require native engines show explicit native-required dialogs. Browser photo light/color controls now use bounded native-style gain/offset/saturation formulas; text/color-management/decoding remain browser implementations. The creator preserves native name/format/hold fields and exposes the browser-only slideshow through an explicit review dialog. Native saved projects, account services, AI/search and edited Media3 output remain unavailable. The video timeline is a single-clip schematic, not the native decoded filmstrip.

Bundled licensed Roboto weights are used to reduce font drift; the license is served under /fonts/. UI review notices sit outside the phone content. Source watchers include React Native source and both shared/native Kotlin editor roots. A build version poll offers **Refresh updated UI** only after a successful changed build; it never reloads selected media or current forms automatically. Refresh remains explicit. After edits to server.mjs, restart the preview server to adopt watcher changes. No APK build is involved.

C034 reusable local region recipes were added to the native photo inspector during this continuation; their labels appear automatically under Tools. They are native-only in the browser preview. UI parity changes are development tooling/fixes, not additional app feature counts.

## C035 selected-color review

Photo editor → Tools → Selective color range now has the native description, checkbox, target-hue swatch and six source-derived slider labels. Apply is one browser Undo transaction; Cancel leaves the photo unchanged. A float32 browser mirror of the production Kotlin kernel supports local preview and PNG output. All 12,288 generated Kotlin sample pixels match exactly, including alpha; canonical neutral/outside/transparent inputs and invalid settings are separately checked. This comparison does not establish Android bitmap decoding, color-management, interpolation, typography or full native pixel parity.

`npm test` includes the kernel comparison when the generated Kotlin review fixture exists, then the full browser scenario. Without that optional ignored fixture it reports only canonical browser cases, not a native fixture pass. The synthetic browser test checks red→green while blue remains unchanged, exported PNG pixels and Undo. Native saved-look/session round trips remain JVM tests; Android instrumentation cases are compiled, not executed. C034 recipes remain native-only in the browser. These are platform preview implementations of the same workflows, not extra app feature counts.

`native-ui-contract.json` guards the EditorChrome and selective-color Kotlin sources mirrored in browser code. If either changes, review/update the browser mirror, regenerate/compare the Kotlin synthetic samples where applicable, then refresh that source digest. This protects against silently stale mirrors; the digests do not prove native UI pixel parity and do not replace Android checks. Native drawing Close/New actions and notice are also extracted from source. A late PNG callback from a closed drawing is ignored.
