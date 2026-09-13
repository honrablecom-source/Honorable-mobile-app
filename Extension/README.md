# Novel Companion

A no-build Chrome Manifest V3 extension. Find a work, highlight known character and place names, and click them for a full-height information sidebar with biographies, portraits when available, and source links.

## Install

1. Open `chrome://extensions` in Chrome.
2. Turn on **Developer mode**.
3. Click **Load unpacked** and select this `Extension` folder.
4. Open an online novel, click the extension, correct the suggested title if needed, and click **Find book**.
5. Select the correct literary work (search can also return adaptations), then **Highlight this page**.
6. Click a highlighted name, or focus it with Tab and press Enter. Escape closes its card.

Use **Remove page highlights** to restore the page. Activate highlighting again after navigating to a new chapter. Newly inserted text on the current page is scanned automatically. For the included local `demo.html`, enable **Allow access to file URLs** in Chrome’s extension details.

## Scope and privacy

- Requests go directly to Wikidata’s Action API. Wikipedia supplies on-demand biographies and images. No backend, paid AI, telemetry, or API keys.
- Only the submitted search title and selected entity IDs are sent. Full URLs and chapter text are not uploaded.
- Uses `activeTab` and `scripting` for the page you activate; API host access is limited to Wikidata and English Wikipedia. Portraits load from Wikimedia.
- Local cache holds up to 20 indexes and 40 detail profiles, reused for 7 days. Clear it from the popup.
- Fetches direct P674 (characters) and P840 (narrative location) links, up to 300 entities per work, in batches of 50. Missing links mean missing highlights; this is not an exhaustive literary database.
- English names and aliases only. Ambiguous aliases are skipped. Names spanning separate HTML elements are not matched. Links, editable fields, navigation, and code are left alone. Up to 3,000 highlights per activation.
- Descriptions can contain spoilers and errors. Opening a sidebar fetches up to 12,000 characters from the linked English Wikipedia article and its thumbnail when available. Wikipedia excerpts link to the source/history and CC BY-SA license; image license links are included. First-appearance work is shown separately from the chapter field, which remains unavailable until a reliable chapter source is integrated. Wikidata structured data is CC0.
- Public APIs have usage limits. Requests time out after 15 seconds and report service errors without automatic retry storms.
- Chrome internal pages, the Chrome Web Store, PDFs, and some protected pages cannot be annotated.

## Verification

Run `node --test tests/data.test.mjs` for fixture-based API and cache tests. Browser smoke test instructions/results are recorded in `tests/README.md`.

## Implementation references

- [Chrome scripting](https://developer.chrome.com/docs/extensions/reference/api/scripting)
- [Wikibase API](https://www.mediawiki.org/wiki/Wikibase/API)
- [Characters, P674](https://www.wikidata.org/wiki/Property:P674)
- [Narrative location, P840](https://www.wikidata.org/wiki/Property:P840)

## Reading beside character information

Opening a profile reserves a separate pane for the story. On desktop, the story scrolls on the left and the profile on the right. Below 760px, the profile docks beneath the story, leaving 58% of the viewport for reading. Both panes scroll independently. Closing the profile restores the body's original layout styles. The current name is kept in view when opening a profile. Sites with custom nested scrolling or unusual root layouts may require site-specific compatibility work.

## Version 0.2 — automatic highlighting (current flow)

The toolbar popup has been removed. After loading/reloading the extension, refresh the reading tab. The content script runs on HTTP(S) pages and performs a lookup only when it recognizes a book title in a novel/book/story/fiction path or a chapter URL. Example: `/novel/pride-and-prejudice/chapter-2`. Only the extracted title is sent; chapter text and full URLs stay local.

A unique exact title/alias match described as a literary work is selected automatically. Unsupported URLs, ambiguous works, missing data, and service failures leave the page unchanged. Coverage is limited to known Wikidata names, not every character. The toolbar icon pauses/resumes highlights for the current document. Chapter navigation triggers another lookup; results are cached. Automatic access on reading pages requires Chrome's site access permission.

Earlier popup installation steps above are superseded by this flow. The preview folder contains only the current automatic reading experience; obsolete popup previews have been removed.

## Version 0.3 — clean reading copy

Right-click the loaded chapter or the extension toolbar icon and select **Read without popups — Novel Companion**. This opens a separate extension tab containing only copied text, automatic name highlights, and the information sidebar. It does not run the source page's scripts, embeds, or ad links. The original URL is retained locally for title detection and a **Copy link** button. Chapter text stays in browser session memory and is removed when its reading tab closes.

This is a clean copy, not a browser-wide ad blocker. It cannot prevent redirects before a chapter loads, stop the original site tab from running, or retrieve unloaded/locked chapters. Close the original site tab after the clean reader opens. Extraction is heuristic; pages with unusual markup may include extra text or omit formatting. The copy is limited to 500,000 characters.

Character and place image areas are blank if no source image is available or if loading fails. No initials or invented portraits are displayed.

## Character role tags

Character sidebars include role badges and an **Edit role tags** control. Available tags: MC (Main Character), FL (Female Lead), ML (Male Lead), Side Character, Antagonist, Supporting Character, Mentor, and Love Interest. Multiple tags can be selected. Untagged characters show **Role unconfirmed**; roles are not inferred from gender or guessed from biography text. Reader-assigned tags are stored locally per novel and character, and are not uploaded. Clearing all checkboxes removes the saved assignment. Place sidebars do not show character roles.

## Power information and chapter summaries

Character profiles include **Power, abilities & weaknesses**. Relevant sentences mentioning abilities, combat, or powers are extracted from the available Wikipedia biography and labeled as related excerpts; these can mention other characters. The extension does not assign an automatic anime power tier. **Add your power / rank notes** stores up to 3,000 characters locally per novel and character; include the arc/chapter and a source for context.

The visible novel/chapter heading is highlighted in gold on supported reading pages. Click it to open **Chapter summary** in the separate reading sidebar. This is a local extractive summarizer: it ranks sentences by recurring words and displays up to five in their original order. It uses up to 100,000 characters from the loaded chapter container, never a Wikipedia plot summary, and uploads no chapter text. It may miss context and cannot summarize unloaded chapters. A page without a recognizable heading/content container may not support this feature.

## Expanded automatic novel lookup

The extension now tries up to three title candidates from recognizable URL paths/query parameters, book metadata, Open Graph titles, and page titles. Generic pages without reading signals are not submitted. It searches Wikidata first, then resolves English Wikipedia article titles/redirects to Wikidata IDs when needed. It checks literary descriptions and exact normalized labels/aliases before selecting a work, avoiding obvious screen adaptations and ambiguous matches.

Character and place indexes use the Wikidata Action API's `wbgetentities`: P674 for characters and P840 for settings. This reads the same structured statements as a SPARQL query without constructing a title-interpolated query. Names, aliases, descriptions, and article links load in batches. Cache entries from the previous title resolver are refreshed automatically. The 300-entity index limit remains; missing database relationships cannot be filled by this lookup. No paid AI or API keys are involved.

The pasted SPARQL sample's URL is malformed. A SPARQL implementation would use `https://query.wikidata.org/sparql` with encoded query parameters; this extension instead uses `https://www.wikidata.org/w/api.php`.

Validation: automated title/alias matching, adaptation rejection, Wikipedia-to-Wikidata resolution, caching and browser interaction tests pass. A live Wikidata search also successfully resolved Pride and Prejudice to Q170583. Complete real-browser behavior across third-party reading sites still depends on their URLs and page structure.
