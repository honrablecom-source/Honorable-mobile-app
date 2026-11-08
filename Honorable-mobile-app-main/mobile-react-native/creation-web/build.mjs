import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import fs from 'node:fs/promises';
const root = path.dirname(fileURLToPath(import.meta.url));
const modules = process.env.HONORABLE_DRAWING_NODE_MODULES || path.join(root, 'node_modules');
const require = createRequire(path.join(modules, '../package.json'));
const { build } = require('esbuild');
const out = process.env.HONORABLE_DRAWING_OUTPUT ? path.resolve(process.env.HONORABLE_DRAWING_OUTPUT) : path.resolve(root, '../android/app/src/main/assets/drawing');
if (out === root || root.startsWith(out + path.sep)) throw Error('Drawing output must not contain its source directory.');
const { collectDrawingInputs, recordDrawingAssets } = require(path.resolve(root, '../../scripts/drawing-assets.cjs'));
const sourceSnapshot = collectDrawingInputs(root);
await fs.mkdir(out, { recursive: true });
// Inline geometry workers for offline WebView: no worker network requests.
const workerSources = new Map();
for (const name of ['shape-boolean', 'svg-import']) {
  const worker = await build({ entryPoints: [path.join(root, name + '-worker.mjs')], bundle: true, minify: true, write: false, format: 'iife', platform: 'browser', target: ['chrome90'], nodePaths: [modules], legalComments: 'inline', banner: { js: 'var process={env:{POLYGON_CLIPPING_MAX_QUEUE_SIZE:20000,POLYGON_CLIPPING_MAX_SWEEPLINE_SEGMENTS:20000}};' } });
  workerSources.set('noble:' + name + '-worker', worker.outputFiles[0].text);
}
const inlineGeometryWorker = { name: 'noble-local-workers', setup(builder) {
  builder.onResolve({ filter: /^noble:(?:shape-boolean|svg-import)-worker$/ }, args => ({ path: args.path, namespace: 'noble-local-worker' }));
  builder.onLoad({ filter: /.*/, namespace: 'noble-local-worker' }, args => ({ contents: 'export default ' + JSON.stringify(workerSources.get(args.path)) + ';', loader: 'js' }));
} };

// Excalidraw 0.18.1 nudges mutate elements without requesting a history increment.
// Apply only to the exact pinned branch; fail the build if upstream changes it.
const localKeyboardHistory = { name: 'noble-keyboard-history', setup(builder) {
  builder.onLoad({ filter: /index\.js$/ }, async args => {
    if (!args.path.replaceAll('\\', '/').endsWith('/@excalidraw/excalidraw/dist/prod/index.js')) return;
    const source = await fs.readFile(args.path, 'utf8');
    const marker = 'a.forEach(p=>{P(p,{x:p.x+m,y:p.y+d},!1),Jt(';
    if (source.split(marker).length !== 2) throw Error('Pinned drawing keyboard branch changed; review its history capture before bundling.');
    return { contents: source.replace(marker, 'this.store.shouldCaptureIncrement(),' + marker), loader: 'js', resolveDir: path.dirname(args.path) };
  });
} };

// The pinned renderer always registers a CDN fallback, even with a local asset path.
// Keep that fallback on the same private origin so offline fonts never attempt a remote request.
const localFonts = { name: 'noble-local-fonts', setup(builder) {
  builder.onLoad({ filter: /chunk-.*\.js$/ }, async args => {
    if (!args.path.includes('@excalidraw')) return;
    const source = await fs.readFile(args.path, 'utf8');
    if (!source.includes('https://esm.sh/')) return;
    const contents = source.replace(/`https:\/\/esm\.sh\/[\s\S]*?\/dist\/prod\/`/g,
      'new URL(typeof window.EXCALIDRAW_ASSET_PATH === "string" ? window.EXCALIDRAW_ASSET_PATH : "/drawing/", window.location.origin).href');
    if (contents === source) throw Error('Pinned offline font fallback changed; review before bundling.');
    return { contents, loader: 'js', resolveDir: path.dirname(args.path) };
  });
} };
await build({ plugins: [localKeyboardHistory, localFonts, inlineGeometryWorker], entryPoints: [path.join(root, 'editor.jsx')], bundle: true, minify: true, format: 'iife', target: ['chrome90'], outfile: path.join(out, 'editor.js'), nodePaths: [modules], conditions: ['production'], define: { 'process.env.NODE_ENV': '"production"', 'import.meta.url': '"https://honorable-drawing.local/drawing/editor.js"' }, legalComments: 'external', loader: { '.woff2': 'file', '.woff': 'file', '.ttf': 'file', '.wasm': 'file' } });
const upstream = path.join(modules, '@excalidraw/excalidraw/dist/prod');
await fs.cp(path.join(upstream, 'fonts'), path.join(out, 'fonts'), { recursive: true });
await fs.cp(path.join(upstream, 'data'), path.join(out, 'data'), { recursive: true });
await fs.cp(path.join(root, 'THIRD_PARTY'), path.join(out, 'THIRD_PARTY'), { recursive: true });
for (const name of await fs.readdir(upstream)) if (name.endsWith('.js')) await fs.copyFile(path.join(upstream, name), path.join(out, name));
await fs.writeFile(path.join(out, 'config.js'), 'window.EXCALIDRAW_ASSET_PATH="https://honorable-drawing.local/drawing/";');
await fs.writeFile(path.join(out, 'index.html'), '<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"><title>Noble drawing</title><link rel="stylesheet" href="editor.css"></head><body><div id="root"></div><script src="config.js"></script><script src="editor.js"></script></body></html>');
recordDrawingAssets(root, out, sourceSnapshot);
console.log('Bundled offline drawing assets in ' + out);
