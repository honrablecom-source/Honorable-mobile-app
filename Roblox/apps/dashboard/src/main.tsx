import { StrictMode, useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import type { AgentEvent, AgentStatus, Game } from '@roblox-cloud/shared';
import './styles.css';

type Bootstrap = { status: AgentStatus | null; games: Game[]; desktopUrl: string };
const api = async <T,>(path: string, init?: RequestInit): Promise<T> => {
  const headers = new Headers(init?.headers); headers.set('content-type', 'application/json');
  const response = await fetch(path, { credentials: 'include', ...init, headers });
  if (!response.ok) throw new Error(String(response.status)); return response.json() as Promise<T>;
};
const duration = (seconds: number | null | undefined): string => {
  if (seconds == null) return '—'; const h = Math.floor(seconds / 3600); const m = Math.floor(seconds % 3600 / 60); const s = seconds % 60;
  return [h, m, s].map((v) => String(v).padStart(2, '0')).join(':');
};

function App() {
  const [csrf, setCsrf] = useState(''); const [data, setData] = useState<Bootstrap | null>(null); const [logs, setLogs] = useState<AgentEvent[]>([]);
  const [login, setLogin] = useState({ username: '', password: '' }); const [error, setError] = useState(''); const [busy, setBusy] = useState(false);
  const load = async (): Promise<void> => { const session = await api<{ csrfToken: string }>('/api/session'); setCsrf(session.csrfToken); setData(await api('/api/status')); };
  useEffect(() => {
    let stream: EventSource | undefined;
    void load().then(() => {
      stream = new EventSource('/api/stream', { withCredentials: true });
      stream.addEventListener('status', (e) => setData((old) => old ? { ...old, status: JSON.parse((e as MessageEvent<string>).data) as AgentStatus } : old));
    }).catch(() => undefined);
    return () => stream?.close();
  }, []);
  const submitLogin = async (event: React.FormEvent): Promise<void> => { event.preventDefault(); setBusy(true); setError(''); try { const result = await api<{ csrfToken: string }>('/api/auth/login', { method: 'POST', body: JSON.stringify(login) }); setCsrf(result.csrfToken); setData(await api('/api/status')); } catch { setError('Login failed'); } finally { setBusy(false); } };
  const control = async (action: 'play' | 'stop' | 'restart', placeId?: string): Promise<void> => { setBusy(true); try { await api('/api/control', { method: 'POST', headers: { 'x-csrf-token': csrf }, body: JSON.stringify({ action, placeId }) }); } finally { setBusy(false); } };
  if (!data) return <main className="login"><form onSubmit={(e) => void submitLogin(e)}><div className="mark">RC</div><h1>ROBLOX CLOUD</h1><p>Private session control</p><label>Username<input autoComplete="username" value={login.username} onChange={(e) => setLogin({ ...login, username: e.target.value })}/></label><label>Password<input type="password" autoComplete="current-password" value={login.password} onChange={(e) => setLogin({ ...login, password: e.target.value })}/></label>{error && <div className="error">{error}</div>}<button disabled={busy}>SIGN IN</button></form></main>;
  const status = data.status; const current = data.games.find((g) => g.placeId === status?.currentPlaceId);
  return <main className="shell"><header><div><span className="eyebrow">CONTROL CENTER</span><h1>ROBLOX CLOUD</h1></div><span className={`pill ${status?.state === 'ROBLOX_RUNNING' ? 'good' : ''}`}><i/>{status?.state.replaceAll('_', ' ') ?? 'OFFLINE'}</span></header>
    <section className="hero"><div><span className="eyebrow">CURRENT GAME</span><h2>{current?.name ?? 'No experience active'}</h2><p>Session {duration(status?.robloxUptimeSeconds)}</p></div><a className="desktop" href={data.desktopUrl} target="_blank" rel="noreferrer">OPEN ROBLOX DESKTOP <b>↗</b></a></section>
    <section className="metrics">{[['VM', status ? 'Online' : 'Disconnected'], ['CPU', status ? `${status.cpuPercent.toFixed(0)}%` : '—'], ['MEMORY', status ? `${status.ramPercent.toFixed(0)}%` : '—'], ['NETWORK', status?.networkOnline ? 'Online' : 'Offline'], ['VM UPTIME', duration(status?.vmUptimeSeconds)], ['RESTARTS', String(status?.restartCount ?? 0)]].map(([k,v]) => <article key={k}><span>{k}</span><strong>{v}</strong></article>)}</section>
    <section className="panel"><div className="panel-head"><div><span className="eyebrow">LIBRARY</span><h2>Experiences</h2></div><div className="controls"><button disabled={busy} onClick={() => void control('stop')}>STOP</button><button disabled={busy} onClick={() => void control('restart')}>RESTART</button><button onClick={() => void api<AgentEvent[]>('/api/logs').then(setLogs)}>LOGS</button></div></div><div className="games">{data.games.map((game) => <article className={game.placeId === status?.currentPlaceId ? 'active' : ''} key={game.placeId}><div className="game-art"><span>{game.name.slice(0,2).toUpperCase()}</span></div><div><h3>{game.name}</h3><small>Place {game.placeId}</small></div><button disabled={busy} onClick={() => void control('play', game.placeId)}>PLAY</button></article>)}</div></section>
    {logs.length > 0 && <section className="panel logs"><div className="panel-head"><h2>Recent events</h2><button onClick={() => setLogs([])}>CLOSE</button></div>{logs.map((entry, i) => <code key={`${entry.timestamp}-${String(i)}`}>{entry.timestamp} · {entry.severity} · {entry.event} · {entry.result}</code>)}</section>}
  </main>;
}

const root = document.getElementById('root');
if (!root) throw new Error('Dashboard root element is missing');
createRoot(root).render(<StrictMode><App/></StrictMode>);
