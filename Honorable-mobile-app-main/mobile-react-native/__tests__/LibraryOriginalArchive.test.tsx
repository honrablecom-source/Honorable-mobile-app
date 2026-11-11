import React from 'react';
import Renderer, { act } from 'react-test-renderer';
import { AppState, Modal } from 'react-native';
import {
  LibraryOriginalArchivePanel,
  validOriginalArchivePreview,
} from '../src/library/LibraryOriginalArchive';
import type {
  LibraryCollectionItem,
  LibraryOriginalArchivePreview,
} from '../src/native/HonorableNative';

const mockPrepare = jest.fn(),
  mockOpen = jest.fn(),
  mockRelease = jest.fn(),
  mockCancel = jest.fn();
let mockOwner = 'synthetic-owner';
jest.mock('../src/native/HonorableNative', () => ({
  honorableNative: {
    prepareLibraryOriginalArchive: (...a: unknown[]) => mockPrepare(...a),
    shareLibraryOriginalArchive: (...a: unknown[]) => mockOpen(...a),
    releaseLibraryOriginalArchive: (...a: unknown[]) => mockRelease(...a),
    cancelLibraryDuplicateCheck: (...a: unknown[]) => mockCancel(...a),
  },
}));
jest.mock('../src/passes/MemoryPassContext', () => ({
  useMemoryPass: () => ({
    signedIn: !!mockOwner,
    account: { accountId: mockOwner },
  }),
}));
const items: LibraryCollectionItem[] = [
  { id: 'zero', uri: 'content://synthetic/one', kind: 'IMAGE' },
  { id: 'two', uri: 'content://synthetic/two', kind: 'IMAGE' },
];
const archive: LibraryOriginalArchivePreview = {
  token: 'archive-one', bytes: 1200, sha256: 'a'.repeat(64),
  entries: [
    {name: 'originals/001.jpg', mime: 'image/jpeg', bytes: 300, sha256: 'b'.repeat(64)},
    {name: 'originals/002.mp4', mime: 'video/mp4', bytes: 400, sha256: 'c'.repeat(64)},
  ],
};
let tree: Renderer.ReactTestRenderer;
let onBusy: jest.Mock, canStart: jest.Mock, selectionCurrent: jest.Mock;
let props: React.ComponentProps<typeof LibraryOriginalArchivePanel>;
let changed: (state: string) => void;
const button = (label: string) =>
  tree.root.findAll(
    n =>
      n.props.accessibilityLabel === label &&
      typeof n.props.onPress === 'function',
  )[0];
const press = async (label: string) => {
  await act(async () => {
    await button(label).props.onPress();
  });
};
const mount = async (extra: Partial<typeof props> = {}) => {
  props = {
    items,
    names: { one: 'First photo', two: 'Second photo' },
    disabled: false,
    canStart,
    selectionCurrent,
    onBusy,
    ...extra,
  };
  await act(async () => {
    tree = Renderer.create(<LibraryOriginalArchivePanel {...props} />);
  });
};
const update = async (extra: Partial<typeof props> = {}) => {
  props = { ...props, ...extra };
  await act(async () => {
    tree.update(<LibraryOriginalArchivePanel {...props} />);
  });
};
const show = () => press('Create original archive');
const prepare = () => press('Prepare original archive');
const visible = () => tree.root.findByType(Modal).props.visible;
beforeEach(() => {
  jest.clearAllMocks();
  mockOwner = 'synthetic-owner';
  onBusy = jest.fn();
  canStart = jest.fn(() => true);
  selectionCurrent = jest.fn(() => true);
  mockPrepare.mockResolvedValue(archive);
  mockOpen.mockResolvedValue({ opened: true });
  jest.spyOn(AppState, 'addEventListener').mockImplementation((_, callback) => {
    changed = callback as (s: string) => void;
    return { remove: jest.fn() };
  });
});
afterEach(async () => {
  if (tree) await act(async () => tree.unmount());
  jest.restoreAllMocks();
});

test('explicit review prepares exact ordered originals and hands off only the prepared token', async () => {
  await mount({ items: [...items].reverse() });
  await show();
  expect(mockPrepare).not.toHaveBeenCalled();
  expect(mockOpen).not.toHaveBeenCalled();
  await prepare();
  expect(mockPrepare.mock.calls[0][0]).toEqual([...items].reverse());
  expect(mockPrepare.mock.calls[0][0][0]).not.toBe(items[1]);
  expect(button('Share prepared original archive')).toBeTruthy();
  await press('Share prepared original archive');
  expect(mockOpen).toHaveBeenCalledWith('archive-one', expect.any(String));
  expect(visible()).toBe(false);
  expect(mockRelease).not.toHaveBeenCalled();
  expect(onBusy.mock.calls.map(c => c[0])).toEqual([true, false, true, false]);
});

test.each([
  'guest',
  'disabled',
  'zero',
  'twenty-one',
  'duplicate',
  'admission',
  'selection',
] as const)('%s cannot enter PDF review or read media', async kind => {
  if (kind === 'guest') mockOwner = '';
  if (kind === 'admission') canStart.mockReturnValue(false);
  if (kind === 'selection') selectionCurrent.mockReturnValue(false);
  await mount(
    kind === 'disabled'
      ? { disabled: true }
      : kind === 'zero'
      ? { items: [] }
      : kind === 'twenty-one'
      ? {
          items: Array.from({ length: 21 }, (_, i) => ({
            id: `${i}`,
            uri: `content://synthetic/${i}`,
            kind: 'IMAGE',
          })),
        }
      : kind === 'duplicate'
      ? { items: [items[0], items[0]] }
      : {},
  );
  await show();
  expect(visible()).toBe(false);
  expect(mockPrepare).not.toHaveBeenCalled();
});

test('same-tick repeated prepare owns admission synchronously', async () => {
  let finish!: (value: LibraryOriginalArchivePreview) => void;
  mockPrepare.mockImplementation(
    () =>
      new Promise(resolve => {
        finish = resolve;
      }),
  );
  await mount();
  await show();
  const handler = button('Prepare original archive').props.onPress;
  act(() => {
    handler();
    handler();
  });
  expect(mockPrepare).toHaveBeenCalledTimes(1);
  expect(onBusy).toHaveBeenCalledWith(true);
  await act(async () => finish(archive));
  expect(onBusy.mock.calls.map(c => c[0])).toEqual([true, false]);
});

test('closing cancels the exact request and disposes late native output', async () => {
  let finish!: (value: LibraryOriginalArchivePreview) => void;
  mockPrepare.mockImplementation(
    () =>
      new Promise(resolve => {
        finish = resolve;
      }),
  );
  await mount();
  await show();
  act(() => {
    button('Prepare original archive').props.onPress();
  });
  const request = mockPrepare.mock.calls[0][1];
  await press('Stop preparing archive');
  expect(mockCancel).toHaveBeenCalledWith(request);
  await act(async () => finish(archive));
  expect(mockRelease).toHaveBeenCalledWith('archive-one');
  expect(visible()).toBe(false);
  expect(onBusy.mock.calls.map(c => c[0])).toEqual([true, false]);
});

test.each(['owner', 'order', 'source', 'background'] as const)(
  '%s change drops prepared PDF and rejects a retained Open callback',
  async kind => {
    await mount();
    await show();
    await prepare();
    const retained = button('Share prepared original archive').props.onPress;
    if (kind === 'owner') {
      mockOwner = 'other-owner';
      await update();
    }
    if (kind === 'order') await update({ items: [...items].reverse() });
    if (kind === 'source')
      await update({
        items: [items[0], { ...items[1], uri: 'content://synthetic/replaced' }],
      });
    if (kind === 'background') act(() => changed('background'));
    await act(async () => retained());
    expect(mockRelease).toHaveBeenCalledWith('archive-one');
    expect(mockOpen).not.toHaveBeenCalled();
    expect(visible()).toBe(false);
  },
);

test('a pending result cannot populate a new account review', async () => {
  let finish!: (value: LibraryOriginalArchivePreview) => void;
  mockPrepare.mockImplementationOnce(
    () =>
      new Promise(resolve => {
        finish = resolve;
      }),
  );
  await mount();
  await show();
  act(() => {
    button('Prepare original archive').props.onPress();
  });
  mockOwner = 'other-owner';
  await update();
  await show();
  await act(async () => finish(archive));
  expect(mockRelease).toHaveBeenCalledWith('archive-one');
  expect(button('Share prepared original archive')).toBeUndefined();
  expect(visible()).toBe(true);
});

test('same-tick selection mutation blocks even a retained Prepare callback', async () => {
  await mount();
  await show();
  const retained = button('Prepare original archive').props.onPress;
  selectionCurrent.mockReturnValue(false);
  await act(async () => retained());
  expect(mockPrepare).not.toHaveBeenCalled();
  expect(onBusy).not.toHaveBeenCalled();
});

test('native failures preserve truthful error and allow a new preparation', async () => {
  mockPrepare.mockRejectedValueOnce(Error('Synthetic unavailable photo'));
  await mount();
  await show();
  await prepare();
  expect(
    tree.root.findAllByProps({ accessibilityRole: 'alert' }).length,
  ).toBeGreaterThan(0);
  expect(button('Share prepared original archive')).toBeUndefined();
  await prepare();
  expect(mockPrepare).toHaveBeenCalledTimes(2);
  expect(button('Share prepared original archive')).toBeTruthy();
});

test('opening failure consumes the old lease instead of reopening an expired token', async () => {
  mockOpen.mockRejectedValueOnce(Error('Synthetic missing PDF viewer'));
  await mount();
  await show();
  await prepare();
  const retained = button('Share prepared original archive').props.onPress;
  await press('Share prepared original archive');
  await act(async () => retained());
  expect(mockOpen).toHaveBeenCalledTimes(1);
  expect(button('Share prepared original archive')).toBeUndefined();
  await prepare();
  expect(mockPrepare).toHaveBeenCalledTimes(2);
});

test('mismatched native entries are released and never offered for opening', async () => {
  mockPrepare.mockResolvedValueOnce({ ...archive, entries: archive.entries.slice(0, 1) });
  await mount();
  await show();
  await prepare();
  expect(mockRelease).toHaveBeenCalledWith('archive-one');
  expect(button('Share prepared original archive')).toBeUndefined();
});

test('changing original count while a preview exists does not render stale archive metadata', async () => {
  await mount();
  await show();
  await prepare();
  await update({
    items: [
      ...items,
      { id: 'three', uri: 'content://synthetic/three', kind: 'IMAGE' },
    ],
  });
  expect(visible()).toBe(false);
  expect(mockRelease).toHaveBeenCalledWith('archive-one');
});

test('retained callbacks cannot reopen a closed/reopened review', async () => {
  await mount();
  await show();
  const retained = button('Prepare original archive').props.onPress;
  await press('Close original archive');
  await show();
  await act(async () => retained());
  expect(mockPrepare).not.toHaveBeenCalled();
  await prepare();
  expect(mockPrepare).toHaveBeenCalledTimes(1);
});

test('prepared file is released on component removal', async () => {
  await mount();
  await show();
  await prepare();
  await act(async () => tree.unmount());
  expect(mockRelease).toHaveBeenCalledWith('archive-one');
});

test('archive schema rejects unsafe paths, unsupported MIME, reordered names, malformed checksums and impossible sizes', () => {
  expect(validOriginalArchivePreview(archive, 2)).toBe(true);
  for (const value of [
    { ...archive, bytes: 0 }, { ...archive, bytes: 257 * 1024 * 1024 + 1 },
    { ...archive, token: '' }, { ...archive, sha256: 'z'.repeat(64) },
    { ...archive, entries: [...archive.entries].reverse() },
    ...[
      {name: '../private.jpg'}, {name: 'originals/002.jpg'}, {mime: 'constructor'},
      {mime: 'image/unknown'}, {bytes: 0}, {bytes: 64 * 1024 * 1024 + 1},
      {bytes: 1.5}, {sha256: 'private raw value'},
    ].map(change => ({...archive, entries: [{...archive.entries[0], ...change}, archive.entries[1]]})),
    {...archive, bytes: 700},
  ]) expect(validOriginalArchivePreview(value as LibraryOriginalArchivePreview, 2)).toBe(false);
});
test('single original and mixed photo-video selection are admitted with explicit metadata warning', async () => {
  await mount({items: [items[0], {...items[1], kind: 'VIDEO'}]});
  await show();
  const text = JSON.stringify(tree.toJSON());
  expect(text).toContain('Original metadata');
  expect(text).toContain('not a sanitized copy');
  await prepare(); expect(mockPrepare.mock.calls[0][0][1].kind).toBe('VIDEO');
  await press('Close original archive');
  await update({items: items.slice(0, 1)}); await show();
  expect(visible()).toBe(true);
});
