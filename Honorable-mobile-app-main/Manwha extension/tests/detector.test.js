import test from "node:test";
import assert from "node:assert/strict";

await import("../src/registry/novels.js");
await import("../src/detection/novel-detector.js");
const { detectNovel, pathCandidate } = globalThis.NovelLensDetector;

function fakeDocument({ title = "", heading = "", og = "", canonical = "", chapterText = "" } = {}) {
  const nodes = {
    'link[rel="canonical"]': canonical ? { href: canonical } : null,
    'meta[property="og:title"]': og ? { content: og } : null,
    h1: heading ? { textContent: heading } : null,
    "article, [class*='chapter-content' i], [id*='chapter-content' i], [class*='reading-content' i], [class*='chapter-body' i]": chapterText ? { textContent: chapterText } : null
  };
  return { title, querySelector: (selector) => nodes[selector] || null, querySelectorAll: () => [] };
}

test("extracts the novel before a chapter path segment", () => {
  assert.equal(pathCandidate(new URL("https://reader.test/novel/reverend-insanity/chapter-327")), "reverend-insanity");
});

test("accepts any NovelFire book from its adapter and page evidence", () => {
  const result = detectNovel(
    fakeDocument({ title: "Shadow Slave - Chapter 327 | Novel Fire", heading: "Shadow Slave Chapter 327", og: "Shadow Slave" }),
    "https://novelfire.net/book/shadow-slave/chapter-327"
  );
  assert.equal(result.status, "accepted");
  assert.equal(result.profile.id, "shadow-slave");
  assert.equal(result.profile.title, "Shadow Slave");
  assert.ok(result.confidence >= 0.85);
});

test("creates a profile for a NovelFire title absent from the registry", () => {
  const result = detectNovel(
    fakeDocument({ title: "A Completely New Story - Chapter 4 | Novel Fire", heading: "A Completely New Story Chapter 4" }),
    "https://novelfire.net/book/a-completely-new-story/chapter-4"
  );
  assert.equal(result.status, "accepted");
  assert.equal(result.profile.id, "a-completely-new-story");
  assert.equal(result.profile.wiki, null);
});

test("accepts NovelFull, FreeWebNovel, and Ranobes novel URLs", () => {
  const cases = [
    ["https://novelfull.com/coiling-dragon.html", "coiling-dragon"],
    ["https://freewebnovel.com/novel/coiling-dragon/chapter-10", "coiling-dragon"],
    ["https://ranobes.net/novels/12345-coiling-dragon-v741610.html", "coiling-dragon"]
  ];
  for (const [url, expected] of cases) {
    const result = detectNovel(fakeDocument({ title: "Coiling Dragon - Chapter 10" }), url);
    assert.equal(result.status, "accepted");
    assert.equal(result.profile.id, expected);
  }
});

test("does not identify an unrelated page", () => {
  assert.equal(detectNovel(fakeDocument({ title: "News" }), "https://example.com/news/today").status, "unknown");
});

test("accepts a chapter on an unknown reading website from independent signals", () => {
  const result = detectNovel(
    fakeDocument({ title: "Mother of Learning — Chapter 12", heading: "Chapter 12" }),
    "https://reader.example/stories/mother-of-learning/chapter-12"
  );
  assert.equal(result.status, "accepted");
  assert.equal(result.profile.id, "mother-of-learning");
  assert.equal(result.profile.sources[0].rootUrl, "https://reader.example/stories/mother-of-learning");
});
