import os from 'node:os';
import { readFile, writeFile } from 'node:fs/promises';
import si from 'systeminformation';
import type { AgentCommand, AgentEvent, AgentState, AgentStatus } from '@roblox-cloud/shared';
import type { AppConfig } from '@roblox-cloud/config';
import { createLogger } from '@roblox-cloud/logging';
import { ApiClient } from './client.js';
import { RobloxManager } from './roblox.js';
import { RestartLimiter } from './watchdog.js';

const wait = (ms: number, signal: AbortSignal): Promise<void> => new Promise((resolve) => { const id = setTimeout(resolve, ms); signal.addEventListener('abort', () => { clearTimeout(id); resolve(); }, { once: true }); });

export class Agent {
  private readonly client: ApiClient; private readonly roblox = new RobloxManager(); private readonly limiter: RestartLimiter;
  private readonly log = createLogger('windows-agent', './data/windows-agent.log');
  private state: AgentState = 'ONLINE'; private currentPlaceId: string | null = null; private startedAt: number | null = null;
  private lastCrashAt: string | null = null; private wasRunning = false; private failures = 0;
  constructor(private readonly config: AppConfig) { this.client = new ApiClient(config.API_URL, config.AGENT_SHARED_SECRET); this.limiter = new RestartLimiter(config.MAX_RESTARTS); }
  async run(signal: AbortSignal): Promise<void> {
    if (!(await this.roblox.discover())) this.state = 'ERROR';
    else if (this.config.REOPEN_LAST_GAME) {
      try {
        const saved = JSON.parse(await readFile('./data/last-session.json', 'utf8')) as { placeId?: string };
        const allowed = this.config.GAMES_JSON.some((game) => game.placeId === saved.placeId);
        if (saved.placeId && allowed && !(await this.roblox.process())) await this.launch(saved.placeId, 'game.launched');
      } catch { /* no prior session */ }
    }
    while (!signal.aborted) {
      try {
        const status = await this.collect(); const response = await this.client.status(status); this.failures = 0;
        for (const command of response.commands) await this.command(command);
        await this.detectCrash(status);
      } catch (error) { this.failures++; this.log.error({ error }, 'agent cycle failed'); }
      const backoff = Math.min(this.config.STATUS_INTERVAL_IDLE_MS, this.config.STATUS_INTERVAL_ACTIVE_MS * 2 ** Math.min(this.failures, 4));
      await wait(backoff, signal);
    }
  }
  private async command(command: AgentCommand): Promise<void> {
    if (command.type === 'GET_STATUS') return;
    if (command.type === 'STOP_ROBLOX') { this.currentPlaceId = null; await this.roblox.stop(); this.state = 'ROBLOX_STOPPED'; return; }
    if (command.type === 'RESTART_ROBLOX') { const placeId = this.currentPlaceId; await this.roblox.stop(); if (placeId) await this.launch(placeId, 'roblox.restarting'); return; }
    await this.launch(command.placeId, 'game.launched');
  }
  private async launch(placeId: string, event: string): Promise<void> {
    this.state = 'LAUNCHING'; this.currentPlaceId = placeId; await this.roblox.launch(placeId); this.startedAt = Date.now();
    await writeFile('./data/last-session.json', JSON.stringify({ placeId }), { encoding: 'utf8', mode: 0o600 });
    await this.emit(event, 'info', 'success');
  }
  private async detectCrash(status: AgentStatus): Promise<void> {
    const running = status.robloxPid !== null;
    if (this.wasRunning && !running && this.currentPlaceId) {
      this.lastCrashAt = new Date().toISOString(); await this.emit('roblox.crashed', 'error', 'detected');
      if (this.config.AUTO_RECOVER) {
        const delay = this.limiter.next();
        if (delay === null) { this.state = 'ERROR'; await this.emit('roblox.restarting', 'error', 'restart_limit_reached'); }
        else { this.state = 'RESTARTING'; await wait(delay, new AbortController().signal); await this.launch(this.currentPlaceId, 'roblox.restarting'); }
      }
    } else if (running && this.startedAt && Date.now() - this.startedAt > 5 * 60_000) this.limiter.reset();
    this.wasRunning = running;
  }
  private async collect(): Promise<AgentStatus> {
    const [load, mem, disks, network, processInfo] = await Promise.all([si.currentLoad(), si.mem(), si.fsSize(), si.inetChecksite('https://www.roblox.com'), this.roblox.process()]);
    if (processInfo) this.state = 'ROBLOX_RUNNING'; else if (this.state !== 'ERROR' && this.state !== 'LAUNCHING' && this.state !== 'RESTARTING') this.state = 'ROBLOX_STOPPED';
    const disk = disks.find((value) => value.mount.toLowerCase().startsWith('c:')) ?? disks[0];
    return { agentId: this.config.AGENT_ID, state: this.state, observedAt: new Date().toISOString(), currentPlaceId: this.currentPlaceId, robloxPid: processInfo?.pid ?? null, robloxMemoryMb: processInfo?.memoryMb ?? null, cpuPercent: load.currentLoad, ramPercent: mem.active / mem.total * 100, availableRamMb: Math.round(mem.available / 1048576), diskPercent: disk?.use ?? 0, networkOnline: network.ok, vmUptimeSeconds: Math.floor(os.uptime()), robloxUptimeSeconds: processInfo && this.startedAt ? Math.floor((Date.now() - this.startedAt) / 1000) : null, restartCount: this.limiter.count, lastCrashAt: this.lastCrashAt, errorCode: this.state === 'ERROR' && !processInfo ? 'ROBLOX_NOT_INSTALLED' : null };
  }
  private async emit(event: string, severity: AgentEvent['severity'], result: string): Promise<void> {
    const value: AgentEvent = { event, severity, timestamp: new Date().toISOString(), sessionId: null, gameId: this.currentPlaceId, result, ...(severity === 'error' ? { restartAttempt: this.limiter.count } : {}) };
    this.log[severity](value); await this.client.event(this.config.AGENT_ID, value);
  }
}
