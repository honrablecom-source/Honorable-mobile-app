(function (root) {
  "use strict";

  function buildTrie(records) {
    const trie = { next: new Map(), outputs: [] };
    for (const record of records) {
      const names = [record.displayName, ...(record.aliases || [])];
      for (const rawName of names) {
        const name = String(rawName || "").trim();
        if (name.length < 3) continue;
        let node = trie;
        for (const char of name.toLocaleLowerCase()) {
          if (!node.next.has(char)) node.next.set(char, { next: new Map(), outputs: [] });
          node = node.next.get(char);
        }
        node.outputs.push({ characterId: record.id, text: name, length: name.length });
      }
    }
    return trie;
  }

  const isWord = (char) => char != null && /[\p{L}\p{N}_]/u.test(char);

  function findMatches(text, trie) {
    const lower = text.toLocaleLowerCase();
    const matches = [];
    for (let start = 0; start < lower.length; start += 1) {
      let node = trie;
      for (let end = start; end < lower.length; end += 1) {
        node = node.next.get(lower[end]);
        if (!node) break;
        for (const output of node.outputs) {
          const stop = end + 1;
          if (!isWord(lower[start - 1]) && !isWord(lower[stop])) {
            matches.push({ start, end: stop, characterId: output.characterId, text: text.slice(start, stop) });
          }
        }
      }
    }
    matches.sort((a, b) => a.start - b.start || (b.end - b.start) - (a.end - a.start));
    const selected = [];
    let cursor = -1;
    for (const match of matches) {
      if (match.start >= cursor) {
        selected.push(match);
        cursor = match.end;
      }
    }
    return selected;
  }

  root.NovelLensMatcher = { buildTrie, findMatches };
})(typeof globalThis !== "undefined" ? globalThis : self);
