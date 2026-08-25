(function () {
  "use strict";
  const SKIPPED = new Set(["SCRIPT", "STYLE", "TEXTAREA", "INPUT", "BUTTON", "A", "CODE", "PRE", "NOSCRIPT", "SELECT", "OPTION"]);
  let profile = null;
  let trie = null;
  let chapterRoot = null;
  let lastUrl = location.href;
  let timer = null;
  let collectedForUrl = null;

  async function start() {
    const detection = NovelLensDetector.detectNovel(document, location.href);
    if (detection.status !== "accepted") return;
    profile = detection.profile;
    const response = await chrome.runtime.sendMessage({ type: "NOVEL_READY", profile }).catch(() => null);
    if (response?.profile) profile = response.profile;
    if (!response?.ok || !response.characters?.length) return;
    trie = NovelLensMatcher.buildTrie(response.characters);
    scan();
    collectUnadded(response.characters);
  }

  async function collectUnadded(characters) {
    if (!chapterRoot || collectedForUrl === location.href || !globalThis.NovelLensUnadded) return;
    collectedForUrl = location.href;
    const knownNames = characters.flatMap((record) => [record.displayName, ...(record.aliases || [])]);
    const candidates = NovelLensUnadded.collect(chapterRoot, knownNames);
    if (!candidates.length) return;
    const result = await chrome.runtime.sendMessage({ type: "UNADDED_NAMES", profile, candidates }).catch(() => null);
    if (result?.characters?.length) {
      const merged = new Map(characters.map((record) => [record.id, record]));
      result.characters.forEach((record) => merged.set(record.id, record));
      trie = NovelLensMatcher.buildTrie([...merged.values()]);
      scan();
    }
  }

  function scan() {
    if (!trie) return;
    chapterRoot = NovelLensSite.findChapterRoot(document);
    if (!chapterRoot) return;
    const walker = document.createTreeWalker(chapterRoot, NodeFilter.SHOW_TEXT, {
      acceptNode(node) {
        const parent = node.parentElement;
        if (!parent || !node.nodeValue?.trim() || SKIPPED.has(parent.tagName)) return NodeFilter.FILTER_REJECT;
        if (parent.closest("[data-novellens-highlight], nav, header, footer, aside, [role='navigation']")) return NodeFilter.FILTER_REJECT;
        return NodeFilter.FILTER_ACCEPT;
      }
    });
    const nodes = [];
    while (walker.nextNode()) nodes.push(walker.currentNode);
    nodes.forEach(highlightNode);
  }

  function highlightNode(textNode) {
    const matches = NovelLensMatcher.findMatches(textNode.nodeValue, trie);
    if (!matches.length) return;
    const fragment = document.createDocumentFragment();
    let cursor = 0;
    for (const match of matches) {
      fragment.append(textNode.nodeValue.slice(cursor, match.start));
      const span = document.createElement("span");
      span.className = "novellens-character";
      span.dataset.novellensHighlight = "true";
      span.dataset.characterId = match.characterId;
      span.title = "Character — open in NovelLens";
      span.textContent = textNode.nodeValue.slice(match.start, match.end);
      fragment.append(span);
      cursor = match.end;
    }
    fragment.append(textNode.nodeValue.slice(cursor));
    textNode.replaceWith(fragment);
  }

  document.addEventListener("click", (event) => {
    const target = event.target.closest?.("[data-novellens-highlight]");
    if (!target || !profile) return;
    event.preventDefault();
    event.stopPropagation();
    chrome.runtime.sendMessage({ type: "CHARACTER_SELECTED", characterId: target.dataset.characterId, profile });
  }, true);

  const observer = new MutationObserver((mutations) => {
    if (!trie || mutations.every((mutation) => [...mutation.addedNodes].every((node) => node.nodeType === 1 && node.matches?.("[data-novellens-highlight]")))) return;
    clearTimeout(timer);
    timer = setTimeout(() => {
      if (location.href !== lastUrl) {
        lastUrl = location.href;
        profile = null; trie = null; collectedForUrl = null;
        start();
      } else scan();
    }, 300);
  });
  observer.observe(document.documentElement, { childList: true, subtree: true });
  start();
})();
