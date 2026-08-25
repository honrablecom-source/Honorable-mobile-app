# Unadded character queue

`name-collector.js` finds repeated proper-name candidates in the chapter being read. Pending candidates are stored in the extension's IndexedDB `unadded` store because an installed Chrome extension cannot write runtime data back into its own source folder.

Records are scoped by `novelId`. A candidate is promoted to the character index only after the configured Fandom wiki returns a character-like page whose title or alias matches the candidate. Rejected and unresolved candidates remain separate and never receive blue highlighting.
