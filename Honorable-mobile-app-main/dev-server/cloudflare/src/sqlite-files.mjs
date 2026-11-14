import path from 'node:path';

// The existing stores keep their atomic-file contract. These are durable SQLite
// records, never the Worker's ephemeral /tmp filesystem. Only account data paths
// can be addressed; there is no access to bundled code or machine files.
export class SqliteFiles {
  constructor(storage, clock = Date.now) {
    this.storage = storage;
    this.clock = clock;
    this.failed = false;
    storage.sql.exec('CREATE TABLE IF NOT EXISTS noble_files (path TEXT PRIMARY KEY, data BLOB NOT NULL, modified REAL NOT NULL)');
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

  row(value) { return [...this.storage.sql.exec('SELECT data, modified FROM noble_files WHERE path = ?', this.key(value))][0]; }
  missing() { return Object.assign(new Error('ACCOUNT_RECORD_NOT_FOUND'), { code: 'ENOENT' }); }
  existsSync(value) {
    const key = this.key(value);
    return Boolean(this.row(key)) || [...this.storage.sql.exec('SELECT path FROM noble_files WHERE substr(path, 1, ?) = ? LIMIT 1', key.length + 1, key + '/')].length > 0;
  }
  mkdirSync(value) { this.key(value === '/account' ? '/account/' : value); }
  readFileSync(value, encoding) {
    return this.run(() => {
      const row = this.row(value);
      if (!row) throw this.missing();
      const bytes = Buffer.from(row.data);
      return encoding ? bytes.toString(typeof encoding === 'string' ? encoding : encoding.encoding) : bytes;
    });
  }
  writeFileSync(value, data, options = {}) {
    return this.run(() => this.storage.transactionSync(() => {
      const key = this.key(value), bytes = Buffer.from(data);
      // Explicit capacity bound below SQLite's per-value limit. A larger ledger
      // needs a normalized schema migration, never silent eviction or truncation.
      if (bytes.length > 1024 * 1024) throw Object.assign(new Error('ACCOUNT_RECORD_CAPACITY'), { code: 'ENOSPC' });
      if (options.flag === 'wx' && this.row(key)) throw Object.assign(new Error('ACCOUNT_RECORD_EXISTS'), { code: 'EEXIST' });
      this.storage.sql.exec('INSERT INTO noble_files(path, data, modified) VALUES (?, ?, ?) ON CONFLICT(path) DO UPDATE SET data=excluded.data, modified=excluded.modified', key, bytes, this.clock());
    }));
  }
  renameSync(source, target) {
    return this.run(() => this.storage.transactionSync(() => {
      const from = this.key(source), to = this.key(target), row = this.row(from);
      if (!row) throw this.missing();
      this.storage.sql.exec('INSERT INTO noble_files(path, data, modified) VALUES (?, ?, ?) ON CONFLICT(path) DO UPDATE SET data=excluded.data, modified=excluded.modified', to, row.data, row.modified);
      this.storage.sql.exec('DELETE FROM noble_files WHERE path = ?', from);
    }));
  }
  unlinkSync(value) {
    return this.run(() => {
      const key = this.key(value);
      if (!this.row(key)) throw this.missing();
      this.storage.sql.exec('DELETE FROM noble_files WHERE path = ?', key);
    });
  }
  statSync(value) {
    return this.run(() => { const row = this.row(value); if (!row) throw this.missing(); return { mtimeMs: row.modified }; });
  }
  readdirSync(value) {
    const prefix = this.key(value) + '/';
    return [...this.storage.sql.exec('SELECT path FROM noble_files WHERE substr(path, 1, ?) = ?', prefix.length, prefix)]
      .map(row => row.path.slice(prefix.length)).filter(name => !name.includes('/'));
  }
}
