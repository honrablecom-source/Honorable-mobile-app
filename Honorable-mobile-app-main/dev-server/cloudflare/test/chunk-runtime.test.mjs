import test from 'node:test';
import assert from 'node:assert/strict';
import { Miniflare } from 'miniflare';
import { readFile, mkdtemp, rm } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { createHash } from 'node:crypto';

test('real workerd persists chunked records above SQLite single-value limits across restart', async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), 'noble-runtime-chunks-'));
  const entry = `
    import { DurableObject } from 'cloudflare:workers';
    import { SqliteFiles } from './sqlite-files.mjs';
    import { createHash } from 'node:crypto';
    export class ChunkTest extends DurableObject {
      async fetch(request) {
        const files = new SqliteFiles(this.ctx.storage);
        if (request.method === 'POST') {
          files.writeFileSync('/account/test.tmp', Buffer.from(await request.arrayBuffer()));
          files.renameSync('/account/test.tmp', '/account/test.json');
          await this.ctx.storage.sync();
        }
        const bytes = files.readFileSync('/account/test.json');
        const max = [...this.ctx.storage.sql.exec('SELECT max(length(data)) AS maximum FROM noble_file_chunks')][0].maximum;
        return Response.json({size:bytes.length,digest:createHash('sha256').update(bytes).digest('hex'),maxChunk:max});
      }
    }
    export default {fetch(request,env){return env.DATA.get(env.DATA.idFromName('fixture')).fetch(request)}};
  `;
  const options = {
    modules: [
      { type: 'ESModule', path: 'entry.mjs', contents: entry },
      { type: 'ESModule', path: 'sqlite-files.mjs', contents: await readFile(new URL('../src/sqlite-files.mjs', import.meta.url), 'utf8') },
    ],
    compatibilityDate: '2026-07-30', compatibilityFlags: ['nodejs_compat'],
    durableObjects: { DATA: { className: 'ChunkTest', useSQLite: true } }, durableObjectsPersist: directory,
  };
  let mf = new Miniflare(options);
  try {
    const bytes = Buffer.alloc(3 * 1024 * 1024 + 17, 171);
    const expected = {size:bytes.length,digest:createHash('sha256').update(bytes).digest('hex'),maxChunk:65536};
    assert.deepEqual(await (await mf.dispatchFetch('https://test.invalid/', {method:'POST',body:bytes})).json(), expected);
    await mf.dispose(); mf = new Miniflare(options);
    assert.deepEqual(await (await mf.dispatchFetch('https://test.invalid/')).json(), expected);
  } finally {
    await mf.dispose();
    await rm(directory, { recursive:true, force:true });
  }
});
