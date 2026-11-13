import { createServer } from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import { watch } from 'node:fs';
import { spawn } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const root = path.dirname(fileURLToPath(import.meta.url));
const output = path.resolve(root, '../../../.context/noble-editor-preview');
const portIndex = process.argv.indexOf('--port');
const port = portIndex < 0 ? 4176 : Number(process.argv[portIndex + 1]);
if (!Number.isInteger(port) || port < 1024 || port > 65535) throw Error('Choose a port from 1024 to 65535.');
let building = false, again = false;
async function bundle() {
  if (building) { again = true; return; }
  building = true;
  try {
    await new Promise((resolve, reject) => {
      const child = spawn(process.execPath, ['build.mjs'], { cwd: root, env: { ...process.env, HONORABLE_DRAWING_OUTPUT: output }, stdio: 'inherit' });
      child.on('error', reject); child.on('exit', code => code === 0 ? resolve() : reject(Error('Preview compilation failed.')));
    });
  } finally { building = false; if (again) { again = false; bundle().catch(error => console.error(error.message)); } }
}
await bundle();
const mime = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.woff2': 'font/woff2', '.woff': 'font/woff', '.ttf': 'font/ttf', '.wasm': 'application/wasm' };
const server = createServer(async (request, response) => {
  try {
    if (request.method !== 'GET' && request.method !== 'HEAD') { response.writeHead(405).end(); return; }
    let pathname = decodeURIComponent(new URL(request.url, 'http://localhost').pathname);
    pathname = pathname.replace(/^\/drawing\//, '/');
    const file = path.resolve(output, '.' + (pathname === '/' ? '/index.html' : pathname));
    if (!file.startsWith(output + path.sep)) { response.writeHead(403).end(); return; }
    const info = await stat(file);
    if (!info.isFile()) { response.writeHead(404).end(); return; }
    const body = pathname === '/config.js' ? Buffer.from('window.EXCALIDRAW_ASSET_PATH="/drawing/";') : await readFile(file);
    response.writeHead(200, { 'Content-Type': mime[path.extname(file)] || 'application/octet-stream', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff', 'Content-Security-Policy': "default-src 'self'; script-src 'self' 'wasm-unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self' blob:; worker-src 'self' blob:; frame-src 'none'; object-src 'none'; base-uri 'none'" });
    response.end(request.method === 'HEAD' ? undefined : body);
  } catch { response.writeHead(404).end(); }
});
server.listen(port, '127.0.0.1', () => console.log(`Noble editor preview ready: http://127.0.0.1:${port} (local web preview; Android unverified)`));
let timer;
const watcher = watch(root, (_, filename) => { if (!filename || !/\.(jsx|mjs|css)$/.test(filename) || filename === 'preview.mjs') return; clearTimeout(timer); timer = setTimeout(() => bundle().catch(error => console.error(error.message)), 250); });
function close() { clearTimeout(timer); watcher.close(); server.close(); }
process.on('SIGTERM', close); process.on('SIGINT', close);
