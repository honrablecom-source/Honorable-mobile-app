# Noble offline drawing

Excalidraw 0.18.1 (MIT) is embedded as a local touch drawing editor. Noble provides white/black chrome, bounded local draft save/restore, offline asset delivery, and PNG transfer into the existing Android photo editor. This is manual drawing, not AI image generation or a replacement for the native photo/video engines.

Rebuild with Node 22: `npm ci`, `npm test`, `npm run build` from this folder. The generated `android/app/src/main/assets/drawing/` bundle is committed so ordinary Android builds do not need another web build. Package versions and integrity hashes are locked. Third-party code and font notices are retained in THIRD_PARTY and the generated bundle.

The Android activity is private and guest-only. Only bundled assets from its synthetic HTTPS origin are served; other requests and navigation are blocked. File/content access, remote collaboration, imported images, external frames and outbound connections are disabled. No account data or media is uploaded.

One editable drawing draft is stored in WebView local storage under the private app origin, without cloud backup. Tap Save draft before leaving. Drafts are bounded to 500 supported objects and 1 MiB; images are rasterized to at most 2048 pixels per edge. Native PNG validation applies independent bounds before opening the photo editor. PNG creation does not overwrite an imported original.

Device/WebView drawing, font, touch, lifecycle and PNG acceptance remain separate from compilation and unit tests. iOS integration is not implemented.
