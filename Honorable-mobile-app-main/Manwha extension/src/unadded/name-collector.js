(function (root) {
  "use strict";
  const ignored = new Set([
    "after", "again", "although", "and", "another", "before", "but", "chapter", "finally", "for", "however",
    "if", "instead", "later", "meanwhile", "no", "now", "once", "perhaps", "soon", "still", "suddenly", "that",
    "the", "then", "there", "therefore", "these", "they", "this", "those", "today", "what", "when", "where",
    "whether", "while", "who", "why", "with", "without", "yes", "yet"
  ]);
  const normalize = (value) => String(value || "").normalize("NFKC").toLocaleLowerCase().replace(/[^\p{L}\p{N}'’ -]+/gu, "").replace(/\s+/g, " ").trim();

  function collect(rootNode, knownNames = []) {
    const known = new Set(knownNames.map(normalize));
    const counts = new Map();
    const text = String(rootNode?.innerText || rootNode?.textContent || "").replace(/\s+/g, " ");
    const pattern = /(?:^|[\s“”"'‘’(—-])([\p{Lu}][\p{L}'’-]{2,}(?:\s+[\p{Lu}][\p{L}'’-]{1,}){0,2})/gu;
    for (const match of text.matchAll(pattern)) {
      const name = match[1].replace(/[.'’-]+$/g, "").trim();
      const normalized = normalize(name);
      const first = normalized.split(" ")[0];
      if (!normalized || known.has(normalized) || ignored.has(first) || /^chapter\b/i.test(name) || /\d/.test(name)) continue;
      counts.set(name, (counts.get(name) || 0) + 1);
    }
    return [...counts].map(([name, count]) => ({ name, normalizedName: normalize(name), count }))
      .filter((item) => item.count >= 2).sort((a, b) => b.count - a.count || a.name.localeCompare(b.name)).slice(0, 40);
  }

  root.NovelLensUnadded = { collect, normalize };
})(typeof globalThis !== "undefined" ? globalThis : self);
