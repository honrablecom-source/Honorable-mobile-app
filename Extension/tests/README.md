# Verification

- `node --test tests/data.test.mjs`: fixture checks for claim filtering, source URL encoding, rate-limit errors, index loading and cache reuse.
- `node tests/browser.cjs` (from Extension): uses Playwright already installed in the sibling mobile project's ui-previews folder. This is a workspace-only test dependency; the extension itself has no dependencies.
- Headless Chromium smoke test covers title suggestion, whole-name matching, excluded links/inputs, card open/close, dynamically added and edited text, and highlight removal. Chrome messaging is mocked in this content-script test.
- JavaScript syntax checked with `node --check`.

The installed-extension popup workflow and live Wikidata requests still need a manual check in Chrome using the installation steps in the main README. Tests use fixtures; they do not prove current book coverage or live service availability.

The browser smoke test also checks non-overlapping reading/profile rectangles at 1280px and 390px viewport widths, a usable mobile reading height, and removal of the temporary body positioning when closed.
