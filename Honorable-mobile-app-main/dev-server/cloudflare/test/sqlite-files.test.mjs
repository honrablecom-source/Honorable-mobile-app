import test from 'node:test';
import assert from 'node:assert/strict';
import { DatabaseSync } from 'node:sqlite';
import { mkdtemp, rm } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { SqliteFiles } from '../src/sqlite-files.mjs';

function adapter(db) {
  return {
    sql: { exec(sql, ...args) { return db.prepare(sql).all(...args); } },
    transactionSync(fn) {
      db.exec('BEGIN');
      try { const result = fn(); db.exec('COMMIT'); return result; }
      catch (error) { db.exec('ROLLBACK'); throw error; }
    },
  };
}

test('SQLite atomic replacement survives reopen and preserves the prior value when rename rolls back', async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), 'noble-sqlite-files-'));
  const filename = path.join(directory, 'account.sqlite');
  let db = new DatabaseSync(filename);
  try {
    let storage = adapter(db), files = new SqliteFiles(storage, () => 1234);
    files.writeFileSync('/account/ledger.json', 'original');
    files.writeFileSync('/account/ledger.json.tmp', 'replacement');
    const original = storage.sql.exec;
    storage.sql.exec = (sql, ...args) => {
      if (sql.startsWith('DELETE')) throw new Error('INJECTED_DISK_FAILURE');
      return original(sql, ...args);
    };
    assert.throws(() => files.renameSync('/account/ledger.json.tmp', '/account/ledger.json'), /INJECTED_DISK_FAILURE/);
    assert.equal(files.failed, true);
    storage.sql.exec = original;
    assert.equal(files.readFileSync('/account/ledger.json', 'utf8'), 'original');
    assert.equal(files.readFileSync('/account/ledger.json.tmp', 'utf8'), 'replacement');
    files.renameSync('/account/ledger.json.tmp', '/account/ledger.json');
    db.close(); db = new DatabaseSync(filename);
    files = new SqliteFiles(adapter(db));
    assert.equal(files.readFileSync('/account/ledger.json', 'utf8'), 'replacement');
    assert.equal(files.existsSync('/account/ledger.json.tmp'), false);
    assert.equal(files.statSync('/account/ledger.json').mtimeMs, 1234);
  } finally {
    db.close();
    await rm(directory, { recursive: true, force: true });
  }
});

test('SQLite storage rejects path escape and oversized writes without changing existing bytes', () => {
  const db = new DatabaseSync(':memory:');
  try {
    const files = new SqliteFiles(adapter(db));
    files.writeFileSync('/account/ledger.json', 'keep');
    for (const name of ['/bundle/secret', '/account/../other', '/account\\ledger.json', '/account/a\0b']) {
      assert.throws(() => files.readFileSync(name), /INVALID_STORAGE_PATH/);
    }
    assert.throws(() => files.writeFileSync('/account/ledger.json', Buffer.alloc(1024 * 1024 + 1)), /ACCOUNT_RECORD_CAPACITY/);
    assert.equal(files.readFileSync('/account/ledger.json', 'utf8'), 'keep');
    assert.equal(files.failed, true);
  } finally { db.close(); }
});

test('attachment binary, exclusive writes, bounded listing and deletion retain filesystem semantics', async () => {
  const db = new DatabaseSync(':memory:');
  try {
    const files = new SqliteFiles(adapter(db), () => 5678);
    files.mkdirSync('/account/ledger.attachments', { recursive: true });
    const bytes = Buffer.from([0, 255, 128, 1]);
    await files.promises.writeFile('/account/ledger.attachments/a.json', bytes, { flag: 'wx' });
    assert.deepEqual(files.readFileSync('/account/ledger.attachments/a.json'), bytes);
    assert.throws(() => files.writeFileSync('/account/ledger.attachments/a.json', 'replace', { flag: 'wx' }), { code: 'EEXIST' });
    assert.equal(files.failed, false);
    assert.equal(files.existsSync('/account/ledger.attachments'), true);
    assert.deepEqual(files.readdirSync('/account/ledger.attachments'), ['a.json']);
    assert.equal(files.statSync('/account/ledger.attachments/a.json').mtimeMs, 5678);
    files.unlinkSync('/account/ledger.attachments/a.json');
    assert.throws(() => files.readFileSync('/account/ledger.attachments/a.json'), { code: 'ENOENT' });
    assert.equal(files.failed, false);
  } finally { db.close(); }
});
