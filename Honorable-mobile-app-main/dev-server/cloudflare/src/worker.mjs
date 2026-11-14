import { DurableObject } from 'cloudflare:workers';
import { httpServerHandler } from 'cloudflare:node';
import accountService from '../../src/server.js';
import { SqliteFiles } from './sqlite-files.mjs';

const json = (status, body) => Response.json(body, { status, headers: { 'Cache-Control': 'no-store' } });

export class NobleAccount extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    this.env = env;
    this.queue = Promise.resolve();
  }

  initialize() {
    const client = this.env.HONORABLE_GOOGLE_WEB_CLIENT_ID;
    if (!/^\d+-[A-Za-z0-9_-]+\.apps\.googleusercontent\.com$/.test(client || '')) throw new Error('ACCOUNT_CONFIGURATION_REQUIRED');
    this.files = new SqliteFiles(this.ctx.storage);
    this.server = accountService.createServer({
      file: '/account/ledger.json', storage: this.files,
      mode: 'production', releaseChannel: 'PRODUCTION', betaRequired: false,
      googleClientId: client, webOrigins: [], adminConfig: null, adminUsers: null,
      // No trust is inferred from client-submitted results or environment flags.
      // Until an approved completion verifier exists, /readyz stays HTTP 503.
      searchCompletionVerifier: undefined,
    });
    this.handler = httpServerHandler(this.server);
  }

  fetch(request) {
    // Keep identity lookups, refresh rotation and ledger operations sequential,
    // including across Google's asynchronous signature-key fetch.
    const result = this.queue.then(() => this.handle(request));
    this.queue = result.catch(() => {});
    return result;
  }

  async handle(request) {
    try {
      if (!this.server) this.initialize();
      const response = await this.handler.fetch(request, this.env, this.ctx);
      const body = await response.arrayBuffer();
      await this.server.analytics.close();
      if (this.files.failed) throw new Error('ACCOUNT_STORAGE_UNAVAILABLE');
      await this.ctx.storage.sync();
      return new Response(body, { status: response.status, headers: response.headers });
    } catch {
      // Never reuse mutated in-memory ledger/session state after a storage fault.
      this.server?.close();
      this.server = null;
      return json(503, { error: 'ACCOUNT_SERVICE_UNAVAILABLE' });
    }
  }
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname.startsWith('/admin') || url.pathname.startsWith('/dev/')) return json(404, { error: 'NOT_FOUND' });
    if (!['GET', 'POST'].includes(request.method)) return json(405, { error: 'METHOD_NOT_ALLOWED' });
    // One authoritative object preserves global identity, purchase ownership,
    // shared installation grants and request-id idempotency across all accounts.
    return env.ACCOUNT.get(env.ACCOUNT.idFromName('production-v1')).fetch(request);
  },
};
