export class RestartLimiter {
  private attempts: number[] = [];
  constructor(private readonly max: number, private readonly windowMs = 15 * 60_000, private readonly delays = [5000, 10000, 20000, 40000, 60000]) {}
  next(now = Date.now()): number | null {
    this.attempts = this.attempts.filter((value) => now - value < this.windowMs);
    if (this.attempts.length >= this.max) return null;
    const delay = this.delays[Math.min(this.attempts.length, this.delays.length - 1)] ?? 60_000;
    this.attempts.push(now); return delay;
  }
  reset(): void { this.attempts = []; }
  get count(): number { return this.attempts.length; }
}
