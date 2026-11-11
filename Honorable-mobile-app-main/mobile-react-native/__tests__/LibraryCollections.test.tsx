import React from 'react';
import Renderer, { act } from 'react-test-renderer';
import { AppState, Modal } from 'react-native';
import {
  LibraryCollectionsPanel,
  collectionEntries,
} from '../src/library/LibraryCollections';
import type {
  LibraryCollection,
  LibraryCollectionItem,
} from '../src/native/HonorableNative';
import { prepareMemoryEntries } from '../src/library/memoryRows';

const mockNative = jest.fn();
let mockOwner = 'synthetic-owner';
jest.mock('../src/native/HonorableNative', () => ({
  honorableNative: {
    libraryCollections: (...args: unknown[]) => mockNative(...args),
  },
}));
jest.mock('../src/passes/MemoryPassContext', () => ({
  useMemoryPass: () => ({
    signedIn: !!mockOwner,
    account: { accountId: mockOwner },
  }),
}));
const chosen: LibraryCollectionItem[] = [
  { id: '1', uri: 'content://synthetic/photo', kind: 'IMAGE' },
];
const saved: LibraryCollection = {
  id: 'collection-1',
  name: 'Trip',
  items: chosen,
};
let tree: Renderer.ReactTestRenderer;
const onOpen = jest.fn(),
  onDelete = jest.fn(),
  onBusy = jest.fn(),
  onReset = jest.fn();
let lifecycle: (state: string) => void;
const button = (label: string) =>
  tree.root.findAll(
    node =>
      node.props.accessibilityLabel === label &&
      typeof node.props.onPress === 'function',
  )[0];
const input = () =>
  tree.root.findAll(
    node =>
      node.props.accessibilityLabel === 'Collection name' &&
      typeof node.props.onChangeText === 'function',
  )[0];
const props = () => ({
  selectedItems: chosen,
  disabled: false,
  onOpen,
  onDelete,
  onBusy,
  onReset,
});
async function mount() {
  await act(async () => {
    tree = Renderer.create(<LibraryCollectionsPanel {...props()} />);
  });
}
async function open() {
  await act(async () => button('Private collections').props.onPress());
}
beforeEach(() => {
  jest.clearAllMocks();
  mockOwner = 'synthetic-owner';
  mockNative.mockReset().mockResolvedValue({
    owner: 'private-synthetic-owner',
    collections: [saved],
  });
  jest
    .spyOn(AppState, 'addEventListener')
    .mockImplementation((_type, listener) => {
      lifecycle = listener as typeof lifecycle;
      return { remove: jest.fn() };
    });
});
afterEach(async () => {
  if (tree) await act(async () => tree.unmount());
  jest.restoreAllMocks();
});

test('reads metadata only after an explicit shelf open and opens the exact saved bindings', async () => {
  await mount();
  expect(mockNative).not.toHaveBeenCalled();
  await open();
  expect(mockNative).toHaveBeenCalledWith('list', '', '', '', []);
  await act(async () => button('Open collection Trip').props.onPress());
  expect(onOpen).toHaveBeenCalledWith(saved);
  expect(tree.root.findByType(Modal).props.visible).toBe(false);
});
test('saves the selected exact identities with a trimmed name and current native owner', async () => {
  await mount();
  await open();
  await act(async () => input().props.onChangeText('  New trip  '));
  await act(async () =>
    button('Save selected originals as collection').props.onPress(),
  );
  expect(mockNative).toHaveBeenLastCalledWith(
    'create',
    'private-synthetic-owner',
    '',
    'New trip',
    chosen,
  );
  expect(input().props.value).toBe('');
  expect(onBusy).toHaveBeenLastCalledWith(false);
});
test('prevents duplicate saves before the native promise settles', async () => {
  await mount();
  await open();
  await act(async () => input().props.onChangeText('New trip'));
  let resolve!: (value: unknown) => void;
  mockNative.mockImplementationOnce(
    () =>
      new Promise(done => {
        resolve = done;
      }),
  );
  const save = button('Save selected originals as collection').props.onPress;
  await act(async () => {
    save();
    save();
  });
  expect(mockNative).toHaveBeenCalledTimes(2);
  expect(button('Close collections').props.disabled).toBe(true);
  await act(async () =>
    resolve({ owner: 'private-synthetic-owner', collections: [saved] }),
  );
  expect(onBusy).toHaveBeenLastCalledWith(false);
});
test('keeps failed save input available for an explicit retry', async () => {
  await mount();
  await open();
  await act(async () => input().props.onChangeText('Trip'));
  mockNative.mockRejectedValueOnce(
    new Error('Choose an unused collection name'),
  );
  await act(async () =>
    button('Save selected originals as collection').props.onPress(),
  );
  expect(input().props.value).toBe('Trip');
  expect(
    tree.root
      .findAllByProps({ accessibilityRole: 'alert' })
      .some(node => node.props.children === 'Choose an unused collection name'),
  ).toBe(true);
  expect(button('Save selected originals as collection').props.disabled).toBe(
    false,
  );
});
test('deletion requires separate review and sends only the selected metadata identifier', async () => {
  await mount();
  await open();
  await act(async () => button('Remove collection Trip').props.onPress());
  expect(mockNative).toHaveBeenCalledTimes(1);
  await act(async () => button('Keep collection').props.onPress());
  expect(mockNative).toHaveBeenCalledTimes(1);
  await act(async () => button('Remove collection Trip').props.onPress());
  mockNative.mockResolvedValueOnce({
    owner: 'private-synthetic-owner',
    collections: [],
  });
  await act(async () => button('Remove collection references').props.onPress());
  expect(mockNative).toHaveBeenLastCalledWith(
    'delete',
    'private-synthetic-owner',
    'collection-1',
    '',
    [],
  );
  expect(onDelete).toHaveBeenCalledWith('collection-1');
});
test('background closes private details and ignores late metadata results', async () => {
  await mount();
  let resolve!: (value: unknown) => void;
  mockNative.mockImplementationOnce(
    () =>
      new Promise(done => {
        resolve = done;
      }),
  );
  await open();
  await act(async () => lifecycle('background'));
  await act(async () =>
    resolve({ owner: 'private-synthetic-owner', collections: [saved] }),
  );
  expect(tree.root.findByType(Modal).props.visible).toBe(false);
  expect(onOpen).not.toHaveBeenCalled();
  expect(onBusy).toHaveBeenLastCalledWith(false);
});
test('changing accounts closes the shelf and an old response cannot populate the new owner', async () => {
  await mount();
  let resolve!: (value: unknown) => void;
  mockNative.mockImplementationOnce(
    () =>
      new Promise(done => {
        resolve = done;
      }),
  );
  await open();
  mockOwner = 'different-owner';
  await act(async () => tree.update(<LibraryCollectionsPanel {...props()} />));
  await act(async () =>
    resolve({ owner: 'private-synthetic-owner', collections: [saved] }),
  );
  expect(tree.root.findByType(Modal).props.visible).toBe(false);
  expect(onReset).toHaveBeenCalledTimes(2);
  expect(onOpen).not.toHaveBeenCalled();
});
test('a changed native namespace is refused rather than displaying a successful mutation', async () => {
  await mount();
  await open();
  await act(async () => input().props.onChangeText('New trip'));
  mockNative.mockResolvedValueOnce({
    owner: 'different-private-owner',
    collections: [],
  });
  await act(async () =>
    button('Save selected originals as collection').props.onPress(),
  );
  expect(input().props.value).toBe('New trip');
  expect(
    tree.root
      .findAllByProps({ accessibilityRole: 'alert' })
      .some(
        node => node.props.children === 'Account changed. Reopen collections.',
      ),
  ).toBe(true);
});
test('loaded filtering refuses URI and media-type substitution under reused IDs', () => {
  const entries = prepareMemoryEntries([
    {
      mediaId: 1,
      mediaUri: 'content://synthetic/photo',
      mediaType: 'IMAGE',
      displayName: 'Photo.jpg',
      capturedAt: 0,
    },
    {
      mediaId: 2,
      mediaUri: 'content://synthetic/reused',
      mediaType: 'IMAGE',
      displayName: 'Other.jpg',
      capturedAt: 0,
    },
    {
      mediaId: 3,
      mediaUri: 'content://synthetic/video',
      mediaType: 'VIDEO',
      displayName: 'Video.mp4',
      capturedAt: 0,
    },
  ]);
  const collection: LibraryCollection = {
    id: 'test',
    name: 'Exact',
    items: [
      ...chosen,
      { id: '2', uri: 'content://synthetic/original-two', kind: 'IMAGE' },
      { id: '3', uri: 'content://synthetic/video', kind: 'IMAGE' },
      { id: '4', uri: 'content://synthetic/not-loaded', kind: 'VIDEO' },
    ],
  };
  expect(
    collectionEntries(entries, collection).map(entry => entry.media.id),
  ).toEqual(['1']);
  expect(collection.items).toHaveLength(4);
  expect(collectionEntries(entries)).toEqual(entries);
});

test('retained collection buttons cannot reopen old-owner references after dismissal and account change', async () => {
  await mount();
  await open();
  const oldOpen = button('Open collection Trip').props.onPress;
  const oldRemove = button('Remove collection Trip').props.onPress;
  await act(async () => button('Close collections').props.onPress());
  mockOwner = 'different-owner';
  await act(async () => tree.update(<LibraryCollectionsPanel {...props()} />));
  await act(async () => {
    oldOpen();
    oldRemove();
  });
  expect(onOpen).not.toHaveBeenCalled();
  expect(tree.root.findByType(Modal).props.visible).toBe(false);
  expect(
    tree.root.findAll(
      node => node.props.accessibilityLabel === 'Remove collection references',
    ),
  ).toHaveLength(0);
});

test('a stale shelf row cannot operate after a refreshed create result replaces the catalog', async () => {
  await mount();
  await open();
  const oldOpen = button('Open collection Trip').props.onPress;
  const oldRemove = button('Remove collection Trip').props.onPress;
  await act(async () => input().props.onChangeText('Other'));
  mockNative.mockResolvedValueOnce({
    owner: 'private-synthetic-owner',
    collections: [saved, { ...saved, id: 'other', name: 'Other' }],
  });
  await act(async () =>
    button('Save selected originals as collection').props.onPress(),
  );
  await act(async () => {
    oldOpen();
    oldRemove();
  });
  expect(onOpen).not.toHaveBeenCalled();
  expect(button('Open collection Other')).toBeDefined();
  expect(
    tree.root.findAll(
      node => node.props.accessibilityLabel === 'Remove collection references',
    ),
  ).toHaveLength(0);
});
