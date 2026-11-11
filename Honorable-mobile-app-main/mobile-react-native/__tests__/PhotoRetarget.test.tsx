import React from 'react';
import Renderer, { act } from 'react-test-renderer';
import { AppState, Image, Modal } from 'react-native';
import { PhotoRetargetPanel } from '../src/library/PhotoPixelDifference';
import type {
  LibraryCollectionItem,
  PhotoDifferencePreview,
} from '../src/native/HonorableNative';

const mockPrepare = jest.fn(),
  mockRelease = jest.fn(),
  mockOpen = jest.fn(),
  mockCancel = jest.fn();
let mockOwner = 'synthetic-owner';
jest.mock('../src/native/HonorableNative', () => ({
  honorableNative: {
    preparePhotoRetarget: (...args: unknown[]) => mockPrepare(...args),
    releasePhotoDifference: (...args: unknown[]) => mockRelease(...args),
    openPhotoDifference: (...args: unknown[]) => mockOpen(...args),
    cancelLibraryDuplicateCheck: (...args: unknown[]) => mockCancel(...args),
  },
}));
jest.mock('../src/passes/MemoryPassContext', () => ({
  useMemoryPass: () => ({
    signedIn: !!mockOwner,
    account: { accountId: mockOwner },
  }),
}));
const items: LibraryCollectionItem[] = [
  { id: '1', uri: 'content://synthetic/one', kind: 'IMAGE' },
];
const preview: PhotoDifferencePreview = {
  operation: 'retarget',
  frames: 1,
  removedColumns: 1,
  token: 'preview-one',
  previewUri: 'content://synthetic.creation/map-one',
  width: 2,
  height: 1,
  sourceWidth: 3,
  sourceHeight: 1,
  sampled: false,
  threshold: 0,
  changedPixels: 1,
  maximumDelta: 255,
  meanDelta: 127.5,
};
const onBusy = jest.fn(),
  canStart = jest.fn();
let lifecycle: (state: string) => void;
let tree: Renderer.ReactTestRenderer;
const props = () => ({ items, disabled: false, canStart, onBusy });
const button = (name: string) =>
  tree.root.findAll(
    node =>
      node.props.accessibilityLabel === name &&
      typeof node.props.onPress === 'function',
  )[0];
async function mount() {
  await act(async () => {
    tree = Renderer.create(<PhotoRetargetPanel {...props()} />);
  });
}
async function show() {
  await act(async () =>
    button('Create content-aware narrowed photo').props.onPress(),
  );
}
async function prepare() {
  await act(async () => button('Prepare narrowed photo').props.onPress());
}
beforeEach(() => {
  jest.clearAllMocks();
  mockOwner = 'synthetic-owner';
  mockPrepare.mockReset().mockResolvedValue(preview);
  mockOpen.mockReset().mockResolvedValue({ opened: true });
  mockRelease.mockReset();
  mockCancel.mockReset();
  canStart.mockReturnValue(true);
  jest
    .spyOn(AppState, 'addEventListener')
    .mockImplementation((_kind, listener) => {
      lifecycle = listener as typeof lifecycle;
      return { remove: jest.fn() };
    });
});
afterEach(async () => {
  if (tree) await act(async () => tree.unmount());
  jest.restoreAllMocks();
});

test('review opening never reads photos or mounts an image before explicit preparation', async () => {
  await mount();
  await show();
  expect(mockPrepare).not.toHaveBeenCalled();
  expect(tree.root.findAllByType(Image)).toHaveLength(0);
  expect(tree.root.findByType(Modal).props.visible).toBe(true);
});
test('only an odd aligned photo stack are admitted', async () => {
  await mount();
  await act(async () =>
    tree.update(
      <PhotoRetargetPanel
        {...props()}
        items={[items[0], { ...items[1], kind: 'VIDEO' }]}
      />,
    ),
  );
  expect(button('Create content-aware narrowed photo').props.disabled).toBe(
    true,
  );
  await show();
  expect(mockPrepare).not.toHaveBeenCalled();
  expect(tree.root.findByType(Modal).props.visible).toBe(false);
});
test('preparation snapshots exact identities and renders only the returned derived PNG', async () => {
  await mount();
  await show();
  await prepare();
  expect(mockPrepare).toHaveBeenCalledWith(
    items,
    10,
    expect.stringMatching(/^noble-pixel-prepare-\d+-\d+$/),
  );
  expect(mockPrepare.mock.calls[0][0]).not.toBe(items);
  expect(tree.root.findByType(Image).props.source).toEqual({
    uri: preview.previewUri,
  });
  expect(onBusy).toHaveBeenLastCalledWith(false);
});
test('same-tick duplicate preparation and competing parent operations cannot start', async () => {
  await mount();
  await show();
  let resolve!: (value: PhotoDifferencePreview) => void;
  mockPrepare.mockImplementationOnce(
    () =>
      new Promise(done => {
        resolve = done;
      }),
  );
  const run = button('Prepare narrowed photo').props.onPress;
  await act(async () => {
    run();
    run();
  });
  expect(mockPrepare).toHaveBeenCalledTimes(1);
  await act(async () => resolve(preview));
  canStart.mockReturnValue(false);
  await prepare();
  expect(mockPrepare).toHaveBeenCalledTimes(1);
});
test('closing releases only the owned staged preview and removes its image', async () => {
  await mount();
  await show();
  await prepare();
  await act(async () => button('Close narrowed preview').props.onPress());
  expect(mockRelease).toHaveBeenCalledWith(preview.token);
  expect(tree.root.findAllByType(Image)).toHaveLength(0);
  expect(tree.root.findByType(Modal).props.visible).toBe(false);
});
test('cancelled preparation releases its late result without repopulating the view', async () => {
  await mount();
  await show();
  let resolve!: (value: PhotoDifferencePreview) => void;
  mockPrepare.mockImplementationOnce(
    () =>
      new Promise(done => {
        resolve = done;
      }),
  );
  await prepare();
  await act(async () => button('Cancel photo narrowing').props.onPress());
  expect(mockCancel).toHaveBeenCalledWith(mockPrepare.mock.calls[0][2]);
  await act(async () => resolve(preview));
  expect(mockRelease).toHaveBeenCalledWith(preview.token);
  expect(tree.root.findByType(Modal).props.visible).toBe(false);
});
test.each(['owner', 'selection'])(
  'a changed %s invalidates pending source ownership and drops late PNGs',
  async kind => {
    await mount();
    await show();
    let resolve!: (value: PhotoDifferencePreview) => void;
    mockPrepare.mockImplementationOnce(
      () =>
        new Promise(done => {
          resolve = done;
        }),
    );
    await prepare();
    if (kind === 'owner') mockOwner = 'another-owner';
    await act(async () =>
      tree.update(
        <PhotoRetargetPanel
          {...props()}
          items={
            kind === 'selection'
              ? [{ ...items[0], uri: 'content://synthetic/changed' }]
              : items
          }
        />,
      ),
    );
    await act(async () => resolve(preview));
    expect(mockCancel).toHaveBeenCalledTimes(1);
    expect(mockRelease).toHaveBeenCalledWith(preview.token);
    expect(tree.root.findAllByType(Image)).toHaveLength(0);
  },
);
test('backgrounding releases staged private output', async () => {
  await mount();
  await show();
  await prepare();
  await act(async () => lifecycle('background'));
  expect(mockRelease).toHaveBeenCalledWith(preview.token);
  expect(tree.root.findAllByType(Image)).toHaveLength(0);
});
test('explicit Open consumes the preview through a distinct owned native request', async () => {
  await mount();
  await show();
  await prepare();
  expect(mockOpen).not.toHaveBeenCalled();
  await act(async () =>
    button('Open narrowed photo as separate photo').props.onPress(),
  );
  expect(mockOpen).toHaveBeenCalledWith(
    preview.token,
    expect.stringMatching(/^noble-pixel-open-\d+-\d+$/),
  );
  expect(mockRelease).not.toHaveBeenCalledWith(preview.token);
  expect(tree.root.findByType(Modal).props.visible).toBe(false);
});
test('failed workspace opening clears the expired preview and keeps an explicit retry', async () => {
  await mount();
  await show();
  await prepare();
  mockOpen.mockRejectedValueOnce(new Error('Preview expired'));
  await act(async () =>
    button('Open narrowed photo as separate photo').props.onPress(),
  );
  expect(mockRelease).toHaveBeenCalledWith(preview.token);
  expect(tree.root.findAllByType(Image)).toHaveLength(0);
  expect(JSON.stringify(tree.toJSON())).toContain('Preview expired');
  expect(button('Prepare narrowed photo').props.disabled).toBe(false);
});
test('retained Cancel cannot dismiss or cancel a successor preparation', async () => {
  await mount();
  await show();
  let first!: (value: PhotoDifferencePreview) => void,
    second!: (value: PhotoDifferencePreview) => void;
  mockPrepare.mockImplementationOnce(
    () =>
      new Promise(done => {
        first = done;
      }),
  );
  await prepare();
  const oldCancel = button('Cancel photo narrowing').props.onPress;
  await act(async () => oldCancel());
  await show();
  mockPrepare.mockImplementationOnce(
    () =>
      new Promise(done => {
        second = done;
      }),
  );
  await prepare();
  await act(async () => oldCancel());
  expect(mockCancel).toHaveBeenCalledTimes(1);
  await act(async () => {
    first(preview);
    second({ ...preview, token: 'successor' });
  });
  expect(tree.root.findByType(Modal).props.visible).toBe(true);
  expect(mockRelease).toHaveBeenCalledWith(preview.token);
  expect(mockRelease).not.toHaveBeenCalledWith('successor');
});
test('unmount cancels pending work and releases any subsequently returned staged file', async () => {
  await mount();
  await show();
  let resolve!: (value: PhotoDifferencePreview) => void;
  mockPrepare.mockImplementationOnce(
    () =>
      new Promise(done => {
        resolve = done;
      }),
  );
  await prepare();
  await act(async () => tree.unmount());
  const calls = onBusy.mock.calls.length;
  await act(async () => resolve(preview));
  expect(mockRelease).toHaveBeenCalledWith(preview.token);
  expect(onBusy).toHaveBeenCalledTimes(calls);
});

test('incorrect native operation or frame count releases output before preview', async () => {
  await mount();
  await show();
  mockPrepare.mockResolvedValueOnce({ ...preview, frames: 5 });
  await prepare();
  expect(mockRelease).toHaveBeenCalledWith(preview.token);
  expect(tree.root.findAllByType(Image)).toHaveLength(0);
  mockPrepare.mockResolvedValueOnce({ ...preview, operation: 'difference' });
  await prepare();
  expect(tree.root.findAllByType(Image)).toHaveLength(0);
});
test.each([0, 2, 3, 5])('refuses %i captures before reading', async n => {
  await mount();
  await act(async () =>
    tree.update(
      <PhotoRetargetPanel
        {...props()}
        items={Array.from({ length: n }, (_, i) => ({
          ...items[0],
          id: String(i),
          uri: `content://synthetic/${i}`,
        }))}
      />,
    ),
  );
  expect(button('Create content-aware narrowed photo').props.disabled).toBe(
    true,
  );
  await show();
  expect(mockPrepare).not.toHaveBeenCalled();
});
test('retained Prepare cannot read replacement captures after selection changes', async () => {
  await mount();
  await show();
  const oldPrepare = button('Prepare narrowed photo').props.onPress;
  await act(async () =>
    tree.update(
      <PhotoRetargetPanel
        {...props()}
        items={items.map(it => ({ ...it, uri: it.uri + '/replaced' }))}
      />,
    ),
  );
  await act(async () => oldPrepare());
  expect(mockPrepare).not.toHaveBeenCalled();
});

const narrowingInput = () =>
  tree.root.findAll(
    n =>
      n.props.accessibilityLabel === 'Narrowing percent' &&
      typeof n.props.onChangeText === 'function',
  )[0];
test('invalid percent refuses private reads and valid percent is passed unchanged', async () => {
  await mount();
  await show();
  await act(async () => narrowingInput().props.onChangeText('13'));
  await prepare();
  expect(mockPrepare).not.toHaveBeenCalled();
  expect(JSON.stringify(tree.toJSON())).toContain(
    'Use a whole narrowing percent from 1 to 12.',
  );
  await act(async () => narrowingInput().props.onChangeText('5'));
  await prepare();
  expect(mockPrepare).toHaveBeenCalledWith(items, 5, expect.any(String));
});
test.each([0, undefined, 129, 1.5])(
  'invalid removed-column metadata %s never renders',
  async removedColumns => {
    await mount();
    await show();
    mockPrepare.mockResolvedValueOnce({ ...preview, removedColumns });
    await prepare();
    expect(mockRelease).toHaveBeenCalledWith(preview.token);
    expect(tree.root.findAllByType(Image)).toHaveLength(0);
  },
);
test('changing percent drops old preview and stale image failure cannot clear successor', async () => {
  await mount();
  await show();
  await prepare();
  const stale = tree.root.findByType(Image).props.onError;
  await act(async () => narrowingInput().props.onChangeText('5'));
  expect(mockRelease).toHaveBeenCalledWith(preview.token);
  mockPrepare.mockResolvedValueOnce({
    ...preview,
    token: 'successor',
    previewUri: 'content://synthetic.creation/successor',
  });
  await prepare();
  await act(async () => stale());
  expect(tree.root.findByType(Image).props.source.uri).toBe(
    'content://synthetic.creation/successor',
  );
});
