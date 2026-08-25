import test from "node:test";
import assert from "node:assert/strict";

await import("../src/matching/matcher.js");
const { buildTrie, findMatches } = globalThis.NovelLensMatcher;

test("matches multiple character names and preserves novel-scoped IDs", () => {
  const trie = buildTrie([
    { id: "reverend-insanity:1", displayName: "Fang Yuan", aliases: ["Hei Tu"] },
    { id: "reverend-insanity:2", displayName: "Bai Ning Bing", aliases: [] }
  ]);
  assert.deepEqual(findMatches("Fang Yuan met Bai Ning Bing.", trie).map((m) => m.characterId), [
    "reverend-insanity:1", "reverend-insanity:2"
  ]);
});

test("uses word boundaries and prefers the longest overlapping name", () => {
  const trie = buildTrie([
    { id: "n:1", displayName: "Fang", aliases: [] },
    { id: "n:2", displayName: "Fang Yuan", aliases: [] }
  ]);
  assert.deepEqual(findMatches("Fang Yuan and Fangyuan", trie).map((m) => m.characterId), ["n:2"]);
});

test("same name in separate indexes cannot cross novels", () => {
  const first = buildTrie([{ id: "novel-a:10", displayName: "Li Wei", aliases: [] }]);
  const second = buildTrie([{ id: "novel-b:20", displayName: "Li Wei", aliases: [] }]);
  assert.equal(findMatches("Li Wei", first)[0].characterId, "novel-a:10");
  assert.equal(findMatches("Li Wei", second)[0].characterId, "novel-b:20");
});
