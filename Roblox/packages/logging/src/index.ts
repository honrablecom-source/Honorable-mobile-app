import pino, { type Logger } from 'pino';

export function createLogger(name: string, logFile?: string): Logger {
  if (!logFile) return pino({ name, level: process.env.LOG_LEVEL ?? 'info' });
  // pino-roll is a runtime-loaded Pino transport and intentionally has no static return type.
  // eslint-disable-next-line @typescript-eslint/no-unsafe-assignment
  const transport = pino.transport({
    target: 'pino-roll',
    options: { file: logFile, frequency: 'daily', size: '10m', mkdir: true, limit: { count: 14 } },
  });
  // eslint-disable-next-line @typescript-eslint/no-unsafe-argument
  return pino({ name, level: process.env.LOG_LEVEL ?? 'info' }, transport);
}
