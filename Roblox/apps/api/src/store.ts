import { DatabaseSync } from 'node:sqlite';
import { mkdirSync } from 'node:fs';
import { dirname } from 'node:path';
import type { AgentCommand, AgentEvent, AgentStatus } from '@roblox-cloud/shared';

export class Store {
  readonly db: DatabaseSync;
  constructor(path: string) {
    mkdirSync(dirname(path), { recursive: true });
    this.db = new DatabaseSync(path);
    this.db.exec(`
      PRAGMA journal_mode=WAL; PRAGMA busy_timeout=5000;
      CREATE TABLE IF NOT EXISTS sessions(id TEXT PRIMARY KEY, user TEXT NOT NULL, csrf TEXT NOT NULL, expires_at INTEGER NOT NULL);
      CREATE TABLE IF NOT EXISTS commands(id TEXT PRIMARY KEY, agent_id TEXT NOT NULL, body TEXT NOT NULL, created_at INTEGER NOT NULL, claimed_at INTEGER);
      CREATE TABLE IF NOT EXISTS agent_status(agent_id TEXT PRIMARY KEY, body TEXT NOT NULL, updated_at INTEGER NOT NULL);
      CREATE TABLE IF NOT EXISTS audit(id INTEGER PRIMARY KEY AUTOINCREMENT, timestamp TEXT NOT NULL, actor TEXT NOT NULL, action TEXT NOT NULL, target TEXT, result TEXT NOT NULL, metadata TEXT);
      CREATE TABLE IF NOT EXISTS events(id INTEGER PRIMARY KEY AUTOINCREMENT, agent_id TEXT NOT NULL, body TEXT NOT NULL, timestamp TEXT NOT NULL);
    `);
  }
  createSession(id: string, user: string, csrf: string, expiresAt: number): void {
    this.db.prepare('INSERT INTO sessions VALUES(?,?,?,?)').run(id, user, csrf, expiresAt);
  }
  session(id: string): { user: string; csrf: string } | undefined {
    const row = this.db.prepare('SELECT user,csrf FROM sessions WHERE id=? AND expires_at>?').get(id, Date.now()) as { user: string; csrf: string } | undefined;
    return row;
  }
  deleteSession(id: string): void { this.db.prepare('DELETE FROM sessions WHERE id=?').run(id); }
  cleanup(): void { this.db.prepare('DELETE FROM sessions WHERE expires_at<=?').run(Date.now()); }
  queue(agentId: string, command: AgentCommand): void {
    this.db.prepare('INSERT INTO commands VALUES(?,?,?,?,NULL)').run(command.commandId, agentId, JSON.stringify(command), Date.now());
  }
  claim(agentId: string): AgentCommand[] {
    const rows = this.db.prepare(`UPDATE commands SET claimed_at=? WHERE id IN (
      SELECT id FROM commands WHERE agent_id=? AND claimed_at IS NULL ORDER BY created_at LIMIT 20
    ) RETURNING body`).all(Date.now(), agentId) as Array<{ body: string }>;
    return rows.map((row) => JSON.parse(row.body) as AgentCommand);
  }
  setStatus(status: AgentStatus): void {
    this.db.prepare('INSERT INTO agent_status VALUES(?,?,?) ON CONFLICT(agent_id) DO UPDATE SET body=excluded.body,updated_at=excluded.updated_at').run(status.agentId, JSON.stringify(status), Date.now());
  }
  status(agentId: string): AgentStatus | null {
    const row = this.db.prepare('SELECT body,updated_at FROM agent_status WHERE agent_id=?').get(agentId) as { body: string; updated_at: number } | undefined;
    if (!row) return null;
    const status = JSON.parse(row.body) as AgentStatus;
    return Date.now() - row.updated_at > 120_000 ? { ...status, state: 'DISCONNECTED' } : status;
  }
  addEvent(agentId: string, event: AgentEvent): void {
    this.db.prepare('INSERT INTO events(agent_id,body,timestamp) VALUES(?,?,?)').run(agentId, JSON.stringify(event), event.timestamp);
    this.db.prepare('DELETE FROM events WHERE id NOT IN (SELECT id FROM events ORDER BY id DESC LIMIT 5000)').run();
  }
  events(agentId: string, limit = 100): AgentEvent[] {
    return (this.db.prepare('SELECT body FROM events WHERE agent_id=? ORDER BY id DESC LIMIT ?').all(agentId, limit) as Array<{ body: string }>).map((r) => JSON.parse(r.body) as AgentEvent);
  }
  audit(actor: string, action: string, target: string | null, result: string, metadata?: object): void {
    this.db.prepare('INSERT INTO audit(timestamp,actor,action,target,result,metadata) VALUES(?,?,?,?,?,?)').run(new Date().toISOString(), actor, action, target, result, metadata ? JSON.stringify(metadata) : null);
  }
}
