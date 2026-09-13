import Fastify, { type FastifyInstance } from 'fastify';
import cookie from '@fastify/cookie';
import cors from '@fastify/cors';
import helmet from '@fastify/helmet';
import rateLimit from '@fastify/rate-limit';
import { z } from 'zod';
import { commandSchema, eventSchema, placeIdSchema, statusSchema } from '@roblox-cloud/shared';
import type { AppConfig } from '@roblox-cloud/config';
import { Store } from './store.js';
import { AuthService } from './auth.js';
import type { ServerResponse } from 'node:http';

const credentials = z.object({ username: z.string().max(80), password: z.string().max(1024) });
const action = z.object({ action: z.enum(['play', 'stop', 'restart']), placeId: placeIdSchema.optional() });

export async function buildApp(config: AppConfig): Promise<FastifyInstance> {
  const app = Fastify({ logger: true, bodyLimit: 128 * 1024 });
  const store = new Store(config.DATABASE_PATH);
  const auth = new AuthService(store, config.ADMIN_USERNAME, config.ADMIN_PASSWORD_HASH, config.SESSION_SECRET, config.AGENT_SHARED_SECRET, config.NODE_ENV === 'production');
  const streams = new Set<ServerResponse>();
  const publish = (event: string, data: unknown): void => {
    const frame = `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`;
    for (const stream of streams) stream.write(frame);
  };
  await app.register(cookie, { secret: config.SESSION_SECRET });
  await app.register(cors, { origin: config.PUBLIC_ORIGIN, credentials: true });
  await app.register(helmet, { contentSecurityPolicy: false });
  await app.register(rateLimit, { max: 120, timeWindow: '1 minute' });
  app.addContentTypeParser('application/json', { parseAs: 'string' }, (request, body, done) => {
    const bodyText = typeof body === 'string' ? body : body.toString('utf8');
    request.rawBody = bodyText; try { done(null, bodyText.length ? JSON.parse(bodyText) : {}); } catch (e) { done(e as Error, undefined); }
  });
  app.setErrorHandler((error: unknown, _request, reply) => {
    app.log.error(error);
    const known = error instanceof Error ? error : new Error('Unknown error');
    const status = typeof error === 'object' && error !== null && 'statusCode' in error && typeof error.statusCode === 'number' ? error.statusCode : 500;
    void reply.code(status).send({ error: status < 500 ? known.message : 'INTERNAL_ERROR' });
  });

  app.get('/health', async () => ({ ok: true }));
  app.post('/api/auth/login', { config: { rateLimit: { max: 5, timeWindow: '15 minutes' } } }, async (request, reply) => {
    const parsed = credentials.safeParse(request.body); if (!parsed.success) return reply.code(400).send({ error: 'INVALID_INPUT' });
    const session = await auth.login(parsed.data.username, parsed.data.password);
    store.audit(parsed.data.username, 'auth.login', null, session ? 'success' : 'failure');
    if (!session) return reply.code(401).send({ error: 'INVALID_CREDENTIALS' });
    auth.cookie(reply, session.id); return { csrfToken: session.csrf };
  });
  app.post('/api/auth/logout', { preHandler: auth.requireUser.bind(auth) }, async (request, reply) => {
    store.deleteSession(request.cookies.rcm_session ?? ''); reply.clearCookie('rcm_session', { path: '/' }); return { ok: true };
  });
  app.get('/api/session', { preHandler: auth.requireUser.bind(auth) }, async (request) => ({ user: request.user?.user, csrfToken: request.user?.csrf }));
  app.get('/api/games', { preHandler: auth.requireUser.bind(auth) }, async () => config.GAMES_JSON);
  app.get('/api/status', { preHandler: auth.requireUser.bind(auth) }, async () => ({ status: store.status(config.AGENT_ID), games: config.GAMES_JSON, desktopUrl: config.GUACAMOLE_URL }));
  app.get('/api/logs', { preHandler: auth.requireUser.bind(auth) }, async () => store.events(config.AGENT_ID));
  app.get('/api/stream', { preHandler: auth.requireUser.bind(auth) }, async (request, reply) => {
    reply.hijack();
    reply.raw.writeHead(200, { 'content-type': 'text/event-stream', 'cache-control': 'no-cache, no-transform', connection: 'keep-alive', 'x-accel-buffering': 'no' });
    streams.add(reply.raw); reply.raw.write(`event: status\ndata: ${JSON.stringify(store.status(config.AGENT_ID))}\n\n`);
    request.raw.on('close', () => streams.delete(reply.raw));
  });
  app.post('/api/control', { preHandler: auth.requireUser.bind(auth) }, async (request, reply) => {
    const parsed = action.safeParse(request.body); if (!parsed.success) return reply.code(400).send({ error: 'INVALID_COMMAND' });
    if (parsed.data.action === 'play' && !parsed.data.placeId) return reply.code(400).send({ error: 'PLACE_ID_REQUIRED' });
    if (parsed.data.placeId && !config.GAMES_JSON.some((game) => game.placeId === parsed.data.placeId)) return reply.code(403).send({ error: 'GAME_NOT_ALLOWED' });
    const type = parsed.data.action === 'play' ? 'LAUNCH_GAME' : parsed.data.action === 'stop' ? 'STOP_ROBLOX' : 'RESTART_ROBLOX';
    const raw = type === 'LAUNCH_GAME' ? { type, commandId: crypto.randomUUID(), placeId: parsed.data.placeId } : { type, commandId: crypto.randomUUID() };
    const command = commandSchema.parse(raw); store.queue(config.AGENT_ID, command); store.audit(request.user?.user ?? 'unknown', `control.${parsed.data.action}`, config.AGENT_ID, 'queued', { commandId: command.commandId, placeId: parsed.data.placeId });
    return reply.code(202).send({ commandId: command.commandId });
  });
  app.post('/api/agent/status', { preHandler: auth.requireAgent.bind(auth) }, async (request, reply) => {
    const parsed = statusSchema.safeParse(request.body); if (!parsed.success || parsed.data.agentId !== config.AGENT_ID) return reply.code(400).send({ error: 'INVALID_STATUS' });
    store.setStatus(parsed.data); publish('status', parsed.data); return { commands: store.claim(config.AGENT_ID) };
  });
  app.post('/api/agent/events', { preHandler: auth.requireAgent.bind(auth) }, async (request, reply) => {
    const parsed = z.object({ agentId: z.string(), event: eventSchema }).safeParse(request.body); if (!parsed.success || parsed.data.agentId !== config.AGENT_ID) return reply.code(400).send({ error: 'INVALID_EVENT' });
    store.addEvent(parsed.data.agentId, parsed.data.event); publish(parsed.data.event.event, parsed.data.event); return { ok: true };
  });
  const cleanup = setInterval(() => store.cleanup(), 60 * 60 * 1000); cleanup.unref();
  const heartbeat = setInterval(() => { for (const stream of streams) stream.write(': heartbeat\n\n'); }, 25_000); heartbeat.unref();
  app.addHook('onClose', async () => { clearInterval(cleanup); clearInterval(heartbeat); for (const stream of streams) stream.end(); store.db.close(); });
  return app;
}
