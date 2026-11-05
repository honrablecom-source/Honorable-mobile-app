const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');
const { spawn } = require('node:child_process');
const script = path.join(__dirname, 'agent-board.cjs');
async function fixture(t) {
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), 'noble-board-'));
  t.after(() => fs.rm(dir, { recursive: true, force: true }));
  const run = (...args) => new Promise((resolve, reject) => {
    const child = spawn(process.execPath, [script, ...args], { env: { ...process.env, NOBLE_AGENT_BOARD_DIR: dir } });
    let out = '', err = '';
    child.stdout.on('data', bytes => { out += bytes; });
    child.stderr.on('data', bytes => { err += bytes; });
    child.on('error', reject);
    child.on('close', code => resolve({ code, out, err }));
  });
  const claim = (agent, scope) => run('claim', '--agent', agent, '--scope', scope, '--message', 'Synthetic task');
  return { dir, run, claim };
}
test('simultaneous overlapping claims have exactly one winner', async t => {
  const { dir, claim } = await fixture(t);
  const results = await Promise.all([claim('main', 'src'), claim('fast', 'src/editor.ts')]);
  assert.deepEqual(results.map(result => result.code).sort(), [0, 1]);
  const state = JSON.parse(await fs.readFile(path.join(dir, 'state.json'), 'utf8'));
  assert.equal(state.claims.length, 1);
  assert.equal(state.events.length, 1);
  assert.match(results.find(result => result.code === 1).err, /Scope conflict/);
});
test('independent concurrent claims and messages are retained without lost writes', async t => {
  const { dir, run, claim } = await fixture(t);
  const results = await Promise.all([claim('main', 'src/photo'), claim('fast', 'src/video'), run('say', '--agent', 'third', '--message', 'Review pending')]);
  assert(results.every(result => result.code === 0));
  const state = JSON.parse(await fs.readFile(path.join(dir, 'state.json'), 'utf8'));
  assert.equal(state.claims.length, 2);
  assert.equal(state.events.length, 3);
  const status = await run('status');
  assert.match(status.out, /Review pending/);
});
test('only the owner can release; released scope becomes available', async t => {
  const { run, claim } = await fixture(t);
  const original = await claim('main', 'src/photo.ts');
  const id = JSON.parse(original.out).id;
  assert.equal((await run('release', '--agent', 'fast', '--id', id, '--message', 'Takeover')).code, 1);
  assert.equal((await claim('fast', 'src/photo.ts')).code, 1);
  assert.equal((await run('release', '--agent', 'main', '--id', id, '--message', 'Checks passed')).code, 0);
  assert.equal((await claim('fast', 'src/photo.ts')).code, 0);
});
test('scope validation rejects traversal, absolute paths and wildcard claims', async t => {
  const { claim } = await fixture(t);
  for (const scope of ['../src', '/tmp', 'C:\\src', 'src/*', 'src/./editor', 'src//editor']) {
    assert.equal((await claim('main', scope)).code, 1, scope);
  }
  assert.equal((await claim('main', 'src/photo')).code, 0);
  assert.equal((await claim('fast', 'src/photos')).code, 0);
});
test('corrupt state is preserved and never silently resets ownership', async t => {
  const { dir, run } = await fixture(t);
  await fs.writeFile(path.join(dir, 'state.json'), '{broken');
  assert.equal((await run('status')).code, 1);
  assert.equal(await fs.readFile(path.join(dir, 'state.json'), 'utf8'), '{broken');
});
