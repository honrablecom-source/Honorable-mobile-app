import { readFile, access } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const manifest = JSON.parse(await readFile(resolve(root, "manifest.json"), "utf8"));
const files = [
  manifest.background.service_worker,
  manifest.side_panel.default_path,
  ...manifest.content_scripts.flatMap((entry) => [...entry.js, ...(entry.css || [])])
];
await Promise.all(files.map((file) => access(resolve(root, file))));
if (manifest.manifest_version !== 3) throw new Error("Manifest must use MV3");
if (!manifest.permissions.includes("sidePanel")) throw new Error("sidePanel permission missing");
const schema = await readFile(resolve(root, "database/schema.sql"), "utf8");
for (const table of ["novels", "source_bindings", "characters", "character_aliases", "sync_runs"]) {
  if (!schema.includes(`TABLE IF NOT EXISTS ${table}`)) throw new Error(`Missing Neon table: ${table}`);
}
if (!schema.includes("characters_novel_unique_name_idx")) throw new Error("Missing novel-scoped character uniqueness constraint");
console.log(`Validated manifest, ${files.length} referenced files, and the normalized Neon schema.`);
