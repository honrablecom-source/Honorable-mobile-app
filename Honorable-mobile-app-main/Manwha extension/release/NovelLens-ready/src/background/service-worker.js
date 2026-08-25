import { db } from "../storage/database.js";
import { MediaWikiClient } from "../wiki/mediawiki-client.js";
import { resolveWiki } from "../wiki/wiki-resolver.js";

const INDEX_MAX_AGE = 7 * 24 * 60 * 60 * 1000;
const DETAILS_MAX_AGE = 30 * 24 * 60 * 60 * 1000;
const CHARACTER_INDEX_VERSION = 2;
const DETAILS_PARSER_VERSION = 2;

chrome.runtime.onInstalled.addListener(() => {
  chrome.sidePanel.setPanelBehavior({ openPanelOnActionClick: true }).catch(() => {});
  syncNovelFireCatalog().catch(() => {});
});

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  handleMessage(message, sender).then(sendResponse).catch((error) => sendResponse({ ok: false, error: error.message }));
  return true;
});

async function handleMessage(message, sender) {
  if (message.type === "NOVEL_READY") return getCharacters(message.profile);
  if (message.type === "CHARACTER_SELECTED") {
    await chrome.storage.local.set({ selectedCharacterId: message.characterId, selectedNovel: message.profile });
    if (sender.tab?.id != null) await chrome.sidePanel.open({ tabId: sender.tab.id });
    return { ok: true };
  }
  if (message.type === "GET_SELECTION") return getSelection();
  if (message.type === "UNADDED_NAMES") return processUnadded(message.profile, message.candidates);
  if (message.type === "LINK_WIKI") return linkWiki(message.profile, message.hostname);
  return { ok: false, error: "Unknown message" };
}

async function processUnadded(profile, candidates) {
  if (!profile?.id || !Array.isArray(candidates)) return { ok: true, characters: [] };
  const now = Date.now();
  const existing = new Map((await db.pendingByNovel(profile.id)).map((item) => [item.normalizedName, item]));
  const queued = [];
  for (const candidate of candidates.slice(0, 40)) {
    const prior = existing.get(candidate.normalizedName);
    const item = {
      id: `${profile.id}:${candidate.normalizedName}`, novelId: profile.id, name: candidate.name,
      normalizedName: candidate.normalizedName, sightings: (prior?.sightings || 0) + candidate.count,
      status: prior?.status || "pending", firstSeenAt: prior?.firstSeenAt || now, lastSeenAt: now
    };
    await db.put("unadded", item);
    if (item.status === "pending" && item.sightings >= 2) queued.push(item);
  }
  if (!profile.wiki) return { ok: true, characters: [] };
  const client = new MediaWikiClient(profile.wiki);
  const added = [];
  for (const item of queued.slice(0, 8)) {
    try {
      const character = await client.findCharacter(profile.id, item.name);
      if (character) {
        await db.put("characters", character);
        await db.put("unadded", { ...item, status: "added", characterId: character.id, checkedAt: Date.now() });
        added.push(character);
      } else await db.put("unadded", { ...item, status: "unverified", checkedAt: Date.now() });
    } catch { /* Keep pending so a temporary wiki failure can be retried later. */ }
  }
  return { ok: true, characters: added };
}

async function getCharacters(profile) {
  const savedProfile = await db.get("novels", profile.id);
  if (!profile.wiki && savedProfile?.wiki) profile.wiki = savedProfile.wiki;
  if (!profile.wiki) profile.wiki = await resolveWiki(profile);
  await db.put("novels", { ...profile, updatedAt: Date.now() });
  await chrome.storage.local.set({ activeNovel: profile });
  if (!profile.wiki) return { ok: true, noWiki: true, profile, characters: [] };
  const cachedResult = dedupeCharacters(await db.byNovel(profile.id));
  const cached = cachedResult.characters;
  if (cachedResult.duplicateIds.length) await db.deleteMany("characters", cachedResult.duplicateIds);
  const sync = await db.get("sync", profile.id);
  if (cached.length && sync?.version === CHARACTER_INDEX_VERSION && Date.now() - sync.updatedAt < INDEX_MAX_AGE) return { ok: true, characters: cached, cached: true, profile };

  try {
    const client = new MediaWikiClient(profile.wiki);
    const imported = await client.importCategory(profile.id, profile.wiki.characterCategories[0]);
    const { characters: records } = dedupeCharacters(imported);
    if (records.length) {
      await db.putMany("characters", records);
      await db.put("sync", { novelId: profile.id, updatedAt: Date.now(), count: records.length, version: CHARACTER_INDEX_VERSION });
      return { ok: true, characters: records, cached: false, profile };
    }
    throw new Error("The wiki character category was empty");
  } catch (error) {
    if (cached.length) return { ok: true, characters: cached, cached: true, warning: error.message, profile };
    throw error;
  }
}

async function getSelection() {
  const { selectedCharacterId, selectedNovel, activeNovel } = await chrome.storage.local.get(["selectedCharacterId", "selectedNovel", "activeNovel"]);
  if (!selectedCharacterId || !selectedNovel || selectedNovel.id !== activeNovel?.id) {
    const pending = activeNovel?.id ? await db.pendingByNovel(activeNovel.id) : [];
    return { ok: true, empty: true, activeNovel, unaddedCount: pending.filter((item) => item.status !== "added").length };
  }
  const character = await db.get("characters", selectedCharacterId);
  if (!character) return { ok: false, error: "Character is no longer in the local index." };
  const cached = await db.get("details", selectedCharacterId);
  if (cached?.parserVersion === DETAILS_PARSER_VERSION && Date.now() - cached.fetchedAt < DETAILS_MAX_AGE) {
    refreshDetails(character, selectedNovel).catch(() => {});
    return { ok: true, character, profile: selectedNovel, details: cached, cached: true, inDatabase: true };
  }
  try {
    const details = await refreshDetails(character, selectedNovel);
    return { ok: true, character, profile: selectedNovel, details, cached: false, inDatabase: true };
  } catch (error) {
    if (cached) return { ok: true, character, profile: selectedNovel, details: cached, cached: true, warning: error.message, inDatabase: true };
    return { ok: true, character, profile: selectedNovel, cached: true, warning: error.message, inDatabase: true, details: {
      characterId: character.id, displayName: character.displayName, aliases: character.aliases || [],
      imageUrl: character.imageUrl, summary: "Detailed wiki information is temporarily unavailable.", fields: {},
      sourcePageTitle: character.pageTitle, fetchedAt: Date.now()
    } };
  }
}

function dedupeCharacters(records) {
  const kept = new Map();
  const duplicateIds = [];
  const score = (record) => (record.imageUrl ? 4 : 0) + (record.summary ? 2 : 0) + Object.keys(record.fields || {}).length;
  for (const record of records) {
    const name = String(record.normalizedName || record.displayName || "").normalize("NFKC").trim().toLocaleLowerCase();
    const key = `${record.novelId}\u0000${name}`;
    const current = kept.get(key);
    if (!current) kept.set(key, record);
    else if (score(record) > score(current)) {
      duplicateIds.push(current.id);
      kept.set(key, record);
    } else duplicateIds.push(record.id);
  }
  return { characters: [...kept.values()], duplicateIds: [...new Set(duplicateIds)] };
}

async function refreshDetails(character, profile) {
  const details = { ...await new MediaWikiClient(profile.wiki).getDetails(character), parserVersion: DETAILS_PARSER_VERSION };
  if (details.aliases?.length) await db.put("characters", { ...character, aliases: details.aliases, imageUrl: details.imageUrl || character.imageUrl, updatedAt: Date.now() });
  await db.put("details", details);
  return details;
}

async function linkWiki(profile, hostname) {
  const cleanHost = String(hostname || "").trim().toLowerCase().replace(/^https?:\/\//, "").split("/")[0];
  if (!/^[a-z0-9.-]+\.fandom\.com$/.test(cleanHost)) throw new Error("Enter a valid fandom.com wiki address.");
  const wiki = { provider: "mediawiki", hostname: cleanHost, apiPath: "/api.php", characterCategories: ["Characters"] };
  const client = new MediaWikiClient(wiki);
  await client.request({ action: "query", meta: "siteinfo" });
  const updated = { ...profile, wiki, updatedAt: Date.now() };
  await db.put("novels", updated);
  await chrome.storage.local.set({ activeNovel: updated });
  return getCharacters(updated);
}

async function syncNovelFireCatalog() {
  const { novelFireCatalogSyncAt } = await chrome.storage.local.get("novelFireCatalogSyncAt");
  if (novelFireCatalogSyncAt && Date.now() - novelFireCatalogSyncAt < INDEX_MAX_AGE) return;
  const entries = new Map();
  for (let page = 1; page <= 10; page += 1) {
    const response = await fetch(`https://novelfire.net/search?page=${page}`);
    if (!response.ok) break;
    const html = await response.text();
    const pattern = /<a\b[^>]*href=["'](?:https:\/\/novelfire\.net)?\/book\/([^"'?#/]+)["'][^>]*>([\s\S]*?)<\/a>/gi;
    for (const match of html.matchAll(pattern)) {
      const title = match[2].replace(/<[^>]+>/g, " ").replace(/&amp;/g, "&").replace(/\s+/g, " ").trim();
      if (title) entries.set(decodeURIComponent(match[1]), title);
    }
  }
  if (entries.size) await chrome.storage.local.set({
    novelFireCatalog: [...entries].map(([id, title]) => ({ id, title })), novelFireCatalogSyncAt: Date.now()
  });
}
