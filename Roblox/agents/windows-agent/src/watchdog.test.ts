import { describe, expect, it } from 'vitest';
import { RestartLimiter } from './watchdog.js';

describe('RestartLimiter', () => {
  it('backs off and caps crash recovery', () => {
    const limiter = new RestartLimiter(3, 60_000, [5, 10, 20]);
    expect([limiter.next(0), limiter.next(1), limiter.next(2), limiter.next(3)]).toEqual([5, 10, 20, null]);
  });
  it('allows retry after the crash window', () => {
    const limiter = new RestartLimiter(1, 100, [5]); limiter.next(0);
    expect(limiter.next(101)).toBe(5);
  });
});
