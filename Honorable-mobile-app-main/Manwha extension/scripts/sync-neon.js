import pg from "pg";
import { readFile } from "node:fs/promises";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const { Client } = pg;
const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const connectionString = process.env.NEON_DATABASE_URL;
if (!connectionString) throw new Error("NEON_DATABASE_URL is required and is never stored by this script.");

const TOP_NOVELS = 1000;
const knownWikis = [
  ["reverend-insanity", "reverend-insanity.fandom.com"],
  ["lord-of-the-mysteries", "lordofthemysteries.fandom.com"],
  ["shadow-slave", "shadowslave.fandom.com"],
  ["omniscient-readers-viewpoint", "omniscient-readers-viewpoint.fandom.com"],
  ["the-beginning-after-the-end", "tbate.fandom.com"],
  ["the-legendary-mechanic", "the-legendary-mechanic.fandom.com"],
  ["the-authors-pov", "the-authors-pov.fandom.com"]
];
const curatedRoles = new Map(Object.entries({
  "reverend-insanity:Fang Yuan": "MC",
  "lord-of-the-mysteries:Klein Moretti": "MC",
  "shadow-slave:Sunless": "MC", "shadow-slave:Nephis": "FMC",
  "omniscient-readers-viewpoint:Kim Dokja": "MC", "omniscient-readers-viewpoint:Yoo Joonghyuk": "MC", "omniscient-readers-viewpoint:Han Sooyoung": "FMC",
  "the-beginning-after-the-end:Arthur Leywin": "MC", "the-beginning-after-the-end:Tessia Eralith": "FMC",
  "the-legendary-mechanic:Han Xiao": "MC",
  "the-authors-pov:Ren Dover": "MC", "the-authors-pov:Amanda Stern": "FMC"
}));
const roleRules = [["FMC", /female protagonists?|female main characters?/i], ["MC", /protagonists?|main characters?/i],
  ["ANT", /antagonists?|villains?/i], ["SUPPORT", /supporting characters?|minor characters?/i]];
const normalize = (value) => String(value || "").toLocaleLowerCase().replace(/[^\p{L}\p{N}]+/gu, " ").trim();
const cleanHtml = (value) => String(value || "").replace(/<[^>]*>/g, " ").replace(/&amp;/g, "&").replace(/&#39;|&apos;/g, "'")
  .replace(/&quot;/g, '"').replace(/\s+/g, " ").trim();

async function fetchTopNovels() {
  const novels = new Map();
  for (let page = 1; novels.size < TOP_NOVELS && page <= 60; page += 1) {
    // NovelFire serves a browser challenge to server-side clients. Jina Reader
    // retrieves the same public page and returns deterministic Markdown.
    const sourceUrl = `https://novelfire.net/search?page=${page}`;
    const response = await fetch(`https://r.jina.ai/${sourceUrl}`, { headers: { "User-Agent": "NovelLens catalog sync/1.0" } });
    if (!response.ok) throw new Error(`NovelFire page ${page}: HTTP ${response.status}`);
    const html = await response.text();
    const markdownPattern = /\]\(https:\/\/novelfire\.net\/book\/([^\s?#/)]+)\s+"([^"]+)"\)/gi;
    const htmlPattern = /<a\b[^>]*href=["'](?:https:\/\/novelfire\.net)?\/book\/([^"'?#/]+)["'][^>]*>([\s\S]*?)<\/a>/gi;
    const matches = [...html.matchAll(markdownPattern)];
    if (!matches.length) matches.push(...html.matchAll(htmlPattern));
    for (const match of matches) {
      const id = decodeURIComponent(match[1]);
      let title = cleanHtml(match[2]).replace(/\s+Rank\s+\d+[\s\S]*$/i, "").replace(/\s+\d+[\d,.]*\s+Chapters?[\s\S]*$/i, "").trim();
      if (!title || title.length > 250) title = id.split("-").map((word) => word[0]?.toUpperCase() + word.slice(1)).join(" ");
      if (!novels.has(id)) novels.set(id, { id, title, rank: novels.size + 1 });
      if (novels.size >= TOP_NOVELS) break;
    }
    await new Promise((resolveDelay) => setTimeout(resolveDelay, 150));
    process.stdout.write(`\rCatalog: ${novels.size}/${TOP_NOVELS}`);
  }
  process.stdout.write("\n");
  if (novels.size < TOP_NOVELS) throw new Error(`NovelFire returned only ${novels.size} unique novels.`);
  return [...novels.values()].slice(0, TOP_NOVELS);
}

async function wikiApi(hostname, params) {
  const url = new URL(`https://${hostname}/api.php`);
  Object.entries({ action: "query", format: "json", formatversion: "2", origin: "*", ...params })
    .forEach(([key, value]) => url.searchParams.set(key, value));
  const response = await fetch(url, { headers: { "User-Agent": "NovelLens character sync/1.0" } });
  if (!response.ok) throw new Error(`${hostname}: HTTP ${response.status}`);
  const result = await response.json();
  if (result.error) throw new Error(`${hostname}: ${result.error.info}`);
  return result;
}

async function fetchCharacters(novelId, hostname) {
  const pageIds = new Set(), visited = new Set(), queue = [{ title: "Category:Characters", depth: 0 }];
  while (queue.length && pageIds.size < 5000) {
    const category = queue.shift();
    if (visited.has(category.title) || category.depth > 4) continue;
    visited.add(category.title);
    let continuation = {};
    do {
      const data = await wikiApi(hostname, { list: "categorymembers", cmtitle: category.title, cmtype: "page|subcat", cmlimit: "max", ...continuation });
      for (const member of data.query?.categorymembers || []) {
        if (member.ns === 0) pageIds.add(member.pageid);
        if (member.ns === 14) queue.push({ title: member.title, depth: category.depth + 1 });
      }
      continuation = data.continue || null;
    } while (continuation);
  }
  const characters = [];
  const ids = [...pageIds];
  for (let start = 0; start < ids.length; start += 50) {
    const data = await wikiApi(hostname, { pageids: ids.slice(start, start + 50).join("|"), prop: "pageimages|categories|extracts",
      piprop: "thumbnail|original", pithumbsize: "500", cllimit: "max", exintro: "1", explaintext: "1", exsectionformat: "plain" });
    for (const page of data.query?.pages || []) {
      const categories = (page.categories || []).map((item) => item.title.replace(/^Category:/, ""));
      const categoryRole = roleRules.find(([, pattern]) => pattern.test(categories.join(" | ")))?.[0];
      characters.push({ novelId, pageId: page.pageid, title: page.title, role: curatedRoles.get(`${novelId}:${page.title}`) || categoryRole || "CHARACTER",
        imageUrl: page.thumbnail?.source || page.original?.source || null, summary: String(page.extract || "").slice(0, 4000) || null, categories });
    }
  }
  return characters;
}

const client = new Client({ connectionString, ssl: { rejectUnauthorized: true } });
await client.connect();
let runId;
try {
  await client.query(await readFile(resolve(root, "database/schema.sql"), "utf8"));
  runId = (await client.query("INSERT INTO sync_runs(source) VALUES('novelfire+fandom') RETURNING id")).rows[0].id;
  const novels = await fetchTopNovels();
  for (const novel of novels) {
    await client.query(`INSERT INTO novels(id,title,normalized_title,novel_fire_rank,source_url,updated_at)
      VALUES($1,$2,$3,$4,$5,now()) ON CONFLICT(id) DO UPDATE SET title=excluded.title, normalized_title=excluded.normalized_title,
      novel_fire_rank=excluded.novel_fire_rank, source_url=excluded.source_url, updated_at=now()`,
      [novel.id, novel.title, normalize(novel.title), novel.rank, `https://novelfire.net/book/${novel.id}`]);
  }
  let totalCharacters = 0;
  for (const [novelId, hostname] of knownWikis) {
    const exists = await client.query("SELECT 1 FROM novels WHERE id=$1", [novelId]);
    if (!exists.rowCount) continue;
    const characters = await fetchCharacters(novelId, hostname);
    await client.query("BEGIN");
    try {
      await client.query("UPDATE novels SET wiki_hostname=$2,wiki_verified=true,character_count=$3,updated_at=now() WHERE id=$1", [novelId, hostname, characters.length]);
      for (const character of characters) {
        const key = `${novelId}:${character.pageId}`;
        await client.query(`INSERT INTO characters(character_key,novel_id,wiki_page_id,page_title,display_name,normalized_name,role_code,image_url,summary,wiki_path,source_categories,fetched_at)
          VALUES($1,$2,$3,$4,$4,$5,$6,$7,$8,$9,$10,now()) ON CONFLICT(novel_id,normalized_name) DO UPDATE SET page_title=excluded.page_title,
          display_name=excluded.display_name,role_code=excluded.role_code,image_url=COALESCE(excluded.image_url,characters.image_url),
          summary=excluded.summary,wiki_path=excluded.wiki_path,source_categories=excluded.source_categories,fetched_at=now()`,
          [key, novelId, character.pageId, character.title, normalize(character.title), character.role, character.imageUrl, character.summary,
            `/wiki/${encodeURIComponent(character.title.replace(/ /g, "_"))}`, JSON.stringify(character.categories)]);
      }
      await client.query("COMMIT");
    } catch (error) { await client.query("ROLLBACK"); throw error; }
    totalCharacters += characters.length;
    console.log(`${novelId}: ${characters.length} characters`);
  }
  await client.query("UPDATE sync_runs SET completed_at=now(),novel_count=$2,character_count=$3,status='complete' WHERE id=$1", [runId, novels.length, totalCharacters]);
  const totals = (await client.query("SELECT count(*)::int novels, sum(character_count)::int characters FROM novels")).rows[0];
  console.log(`Neon complete: ${totals.novels} novels, ${totals.characters || 0} characters.`);
} catch (error) {
  if (runId) await client.query("UPDATE sync_runs SET completed_at=now(),status='failed',error=$2 WHERE id=$1", [runId, error.message]).catch(() => {});
  throw error;
} finally { await client.end(); }
