# NovelLens

NovelLens is a dependency-free Chrome Manifest V3 extension. It identifies the current novel, imports that novel's character index from MediaWiki/Fandom, highlights character names only inside chapter content, and shows lazily fetched character details in Chrome's side panel.

## Install locally

1. Open `chrome://extensions`.
2. Enable **Developer mode**.
3. Choose **Load unpacked** and select this folder.
4. Open a chapter on NovelFire, NovelFull, FreeWebNovel, or Ranobes. Their site adapters identify the novel without requiring it to be hard-coded.

Click the toolbar icon to open the side panel. Click any blue character name to show its profile.

NovelLens refreshes a local sample of NovelFire's catalog (the first 200 current search results) weekly. It also creates profiles directly from any NovelFire book URL, so detection is not limited to that sample. For an unknown title it tries verified Fandom hostname candidates. If no safe match exists, the side panel asks for the correct Fandom wiki once and remembers it.

## Privacy and cache

Chapter text never leaves the browser. Network requests contain only MediaWiki API parameters for character lists and selected character pages. Character names and details are cached in IndexedDB. The character index refreshes after seven days; details refresh after thirty days.

## Neon catalog

The central database uses Neon PostgreSQL and currently contains the top 1,000 NovelFire profiles plus character data from verified Fandom communities. Large character JSON files are deliberately not bundled with the extension. The owner connection string is used only by the local sync process and is never shipped to browsers. See `database/schema.sql` and run `npm run sync:neon` with `NEON_DATABASE_URL` in the environment to refresh it.

## Development

Run `npm test` for unit tests and `npm run check` for a manifest/source validation pass. No install or build step is required.
