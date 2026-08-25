const normalize = (value) => String(value || "").toLowerCase().replace(/\bwiki\b/g, "").replace(/[^a-z0-9]+/g, " ").trim();
function similarity(expected, actual) {
  const a = new Set(normalize(expected).split(" ").filter(Boolean));
  const b = new Set(normalize(actual).split(" ").filter(Boolean));
  if (!a.size || !b.size) return 0;
  return [...a].filter((word) => b.has(word)).length / Math.max(a.size, b.size);
}
function candidates(profile) {
  const compact = profile.id.replace(/-/g, "");
  const values = [profile.id, compact];
  if (profile.id.startsWith("the-")) values.push(profile.id.slice(4), compact.slice(3));
  return [...new Set(values)].map((slug) => `${slug}.fandom.com`);
}
async function probe(hostname, expectedTitle) {
  const url = new URL(`https://${hostname}/api.php`);
  url.search = new URLSearchParams({ action: "query", meta: "siteinfo", siprop: "general", format: "json", origin: "*" });
  const response = await fetch(url);
  if (!response.ok) return null;
  const siteName = (await response.json()).query?.general?.sitename || "";
  return similarity(expectedTitle, siteName) >= .6 ? { hostname, siteName } : null;
}
export async function resolveWiki(profile) {
  if (profile.wiki) return profile.wiki;
  for (const hostname of candidates(profile)) {
    if (await probe(hostname, profile.title).catch(() => null)) {
      return { provider: "mediawiki", hostname, apiPath: "/api.php", characterCategories: ["Characters"] };
    }
  }
  return null;
}
