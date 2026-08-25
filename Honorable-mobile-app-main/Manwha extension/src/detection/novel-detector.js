(function (root) {
  "use strict";
  const normalize = (value) => String(value || "").replace(/[_-]+/g, " ")
    .replace(/\b(chapter|chap|ch)\s*[\d.:-]+.*$/i, "")
    .replace(/\s*[|–—-]\s*(novel fire|read|chapter|webnovel|novel).*$/i, "")
    .replace(/[^\p{L}\p{N}'’]+/gu, " ").replace(/\s+/g, " ").trim().toLowerCase();
  const slugify = (value) => normalize(value).replace(/[’']/g, "").replace(/\s+/g, "-");
  const titleFromSlug = (slug) => String(slug || "").split("-").filter(Boolean)
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1)).join(" ");
  function pathCandidate(url) {
    const parts = url.pathname.split("/").filter(Boolean);
    for (const key of ["novel", "book", "story"]) {
      const value = url.searchParams.get(key);
      if (value) return decodeURIComponent(value);
    }
    const book = parts.findIndex((part) => part.toLowerCase() === "book");
    if (book >= 0 && parts[book + 1]) return decodeURIComponent(parts[book + 1]);
    const novel = parts.findIndex((part) => /^(novel|novels)$/i.test(part));
    if (novel >= 0 && parts[novel + 1]) return decodeURIComponent(parts[novel + 1])
      .replace(/^\d+-/, "").replace(/\.html$/i, "").replace(/-v\d+$/i, "");
    if (/novelfull\.(com|net|in)$/i.test(url.hostname) && parts[0]?.endsWith(".html")) {
      return decodeURIComponent(parts[0]).replace(/\.html$/i, "");
    }
    const chapter = parts.findIndex((part) => /^(chapter|chap|ch)(?:[-_]?\d+|[-_/])/i.test(part));
    if (chapter > 0) return decodeURIComponent(parts[chapter - 1]).replace(/\.html$/i, "");
    const numberedChapter = parts.findIndex((part, index) => index > 0 && /^\d+(?:[-_.]\d+)?(?:\.html)?$/i.test(part));
    return numberedChapter > 0 ? decodeURIComponent(parts[numberedChapter - 1]).replace(/\.html$/i, "") : null;
  }
  function chapterEvidence(doc, url) {
    const pathSignal = /(?:^|\/)(?:chapter|chap|ch)(?:[-_/]?\d+|\/)/i.test(url.pathname);
    const heading = `${doc.querySelector("h1")?.textContent || ""} ${doc.title || ""}`;
    const titleSignal = /\b(?:chapter|chap|ch)\s*[-:#.]?\s*\d+/i.test(heading);
    const rootNode = doc.querySelector("article, [class*='chapter-content' i], [id*='chapter-content' i], [class*='reading-content' i], [class*='chapter-body' i]");
    const contentSignal = String(rootNode?.textContent || "").trim().length >= 500;
    return { pathSignal, titleSignal, contentSignal };
  }
  function rootUrl(url) {
    const parts = url.pathname.split("/").filter(Boolean);
    const chapter = parts.findIndex((part) => /^(?:chapter|chap|ch)(?:[-_]?\d+|[-_/])/i.test(part) || /^\d+(?:[-_.]\d+)?(?:\.html)?$/i.test(part));
    return chapter > 0 ? `${url.origin}/${parts.slice(0, chapter).join("/")}` : `${url.origin}${url.pathname}`;
  }
  function extractPageTitle(doc, slug) {
    const candidates = [
      ...[...doc.querySelectorAll("[class*='breadcrumb' i] a, [aria-label*='breadcrumb' i] a")].map((node) => node.textContent),
      doc.querySelector('meta[property="og:title"]')?.content, doc.querySelector("h1")?.textContent, doc.title
    ].filter(Boolean);
    const cleaned = candidates.map((value) => String(value).replace(/\s*[|–—-]\s*Novel Fire.*$/i, "")
      .replace(/\s+(?:chapter|chap|ch)\s*[\d.:-]+.*$/i, "").trim())
      .filter((value) => value.length >= 2 && !/^(home|novel|chapter)$/i.test(value));
    return cleaned.find((value) => slugify(value) === slug) || cleaned.find((value) => normalize(value).length > 2) || titleFromSlug(slug);
  }
  function detectNovel(doc, locationHref, savedBindings = []) {
    const url = new URL(locationHref);
    const slug = slugify(pathCandidate(url));
    if (!slug) return { status: "unknown", confidence: 0, evidence: [] };
    const title = extractPageTitle(doc, slug);
    const stored = savedBindings.find((item) => item.hostname === url.hostname && item.novelSlug === slug);
    const adapters = [
      [/novelfire\.net$/i, "novelFireAdapter"],
      [/novelfull\.(com|net|in)$/i, "novelFullAdapter"],
      [/freewebnovel\.com$/i, "freeWebNovelAdapter"],
      [/ranobes\.net$/i, "ranobesAdapter"]
    ];
    const adapter = adapters.find(([pattern]) => pattern.test(url.hostname));
    const signals = chapterEvidence(doc, url);
    const evidence = [{ source: adapter?.[1] || "urlPath", points: adapter ? 70 : 25 }, { source: "pageTitle", points: 25 }];
    if (!adapter && signals.pathSignal) evidence.push({ source: "chapterPath", points: 20 });
    if (!adapter && signals.titleSignal) evidence.push({ source: "chapterTitle", points: 15 });
    if (!adapter && signals.contentSignal) evidence.push({ source: "chapterContent", points: 20 });
    if (stored) evidence.push({ source: "savedBinding", points: 100 });
    const points = evidence.reduce((sum, item) => sum + item.points, 0);
    const wikiHost = root.NovelLensRegistry?.wikiMappings?.[slug];
    return {
      status: (adapter || stored || ((signals.pathSignal || signals.titleSignal) && points >= 65)) ? "accepted" : "uncertain", confidence: Math.min(.99, points / 100), evidence,
      profile: {
        id: slug, title, normalizedTitle: normalize(title), aliases: [title],
        sources: [{ hostname: url.hostname, novelSlug: slug, rootUrl: rootUrl(url) }],
        wiki: wikiHost ? { provider: "mediawiki", hostname: wikiHost, apiPath: "/api.php", characterCategories: ["Characters"] } : null,
        createdAt: Date.now(), updatedAt: Date.now()
      }
    };
  }
  root.NovelLensDetector = { detectNovel, normalize, slugify, pathCandidate, titleFromSlug };
})(typeof globalThis !== "undefined" ? globalThis : self);
