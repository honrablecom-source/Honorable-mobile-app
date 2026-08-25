(function (root) {
  "use strict";

  const wikiMappings = {
    "reverend-insanity": "reverend-insanity.fandom.com",
    "lord-of-the-mysteries": "lordofthemysteries.fandom.com",
    "shadow-slave": "shadowslave.fandom.com",
    "omniscient-readers-viewpoint": "omniscient-readers-viewpoint.fandom.com",
    "the-beginning-after-the-end": "tbate.fandom.com",
    "the-legendary-mechanic": "the-legendary-mechanic.fandom.com",
    "the-authors-pov": "the-authors-pov.fandom.com"
  };
  root.NovelLensRegistry = Object.freeze({ wikiMappings });
})(typeof globalThis !== "undefined" ? globalThis : self);
