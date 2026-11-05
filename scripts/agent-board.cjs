#!/usr/bin/env node
// Local coordination only. No network calls, source reads, or Git mutations.
const fs = require('node:fs/promises');
const path = require('node:path');
const { randomUUID } = require('node:crypto');
const root = path.resolve(__dirname, '..');
const storage = process.env.NOBLE_AGENT_BOARD_DIR || path.join(root, '.context/agent-board');
const stateFile = path.join(storage, 'state.json');
const lock = path.join(storage, 'write.lock');
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
function args(argv) {
  const result = { command: argv[0], scopes: [] };
  for (let i = 1; i < argv.length; i += 2) {
    const key = argv[i], value = argv[i + 1];
    if (!['--agent', '--scope', '--message', '--id'].includes(key) || !value || value.startsWith('--')) throw Error('Expected --agent, --scope (repeatable), --message or --id followed by a value.');
    if (key === '--scope') result.scopes.push(value);
    else result[key.slice(2)] = value;
  }
  if (!['status', 'claim', 'say', 'release'].includes(result.command)) throw Error('Usage: node scripts/agent-board.cjs status | claim --agent NAME --scope PATH --message TASK | say --agent NAME --message UPDATE | release --agent NAME --id CLAIM_ID --message RESULT');
  if (result.command !== 'status' && !/^[a-zA-Z0-9_-]{1,48}$/.test(result.agent || '')) throw Error('Use a stable agent name: 1–48 letters, numbers, underscores or hyphens.');
  if (result.command !== 'status' && (!result.message || result.message.length > 3000)) throw Error('Provide a nonempty --message of at most 3000 characters. Never include secrets or personal media.');
  return result;
}
function scope(value) {
  const normalized = value.replaceAll('\\', '/').replace(/\/$/, '');
  if (!normalized || normalized.startsWith('/') || normalized.includes(':') || /[*?\[\]]/.test(normalized) || normalized.split('/').some(part => !part || part === '.' || part === '..')) throw Error('Scopes must be literal repository-relative files or directories; no wildcards or traversal.');
  return normalized;
}
const overlaps = (a, b) => a === b || a.startsWith(b + '/') || b.startsWith(a + '/');
function view(state) {
  const lines = ['# Noble shared agent board', '', 'Local to this checkout. Claims are advisory: every agent must follow docs/AGENT_COORDINATION.md.', '', '## Active claims', ''];
  for (const claim of state.claims) lines.push(`- ${claim.id} · ${claim.agent} · ${claim.at}`, `  ${JSON.stringify(claim.message)}`, ...claim.scopes.map(item => `  - ${item}`));
  if (!state.claims.length) lines.push('None.');
  lines.push('', '## Recent messages', '');
  for (const item of state.events.slice(-40)) lines.push(`- ${item.at} · ${item.agent} · ${item.kind}: ${JSON.stringify(item.message)}`);
  return lines.join('\n') + '\n';
}
async function main() {
  const options = args(process.argv.slice(2));
  await fs.mkdir(storage, { recursive: true });
  let acquired = false;
  for (let attempt = 0; attempt < 100; attempt++) {
    try { await fs.mkdir(lock); acquired = true; break; }
    catch (error) { if (error.code !== 'EEXIST') throw error; await sleep(50); }
  }
  if (!acquired) throw Error('Board is busy or a writer stopped with its lock held. Retry; do not delete the lock until its owning process is confirmed stopped.');
  try {
    let state;
    try { state = JSON.parse(await fs.readFile(stateFile, 'utf8')); }
    catch (error) { if (error.code !== 'ENOENT') throw error; state = { version: 1, claims: [], events: [] }; }
    if (state.version !== 1 || !Array.isArray(state.claims) || !Array.isArray(state.events)) throw Error('Unsupported/corrupt board. Preserve it and investigate; do not reset claims.');
    if (options.command === 'status') { process.stdout.write(view(state)); return; }
    const at = new Date().toISOString();
    let id;
    if (options.command === 'claim') {
      if (!options.scopes.length) throw Error('Claim at least one --scope before editing.');
      const scopes = [...new Set(options.scopes.map(scope))];
      const conflict = state.claims.find(claim => claim.scopes.some(a => scopes.some(b => overlaps(a, b))));
      if (conflict) throw Error(`Scope conflict with ${conflict.agent}, claim ${conflict.id}. Read status and coordinate; no files were changed.`);
      id = randomUUID();
      state.claims.push({ id, agent: options.agent, at, message: options.message, scopes });
    }
    if (options.command === 'release') {
      const index = state.claims.findIndex(claim => claim.id === options.id);
      if (index < 0) throw Error('Claim not found.');
      if (state.claims[index].agent !== options.agent) throw Error('Only the named owner may release this claim. Ask that chat to hand it off.');
      id = options.id;
      state.claims.splice(index, 1);
    }
    state.events.push({ at, agent: options.agent, kind: options.command, message: options.message, ...(id ? { id } : {}) });
    // The state is canonical; Markdown is only a readable snapshot.
    const temporary = stateFile + '.' + randomUUID() + '.tmp';
    await fs.writeFile(temporary, JSON.stringify(state, null, 2) + '\n');
    await fs.rename(temporary, stateFile);
    await fs.writeFile(path.join(storage, 'BOARD.md'), view(state));
    console.log(JSON.stringify({ ok: true, ...(id ? { id } : {}), board: path.join(storage, 'BOARD.md') }));
  } finally { await fs.rmdir(lock); }
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });
