import {
  OwnedAiSearch,
  OwnedAiSearchCancelled,
  AiCreditStatusUnknown,
  type AiSearchDependencies,
  type AvailableAiModel,
} from '../src/search/OwnedAiSearch';
import type { SearchResponse } from '../src/native/HonorableNative';
const response = (): SearchResponse => ({
  confident: true,
  decision: 'MATCH',
  semantic: 0.8,
  margin: 0.2,
  results: [
    {
      rank: 2,
      mediaId: 10,
      mediaUri: 'content://synthetic/10',
      mediaType: 'IMAGE',
      displayName: 'Synthetic',
      score: 0.8,
      semantic: 0.8,
      confidence: 'STRONG',
      evidence: ['semantic'],
    },
    {
      rank: 0,
      mediaId: 11,
      mediaUri: 'content://synthetic/11',
      mediaType: 'VIDEO',
      displayName: 'Synthetic video',
      score: 0.7,
      semantic: 0.7,
      confidence: 'POSSIBLE',
      evidence: ['frame'],
      bestTimestampMs: 1200,
    },
  ],
});
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: Error) => void;
  const promise = new Promise<T>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}
function fixture() {
  let serial = 0,
    current = true;
  const dependencies: AiSearchDependencies = {
    selectModel: jest.fn(async () => undefined),
    start: jest.fn(async () => undefined),
    search: jest.fn(async () => response()),
    finish: jest.fn(async () => undefined),
    cancelNative: jest.fn(),
    isCurrent: () => current,
    requestId: () => `synthetic-${++serial}`,
    onPhase: jest.fn(),
  };
  return {
    dependencies,
    runner: new OwnedAiSearch(dependencies),
    invalidate: () => {
      current = false;
    },
  };
}
test('boundary order stays select/start/native/complete; prompt and native rank order are retained', async () => {
  const f = fixture(),
    order: string[] = [];
  f.dependencies.selectModel = jest.fn(async () => {
    order.push('select');
  });
  f.dependencies.start = jest.fn(async () => {
    order.push('start');
  });
  const native = response();
  f.dependencies.search = jest.fn(async () => {
    order.push('native');
    return native;
  });
  f.dependencies.finish = jest.fn(async () => {
    order.push('complete');
  });
  const found = await f.runner.run('  birthday cake and candles  ', 'SERAN_V2');
  expect(order).toEqual(['select', 'start', 'native', 'complete']);
  expect(f.dependencies.search).toHaveBeenCalledWith(
    'birthday cake and candles',
  );
  expect(f.dependencies.start).toHaveBeenCalledWith('SERAN_V2', 'synthetic-1');
  expect(f.dependencies.finish).toHaveBeenCalledWith('synthetic-1', 'SUCCESS');
  expect(found?.results.map(x => x.rank)).toEqual([2, 0]);
  native.results[0].evidence.push('later');
  expect(found?.results[0].evidence).toEqual(['semantic']);
  expect(f.runner.busy).toBe(false);
});
test('V3 and blank input never enter inference or ledger routes', async () => {
  const f = fixture();
  await expect(
    f.runner.run('synthetic', 'SERAN_V3' as AvailableAiModel),
  ).rejects.toThrow('locked');
  await expect(f.runner.run('   ', 'SERAN_V1')).rejects.toThrow('Describe');
  expect(f.dependencies.selectModel).not.toHaveBeenCalled();
  expect(f.dependencies.start).not.toHaveBeenCalled();
  expect(f.dependencies.search).not.toHaveBeenCalled();
});
test('admission cancellation releases only its request and retains the gate until admission settles', async () => {
  const f = fixture(),
    start = deferred<void>();
  f.dependencies.start = jest.fn(() => start.promise);
  const pending = f.runner.run('synthetic', 'SERAN_V1');
  await Promise.resolve();
  expect(f.runner.phase).toBe('STARTING');
  expect(f.runner.cancel()).toBe(true);
  await expect(f.runner.run('successor', 'SERAN_V1')).rejects.toThrow('Finish');
  expect(f.dependencies.cancelNative).not.toHaveBeenCalled();
  start.resolve();
  await expect(pending).rejects.toBeInstanceOf(OwnedAiSearchCancelled);
  expect(f.dependencies.search).not.toHaveBeenCalled();
  expect(f.dependencies.finish).toHaveBeenCalledWith('synthetic-1', 'FAILED');
});
test('accepted inference cancellation cannot request SUCCESS when native returns matches late', async () => {
  const f = fixture(),
    native = deferred<SearchResponse>();
  f.dependencies.search = jest.fn(() => native.promise);
  const pending = f.runner.run('synthetic', 'SERAN_V2');
  await Promise.resolve();
  await Promise.resolve();
  expect(f.runner.phase).toBe('SEARCHING');
  expect(f.runner.cancel()).toBe(true);
  expect(f.dependencies.cancelNative).toHaveBeenCalledTimes(1);
  expect(f.runner.busy).toBe(true);
  native.resolve(response());
  await expect(pending).rejects.toBeInstanceOf(OwnedAiSearchCancelled);
  expect(f.dependencies.finish).toHaveBeenCalledTimes(1);
  expect(f.dependencies.finish).toHaveBeenCalledWith('synthetic-1', 'FAILED');
});
test('empty/error/malformed native results never request SUCCESS', async () => {
  for (const kind of ['empty', 'error', 'malformed']) {
    const f = fixture();
    f.dependencies.search = jest.fn(async () => {
      if (kind === 'error') throw Error('native failed');
      if (kind === 'empty') return { ...response(), results: [] };
      return {
        ...response(),
        results: [{ ...response().results[0], score: NaN }],
      };
    });
    const pending = f.runner.run('synthetic', 'SERAN_V1');
    if (kind === 'empty') expect((await pending)?.results).toEqual([]);
    else await expect(pending).rejects.toThrow();
    expect(f.dependencies.finish).toHaveBeenCalledWith('synthetic-1', 'FAILED');
    expect(f.dependencies.finish).not.toHaveBeenCalledWith(
      expect.anything(),
      'SUCCESS',
    );
  }
});
test('owner loss before inference prevents admission; owner loss during finalization cannot leak results', async () => {
  const f = fixture(),
    select = deferred<void>();
  f.dependencies.selectModel = jest.fn(() => select.promise);
  const pending = f.runner.run('synthetic', 'SERAN_V1');
  f.invalidate();
  select.resolve();
  await expect(pending).rejects.toBeInstanceOf(OwnedAiSearchCancelled);
  expect(f.dependencies.start).not.toHaveBeenCalled();
  const g = fixture(),
    finish = deferred<void>();
  g.dependencies.finish = jest.fn(() => finish.promise);
  const completed = g.runner.run('synthetic', 'SERAN_V1');
  await Promise.resolve();
  await Promise.resolve();
  await Promise.resolve();
  expect(g.runner.phase).toBe('FINALIZING');
  expect(g.runner.cancel()).toBe(false);
  g.invalidate();
  finish.resolve();
  expect(await completed).toBeUndefined();
  expect(g.dependencies.finish).toHaveBeenCalledTimes(1);
  expect(g.dependencies.cancelNative).not.toHaveBeenCalled();
});
test('unknown release status is never reported as a confirmed refund', async () => {
  const f = fixture();
  f.dependencies.start = jest.fn(async () => {
    throw Error('network');
  });
  f.dependencies.finish = jest.fn(async () => {
    throw Error('offline');
  });
  await expect(f.runner.run('synthetic', 'SERAN_V1')).rejects.toBeInstanceOf(
    AiCreditStatusUnknown,
  );
  expect(f.dependencies.search).not.toHaveBeenCalled();
  expect(f.dependencies.finish).toHaveBeenCalledWith('synthetic-1', 'FAILED');
  expect(f.runner.busy).toBe(false);
});
test('request IDs cannot replay; UI observer errors cannot alter settlement', async () => {
  const f = fixture();
  f.dependencies.requestId = () => 'same';
  f.dependencies.onPhase = () => {
    throw Error('observer');
  };
  await f.runner.run('first', 'SERAN_V1');
  await expect(f.runner.run('second', 'SERAN_V1')).rejects.toThrow('fresh');
  expect(f.dependencies.start).toHaveBeenCalledTimes(1);
  expect(f.dependencies.finish).toHaveBeenCalledWith('same', 'SUCCESS');
});
