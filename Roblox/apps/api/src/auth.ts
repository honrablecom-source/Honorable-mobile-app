import { createHmac, randomBytes, timingSafeEqual } from 'node:crypto';
import { verify } from '@node-rs/argon2';
import type { FastifyReply, FastifyRequest } from 'fastify';
import type { Store } from './store.js';

const SESSION_TTL = 8 * 60 * 60 * 1000;
const safeEqual = (a: string, b: string): boolean => {
  const aa = Buffer.from(a); const bb = Buffer.from(b);
  return aa.length === bb.length && timingSafeEqual(aa, bb);
};

export class AuthService {
  constructor(private readonly store: Store, private readonly username: string, private readonly passwordHash: string, private readonly sessionSecret: string, private readonly agentSecret: string, private readonly secure: boolean) {}
  async login(username: string, password: string): Promise<{ id: string; csrf: string } | null> {
    if (!safeEqual(username, this.username) || !(await verify(this.passwordHash, password))) return null;
    const id = randomBytes(32).toString('base64url'); const csrf = randomBytes(24).toString('base64url');
    this.store.createSession(id, username, csrf, Date.now() + SESSION_TTL); return { id, csrf };
  }
  cookie(reply: FastifyReply, id: string): void { reply.setCookie('rcm_session', id, { httpOnly: true, secure: this.secure, sameSite: 'strict', path: '/', maxAge: SESSION_TTL / 1000 }); }
  async requireUser(request: FastifyRequest, reply: FastifyReply): Promise<void> {
    const session = this.store.session(request.cookies.rcm_session ?? '');
    if (!session) return reply.code(401).send({ error: 'UNAUTHORIZED' });
    request.user = session;
    if (!['GET', 'HEAD', 'OPTIONS'].includes(request.method) && request.headers['x-csrf-token'] !== session.csrf) return reply.code(403).send({ error: 'CSRF_INVALID' });
  }
  async requireAgent(request: FastifyRequest, reply: FastifyReply): Promise<void> {
    const timestamp = request.headers['x-agent-timestamp']; const signature = request.headers['x-agent-signature'];
    if (typeof timestamp !== 'string' || typeof signature !== 'string' || Math.abs(Date.now() - Number(timestamp)) > 60_000) { await reply.code(401).send({ error: 'AGENT_AUTH_INVALID' }); return; }
    const expected = createHmac('sha256', this.agentSecret).update(`${timestamp}.${request.rawBody ?? ''}`).digest('hex');
    if (!safeEqual(signature, expected)) await reply.code(401).send({ error: 'AGENT_AUTH_INVALID' });
  }
}

declare module 'fastify' {
  interface FastifyRequest { user?: { user: string; csrf: string }; rawBody?: string }
}
