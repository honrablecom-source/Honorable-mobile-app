import React from 'react';
import Renderer, { act } from 'react-test-renderer';
import { AppState, Modal } from 'react-native';
import { LibraryIntegrityPanel } from '../src/library/LibraryIntegrity';
import type {
  LibraryCollectionItem,
  LibraryIntegritySnapshot,
} from '../src/native/HonorableNative';

const mockNative = jest.fn(),
  mockCancel = jest.fn();
let mockOwner = 'synthetic-owner';
jest.mock('../src/native/HonorableNative', () => ({
  honorableNative: {
    libraryIntegrity: (...args: unknown[]) => mockNative(...args),
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
];
const snapshot: LibraryIntegritySnapshot = {
  owner: 'private-synthetic-owner',
  baselines: [{ id: 'before', name: 'Before', recordedAt: 1000, count: 1 }],
};
const verified: LibraryIntegritySnapshot = {
  ...snapshot,
  verification: {
    baselineId: 'before',
    checkedAt: 2000,
    items: [
      {
        id: '1',
        label: 'Photo.jpg',
        status: 'MATCH',
        previousBytes: 3,
        currentBytes: 3,
      },
    ],
  },
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
const input = () =>
  tree.root.findAll(
    node =>
      node.props.accessibilityLabel === 'Integrity baseline name' &&
      typeof node.props.onChangeText === 'function',
  )[0];
async function mount() {
  await act(async () => {
    tree = Renderer.create(<LibraryIntegrityPanel {...props()} />);
  });
}
async function open() {
  await act(async () => button('Private integrity baselines').props.onPress());
}
beforeEach(() => {
  jest.clearAllMocks();
  mockOwner = 'synthetic-owner';
  mockNative.mockReset().mockResolvedValue(snapshot);
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

test('never reads or opens metadata automatically and explicitly lists the current private shelf', async () => {
  await mount();
  expect(mockNative).not.toHaveBeenCalled();
  await open();
  expect(mockNative).toHaveBeenCalledWith(
    'list',
    '',
    '',
    '',
    [],
    expect.stringMatching(/^noble-integrity-\d+-\d+$/),
  );
  expect(button('Verify baseline Before')).toBeDefined();
});
test('records a new baseline from exact selected references and trimmed name, never client hashes', async () => {
  await mount();
  await open();
  await act(async () => input().props.onChangeText('  My baseline  '));
  await act(async () =>
    button('Record selected original fingerprints').props.onPress(),
  );
  expect(mockNative).toHaveBeenLastCalledWith(
    'create',
    snapshot.owner,
    '',
    'My baseline',
    items,
    expect.any(String),
  );
  expect(input().props.value).toBe('');
  expect(onBusy).toHaveBeenLastCalledWith(false);
});
test('same-tick repeated recording cannot create a second request', async () => {
  await mount();
  await open();
  await act(async () => input().props.onChangeText('New baseline'));
  let resolve!: (value: LibraryIntegritySnapshot) => void;
  mockNative.mockImplementationOnce(
    () =>
      new Promise(done => {
        resolve = done;
      }),
  );
  const save = button('Record selected original fingerprints').props.onPress;
  await act(async () => {
    save();
    save();
  });
  expect(mockNative).toHaveBeenCalledTimes(2);
  await act(async () => resolve(snapshot));
  expect(onBusy).toHaveBeenLastCalledWith(false);
});
test('verification uses the saved baseline ID rather than current selection and never rewrites hashes', async () => {
  await mount();
  await open();
  mockNative.mockResolvedValueOnce(verified);
  await act(async () => button('Verify baseline Before').props.onPress());
  expect(mockNative).toHaveBeenLastCalledWith(
    'verify',
    snapshot.owner,
    'before',
    '',
    [],
    expect.any(String),
  );
  const output = JSON.stringify(tree.toJSON());
  expect(output).toContain('Matches recorded bytes');
  expect(output).toContain('Photo.jpg');
  expect(
    mockNative.mock.calls.filter(call => call[0] === 'create'),
  ).toHaveLength(0);
});
test('failed verification clears a prior success and leaves the recorded shelf unchanged', async () => {
  await mount();
  await open();
  mockNative.mockResolvedValueOnce(verified);
  await act(async () => button('Verify baseline Before').props.onPress());
  mockNative.mockRejectedValueOnce(new Error('Provider unavailable'));
  await act(async () => button('Verify baseline Before').props.onPress());
  const output = JSON.stringify(tree.toJSON());
  expect(output).not.toContain('Matches recorded bytes');
  expect(output).toContain('Provider unavailable');
  expect(button('Verify baseline Before')).toBeDefined();
});
test('missing, changed and unreadable statuses stay distinct in the report', async () => {
  await mount();
  await open();
  mockNative.mockResolvedValueOnce({
    ...snapshot,
    verification: {
      baselineId: 'before',
      checkedAt: 2000,
      items: [
        {
          id: '1',
          label: 'One',
          status: 'CHANGED',
          previousBytes: 3,
          currentBytes: 4,
        },
        { id: '2', label: 'Two', status: 'MISSING', previousBytes: 3 },
        { id: '3', label: 'Three', status: 'UNAVAILABLE', previousBytes: 3 },
      ],
    },
  });
  await act(async () => button('Verify baseline Before').props.onPress());
  const output = JSON.stringify(tree.toJSON());
  expect(output).toContain('Bytes changed');
  expect(output).toContain('Saved identity missing');
  expect(output).toContain('Not checked: unreadable');
});
test('removal needs a separate review and removes only the chosen baseline reference', async () => {
  await mount();
  await open();
  await act(async () => button('Remove baseline Before').props.onPress());
  expect(mockNative).toHaveBeenCalledTimes(1);
  await act(async () => button('Keep integrity baseline').props.onPress());
  expect(mockNative).toHaveBeenCalledTimes(1);
  await act(async () => button('Remove baseline Before').props.onPress());
  mockNative.mockResolvedValueOnce({ owner: snapshot.owner, baselines: [] });
  await act(async () => button('Remove recorded baseline').props.onPress());
  expect(mockNative).toHaveBeenLastCalledWith(
    'delete',
    snapshot.owner,
    'before',
    '',
    [],
    expect.any(String),
  );
});
test('cancellation is owned and late completion cannot repopulate a closed shelf', async () => {
  await mount();
  let resolve!: (value: LibraryIntegritySnapshot) => void;
  mockNative.mockImplementationOnce(
    () =>
      new Promise(done => {
        resolve = done;
      }),
  );
  await open();
  const id = mockNative.mock.calls[0][5];
  await act(async () => button('Cancel integrity work').props.onPress());
  expect(mockCancel).toHaveBeenCalledWith(id);
  await act(async () => resolve(snapshot));
  expect(tree.root.findByType(Modal).props.visible).toBe(false);
});
test('background and account changes close private details and cancel only owned work', async () => {
  await mount();
  let resolve!: (value: LibraryIntegritySnapshot) => void;
  mockNative.mockImplementationOnce(
    () =>
      new Promise(done => {
        resolve = done;
      }),
  );
  await open();
  await act(async () => lifecycle('background'));
  expect(mockCancel).toHaveBeenCalledTimes(1);
  mockOwner = 'another-owner';
  await act(async () => tree.update(<LibraryIntegrityPanel {...props()} />));
  await act(async () => resolve(snapshot));
  expect(tree.root.findByType(Modal).props.visible).toBe(false);
});
test('an old row cannot verify or remove after a replacement catalog or account dismissal', async () => {
  await mount();
  await open();
  const verify = button('Verify baseline Before').props.onPress,
    remove = button('Remove baseline Before').props.onPress;
  await act(async () => input().props.onChangeText('Other'));
  mockNative.mockResolvedValueOnce({
    ...snapshot,
    baselines: [
      ...snapshot.baselines,
      { id: 'other', name: 'Other', count: 1, recordedAt: 1000 },
    ],
  });
  await act(async () =>
    button('Record selected original fingerprints').props.onPress(),
  );
  const count = mockNative.mock.calls.length;
  await act(async () => {
    verify();
    remove();
  });
  expect(mockNative).toHaveBeenCalledTimes(count);
  expect(button('Verify baseline Other')).toBeDefined();
});
test('retained cancel cannot dismiss a successor request and shared admission prevents racing tools', async () => {
  await mount();
  let first!: (value: LibraryIntegritySnapshot) => void,
    second!: (value: LibraryIntegritySnapshot) => void;
  mockNative.mockImplementationOnce(
    () =>
      new Promise(done => {
        first = done;
      }),
  );
  await open();
  const oldCancel = button('Cancel integrity work').props.onPress;
  await act(async () => oldCancel());
  mockNative.mockImplementationOnce(
    () =>
      new Promise(done => {
        second = done;
      }),
  );
  await open();
  await act(async () => oldCancel());
  expect(mockCancel).toHaveBeenCalledTimes(1);
  await act(async () => {
    second(snapshot);
    first(snapshot);
  });
  expect(tree.root.findByType(Modal).props.visible).toBe(true);
  await act(async () => button('Close integrity shelf').props.onPress());
  canStart.mockReturnValue(false);
  await open();
  expect(mockNative).toHaveBeenCalledTimes(2);
});
test('failed recording preserves name and no-match semantics for explicit retry', async () => {
  await mount();
  await open();
  await act(async () => input().props.onChangeText('Before'));
  mockNative.mockRejectedValueOnce(
    new Error('Every original must be fully checked'),
  );
  await act(async () =>
    button('Record selected original fingerprints').props.onPress(),
  );
  expect(input().props.value).toBe('Before');
  expect(JSON.stringify(tree.toJSON())).toContain(
    'Every original must be fully checked',
  );
  expect(
    mockNative.mock.calls.filter(call => call[0] === 'delete'),
  ).toHaveLength(0);
});

test('unmount cancels its pending native request and ignores a late private shelf', async () => {
  await mount();
  let resolve!: (value: LibraryIntegritySnapshot) => void;
  mockNative.mockImplementationOnce(
    () =>
      new Promise(done => {
        resolve = done;
      }),
  );
  await open();
  const id = mockNative.mock.calls[0][5];
  await act(async () => tree.unmount());
  const changes = onBusy.mock.calls.length;
  expect(mockCancel).toHaveBeenCalledWith(id);
  await act(async () => resolve(snapshot));
  expect(onBusy).toHaveBeenCalledTimes(changes);
});

test('a mismatched native owner response cannot replace the visible private shelf', async () => {
  await mount();
  await open();
  await act(async () => input().props.onChangeText('New baseline'));
  mockNative.mockResolvedValueOnce({
    owner: 'other-owner',
    baselines: [
      {
        id: 'private-other',
        name: 'Other private name',
        count: 1,
        recordedAt: 1000,
      },
    ],
  });
  await act(async () =>
    button('Record selected original fingerprints').props.onPress(),
  );
  expect(input().props.value).toBe('New baseline');
  const output = JSON.stringify(tree.toJSON());
  expect(output).toContain('Account changed. Reopen integrity baselines.');
  expect(output).not.toContain('Other private name');
});
