import pg from "pg";
import { readFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const connectionString = process.env.NEON_DATABASE_URL;
if (!connectionString) throw new Error("Set NEON_DATABASE_URL without saving it in the project.");

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const client = new pg.Client({ connectionString, ssl: { rejectUnauthorized: true } });
await client.connect();
try {
  const before = await client.query(`SELECT count(*)::int AS count FROM (
    SELECT novel_id, normalized_name FROM characters GROUP BY novel_id, normalized_name HAVING count(*) > 1
  ) duplicates`);
  const characterCountBefore = Number((await client.query("SELECT count(*) FROM characters")).rows[0].count);
  await client.query("BEGIN");
  await client.query(await readFile(resolve(root, "database/schema.sql"), "utf8"));
  await client.query("COMMIT");
  const verification = await client.query(`SELECT
    (SELECT count(*)::int FROM novels) AS novels,
    (SELECT count(*)::int FROM characters) AS characters,
    (SELECT count(*)::int FROM (SELECT novel_id, normalized_name FROM characters GROUP BY novel_id, normalized_name HAVING count(*) > 1) d) AS duplicates`);
  const row = verification.rows[0];
  console.log(`Neon verified: ${row.novels} novels, ${row.characters} characters (${characterCountBefore - row.characters} non-character/duplicate records removed), ${row.duplicates} duplicate novel/name pairs (before: ${before.rows[0].count}).`);
} catch (error) {
  await client.query("ROLLBACK").catch(() => {});
  throw error;
} finally {
  await client.end();
}
