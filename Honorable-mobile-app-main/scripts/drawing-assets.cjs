const fs = require('node:fs');
const path = require('node:path');
const {createHash} = require('node:crypto');
const MANIFEST = 'noble-drawing-manifest.json';
const requiredSources = ['editor.jsx', 'honorable.css', 'build.mjs', 'package.json', 'package-lock.json'];
const requiredAssets = ['index.html', 'config.js', 'editor.js', 'editor.css'];
const digest = bytes => createHash('sha256').update(bytes).digest('hex');
const equal = (a,b) => JSON.stringify(a) === JSON.stringify(b);
function describe(file) {
  const info = fs.lstatSync(file);
  if (!info.isFile() || info.isSymbolicLink()) throw Error('Drawing inputs and assets must be regular files, not links.');
  const bytes = fs.readFileSync(file);
  return {bytes: bytes.length, sha256: digest(bytes)};
}
function walk(root, prefix = '') {
  const rootInfo = fs.lstatSync(root);
  if (!rootInfo.isDirectory() || rootInfo.isSymbolicLink()) throw Error('Drawing input and asset trees must be regular directories, not links.');
  const files = {};
  for (const name of fs.readdirSync(root).sort()) {
    if (name.startsWith('.')) throw Error('Hidden files are not allowed in drawing asset or notice trees.');
    const relative = prefix ? `${prefix}/${name}` : name, file = path.join(root,name), info = fs.lstatSync(file);
    if (info.isSymbolicLink()) throw Error('Drawing inputs and assets must not contain links.');
    if (info.isDirectory()) Object.assign(files,walk(file,relative));
    else files[relative] = describe(file);
  }
  return files;
}
function collectDrawingInputs(source) {
  const files = {};
  for (const name of fs.readdirSync(source).sort()) {
    if (name.startsWith('.') || !/\.(?:[cm]?[jt]sx?|css|json|py)$/.test(name)) continue;
    files[name] = describe(path.join(source,name));
  }
  for (const name of requiredSources) if (!files[name]?.bytes) throw Error(`Missing drawing source: ${name}`);
  const notices = walk(path.join(source,'THIRD_PARTY'),'THIRD_PARTY');
  if (!Object.keys(notices).length) throw Error('Drawing source notices are missing.');
  const ai = fs.existsSync(path.join(source,'ai-assets')) ? walk(path.join(source,'ai-assets'),'ai-assets') : {};
  return {files: {...files,...notices,...ai}, toolingSha256: digest(fs.readFileSync(__filename))};
}
function outputFiles(assets) {
  const files = walk(assets);
  delete files[MANIFEST];
  for (const name of requiredAssets) if (!files[name]?.bytes) throw Error(`Missing drawing asset: ${name}`);
  return files;
}
function checkNotices(source,assets) {
  for (const [name,description] of Object.entries(source.files)) if (name.startsWith('THIRD_PARTY/') && !equal(description,assets[name])) throw Error(`Drawing notice was not copied correctly: ${name}`);
}
function recordDrawingAssets(source, assets, before) {
  const current = collectDrawingInputs(source);
  if (!before || !equal(before,current)) throw Error('Drawing sources changed during generation; regenerate assets.');
  const files = outputFiles(assets); checkNotices(current,files);
  const manifest = {version:1, source:current, assets:files};
  fs.writeFileSync(path.join(assets,MANIFEST),JSON.stringify(manifest,null,2)+'\n');
  return manifest;
}
function verifyDrawingAssets(source,assets) {
  const file = path.join(assets,MANIFEST);
  if (!fs.existsSync(file)) throw Error('Drawing manifest is missing; regenerate drawing assets.');
  const info = fs.lstatSync(file);
  if (!info.isFile() || info.isSymbolicLink()) throw Error('Drawing manifest must be a regular file.');
  if (info.size > 4*1024*1024) throw Error('Drawing manifest is too large.');
  const manifest = JSON.parse(fs.readFileSync(file,'utf8'));
  if (manifest?.version !== 1 || !manifest.source || !manifest.assets) throw Error('Unsupported drawing manifest.');
  const current = collectDrawingInputs(source);
  if (!equal(manifest.source,current)) throw Error('Drawing sources changed since generation; regenerate assets.');
  const files = outputFiles(assets);
  if (!equal(manifest.assets,files)) throw Error('Drawing asset bytes or file set changed since generation.');
  checkNotices(current,files);
  return manifest;
}
module.exports = {MANIFEST,collectDrawingInputs,recordDrawingAssets,verifyDrawingAssets};
if (require.main === module) {
  try {
    if (process.argv.length !== 4) throw Error('Usage: node drawing-assets.cjs <creation-web> <drawing-assets>');
    const manifest = verifyDrawingAssets(path.resolve(process.argv[2]),path.resolve(process.argv[3]));
    console.log(`Drawing source and ${Object.keys(manifest.assets).length} generated files match. Native/device acceptance is separate.`);
  } catch(error) { console.error(error.message); process.exitCode = 1; }
}
