import React from 'react';
import Renderer, { act } from 'react-test-renderer';
import { AppState, Modal } from 'react-native';
import { LibraryDuplicateCheck } from '../src/library/LibraryDuplicateCheck';
import type {
  LibraryCollectionItem,
  LibraryDuplicateReport,
} from '../src/native/HonorableNative';

const mockCompare = jest.fn(),
  mockCancel = jest.fn();
let mockOwner = 'synthetic-owner';
jest.mock('../src/native/HonorableNative', () => ({
  honorableNative: {
    compareLibraryOriginals: (...args: unknown[]) => mockCompare(...args),
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
  { id: '1', uri: 'content://synthetic/photo', kind: 'IMAGE' },
  { id: '2', uri: 'content://synthetic/video', kind: 'VIDEO' },
];
const report: LibraryDuplicateReport = {
  bytesRead: 6,
  checked: items.map(item => ({
    id: item.id,
    bytes: 3,
    sha256: 'a'.repeat(64),
  })),
  skipped: [],
  groups: [{ bytes: 3, sha256: 'a'.repeat(64), ids: ['1', '2'] }],
};
const onBusy = jest.fn(),
  canStart = jest.fn();
let lifecycle: (state: string) => void;
let tree: Renderer.ReactTestRenderer;
const label = 'Check selected originals for exact duplicates';
const button = (name: string) =>
  tree.root.findAll(
    node =>
      node.props.accessibilityLabel === name &&
      typeof node.props.onPress === 'function',
  )[0];
const props = () => ({ items, disabled: false, canStart, onBusy });
async function mount() {
  await act(async () => {
    tree = Renderer.create(<LibraryDuplicateCheck {...props()} />);
  });
}
async function start() {
  await act(async () => button(label).props.onPress());
}
beforeEach(() => {
  jest.clearAllMocks();
  mockOwner = 'synthetic-owner';
  canStart.mockReturnValue(true);
  mockCompare.mockReset().mockResolvedValue(report);
  mockCancel.mockReset();
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

test('never reads automatically and requires at least two selected originals', async () => {
  await mount();
  expect(mockCompare).not.toHaveBeenCalled();
  expect(button(label).props.disabled).toBe(false);
  await act(async () =>
    tree.update(<LibraryDuplicateCheck {...props()} items={[items[0]]} />),
  );
  expect(button(label).props.disabled).toBe(true);
  await act(async () => button(label).props.onPress());
  expect(mockCompare).not.toHaveBeenCalled();
});
test('explicit comparison sends only exact selected identities and a unique bounded request', async () => {
  await mount();
  await start();
  expect(mockCompare).toHaveBeenCalledWith(
    items,
    expect.stringMatching(/^noble-duplicates-\d+-\d+$/),
  );
  expect(mockCompare.mock.calls[0][0]).not.toBe(items);
  expect(tree.root.findByType(Modal).props.visible).toBe(true);
  expect(button('Close duplicate report')).toBeDefined();
  expect(onBusy).toHaveBeenLastCalledWith(false);
});
test('same-tick repeated starts cannot create another native request', async () => {
  await mount();
  let resolve!: (value: LibraryDuplicateReport) => void;
  mockCompare.mockImplementationOnce(
    () =>
      new Promise(done => {
        resolve = done;
      }),
  );
  const run = button(label).props.onPress;
  await act(async () => {
    run();
    run();
  });
  expect(mockCompare).toHaveBeenCalledTimes(1);
  await act(async () => resolve(report));
  expect(onBusy).toHaveBeenLastCalledWith(false);
});
test('shared pending admission prevents a competing same-tick Library operation', async () => {
  await mount();
  canStart.mockReturnValue(false);
  await start();
  expect(mockCompare).not.toHaveBeenCalled();
  expect(onBusy).not.toHaveBeenCalled();
});
test('cancel addresses only this native request and ignores its late report', async () => {
  await mount();
  let resolve!: (value: LibraryDuplicateReport) => void;
  mockCompare.mockImplementationOnce(
    () =>
      new Promise(done => {
        resolve = done;
      }),
  );
  await start();
  const request = mockCompare.mock.calls[0][1];
  await act(async () => button('Cancel duplicate check').props.onPress());
  expect(mockCancel).toHaveBeenCalledWith(request);
  await act(async () => resolve(report));
  expect(tree.root.findByType(Modal).props.visible).toBe(false);
  expect(onBusy).toHaveBeenLastCalledWith(false);
});
test('an old cancel callback cannot cancel or dismiss a successor report', async () => {
  await mount();
  let first!: (value: LibraryDuplicateReport) => void,
    second!: (value: LibraryDuplicateReport) => void;
  mockCompare.mockImplementationOnce(
    () =>
      new Promise(done => {
        first = done;
      }),
  );
  await start();
  const oldCancel = button('Cancel duplicate check').props.onPress;
  await act(async () => oldCancel());
  mockCompare.mockImplementationOnce(
    () =>
      new Promise(done => {
        second = done;
      }),
  );
  await start();
  await act(async () => oldCancel());
  expect(mockCancel).toHaveBeenCalledTimes(1);
  expect(tree.root.findByType(Modal).props.visible).toBe(true);
  await act(async () => {
    second(report);
    first(report);
  });
  expect(button('Close duplicate report')).toBeDefined();
});
test('backgrounding cancels active IO and clears private results', async () => {
  await mount();
  let resolve!: (value: LibraryDuplicateReport) => void;
  mockCompare.mockImplementationOnce(
    () =>
      new Promise(done => {
        resolve = done;
      }),
  );
  await start();
  await act(async () => lifecycle('background'));
  expect(mockCancel).toHaveBeenCalledWith(mockCompare.mock.calls[0][1]);
  await act(async () => resolve(report));
  expect(tree.root.findByType(Modal).props.visible).toBe(false);
});
test('account changes cancel old-owner work and refuse a returned old-owner report', async () => {
  await mount();
  let resolve!: (value: LibraryDuplicateReport) => void;
  mockCompare.mockImplementationOnce(
    () =>
      new Promise(done => {
        resolve = done;
      }),
  );
  await start();
  mockOwner = 'another-owner';
  await act(async () => tree.update(<LibraryDuplicateCheck {...props()} />));
  expect(mockCancel).toHaveBeenCalledTimes(1);
  await act(async () => resolve(report));
  expect(tree.root.findByType(Modal).props.visible).toBe(false);
});
test('unmount cancels owned work without reporting a late completion', async () => {
  await mount();
  let resolve!: (value: LibraryDuplicateReport) => void;
  mockCompare.mockImplementationOnce(
    () =>
      new Promise(done => {
        resolve = done;
      }),
  );
  await start();
  await act(async () => tree.unmount());
  const changes = onBusy.mock.calls.length;
  await act(async () => resolve(report));
  expect(mockCancel).toHaveBeenCalledTimes(1);
  expect(onBusy).toHaveBeenCalledTimes(changes);
});
test('native failure leaves a visible retry path without a false successful report', async () => {
  await mount();
  mockCompare.mockRejectedValueOnce(new Error('Original access changed'));
  await start();
  expect(
    tree.root
      .findAllByProps({ accessibilityRole: 'alert' })
      .some(node => node.props.children === 'Original access changed'),
  ).toBe(true);
  await start();
  expect(button('Close duplicate report')).toBeDefined();
  expect(mockCompare).toHaveBeenCalledTimes(2);
});
test('skipped originals are disclosed separately from checked files and matching groups', async () => {
  await mount();
  mockCompare.mockResolvedValueOnce({
    bytesRead: 3,
    checked: [report.checked[0]],
    groups: [],
    skipped: [{ id: '2', reason: 'FILE_LIMIT' }],
  });
  await start();
  const json = JSON.stringify(tree.toJSON());
  expect(json).toContain('Skipped files were not compared');
  expect(json).toContain('per-file read limit');
});
test('a lost cancel transport still releases local ownership and suppresses late output', async () => {
  await mount();
  let resolve!: (value: LibraryDuplicateReport) => void;
  mockCompare.mockImplementationOnce(
    () =>
      new Promise(done => {
        resolve = done;
      }),
  );
  mockCancel.mockImplementationOnce(() => {
    throw Error('Bridge already closed');
  });
  await start();
  await act(async () => button('Cancel duplicate check').props.onPress());
  await act(async () => resolve(report));
  expect(tree.root.findByType(Modal).props.visible).toBe(false);
  expect(onBusy).toHaveBeenLastCalledWith(false);
});

test('display names stay in the reviewed UI snapshot and never enter native hashing arguments', async () => {
  await act(async () => {
    tree = Renderer.create(
      <LibraryDuplicateCheck
        {...props()}
        displayNames={{ '1': 'Photo one.jpg', '2': 'Clip two.mp4' }}
      />,
    );
  });
  let resolve!: (value: LibraryDuplicateReport) => void;
  mockCompare.mockImplementationOnce(
    () =>
      new Promise(done => {
        resolve = done;
      }),
  );
  await start();
  await act(async () =>
    tree.update(
      <LibraryDuplicateCheck
        {...props()}
        displayNames={{ '1': 'Different later label' }}
      />,
    ),
  );
  await act(async () => resolve(report));
  expect(mockCompare.mock.calls[0][0]).toEqual(items);
  expect(JSON.stringify(mockCompare.mock.calls[0])).not.toContain(
    'Photo one.jpg',
  );
  const output = JSON.stringify(tree.toJSON());
  expect(output).toContain('Photo one.jpg');
  expect(output).not.toContain('Different later label');
});
