# Creative engine reuse review

October 24, 2026. Candidate only; no new app engine is integrated yet.

## miniPaint

- Upstream: https://github.com/viliusle/miniPaint
- Version 4.14.3, source commit a79733eb803fc97084ef0ee4faa96b031e69e1c0.
- Main project license: MIT; copyright ViliusL and notice retention required.
- Runtime dependency audit found GPL-3.0 AlertifyJS 1.14.0. Do not distribute the upstream prebuilt bundle as if every component were MIT.
- Outside the app, an original Honorable adapter replaces the notifier imports; the GPL dependency was removed. This adapter only uses DOM textContent for local error/success/warning notices. The candidate rebuild passes. No GPL notifier implementation was copied.
- External service configuration was blanked and fonts restricted to system choices before rebuilding. No upstream service keys will be included. No real account secrets are needed.
- Initial headless test at 390px loads 96 modules, accepts a local test image, changes layer opacity and undoes it, and returns the expected RGB pixels. No external requests or page errors were observed in this test. This is not Android/device acceptance or proof of all tools.
- Next: license/notice completeness, mobile styling, local-origin/CSP checks, canvas/layer/project bounds, import provenance, protected original files, PNG handoff, editable saves, native guest entry, tests and a fresh APK. Do not count module files, style variants or disabled menu items as 90 finished workflows.

The existing Excalidraw MIT integration stays intact. The completed native photo/video editors are preserved; reuse should add capabilities rather than replace them. Genuine generative image/video and AI rigging remain unimplemented.
