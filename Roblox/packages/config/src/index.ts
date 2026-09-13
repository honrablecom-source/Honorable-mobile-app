import { gamesSchema } from '@roblox-cloud/shared';
import { z } from 'zod';

const configSchema = z.object({
  NODE_ENV: z.enum(['development', 'test', 'production']).default('development'),
  HOST: z.string().default('127.0.0.1'),
  PORT: z.coerce.number().int().min(1).max(65535).default(4000),
  PUBLIC_ORIGIN: z.url(),
  DATABASE_PATH: z.string().min(1).default('./data/manager.db'),
  ADMIN_USERNAME: z.string().min(3).max(80),
  ADMIN_PASSWORD_HASH: z.string().startsWith('$argon2id$'),
  SESSION_SECRET: z.string().min(32),
  AGENT_SHARED_SECRET: z.string().min(32),
  AGENT_ID: z.string().min(1).max(100).default('windows-vm-1'),
  API_URL: z.url(),
  GUACAMOLE_URL: z.url(),
  GAMES_JSON: z.string().transform((raw, ctx) => {
    try { return gamesSchema.parse(JSON.parse(raw)); }
    catch (error) { ctx.addIssue({ code: 'custom', message: `Invalid GAMES_JSON: ${String(error)}` }); return z.NEVER; }
  }),
  AUTO_RECOVER: z.stringbool().default(true),
  REOPEN_LAST_GAME: z.stringbool().default(true),
  MAX_RESTARTS: z.coerce.number().int().min(0).max(20).default(5),
  STATUS_INTERVAL_ACTIVE_MS: z.coerce.number().int().min(5000).default(10000),
  STATUS_INTERVAL_IDLE_MS: z.coerce.number().int().min(30000).default(45000),
});

export type AppConfig = z.infer<typeof configSchema>;
export const loadConfig = (env: NodeJS.ProcessEnv = process.env): AppConfig => configSchema.parse(env);
