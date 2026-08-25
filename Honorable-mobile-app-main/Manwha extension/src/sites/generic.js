(function (root) {
  "use strict";
  const selectors = [
    "article [class*='chapter-content' i]", "article [class*='chapter-body' i]",
    "[class*='chapter-content' i]", "[id*='chapter-content' i]",
    "[class*='reading-content' i]", "[id*='reading-content' i]",
    "main article", "article", "main"
  ];

  function score(node) {
    const text = node?.innerText?.trim() || "";
    const paragraphs = node?.querySelectorAll?.("p").length || 0;
    return text.length + paragraphs * 250;
  }

  function findChapterRoot(doc) {
    const candidates = selectors.flatMap((selector) => [...doc.querySelectorAll(selector)]);
    const best = candidates.sort((a, b) => score(b) - score(a))[0];
    return score(best) >= 700 ? best : null;
  }

  root.NovelLensSite = { findChapterRoot };
})(typeof globalThis !== "undefined" ? globalThis : self);
