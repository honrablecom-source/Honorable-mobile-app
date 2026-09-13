import { z } from 'zod';

export const placeIdSchema = z.string().regex(/^\d{1,20}$/, 'Place ID must contain 1-20 digits');
export const gameSchema = z.object({ name: z.string().trim().min(1).max(80), placeId: placeIdSchema });
export const gamesSchema = z.array(gameSchema).min(1).max(100);

export const commandSchema = z.discriminatedUnion('type', [
  z.object({ type: z.literal('LAUNCH_GAME'), commandId: z.uuid(), placeId: placeIdSchema }),
  z.object({ type: z.literal('STOP_ROBLOX'), commandId: z.uuid() }),
  z.object({ type: z.literal('RESTART_ROBLOX'), commandId: z.uuid() }),
  z.object({ type: z.literal('GET_STATUS'), commandId: z.uuid() }),
]);

export const agentStateSchema = z.enum([
  'ONLINE', 'ROBLOX_RUNNING', 'ROBLOX_STOPPED', 'LAUNCHING', 'DISCONNECTED', 'RESTARTING', 'ERROR',
]);

export const statusSchema = z.object({
  agentId: z.string().min(1).max(100),
  state: agentStateSchema,
  observedAt: z.iso.datetime(),
  currentPlaceId: placeIdSchema.nullable(),
  robloxPid: z.number().int().positive().nullable(),
  robloxMemoryMb: z.number().nonnegative().nullable(),
  cpuPercent: z.number().min(0).max(100),
  ramPercent: z.number().min(0).max(100),
  availableRamMb: z.number().nonnegative(),
  diskPercent: z.number().min(0).max(100),
  networkOnline: z.boolean(),
  vmUptimeSeconds: z.number().int().nonnegative(),
  robloxUptimeSeconds: z.number().int().nonnegative().nullable(),
  restartCount: z.number().int().nonnegative(),
  lastCrashAt: z.iso.datetime().nullable(),
  errorCode: z.string().max(100).nullable(),
});

export const eventSchema = z.object({
  event: z.string().regex(/^[a-z]+\.[a-z]+$/),
  severity: z.enum(['debug', 'info', 'warn', 'error']),
  timestamp: z.iso.datetime(),
  sessionId: z.string().nullable(),
  gameId: placeIdSchema.nullable(),
  result: z.string().max(200),
  error: z.string().max(2000).optional(),
  restartAttempt: z.number().int().nonnegative().optional(),
});

export type Game = z.infer<typeof gameSchema>;
export type AgentCommand = z.infer<typeof commandSchema>;
export type AgentStatus = z.infer<typeof statusSchema>;
export type AgentEvent = z.infer<typeof eventSchema>;
export type AgentState = z.infer<typeof agentStateSchema>;

export function robloxUri(placeId: string): string {
  return `roblox://placeId=${placeIdSchema.parse(placeId)}`;
}
