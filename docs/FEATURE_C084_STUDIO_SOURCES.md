# C084 — Find a source beyond Studio's first page

1 new experimental source workflow, 0 separate fixes, 0 scaffolding outcomes.
Source register: 83/500 workflows, 45 beyond the batch baseline of 38. Broad
native/APK build target remains approximately 138; this milestone does not build
an APK or refresh packaged drawing assets.

Studio's source browser now lets users find and open media beyond the first 12
visible items. Previous/next navigation renders at most 12 source cards at once;
additional library pages reuse the existing native pagination API. Filename
filtering is case-insensitive and trims surrounding whitespace. It applies only
to loaded items of the selected photo/video type, with visible ranges and explicit
loaded-only guidance. Clear and workspace-type changes reset the visible page.
If a library refresh shrinks the list, paging clamps to an available page.

Loading another library page retains the filename/type filter. Same-tick repeated
requests are blocked, loading is announced, and failures remain retryable. Empty
matches still offer the direct file picker. Opening a card passes that card's URI
and media type to the existing editor path. Filtering is local React state: no
Seran query, credit charge, media upload or new native bridge API is introduced.
Existing monochrome controls, 48px targets and reduced-motion inspector behavior
are retained. Existing React Native controls are reused; no application dependency
or lockfile changes are needed.

Evidence under .context/studio-source-browser:

- All 168 Jest tests in 18 suites pass, including four new actual Studio screen
  cases covering bounded browsing/back navigation, shrinking lists, filtering,
  query/type preservation, exact selected URIs, same-tick admission, retry and
  direct import without matches. Typecheck exits 0. Lint exits 0 with 0 errors and
  38 warnings.
- The committed studio-source-preview.mjs harness renders the actual StudioScreen
  through the existing React Native Web adapter, using synthetic media/library
  and editor callbacks. Browser interactions check next/previous paging with 12
  rendered source cards, local filename filtering/clear, failure/retry, source
  identity and no-match picker. It checks 320/390/844px overflow, with zero page
  errors or external requests. The 390px pager and no-match screenshots were
  visually reviewed.

The web harness does not execute Android widgets, native provider paging, actual
media decoding, keyboard/IME, accessibility services, editor launch or real-device
memory behavior. Those gates and full native/APK integration remain pending. The
native search/indexing algorithm, ranking, billing catalog and server ledger are
unchanged. Filename text is not saved or uploaded by this workflow.

Reproduce the browser check after restoring the existing workspace-preview
dependencies and installing a Playwright Chromium headless shell:

```sh
PLAYWRIGHT_BROWSERS_PATH=/tmp/noble-studio-browser-cache node Honorable-mobile-app-main/mobile-react-native/workspace-preview/studio-source-preview.mjs
```
