import { access, readdir, stat } from 'node:fs/promises';
import { constants } from 'node:fs';
import { join } from 'node:path';
import { execFile, spawn } from 'node:child_process';
import { promisify } from 'node:util';
import { robloxUri } from '@roblox-cloud/shared';

const execFileAsync = promisify(execFile);
const PLAYER = 'RobloxPlayerBeta.exe';

export class RobloxManager {
  async discover(): Promise<string | null> {
    const roots = [
      process.env.LOCALAPPDATA ? join(process.env.LOCALAPPDATA, 'Roblox', 'Versions') : '',
      process.env.PROGRAMFILES ? join(process.env.PROGRAMFILES, 'Roblox', 'Versions') : '',
      process.env['PROGRAMFILES(X86)'] ? join(process.env['PROGRAMFILES(X86)'], 'Roblox', 'Versions') : '',
    ].filter(Boolean);
    const found: Array<{ path: string; mtime: number }> = [];
    for (const root of roots) {
      try {
        for (const entry of await readdir(root, { withFileTypes: true })) {
          if (!entry.isDirectory()) continue;
          const path = join(root, entry.name, PLAYER);
          try { await access(path, constants.X_OK); found.push({ path, mtime: (await stat(path)).mtimeMs }); } catch { /* not a player version */ }
        }
      } catch { /* optional installation root */ }
    }
    return found.sort((a, b) => b.mtime - a.mtime)[0]?.path ?? null;
  }
  async process(): Promise<{ pid: number; memoryMb: number } | null> {
    if (process.platform !== 'win32') return null;
    try {
      const { stdout } = await execFileAsync('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command', `Get-Process -Name RobloxPlayerBeta -ErrorAction SilentlyContinue | Select-Object -First 1 Id,WorkingSet64 | ConvertTo-Json -Compress`], { windowsHide: true, timeout: 5000 });
      if (!stdout.trim()) return null;
      const value = JSON.parse(stdout) as { Id: number; WorkingSet64: number };
      return { pid: value.Id, memoryMb: Math.round(value.WorkingSet64 / 1048576) };
    } catch { return null; }
  }
  async launch(placeId: string): Promise<void> {
    if (!(await this.discover())) throw new Error('ROBLOX_NOT_INSTALLED');
    if (process.platform !== 'win32') throw new Error('WINDOWS_REQUIRED');
    const child = spawn('cmd.exe', ['/d', '/s', '/c', 'start', '', robloxUri(placeId)], { detached: true, windowsHide: true, stdio: 'ignore' });
    child.unref();
  }
  async stop(): Promise<void> {
    if (process.platform !== 'win32') return;
    try { await execFileAsync('taskkill.exe', ['/IM', PLAYER, '/T'], { windowsHide: true, timeout: 10000 }); } catch { /* already stopped */ }
  }
}
