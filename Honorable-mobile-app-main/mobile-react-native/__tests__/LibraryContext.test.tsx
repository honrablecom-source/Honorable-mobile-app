import React from 'react';
import Renderer, { act } from 'react-test-renderer';
import { LibraryProvider, useLibrary } from '../src/library/LibraryContext';
import {
  honorableNative,
  LibraryItem,
  IndexStatus,
} from '../src/native/HonorableNative';
jest.mock('../src/native/HonorableNative', () => ({
  honorableNative: {
    getStatus: jest.fn(),
    getLibrary: jest.fn(),
    refreshIndex: jest.fn(),
  },
}));
const native = honorableNative as jest.Mocked<typeof honorableNative>;
const status = (allowed = true): IndexStatus => ({
  engine: 'REAL',
  permissionGranted: allowed,
  indexedCount: allowed ? 100 : 0,
  status: 'READY',
});
const item = (mediaId: number): LibraryItem => ({
  mediaId,
  mediaUri: `content://synthetic/${mediaId}`,
  mediaType: 'IMAGE',
  displayName: `Synthetic ${mediaId}`,
  capturedAt: 0,
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
let value: ReturnType<typeof useLibrary>, tree: Renderer.ReactTestRenderer;
function Probe() {
  value = useLibrary();
  return null;
}
beforeEach(() => {
  jest.resetAllMocks();
  native.getStatus.mockResolvedValue(status());
  native.getLibrary.mockResolvedValue({ total: 100, items: [item(1)] });
  native.refreshIndex.mockResolvedValue({
    added: 0,
    updated: 0,
    deleted: 0,
    failed: 0,
    skipped: 0,
    indexedCount: 100,
  });
});
afterEach(async () => {
  await act(async () => tree?.unmount());
  jest.useRealTimers();
});
async function mount() {
  await act(async () => {
    tree = Renderer.create(
      <LibraryProvider>
        <Probe />
      </LibraryProvider>,
    );
  });
}
test('late load-more cannot repopulate a library cleared by a newer permission reload', async () => {
  await mount();
  const page = deferred<{ total: number; items: LibraryItem[] }>();
  native.getLibrary.mockReturnValueOnce(page.promise);
  let pending!: Promise<void>;
  await act(async () => {
    pending = value.loadMore();
  });
  native.getStatus.mockResolvedValueOnce(status(false));
  await act(async () => value.reload());
  expect(value.items).toEqual([]);
  await act(async () => {
    page.resolve({ total: 100, items: [item(2)] });
    await pending;
  });
  expect(value.items).toEqual([]);
  expect(value.total).toBe(0);
  expect(value.status?.permissionGranted).toBe(false);
});
test('older reload page cannot replace a newer cleared result', async () => {
  await mount();
  const page = deferred<{ total: number; items: LibraryItem[] }>();
  native.getLibrary.mockReturnValueOnce(page.promise);
  let older!: Promise<void>;
  await act(async () => {
    older = value.reload();
  });
  native.getStatus.mockResolvedValueOnce(status(false));
  await act(async () => value.reload());
  await act(async () => {
    page.resolve({ total: 100, items: [item(3)] });
    await older;
  });
  expect(value.items).toEqual([]);
  expect(value.total).toBe(0);
});
test('stale reload failures do not replace the latest successful status', async () => {
  await mount();
  const old = deferred<IndexStatus>();
  native.getStatus.mockReturnValueOnce(old.promise);
  let pending!: Promise<void>;
  await act(async () => {
    pending = value.reload();
  });
  await act(async () => value.reload());
  await act(async () => {
    old.reject(new Error('Obsolete failure'));
    await pending;
  });
  expect(value.error).toBe('');
  expect(value.items).toEqual([item(1)]);
});
test('duplicate load-more requests share admission and successful pages still append once', async () => {
  await mount();
  const page = deferred<{ total: number; items: LibraryItem[] }>();
  native.getLibrary.mockReturnValueOnce(page.promise);
  let first!: Promise<void>, second!: Promise<void>;
  await act(async () => {
    first = value.loadMore();
    second = value.loadMore();
  });
  expect(native.getLibrary).toHaveBeenCalledTimes(2);
  await act(async () => {
    page.resolve({ total: 100, items: [item(1), item(2)] });
    await Promise.all([first, second]);
  });
  expect(value.items).toEqual([item(1), item(2)]);
});
test('load-more errors are reported and a retry remains usable', async () => {
  await mount();
  native.getLibrary.mockRejectedValueOnce(new Error('Synthetic page failure'));
  await act(async () => value.loadMore());
  expect(value.error).toBe('Synthetic page failure');
  native.getLibrary.mockResolvedValueOnce({ total: 2, items: [item(2)] });
  await act(async () => value.loadMore());
  expect(value.items).toEqual([item(1), item(2)]);
  expect(value.error).toBe('');
});
test('status polling from an older generation cannot revive permission after reload', async () => {
  jest.useFakeTimers();
  await mount();
  const indexing = deferred<Awaited<ReturnType<typeof native.refreshIndex>>>();
  native.refreshIndex.mockReturnValueOnce(indexing.promise);
  let pending!: Promise<void>;
  await act(async () => {
    pending = value.reindex();
  });
  const poll = deferred<IndexStatus>();
  native.getStatus.mockReturnValueOnce(poll.promise);
  await act(async () => {
    jest.advanceTimersByTime(750);
  });
  native.getStatus.mockResolvedValue(status(false));
  await act(async () => value.reload());
  await act(async () => {
    poll.resolve(status());
  });
  expect(value.status?.permissionGranted).toBe(false);
  expect(value.items).toEqual([]);
  await act(async () => {
    indexing.resolve({
      added: 0,
      updated: 0,
      deleted: 0,
      failed: 0,
      skipped: 0,
      indexedCount: 0,
    });
    await pending;
  });
});
test('polling permission loss clears already displayed items', async () => {
  jest.useFakeTimers();
  await mount();
  const indexing = deferred<Awaited<ReturnType<typeof native.refreshIndex>>>();
  native.refreshIndex.mockReturnValueOnce(indexing.promise);
  let pending!: Promise<void>;
  await act(async () => {
    pending = value.reindex();
  });
  native.getStatus.mockResolvedValue(status(false));
  await act(async () => {
    jest.advanceTimersByTime(750);
  });
  expect(value.items).toEqual([]);
  expect(value.total).toBe(0);
  await act(async () => {
    indexing.resolve({
      added: 0,
      updated: 0,
      deleted: 0,
      failed: 0,
      skipped: 0,
      indexedCount: 0,
    });
    await pending;
  });
});
test('an unmounted provider cannot start page requests from late status or retained callbacks', async () => {
  const first = deferred<IndexStatus>();
  native.getStatus.mockReturnValueOnce(first.promise);
  await mount();
  const reload = value.reload;
  await act(async () => tree.unmount());
  await act(async () => {
    first.resolve(status());
    await reload();
  });
  expect(native.getStatus).toHaveBeenCalledTimes(1);
  expect(native.getLibrary).not.toHaveBeenCalled();
});
test('an old index failure cannot replace a newer successful reload', async () => {
  await mount();
  const index = deferred<Awaited<ReturnType<typeof native.refreshIndex>>>();
  native.refreshIndex.mockReturnValueOnce(index.promise);
  let pending!: Promise<void>;
  await act(async () => {
    pending = value.reindex();
  });
  await act(async () => value.reload());
  await act(async () => {
    index.reject(new Error('Obsolete index failure'));
    await pending;
  });
  expect(value.error).toBe('');
  expect(value.indexing).toBe(false);
  expect(value.items).toEqual([item(1)]);
});
test('a slow status poll does not accumulate overlapping native requests', async () => {
  jest.useFakeTimers();
  await mount();
  const indexing = deferred<Awaited<ReturnType<typeof native.refreshIndex>>>();
  native.refreshIndex.mockReturnValueOnce(indexing.promise);
  let pending!: Promise<void>;
  await act(async () => {
    pending = value.reindex();
  });
  const poll = deferred<IndexStatus>();
  native.getStatus.mockReturnValueOnce(poll.promise);
  const before = native.getStatus.mock.calls.length;
  await act(async () => {
    jest.advanceTimersByTime(3000);
  });
  expect(native.getStatus).toHaveBeenCalledTimes(before + 1);
  await act(async () => {
    poll.resolve(status());
  });
  await act(async () => {
    jest.advanceTimersByTime(750);
  });
  expect(native.getStatus).toHaveBeenCalledTimes(before + 2);
  await act(async () => {
    indexing.resolve({
      added: 0,
      updated: 0,
      deleted: 0,
      failed: 0,
      skipped: 0,
      indexedCount: 100,
    });
    await pending;
  });
});
