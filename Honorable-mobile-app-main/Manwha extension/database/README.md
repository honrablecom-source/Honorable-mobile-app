# Bundled character database

The full catalog lives in Neon, not in bundled JSON. `schema.sql` defines normalized novel, character, alias, and sync tables. Every character key is `<novelId>:<wikiPageId>`, keeping identical names in different novels separate.

Role codes are `MC` (main character/protagonist), `FMC` (female lead/protagonist), `ANT` (antagonist), `SUPPORT` (supporting character), and `CHARACTER` when the source wiki does not provide a safe classification. Roles are derived only from Fandom page categories and are intentionally conservative.

Set `NEON_DATABASE_URL` only in the process environment and run `npm run sync:neon` to refresh the top 1,000 NovelFire profiles and verified MediaWiki character trees. The credential must never be placed in extension code because users can inspect unpacked extensions. Images use Fandom PageImages URLs and detailed profiles are refreshed lazily when a reader clicks a highlighted name.

Additional metadata catalogs use normalized `source_bindings`: run `npm run sync:catalogs` for NovelFull, FreeWebNovel, and Ranobes. Run `npm run expand:characters` to resume checkpointed, title-verified Fandom discovery and rich character imports. Discovery versions and pending zero-character wiki mappings make interrupted or rate-limited runs safely resumable without duplicating records.
