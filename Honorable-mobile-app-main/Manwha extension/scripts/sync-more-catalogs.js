import pg from "pg";
import { readFile } from "node:fs/promises";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const { Client } = pg;
const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const connectionString = process.env.NEON_DATABASE_URL;
if (!connectionString) throw new Error("NEON_DATABASE_URL is required and is never stored.");
const titleFromSlug = (slug) => slug.split("-").filter(Boolean).map((word) => word === "i" ? "I" : word[0].toUpperCase() + word.slice(1)).join(" ");
const normalize = (value) => String(value).toLocaleLowerCase().replace(/[^\p{L}\p{N}]+/gu, " ").trim();
async function reader(url) {
  const response = await fetch(`https://r.jina.ai/${url}`, { signal: AbortSignal.timeout(60_000), headers: { "User-Agent": "NovelLens public catalog sync/1.0" } });
  if (!response.ok) throw new Error(`${url}: HTTP ${response.status}`);
  return response.text();
}
function unique(source, pattern, urlFor) {
  const records = new Map();
  for (const match of source.matchAll(pattern)) {
    const slug = match[1].toLowerCase();
    records.set(slug, { slug, title: match[2]?.trim() || titleFromSlug(slug), url: urlFor(slug, match) });
  }
  return [...records.values()];
}
async function catalogs() {
  const [novelFullSource, freeWebNovelSource] = await Promise.all([
    reader("https://novelfull.com/sitemap.xml"), reader("https://freewebnovel.com/sitemap.xml")
  ]);
  const novelFull = unique(novelFullSource, /https:\/\/novelfull\.com\/([a-z0-9][a-z0-9-]+)\.html/gi,
    (slug) => `https://novelfull.com/${slug}.html`);
  const freeWebNovel = unique(freeWebNovelSource, /https:\/\/freewebnovel\.com\/novel\/([a-z0-9][a-z0-9-]+)/gi,
    (slug) => `https://freewebnovel.com/novel/${slug}`);
  const ranobes = new Map();
  for (const period of ["all_time", "three_years_2", "three_years", "two_years_2", "two_years", "bi_annual", "quarter", "month"]) {
    const source = await reader(`https://ranobes.net/ranking/${period}/`);
    const pattern = /## \[([^\]]+)\]\(https:\/\/ranobes\.net\/novels\/[^\s/)]+-([a-z0-9][a-z0-9-]+)\.html\)/gi;
    for (const match of source.matchAll(pattern)) {
      const slug = match[2].replace(/-v\d+$/i, "");
      ranobes.set(slug, { slug, title: match[1].trim(), url: match[0].match(/https:[^)]+/)?.[0] || `https://ranobes.net/novels/${match[2]}.html` });
    }
  }
  return { "novelfull.com": novelFull, "freewebnovel.com": freeWebNovel, "ranobes.net": [...ranobes.values()] };
}

const client = new Client({ connectionString, ssl: { rejectUnauthorized: true } });
await client.connect();
try {
  await client.query(await readFile(resolve(root, "database/schema.sql"), "utf8"));
  const sources = await catalogs();
  for (const [site, records] of Object.entries(sources)) {
    let rank = 0;
    for (const record of records) {
      rank += 1;
      await client.query(`INSERT INTO novels(id,title,normalized_title,source_site,source_url,created_at,updated_at)
        VALUES($1,$2,$3,$4,$5,now(),now()) ON CONFLICT(id) DO NOTHING`, [record.slug, record.title, normalize(record.title), site, record.url]);
      await client.query(`INSERT INTO source_bindings(source_site,source_slug,novel_id,source_url,source_rank,updated_at)
        VALUES($1,$2,$2,$3,$4,now()) ON CONFLICT(source_site,source_slug) DO UPDATE SET source_url=excluded.source_url,source_rank=excluded.source_rank,updated_at=now()`,
        [site, record.slug, record.url, rank]);
    }
    console.log(`${site}: ${records.length} source bindings`);
  }
  const totals = (await client.query(`SELECT (SELECT count(*)::int FROM novels) novels,
    (SELECT count(*)::int FROM source_bindings) bindings, (SELECT count(*)::int FROM characters) characters`)).rows[0];
  console.log(`Catalog complete: ${totals.novels} novels, ${totals.bindings} bindings, ${totals.characters} characters.`);
} finally { await client.end(); }
