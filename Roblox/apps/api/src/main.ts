import { loadConfig } from '@roblox-cloud/config';
import { buildApp } from './app.js';

const config = loadConfig();
const app = await buildApp(config);
await app.listen({ host: config.HOST, port: config.PORT });
const shutdown = async (): Promise<void> => { await app.close(); process.exit(0); };
process.on('SIGINT', () => void shutdown()); process.on('SIGTERM', () => void shutdown());
