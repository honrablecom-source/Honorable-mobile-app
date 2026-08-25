import pg from "pg";
import { readFile } from "node:fs/promises";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const { Client } = pg;
const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const connectionString = process.env.NEON_DATABASE_URL;
if (!connectionString) throw new Error("NEON_DATABASE_URL is required and is never stored.");
const TARGET = 100_000;
const DISCOVERY_VERSION = 4;
const normalize = (value) => String(value || "").toLowerCase().replace(/\b(wiki|novel|webnovel)\b/g, " ").replace(/[^a-z0-9]+/g, " ").trim();
const stripWiki = (value) => String(value || "").replace(/<ref\b[^>]*>[\s\S]*?<\/ref>|<ref\b[^>]*\/\s*>/gi, " ")
  .replace(/\[\[(?:[^\]|]+\|)?([^\]]+)\]\]/g, "$1").replace(/\{\{[^{}]*\}\}/g, " ").replace(/'{2,}/g, "").replace(/<[^>]+>/g, " ").replace(/\s+/g, " ").trim();
const curated = new Map([
  ["the-authors-pov", "the-authors-pov.fandom.com"], ["supreme-magus", "supreme-magus.fandom.com"],
  ["the-primal-hunter", "the-primal-hunter.fandom.com"], ["the-perfect-run", "the-perfect-run.fandom.com"],
  ["mother-of-learning", "mother-of-learning.fandom.com"], ["the-innkeeper", "the-innkeeper.fandom.com"],
  ["cultivation-online", "cultivation-online.fandom.com"], ["the-mech-touch", "the-mech-touch.fandom.com"],
  ["chrysalis", "chrysalis.fandom.com"], ["against-the-gods", "ni-tian-xie-shen-against-the-gods.fandom.com"],
  ["martial-peak", "martial-peak-mp.fandom.com"], ["the-beginning-after-the-end", "tbate.fandom.com"],
  ["lord-of-the-mysteries", "lordofthemysteries.fandom.com"], ["shadow-slave", "shadowslave.fandom.com"]
]);
const roleOverrides = new Map([
  ["Fang Yuan", "MC"], ["Klein Moretti", "MC"], ["Sunless", "MC"], ["Nephis", "FMC"],
  ["Kim Dokja", "MC"], ["Arthur Leywin", "MC"], ["Han Xiao", "MC"], ["Ren Dover", "MC"]
]);

function similarity(a, b) {
  const left = new Set(normalize(a).split(" ").filter(Boolean)), right = new Set(normalize(b).split(" ").filter(Boolean));
  if (!left.size || !right.size) return 0;
  const overlap = [...left].filter((word) => right.has(word)).length;
  return (2 * overlap) / (left.size + right.size);
}
function hostCandidates(id) {
  return [`${id.replace(/-/g, "")}.fandom.com`];
}
async function api(hostname, params, timeout = 15_000, attempt = 0) {
  const url = new URL(`https://${hostname}/api.php`);
  Object.entries({ action: "query", format: "json", formatversion: "2", origin: "*", ...params }).forEach(([key, value]) => url.searchParams.set(key, value));
  const response = await fetch(url, { signal: AbortSignal.timeout(timeout), headers: { "User-Agent": "NovelLens verified character sync/1.0" } });
  if (response.status === 429 && attempt < 3) {
    await new Promise((resolveDelay) => setTimeout(resolveDelay, 2500 * (attempt + 1)));
    return api(hostname, params, timeout, attempt + 1);
  }
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  const data = await response.json();
  if (data.error) throw new Error(data.error.info);
  return data;
}
async function verifyWiki(novel) {
  const hosts = [...new Set([curated.get(novel.id), ...hostCandidates(novel.id)].filter(Boolean))];
  for (const hostname of hosts) {
    try {
      const data = await api(hostname, { meta: "siteinfo", siprop: "general" }, 3_500);
      const siteName = data.query?.general?.sitename || "";
      if (similarity(novel.title, siteName) >= .72) return hostname;
    } catch { /* Candidate does not exist or is unavailable. */ }
  }
  return null;
}
async function pageIdsFromTree(hostname) {
  const pageIds = new Set(), visited = new Set(), queue = [{ title: "Category:Characters", depth: 0 }];
  while (queue.length && pageIds.size < 5000) {
    const current = queue.shift();
    if (visited.has(current.title) || current.depth > 4) continue;
    visited.add(current.title);
    let continuation = {};
    do {
      const data = await api(hostname, { list: "categorymembers", cmtitle: current.title, cmtype: "page|subcat", cmlimit: "max", ...continuation });
      for (const member of data.query?.categorymembers || []) {
        if (member.ns === 0) pageIds.add(member.pageid);
        if (member.ns === 14) queue.push({ title: member.title, depth: current.depth + 1 });
      }
      continuation = data.continue || null;
    } while (continuation);
  }
  return [...pageIds];
}
function parseFields(wikitext) {
  const fields = {};
  for (const line of String(wikitext || "").split("\n")) {
    const match = line.match(/^\s*\|\s*([^=|]{1,60})\s*=\s*(.+)$/);
    if (!match) continue;
    const key = stripWiki(match[1]), value = stripWiki(match[2]);
    if (key && value && value.length <= 1000 && !/^(image|caption|color|width|template)$/i.test(key)) fields[key] = value;
    if (Object.keys(fields).length >= 60) break;
  }
  return fields;
}
function roleFor(title, categories) {
  if (roleOverrides.has(title)) return roleOverrides.get(title);
  const text = categories.join(" | ");
  if (/female protagonists?|female main characters?/i.test(text)) return "FMC";
  if (/protagonists?|main characters?/i.test(text)) return "MC";
  if (/antagonists?|villains?/i.test(text)) return "ANT";
  if (/supporting characters?|minor characters?/i.test(text)) return "SUPPORT";
  return "CHARACTER";
}
async function fetchCharacters(novelId, hostname) {
  const ids = await pageIdsFromTree(hostname), characters = [];
  for (let start = 0; start < ids.length; start += 25) {
    const data = await api(hostname, { pageids: ids.slice(start, start + 25).join("|"),
      prop: "pageimages|categories|extracts|revisions", piprop: "thumbnail|original", pithumbsize: "600", cllimit: "max",
      exintro: "1", explaintext: "1", exsectionformat: "plain", rvprop: "content", rvslots: "main" }, 30_000);
    for (const page of data.query?.pages || []) {
      const categories = (page.categories || []).map((item) => item.title.replace(/^Category:/, ""));
      const wikitext = page.revisions?.[0]?.slots?.main?.content || "";
      characters.push({ pageId: page.pageid, title: page.title, role: roleFor(page.title, categories), categories,
        image: page.thumbnail?.source || page.original?.source || null, summary: String(page.extract || "").slice(0, 5000) || null,
        fields: parseFields(wikitext) });
    }
  }
  return characters;
}

const client = new Client({ connectionString, ssl: { rejectUnauthorized: true } });
await client.connect();
try {
  await client.query(await readFile(resolve(root, "database/schema.sql"), "utf8"));
  let total = Number((await client.query("SELECT count(*) FROM characters")).rows[0].count);
  const size = (await client.query("SELECT pg_size_pretty(pg_database_size(current_database())) size")).rows[0].size;
  console.log(`Verified Neon: ${total} characters; database size ${size}.`);
  const unchecked = (await client.query(`SELECT id,title,wiki_hostname,wiki_checked_at FROM novels
    WHERE wiki_hostname IS NULL AND wiki_discovery_version < $1 ORDER BY novel_fire_rank NULLS LAST, id`, [DISCOVERY_VERSION])).rows;
  const novels = [];
  const discoveryBatchSize = 20;
  for (let start = 0; start < unchecked.length && total < TARGET; start += discoveryBatchSize) {
    const batch = unchecked.slice(start, start + discoveryBatchSize);
    const matches = await Promise.all(batch.map(async (novel) => ({ novel, hostname: await verifyWiki(novel) })));
    for (const match of matches) {
      if (match.hostname) {
        await client.query("UPDATE novels SET wiki_hostname=$2,wiki_verified=true,wiki_checked_at=now(),wiki_discovery_version=$3 WHERE id=$1", [match.novel.id, match.hostname, DISCOVERY_VERSION]);
        novels.push({ ...match.novel, wiki_hostname: match.hostname });
      } else {
        await client.query("UPDATE novels SET wiki_checked_at=now(),wiki_discovery_version=$2 WHERE id=$1", [match.novel.id, DISCOVERY_VERSION]);
      }
    }
    console.log(`Wiki discovery: ${Math.min(start + discoveryBatchSize, unchecked.length)}/${unchecked.length}; verified ${novels.length}`);
  }
  const pending = (await client.query(`SELECT id,title,wiki_hostname,wiki_checked_at FROM novels
    WHERE wiki_hostname IS NOT NULL AND character_count=0 ORDER BY id`)).rows;
  const queuedIds = new Set(novels.map((novel) => novel.id));
  for (const novel of pending) if (!queuedIds.has(novel.id)) novels.push(novel);
  for (const novel of novels) {
    if (total >= TARGET) break;
    const hostname = novel.wiki_hostname;
    let characters;
    try { characters = await fetchCharacters(novel.id, hostname); } catch (error) {
      console.log(`${novel.id}: skipped (${error.message})`); continue;
    }
    if (!characters.length) { await client.query("UPDATE novels SET wiki_checked_at=now() WHERE id=$1", [novel.id]); continue; }
    await client.query("BEGIN");
    try {
      for (const item of characters) {
        const key = `${novel.id}:${item.pageId}`;
        await client.query(`INSERT INTO characters(character_key,novel_id,wiki_page_id,page_title,display_name,normalized_name,role_code,image_url,summary,profile_fields,wiki_path,source_categories,fetched_at)
          VALUES($1,$2,$3,$4,$4,$5,$6,$7,$8,$9,$10,$11,now()) ON CONFLICT(novel_id,normalized_name) DO UPDATE SET display_name=excluded.display_name,
          role_code=excluded.role_code,image_url=COALESCE(excluded.image_url,characters.image_url),summary=COALESCE(excluded.summary,characters.summary),
          profile_fields=excluded.profile_fields,wiki_path=excluded.wiki_path,source_categories=excluded.source_categories,fetched_at=now()`,
          [key, novel.id, item.pageId, item.title, normalize(item.title), item.role, item.image, item.summary, JSON.stringify(item.fields),
            `/wiki/${encodeURIComponent(item.title.replace(/ /g, "_"))}`, JSON.stringify(item.categories)]);
      }
      const count = Number((await client.query("SELECT count(*) FROM characters WHERE novel_id=$1", [novel.id])).rows[0].count);
      await client.query("UPDATE novels SET wiki_hostname=$2,wiki_verified=true,wiki_checked_at=now(),character_count=$3,updated_at=now() WHERE id=$1", [novel.id, hostname, count]);
      await client.query("COMMIT");
    } catch (error) { await client.query("ROLLBACK"); throw error; }
    total = Number((await client.query("SELECT count(*) FROM characters")).rows[0].count);
    console.log(`${novel.id}: ${characters.length} wiki characters; Neon total ${total}/${TARGET}`);
  }
  const final = (await client.query(`SELECT (SELECT count(*)::int FROM novels) novels,
    (SELECT count(*)::int FROM characters) characters, pg_size_pretty(pg_database_size(current_database())) size`)).rows[0];
  console.log(`Final Neon verification: ${final.novels} novels, ${final.characters} characters, ${final.size}.`);
  if (final.characters < TARGET) process.exitCode = 2;
} finally { await client.end(); }
