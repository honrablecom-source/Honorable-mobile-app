import { loadConfig } from '@roblox-cloud/config';
import { Agent } from './agent.js';

if (process.platform !== 'win32') throw new Error('The Windows agent must run on Windows');
const controller = new AbortController();
process.on('SIGINT', () => controller.abort()); process.on('SIGTERM', () => controller.abort());
await new Agent(loadConfig()).run(controller.signal);
