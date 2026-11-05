#!/usr/bin/env node
const http = require('node:http');
const fs = require('node:fs/promises');
const path = require('node:path');
const { spawn } = require('node:child_process');
const root = path.resolve(__dirname, '../..');
const board = process.env.NOBLE_AGENT_BOARD_DIR || path.join(root, '.context/agent-board');
const publicFiles = { '/': ['index.html', 'text/html'], '/app.js': ['app.js', 'text/javascript'], '/style.css': ['style.css', 'text/css'] };
function send(response, status, data, type = 'application/json') {
  response.writeHead(status, {
    'Content-Type': `${type}; charset=utf-8`, 'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff', 'Referrer-Policy': 'no-referrer',
    'Content-Security-Policy': "default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self' data:; base-uri 'none'; form-action 'self'; frame-ancestors 'none'",
  });
  response.end(type === 'application/json' ? JSON.stringify(data) : data);
}
async function readBoard() {
  try {
    const state = JSON.parse(await fs.readFile(path.join(board, 'state.json'), 'utf8'));
    if (state.version !== 1 || !Array.isArray(state.claims) || !Array.isArray(state.events)) throw Error('Invalid board');
    return { version: 1, claims: state.claims, events: state.events.slice(-200), totalEvents: state.events.length };
  } catch (error) {
    if (error.code === 'ENOENT') return { version: 1, claims: [], events: [], totalEvents: 0 };
    throw error;
  }
}
function postMessage(message) {
  return new Promise((resolve, reject) => {
    const child = spawn(process.execPath, [path.join(root, 'scripts/agent-board.cjs'), 'say', '--agent', 'owner', '--message', message], {
      cwd: root, env: { ...process.env, NOBLE_AGENT_BOARD_DIR: board }, stdio: ['ignore', 'ignore', 'pipe'],
    });
    let error = '';
    child.stderr.on('data', bytes => { error += bytes; });
    child.on('error', reject);
    child.on('close', code => code === 0 ? resolve() : reject(Error(error.trim() || 'Message could not be saved')));
  });
}
function createChatServer() {
  return http.createServer(async (request, response) => {
    try {
      const url = new URL(request.url, 'http://localhost');
      if (request.method === 'GET' && url.pathname === '/api/board') return send(response, 200, await readBoard());
      if (request.method === 'POST' && url.pathname === '/api/messages') {
        let origin;
        try { origin = new URL(request.headers.origin); } catch { return send(response, 403, { error: 'Open the chat page to send a message.' }); }
        const hosts = [request.headers.host, request.headers['x-forwarded-host']].filter(Boolean);
        if (!['http:', 'https:'].includes(origin.protocol) || !hosts.includes(origin.host)) return send(response, 403, { error: 'Cross-site messages are not allowed.' });
        if (request.headers['content-type']?.split(';')[0] !== 'application/json') return send(response, 415, { error: 'Expected JSON.' });
        let bytes = 0; const chunks = [];
        for await (const chunk of request) {
          bytes += chunk.length;
          if (bytes > 16384) return send(response, 413, { error: 'Message is too long.' });
          chunks.push(chunk);
        }
        let message;
        try { message = JSON.parse(Buffer.concat(chunks).toString('utf8')).message; } catch { return send(response, 400, { error: 'Invalid message.' }); }
        if (typeof message !== 'string' || !message.trim() || message.trim().length > 3000 || /[\u0000-\u0008\u000b\u000c\u000e-\u001f]/.test(message)) return send(response, 400, { error: 'Write 1–3000 characters without control characters.' });
        await postMessage(message.trim());
        return send(response, 201, { ok: true });
      }
      const file = publicFiles[url.pathname];
      if (file && (request.method === 'GET' || request.method === 'HEAD')) {
        const contents = request.method === 'HEAD' ? '' : await fs.readFile(path.join(__dirname, file[0]));
        return send(response, 200, contents, file[1]);
      }
      return send(response, 404, { error: 'Not found.' });
    } catch { return send(response, 503, { error: 'Board unavailable. Your draft is kept; try again.' }); }
  });
}
if (require.main === module) {
  const index = process.argv.indexOf('--port');
  const port = index < 0 ? 4180 : Number(process.argv[index + 1]);
  if (!Number.isInteger(port) || port < 1024 || port > 65535) throw Error('Invalid port');
  const server = createChatServer();
  server.listen(port, '0.0.0.0', () => console.log(`Noble team chat: http://localhost:${port} · keep the forwarded port private.`));
  process.on('SIGTERM', () => server.close());
  process.on('SIGINT', () => server.close());
}
module.exports = { createChatServer };
