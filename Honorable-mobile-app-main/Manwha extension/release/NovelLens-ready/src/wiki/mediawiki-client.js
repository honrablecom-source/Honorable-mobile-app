const cleanText = (value) => String(value || "").replace(/\s+/g, " ").trim();
const normalizeName = (value) => cleanText(value).normalize("NFKC").toLocaleLowerCase();
const negativeEntity = /\b(locations?|geography|districts?|cities|countries|organizations?|factions?|items?|weapons?|abilities|skills?|chapters?|volumes?|episodes?|terminology|concepts?|events?)\b/i;
const positiveCharacter = /\b(characters?|protagonists?|antagonists?|villains?|people|humans?|demons?|saints?|awakened)\b/i;
const aiImageMarker = /(?:midjourney|stable[_ -]?diffusion|dall[_ -]?e|ai[_ -]?(?:art|generated)|generated[_ -]?art)/i;
function verifiedImage(page) {
  const primary = page.thumbnail?.source || page.original?.source || null;
  if (primary && !aiImageMarker.test(decodeURIComponent(primary))) return primary;
  const rawInfoboxes = page.pageprops?.infoboxes || "";
  const candidates = [...rawInfoboxes.matchAll(/"url":"([^"\\]*(?:\\.[^"\\]*)*)"/g)]
    .map((match) => match[1].replace(/\\\//g, "/").replace(/\\u0026/gi, "&"));
  const safeCandidate = candidates.find((url) => /^https:\/\//i.test(url) && !aiImageMarker.test(decodeURIComponent(url)));
  if (safeCandidate) return safeCandidate;
  try {
    const infoboxes = JSON.parse(rawInfoboxes || "[]");
    const parsedCandidates = [];
    const visit = (value) => {
      if (Array.isArray(value)) value.forEach(visit);
      else if (value && typeof value === "object") {
        if (typeof value.url === "string") parsedCandidates.push(value.url.replace(/\\\//g, "/"));
        Object.values(value).forEach(visit);
      }
    };
    visit(infoboxes);
    return parsedCandidates.find((url) => /^https:\/\//i.test(url) && !aiImageMarker.test(decodeURIComponent(url))) || null;
  } catch { return null; }
}
const isCharacterPage = (page, categories, aliases = [], strict = false) => {
  const categoryText = categories.join(" | ");
  if (!strict) return true;
  if (positiveCharacter.test(categoryText)) return true;
  if (negativeEntity.test(categoryText)) return false;
  return aliases.length > 0 || /\b(character|person)\b/i.test(page.pageprops?.description || "");
};
const roleFromCategories = (categories) => {
  const categoryText = categories.join(" | ");
  return /female protagonists?|female main characters?/i.test(categoryText) ? "FMC"
    : /protagonists?|main characters?|main cast/i.test(categoryText) ? "MC"
    : /antagonists?|villains?/i.test(categoryText) ? "ANT"
    : /supporting characters?|minor characters?/i.test(categoryText) ? "SUPPORT" : "CHARACTER";
};
function aliasesFromWikiText(wikitext, title) {
  const aliases = [];
  for (const line of String(wikitext || "").split("\n")) {
    const match = line.match(/^\s*\|\s*(aliases?|alias|nicknames?|also known as|name)\s*=\s*(.+)$/i);
    if (!match) continue;
    const value = match[2].replace(/<ref\b[^>]*>[\s\S]*?<\/ref>|<ref\b[^>]*\/\s*>/gi, " ")
      .replace(/\[\[(?:[^\]|]+\|)?([^\]]+)\]\]/g, "$1").replace(/\{\{[^{}]*\}\}/g, " ").replace(/<[^>]+>/g, " ");
    aliases.push(...value.split(/[,;•]|<br\s*\/?\s*>|\s\/\s/i).map(cleanText));
  }
  return [...new Set(aliases.filter((alias) => alias.length >= 3 && normalizeName(alias) !== normalizeName(title)))].slice(0, 16);
}

export class MediaWikiClient {
  constructor(binding) {
    this.binding = binding;
    this.endpoint = `https://${binding.hostname}${binding.apiPath || "/api.php"}`;
  }

  async request(params) {
    const url = new URL(this.endpoint);
    Object.entries({ origin: "*", format: "json", ...params }).forEach(([key, value]) => url.searchParams.set(key, value));
    const response = await fetch(url);
    if (!response.ok) throw new Error(`Wiki request failed (${response.status})`);
    const data = await response.json();
    if (data.error) throw new Error(data.error.info || "Wiki API error");
    return data;
  }

  async importCategory(novelId, category) {
    const pageIds = new Set();
    const visited = new Set();
    const queue = [{ title: `Category:${category}`, depth: 0 }];
    while (queue.length && pageIds.size < 5000) {
      const current = queue.shift();
      if (visited.has(current.title) || current.depth > 4) continue;
      visited.add(current.title);
      let continuation;
      do {
        const data = await this.request({ action: "query", list: "categorymembers", cmtitle: current.title,
          cmtype: "page|subcat", cmlimit: "max", ...(continuation ? { cmcontinue: continuation } : {}) });
        for (const page of data.query?.categorymembers || []) {
          if (page.ns === 0) pageIds.add(page.pageid);
          if (page.ns === 14) queue.push({ title: page.title, depth: current.depth + 1 });
        }
        continuation = data.continue?.cmcontinue;
      } while (continuation);
    }
    const records = [];
    const ids = [...pageIds];
    for (let start = 0; start < ids.length; start += 50) {
      const data = await this.request({ action: "query", pageids: ids.slice(start, start + 50).join("|"),
        prop: "pageimages|categories|revisions|pageprops", piprop: "thumbnail|original", pithumbsize: "500", cllimit: "max",
        rvprop: "content", rvslots: "main", formatversion: "2" });
      for (const page of data.query?.pages || []) {
        const categories = (page.categories || []).map((item) => item.title.replace(/^Category:/, ""));
        const wikitext = page.revisions?.[0]?.slots?.main?.content || "";
        const aliases = aliasesFromWikiText(wikitext, page.title);
        if (!isCharacterPage(page, categories, aliases)) continue;
        const roleCode = roleFromCategories(categories);
        records.push({ id: `${novelId}:${page.pageid}`, novelId, wikiPageId: page.pageid,
          pageTitle: page.title, displayName: page.title, normalizedName: normalizeName(page.title), aliases, roleCode,
          categories, imageUrl: verifiedImage(page),
          wikiPath: `/wiki/${encodeURIComponent(page.title.replace(/ /g, "_"))}`, detailsLoaded: false, updatedAt: Date.now() });
      }
    }
    return records;
  }

  async findCharacter(novelId, candidate) {
    const search = await this.request({ action: "query", list: "search", srsearch: candidate, srnamespace: "0", srlimit: "8", formatversion: "2" });
    const ids = (search.query?.search || []).map((item) => item.pageid).filter(Boolean);
    if (!ids.length) return null;
    const data = await this.request({ action: "query", pageids: ids.join("|"), prop: "pageimages|categories|revisions|pageprops",
      piprop: "thumbnail|original", pithumbsize: "500", cllimit: "max", rvprop: "content", rvslots: "main", formatversion: "2" });
    for (const page of data.query?.pages || []) {
      const categories = (page.categories || []).map((item) => item.title.replace(/^Category:/, ""));
      const aliases = aliasesFromWikiText(page.revisions?.[0]?.slots?.main?.content || "", page.title);
      const exact = [page.title, ...aliases].some((name) => normalizeName(name) === normalizeName(candidate));
      if (!exact || !isCharacterPage(page, categories, aliases, true)) continue;
      return { id: `${novelId}:${page.pageid}`, novelId, wikiPageId: page.pageid, pageTitle: page.title,
        displayName: page.title, normalizedName: normalizeName(page.title), aliases, roleCode: roleFromCategories(categories), categories,
        imageUrl: verifiedImage(page),
        wikiPath: `/wiki/${encodeURIComponent(page.title.replace(/ /g, "_"))}`, detailsLoaded: false, updatedAt: Date.now() };
    }
    return null;
  }

  async getDetails(character) {
    const data = await this.request({
      action: "parse", page: character.pageTitle,
      prop: "displaytitle|text|wikitext|revid", formatversion: "2"
    });
    const imageData = await this.request({
      action: "query", prop: "pageimages|pageprops", pageids: String(character.wikiPageId),
      piprop: "thumbnail|original", pithumbsize: "500"
    }).catch(() => null);
    return parseWikiDetails(data.parse, imageData, character);
  }
}

export function parseWikiDetails(parsed, imageData, character) {
  const rawHtml = String(parsed?.text || "");
  const safeHtml = rawHtml
    .replace(/<(script|style|noscript|iframe|form|button)\b[^>]*>[\s\S]*?<\/\1>/gi, " ")
    .replace(/<sup\b[^>]*class=["'][^"']*reference[^"']*["'][^>]*>[\s\S]*?<\/sup>/gi, " ")
    .replace(/<!--([\s\S]*?)-->/g, " ");
  const decode = (value) => String(value || "")
    .replace(/<br\s*\/?>/gi, " • ").replace(/<\/li>/gi, " • ").replace(/<[^>]*>/g, " ")
    .replace(/&nbsp;/gi, " ").replace(/&amp;/gi, "&").replace(/&lt;/gi, "<")
    .replace(/&gt;/gi, ">").replace(/&quot;/gi, '"').replace(/&#39;|&apos;/gi, "'")
    .replace(/&#(\d+);/g, (_, code) => String.fromCodePoint(Number(code)))
    .replace(/\s*\[\s*\d+(?:\s*[,–-]\s*\d+)*\s*\]\s*/g, " ")
    .replace(/[†‡]+/g, " • ").replace(/(?:\s*•\s*){2,}/g, " • ");
  const fields = {};
  const portableRows = safeHtml.match(/<[^>]+class=["'][^"']*\bpi-item\b[^"']*\bpi-data\b[^"']*["'][^>]*>[\s\S]*?<\/[^>]+>/gi) || [];
  portableRows.forEach((row) => {
    const label = cleanText(decode(row.match(/class=["'][^"']*pi-data-label[^"']*["'][^>]*>([\s\S]*?)<\//i)?.[1]));
    const value = cleanText(decode(row.match(/class=["'][^"']*pi-data-value[^"']*["'][^>]*>([\s\S]*?)<\//i)?.[1]));
    if (label && value && label.length < 80 && value.length < 1000) fields[label] = value;
  });
  const rows = safeHtml.match(/<tr\b[^>]*>[\s\S]*?<\/tr>/gi) || [];
  rows.forEach((row) => {
    const label = cleanText(decode(row.match(/<th\b[^>]*>([\s\S]*?)<\/th>/i)?.[1]));
    const value = cleanText(decode(row.match(/<td\b[^>]*>([\s\S]*?)<\/td>/i)?.[1]));
    if (label && value && label.length < 80 && value.length < 1000) fields[label] = value;
  });
  const paragraphs = (safeHtml.match(/<p\b[^>]*>[\s\S]*?<\/p>/gi) || [])
    .map((paragraph) => cleanText(decode(paragraph))).filter((paragraph) => paragraph.length > 60)
    .filter((paragraph) => !/^\d+\s*\(\s*chapter\b/i.test(paragraph) && !/^(references?|navigation|gallery)\b/i.test(paragraph))
    .sort((a, b) => scoreSummary(b) - scoreSummary(a));
  const page = imageData?.query?.pages?.[character.wikiPageId];
  const imageUrl = page ? verifiedImage(page) : null;
  const aliasValue = Object.entries(fields).find(([key]) => /^(aliases?|also known as|nicknames?)$/i.test(key))?.[1];
  const aliases = aliasValue ? aliasValue.split(/[,;•]|\s\/\s/).map(cleanText).filter((value) => value && value !== character.displayName).slice(0, 12) : character.aliases || [];
  return {
    characterId: character.id, displayName: character.displayName,
    aliases, imageUrl: imageUrl || character.imageUrl,
    summary: paragraphs[0] || "Select full information to view the available wiki fields.", fields,
    sourcePageTitle: character.pageTitle, revisionId: parsed?.revid, fetchedAt: Date.now()
  };
}

function scoreSummary(value) {
  const words = String(value).match(/[\p{L}]{3,}/gu)?.length || 0;
  const citations = String(value).match(/\bchapter\s*\d+|\(chapter/gi)?.length || 0;
  return Math.min(String(value).length, 1200) + words * 3 - citations * 80;
}
