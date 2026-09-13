import { createHmac } from 'node:crypto';
import type { AgentCommand, AgentEvent, AgentStatus } from '@roblox-cloud/shared';

export class ApiClient {
  constructor(private readonly baseUrl: string, private readonly secret: string) {}
  private async post<T>(path: string, payload: unknown): Promise<T> {
    const body = JSON.stringify(payload); const timestamp = String(Date.now());
    const signature = createHmac('sha256', this.secret).update(`${timestamp}.${body}`).digest('hex');
    const response = await fetch(new URL(path, this.baseUrl), { method: 'POST', headers: { 'content-type': 'application/json', 'x-agent-timestamp': timestamp, 'x-agent-signature': signature }, body, signal: AbortSignal.timeout(15_000) });
    if (!response.ok) throw new Error(`API_${String(response.status)}`);
    return response.json() as Promise<T>;
  }
  status(value: AgentStatus): Promise<{ commands: AgentCommand[] }> { return this.post('/api/agent/status', value); }
  event(agentId: string, event: AgentEvent): Promise<{ ok: true }> { return this.post('/api/agent/events', { agentId, event }); }
}
