import { describe, expect, it } from 'vitest';
import { commandSchema, gamesSchema, placeIdSchema, robloxUri } from './index.js';

describe('Place IDs and launch URIs', () => {
  it('accepts numeric IDs and generates only Roblox URIs', () => {
    expect(placeIdSchema.parse('123456')).toBe('123456');
    expect(robloxUri('123456')).toBe('roblox://placeId=123456');
  });
  it.each(['', 'abc', '1&evil=true', '-1', '1'.repeat(21)])('rejects %j', (value) => {
    expect(() => placeIdSchema.parse(value)).toThrow();
  });
});

describe('Configuration and commands', () => {
  it('validates game configuration', () => {
    expect(gamesSchema.parse([{ name: 'Game', placeId: '42' }])).toHaveLength(1);
  });
  it('rejects arbitrary commands', () => {
    expect(() => commandSchema.parse({ type: 'SHELL', commandId: crypto.randomUUID() })).toThrow();
  });
});
