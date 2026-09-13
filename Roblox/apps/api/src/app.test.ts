import { createHmac } from 'node:crypto';
import { mkdtemp } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { hash } from '@node-rs/argon2';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import type { FastifyInstance } from 'fastify';
import type { AppConfig } from '@roblox-cloud/config';
import { buildApp } from './app.js';

let app: FastifyInstance;
let config: AppConfig;
beforeEach(async () => {
  const dir = await mkdtemp(join(tmpdir(), 'rcm-test-'));
  config = { NODE_ENV: 'test', HOST: '127.0.0.1', PORT: 4000, PUBLIC_ORIGIN: 'http://localhost:5173', DATABASE_PATH: join(dir, 'test.db'), ADMIN_USERNAME: 'admin', ADMIN_PASSWORD_HASH: await hash('correct horse battery staple'), SESSION_SECRET: 's'.repeat(32), AGENT_SHARED_SECRET: 'a'.repeat(32), AGENT_ID: 'agent-1', API_URL: 'https://example.com', GUACAMOLE_URL: 'https://desktop.example.com', GAMES_JSON: [{ name: 'Allowed', placeId: '123' }], AUTO_RECOVER: true, REOPEN_LAST_GAME: true, MAX_RESTARTS: 5, STATUS_INTERVAL_ACTIVE_MS: 10000, STATUS_INTERVAL_IDLE_MS: 45000 };
  app = await buildApp(config);
});
afterEach(async () => app.close());

describe('authentication and authorization', () => {
  it('rejects invalid credentials and protects controls', async () => {
    expect((await app.inject({ method: 'POST', url: '/api/auth/login', payload: { username: 'admin', password: 'wrong-password' } })).statusCode).toBe(401);
    expect((await app.inject({ method: 'POST', url: '/api/control', payload: { action: 'stop' } })).statusCode).toBe(401);
  });
  it('requires CSRF and only permits configured games', async () => {
    const login = await app.inject({ method: 'POST', url: '/api/auth/login', payload: { username: 'admin', password: 'correct horse battery staple' } });
    const cookie = login.cookies[0]?.value ?? ''; const csrf = login.json<{ csrfToken: string }>().csrfToken;
    expect((await app.inject({ method: 'POST', url: '/api/control', cookies: { rcm_session: cookie }, payload: { action: 'stop' } })).statusCode).toBe(403);
    expect((await app.inject({ method: 'POST', url: '/api/control', cookies: { rcm_session: cookie }, headers: { 'x-csrf-token': csrf }, payload: { action: 'play', placeId: '999' } })).statusCode).toBe(403);
    expect((await app.inject({ method: 'POST', url: '/api/control', cookies: { rcm_session: cookie }, headers: { 'x-csrf-token': csrf }, payload: { action: 'play', placeId: '123' } })).statusCode).toBe(202);
  });
});

describe('agent authentication', () => {
  it('rejects unsigned requests and accepts valid HMAC', async () => {
    const body = JSON.stringify({ agentId: 'agent-1', state: 'ONLINE', observedAt: new Date().toISOString(), currentPlaceId: null, robloxPid: null, robloxMemoryMb: null, cpuPercent: 1, ramPercent: 2, availableRamMb: 1000, diskPercent: 3, networkOnline: true, vmUptimeSeconds: 20, robloxUptimeSeconds: null, restartCount: 0, lastCrashAt: null, errorCode: null });
    expect((await app.inject({ method: 'POST', url: '/api/agent/status', payload: body, headers: { 'content-type': 'application/json' } })).statusCode).toBe(401);
    const timestamp = String(Date.now()); const signature = createHmac('sha256', config.AGENT_SHARED_SECRET).update(`${timestamp}.${body}`).digest('hex');
    expect((await app.inject({ method: 'POST', url: '/api/agent/status', payload: body, headers: { 'content-type': 'application/json', 'x-agent-timestamp': timestamp, 'x-agent-signature': signature } })).statusCode).toBe(200);
  });
});
