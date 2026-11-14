import path from 'node:path';
import { createHash } from 'node:crypto';

export const MAX_RECORD_BYTES = 16 * 1024 * 1024;
export const CHUNK_BYTES = 64 * 1024;
const digest = bytes => createHash('sha256').update(bytes).digest('hex');
// Old adapters must reject migrated records, never mistake an absent old row for
// a new empty ledger after an accidental code rollback.
const LEGACY_GUARD = Buffer.from('NOBLE_CHUNKED_RECORD_V2');

// Preserve atomic-file semantics in durable SQLite, never ephemeral Worker disk.
// Legacy records remain readable and migrate only on their next successful write.
export class SqliteFiles {
  constructor(storage, clock = Date.now) {
    this.storage = storage;
    this.clock = clock;
    this.failed = false;
    storage.sql.exec('CREATE TABLE IF NOT EXISTS noble_files (path TEXT PRIMARY KEY, data BLOB NOT NULL, modified REAL NOT NULL)');
    storage.sql.exec('CREATE TABLE IF NOT EXISTS noble_chunk_files (path TEXT PRIMARY KEY, size INTEGER NOT NULL, chunks INTEGER NOT NULL, digest TEXT NOT NULL, modified REAL NOT NULL)');
    storage.sql.exec('CREATE TABLE IF NOT EXISTS noble_file_chunks (path TEXT NOT NULL, part INTEGER NOT NULL, data BLOB NOT NULL, PRIMARY KEY(path, part))');
    this.promises = Object.fromEntries(['mkdir', 'writeFile', 'rename'].map(name => [name, async (...args) => this[name + 'Sync'](...args)]));
  }
  key(value) {
    if (typeof value !== 'string' || value.includes('\\') || value.includes('\0') || value.split('/').includes('..')) throw new Error('INVALID_STORAGE_PATH');
    const key = path.posix.normalize(value);
    if (!key.startsWith('/account/') || key.length > 256) throw new Error('INVALID_STORAGE_PATH');
    return key;
  }
  run(fn) {
    try { return fn(); }
    catch (error) {
      if (!['ENOENT', 'EEXIST'].includes(error.code)) this.failed = true;
      throw error;
    }
  }
  row(value) {
    const key = this.key(value);
    const chunked = [...this.storage.sql.exec('SELECT size, chunks, digest, modified FROM noble_chunk_files WHERE path = ?', key)][0];
    if (chunked) return { ...chunked, chunked: true };
    const legacy = [...this.storage.sql.exec('SELECT data, modified FROM noble_files WHERE path = ?', key)][0];
    if (legacy && Buffer.from(legacy.data).equals(LEGACY_GUARD)) throw new Error('ACCOUNT_RECORD_CORRUPT');
    return legacy;
  }
  missing() { return Object.assign(new Error('ACCOUNT_RECORD_NOT_FOUND'), { code: 'ENOENT' }); }
  names(prefix) {
    return [...this.storage.sql.exec('SELECT path FROM noble_files WHERE substr(path, 1, ?) = ? UNION SELECT path FROM noble_chunk_files WHERE substr(path, 1, ?) = ?', prefix.length, prefix, prefix.length, prefix)].map(row => row.path);
  }
  existsSync(value) { return this.run(() => { const key = this.key(value); return Boolean(this.row(key)) || this.names(key.replace(/\/$/, '') + '/').length > 0; }); }
  mkdirSync(value) { this.key(value === '/account' ? '/account/' : value); }
  readFileSync(value, encoding) {
    return this.run(() => {
      const key = this.key(value), row = this.row(key);
      if (!row) throw this.missing();
      let bytes;
      if (row.chunked) {
        if (!Number.isSafeInteger(row.size) || row.size < 0 || row.size > MAX_RECORD_BYTES || row.chunks !== Math.ceil(row.size / CHUNK_BYTES)) throw new Error('ACCOUNT_RECORD_CORRUPT');
        const parts = [...this.storage.sql.exec('SELECT part, data FROM noble_file_chunks WHERE path = ? ORDER BY part', key)];
        if (parts.length !== row.chunks || parts.some((part, index) => part.part !== index || part.data.byteLength !== Math.min(CHUNK_BYTES, row.size - index * CHUNK_BYTES))) throw new Error('ACCOUNT_RECORD_CORRUPT');
        bytes = Buffer.concat(parts.map(part => Buffer.from(part.data)), row.size);
        if (digest(bytes) !== row.digest) throw new Error('ACCOUNT_RECORD_CORRUPT');
      } else bytes = Buffer.from(row.data);
      return encoding ? bytes.toString(typeof encoding === 'string' ? encoding : encoding.encoding) : bytes;
    });
  }
  remove(key) {
    for (const table of ['noble_file_chunks', 'noble_chunk_files', 'noble_files']) this.storage.sql.exec(`DELETE FROM ${table} WHERE path = ?`, key);
  }
  writeFileSync(value, data, options = {}) {
    return this.run(() => this.storage.transactionSync(() => {
      const key = this.key(value), bytes = Buffer.from(data);
      if (bytes.length > MAX_RECORD_BYTES) throw Object.assign(new Error('ACCOUNT_RECORD_CAPACITY'), { code: 'ENOSPC' });
      if (options.flag === 'wx' && this.row(key)) throw Object.assign(new Error('ACCOUNT_RECORD_EXISTS'), { code: 'EEXIST' });
      this.remove(key);
      for (let offset = 0, part = 0; offset < bytes.length; offset += CHUNK_BYTES, part++) {
        this.storage.sql.exec('INSERT INTO noble_file_chunks(path, part, data) VALUES (?, ?, ?)', key, part, bytes.subarray(offset, offset + CHUNK_BYTES));
      }
      this.storage.sql.exec('INSERT INTO noble_chunk_files(path, size, chunks, digest, modified) VALUES (?, ?, ?, ?, ?)', key, bytes.length, Math.ceil(bytes.length / CHUNK_BYTES), digest(bytes), this.clock());
      this.storage.sql.exec('INSERT INTO noble_files(path, data, modified) VALUES (?, ?, ?)', key, LEGACY_GUARD, this.clock());
    }));
  }
  renameSync(source, target) {
    return this.run(() => this.storage.transactionSync(() => {
      const from = this.key(source), to = this.key(target), row = this.row(from);
      if (!row) throw this.missing();
      if (from === to) return;
      this.remove(to);
      if (row.chunked) {
        this.storage.sql.exec('UPDATE noble_chunk_files SET path = ? WHERE path = ?', to, from);
        this.storage.sql.exec('UPDATE noble_file_chunks SET path = ? WHERE path = ?', to, from);
        this.storage.sql.exec('UPDATE noble_files SET path = ? WHERE path = ?', to, from);
      } else {
        this.storage.sql.exec('INSERT INTO noble_files(path, data, modified) VALUES (?, ?, ?)', to, row.data, row.modified);
        this.storage.sql.exec('DELETE FROM noble_files WHERE path = ?', from);
      }
    }));
  }
  unlinkSync(value) {
    return this.run(() => this.storage.transactionSync(() => { const key = this.key(value); if (!this.row(key)) throw this.missing(); this.remove(key); }));
  }
  statSync(value) {
    return this.run(() => { const row = this.row(value); if (!row) throw this.missing(); return { mtimeMs: row.modified }; });
  }
  readdirSync(value) {
    return this.run(() => { const prefix = this.key(value).replace(/\/$/, '') + '/'; return this.names(prefix).map(name => name.slice(prefix.length)).filter(name => !name.includes('/')); });
  }
}
