import React from 'react';
import Renderer, { act } from 'react-test-renderer';
import { AppState, Modal } from 'react-native';
import {
  PhotoAlbumPdfPanel,
  validPhotoPdfPreview,
} from '../src/library/PhotoAlbumPdf';
import type {
  LibraryCollectionItem,
  PhotoAlbumPdfPreview,
} from '../src/native/HonorableNative';

const mockPrepare = jest.fn(),
  mockOpen = jest.fn(),
  mockRelease = jest.fn(),
  mockCancel = jest.fn();
let mockOwner = 'synthetic-owner';
jest.mock('../src/native/HonorableNative', () => ({
  honorableNative: {
    preparePhotoAlbumPdf: (...a: unknown[]) => mockPrepare(...a),
    openPhotoAlbumPdf: (...a: unknown[]) => mockOpen(...a),
    releasePhotoAlbumPdf: (...a: unknown[]) => mockRelease(...a),
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
  { id: 'one', uri: 'content://synthetic/one', kind: 'IMAGE' },
  { id: 'two', uri: 'content://synthetic/two', kind: 'IMAGE' },
];
const pdf: PhotoAlbumPdfPreview = {
  token: 'pdf-one',
  bytes: 1200,
  pages: [
    {
      width: 100,
      height: 200,
      sourceWidth: 100,
      sourceHeight: 200,
      sampled: false,
    },
    {
      width: 1024,
      height: 512,
      sourceWidth: 2048,
      sourceHeight: 1024,
      sampled: true,
    },
  ],
};
let tree: Renderer.ReactTestRenderer;
let onBusy: jest.Mock, canStart: jest.Mock, selectionCurrent: jest.Mock;
let props: React.ComponentProps<typeof PhotoAlbumPdfPanel>;
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
    tree = Renderer.create(<PhotoAlbumPdfPanel {...props} />);
  });
};
const update = async (extra: Partial<typeof props> = {}) => {
  props = { ...props, ...extra };
  await act(async () => {
    tree.update(<PhotoAlbumPdfPanel {...props} />);
  });
};
const show = () => press('Create photo PDF');
const prepare = () => press('Prepare photo PDF');
const visible = () => tree.root.findByType(Modal).props.visible;
beforeEach(() => {
  jest.clearAllMocks();
  mockOwner = 'synthetic-owner';
  onBusy = jest.fn();
  canStart = jest.fn(() => true);
  selectionCurrent = jest.fn(() => true);
  mockPrepare.mockResolvedValue(pdf);
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
  expect(button('Open prepared photo PDF')).toBeTruthy();
  await press('Open prepared photo PDF');
  expect(mockOpen).toHaveBeenCalledWith('pdf-one', expect.any(String));
  expect(visible()).toBe(false);
  expect(mockRelease).not.toHaveBeenCalled();
  expect(onBusy.mock.calls.map(c => c[0])).toEqual([true, false, true, false]);
});

test.each([
  'guest',
  'disabled',
  'one',
  'nine',
  'video',
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
      : kind === 'one'
      ? { items: items.slice(0, 1) }
      : kind === 'nine'
      ? {
          items: Array.from({ length: 9 }, (_, i) => ({
            id: `${i}`,
            uri: `content://synthetic/${i}`,
            kind: 'IMAGE',
          })),
        }
      : kind === 'video'
      ? { items: [items[0], { ...items[1], kind: 'VIDEO' }] }
      : kind === 'duplicate'
      ? { items: [items[0], items[0]] }
      : {},
  );
  await show();
  expect(visible()).toBe(false);
  expect(mockPrepare).not.toHaveBeenCalled();
});

test('same-tick repeated prepare owns admission synchronously', async () => {
  let finish!: (value: PhotoAlbumPdfPreview) => void;
  mockPrepare.mockImplementation(
    () =>
      new Promise(resolve => {
        finish = resolve;
      }),
  );
  await mount();
  await show();
  const handler = button('Prepare photo PDF').props.onPress;
  act(() => {
    handler();
    handler();
  });
  expect(mockPrepare).toHaveBeenCalledTimes(1);
  expect(onBusy).toHaveBeenCalledWith(true);
  await act(async () => finish(pdf));
  expect(onBusy.mock.calls.map(c => c[0])).toEqual([true, false]);
});

test('closing cancels the exact request and disposes late native output', async () => {
  let finish!: (value: PhotoAlbumPdfPreview) => void;
  mockPrepare.mockImplementation(
    () =>
      new Promise(resolve => {
        finish = resolve;
      }),
  );
  await mount();
  await show();
  act(() => {
    button('Prepare photo PDF').props.onPress();
  });
  const request = mockPrepare.mock.calls[0][1];
  await press('Stop preparing PDF');
  expect(mockCancel).toHaveBeenCalledWith(request);
  await act(async () => finish(pdf));
  expect(mockRelease).toHaveBeenCalledWith('pdf-one');
  expect(visible()).toBe(false);
  expect(onBusy.mock.calls.map(c => c[0])).toEqual([true, false]);
});

test.each(['owner', 'order', 'source', 'background'] as const)(
  '%s change drops prepared PDF and rejects a retained Open callback',
  async kind => {
    await mount();
    await show();
    await prepare();
    const retained = button('Open prepared photo PDF').props.onPress;
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
    expect(mockRelease).toHaveBeenCalledWith('pdf-one');
    expect(mockOpen).not.toHaveBeenCalled();
    expect(visible()).toBe(false);
  },
);

test('a pending result cannot populate a new account review', async () => {
  let finish!: (value: PhotoAlbumPdfPreview) => void;
  mockPrepare.mockImplementationOnce(
    () =>
      new Promise(resolve => {
        finish = resolve;
      }),
  );
  await mount();
  await show();
  act(() => {
    button('Prepare photo PDF').props.onPress();
  });
  mockOwner = 'other-owner';
  await update();
  await show();
  await act(async () => finish(pdf));
  expect(mockRelease).toHaveBeenCalledWith('pdf-one');
  expect(button('Open prepared photo PDF')).toBeUndefined();
  expect(visible()).toBe(true);
});

test('same-tick selection mutation blocks even a retained Prepare callback', async () => {
  await mount();
  await show();
  const retained = button('Prepare photo PDF').props.onPress;
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
  expect(button('Open prepared photo PDF')).toBeUndefined();
  await prepare();
  expect(mockPrepare).toHaveBeenCalledTimes(2);
  expect(button('Open prepared photo PDF')).toBeTruthy();
});

test('opening failure consumes the old lease instead of reopening an expired token', async () => {
  mockOpen.mockRejectedValueOnce(Error('Synthetic missing PDF viewer'));
  await mount();
  await show();
  await prepare();
  const retained = button('Open prepared photo PDF').props.onPress;
  await press('Open prepared photo PDF');
  await act(async () => retained());
  expect(mockOpen).toHaveBeenCalledTimes(1);
  expect(button('Open prepared photo PDF')).toBeUndefined();
  await prepare();
  expect(mockPrepare).toHaveBeenCalledTimes(2);
});

test('mismatched native pages are released and never offered for opening', async () => {
  mockPrepare.mockResolvedValueOnce({ ...pdf, pages: pdf.pages.slice(0, 1) });
  await mount();
  await show();
  await prepare();
  expect(mockRelease).toHaveBeenCalledWith('pdf-one');
  expect(button('Open prepared photo PDF')).toBeUndefined();
});

test('changing page count while a preview exists does not render stale page metadata', async () => {
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
  expect(mockRelease).toHaveBeenCalledWith('pdf-one');
});

test('retained callbacks cannot reopen a closed/reopened review', async () => {
  await mount();
  await show();
  const retained = button('Prepare photo PDF').props.onPress;
  await press('Close photo PDF');
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
  expect(mockRelease).toHaveBeenCalledWith('pdf-one');
});

test('native output shape enforces bounded complete pages and truthful sampled metadata', () => {
  expect(validPhotoPdfPreview(pdf, 2)).toBe(true);
  for (const value of [
    { ...pdf, bytes: 0 },
    { ...pdf, bytes: 16 * 1024 * 1024 + 1 },
    { ...pdf, token: '' },
    { ...pdf, pages: [{ ...pdf.pages[0], sampled: true }, pdf.pages[1]] },
    { ...pdf, pages: [{ ...pdf.pages[0], width: 1025 }, pdf.pages[1]] },
    { ...pdf, pages: [{ ...pdf.pages[0], sourceWidth: 32769 }, pdf.pages[1]] },
  ])
    expect(validPhotoPdfPreview(value, 2)).toBe(false);
});
