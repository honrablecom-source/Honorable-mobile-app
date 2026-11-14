import test from 'node:test';
import assert from 'node:assert/strict';
import { DatabaseSync } from 'node:sqlite';
import { mkdtemp, rm } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { SqliteFiles, MAX_RECORD_BYTES, CHUNK_BYTES } from '../src/sqlite-files.mjs';

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
    assert.throws(() => files.writeFileSync('/account/ledger.json', Buffer.alloc(MAX_RECORD_BYTES + 1)), /ACCOUNT_RECORD_CAPACITY/);
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

test('multi-megabyte records survive reopen with bounded SQLite values and exact Unicode bytes', async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), 'noble-chunk-reopen-'));
  const filename = path.join(directory, 'account.sqlite');
  let db = new DatabaseSync(filename);
  try {
    const bytes = Buffer.from(JSON.stringify({ profile: 'Test 🧪', payload: 'é'.repeat(1100000) }));
    let files = new SqliteFiles(adapter(db), () => 8901);
    files.writeFileSync('/account/ledger.tmp', bytes);
    files.renameSync('/account/ledger.tmp', '/account/ledger.json');
    files.renameSync('/account/ledger.json', '/account/ledger.json');
    const rows = db.prepare('SELECT length(data) AS size FROM noble_file_chunks').all();
    assert(rows.length > 32);
    assert(rows.every(row => row.size <= CHUNK_BYTES));
    db.close(); db = new DatabaseSync(filename);
    files = new SqliteFiles(adapter(db));
    assert.deepEqual(files.readFileSync('/account/ledger.json'), bytes);
    assert.equal(files.statSync('/account/ledger.json').mtimeMs, 8901);
    files.writeFileSync('/account/ledger.json', 'shorter');
    assert.equal(db.prepare('SELECT count(*) AS count FROM noble_file_chunks').get().count, 1);
    assert.equal(files.readFileSync('/account/ledger.json', 'utf8'), 'shorter');
    files.writeFileSync('/account/empty', '');
    assert.equal(files.readFileSync('/account/empty', 'utf8'), '');
  } finally {
    db.close();
    await rm(directory, { recursive: true, force: true });
  }
});

test('interrupted chunk write rolls back metadata and data; missing or modified chunks fail closed', () => {
  const db = new DatabaseSync(':memory:');
  try {
    const storage = adapter(db), files = new SqliteFiles(storage);
    files.writeFileSync('/account/ledger.json', 'keep-original');
    const original = storage.sql.exec;
    storage.sql.exec = (sql, ...args) => {
      if (sql.startsWith('INSERT INTO noble_file_chunks') && args[1] === 2) throw new Error('INJECTED_CHUNK_FAILURE');
      return original(sql, ...args);
    };
    assert.throws(() => files.writeFileSync('/account/ledger.json', Buffer.alloc(CHUNK_BYTES * 4, 7)), /INJECTED_CHUNK_FAILURE/);
    storage.sql.exec = original;
    assert.equal(files.readFileSync('/account/ledger.json', 'utf8'), 'keep-original');
    files.writeFileSync('/account/large', Buffer.alloc(CHUNK_BYTES * 3, 7));
    db.prepare('DELETE FROM noble_file_chunks WHERE path = ? AND part = 1').run('/account/large');
    assert.throws(() => files.readFileSync('/account/large'), /ACCOUNT_RECORD_CORRUPT/);
    files.writeFileSync('/account/large', Buffer.alloc(CHUNK_BYTES * 3, 7));
    db.prepare('UPDATE noble_file_chunks SET data = ? WHERE path = ? AND part = 1').run(Buffer.alloc(CHUNK_BYTES, 8), '/account/large');
    assert.throws(() => files.readFileSync('/account/large'), /ACCOUNT_RECORD_CORRUPT/);
    assert.equal(files.failed, true);
  } finally { db.close(); }
});

test('legacy deployment records remain intact until atomic migration and can replace either format', () => {
  const db = new DatabaseSync(':memory:');
  try {
    const files = new SqliteFiles(adapter(db));
    db.prepare('INSERT INTO noble_files VALUES (?, ?, ?)').run('/account/legacy.json', Buffer.from('legacy balance'), 4567);
    assert.equal(files.readFileSync('/account/legacy.json', 'utf8'), 'legacy balance');
    files.writeFileSync('/account/new.json', 'new balance');
    assert.deepEqual(files.readdirSync('/account/').sort(), ['legacy.json', 'new.json']);
    files.renameSync('/account/legacy.json', '/account/new.json');
    assert.equal(files.readFileSync('/account/new.json', 'utf8'), 'legacy balance');
    assert.equal(files.statSync('/account/new.json').mtimeMs, 4567);
    files.writeFileSync('/account/new.json', 'migrated balance');
    const legacyBytes = db.prepare('SELECT data FROM noble_files WHERE path = ?').get('/account/new.json').data;
    assert.throws(() => JSON.parse(Buffer.from(legacyBytes).toString('utf8')), SyntaxError);
    assert.equal(files.readFileSync('/account/new.json', 'utf8'), 'migrated balance');
    files.unlinkSync('/account/new.json');
    assert.equal(db.prepare('SELECT count(*) AS count FROM noble_file_chunks').get().count, 0);
  } finally { db.close(); }
});
