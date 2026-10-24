import React from 'react';
import { Alert, Text } from 'react-native';
import Renderer, { act } from 'react-test-renderer';
import { LibraryGate } from '../src/library/LibraryGate';
import {
  honorableNative,
  type IndexStatus,
} from '../src/native/HonorableNative';

let mockStatus: IndexStatus;
let mockLoading = false;
const mockRequestAccess = jest.fn();
const mockReindex = jest.fn();
jest.mock('../src/library/LibraryContext', () => ({
  useLibrary: () => ({
    status: mockStatus,
    loading: mockLoading,
    indexing: false,
    error: '',
    requestAccess: mockRequestAccess,
    reindex: mockReindex,
  }),
}));
jest.mock('../src/native/HonorableNative', () => ({
  honorableNative: {
    pickWorkspaceMedia: jest.fn(),
    editPhoto: jest.fn(),
    editVideo: jest.fn(),
  },
}));
jest.mock('react-native-safe-area-context', () => ({
  SafeAreaView: require('react-native').View,
}));
jest.mock('lucide-react-native', () => ({
  Images: require('react-native').View,
  ShieldCheck: require('react-native').View,
}));

const native = honorableNative as jest.Mocked<typeof honorableNative>;
let tree: Renderer.ReactTestRenderer;
const button = (label: string) =>
  tree.root.findAll(
    node =>
      node.props.accessibilityLabel === label &&
      typeof node.props.onPress === 'function',
  )[0];
const render = async () => {
  await act(async () => {
    tree = Renderer.create(
      <LibraryGate>
        <Text testID="main-app">Main app</Text>
      </LibraryGate>,
    );
  });
};
beforeEach(() => {
  jest.resetAllMocks();
  mockLoading = false;
  mockStatus = {
    engine: 'REAL',
    permissionGranted: false,
    indexedCount: 0,
    status: 'IDLE',
  };
  native.pickWorkspaceMedia.mockResolvedValue({ uri: 'content://test/chosen' });
  native.editPhoto.mockResolvedValue({ opened: true });
  native.editVideo.mockResolvedValue({ opened: true });
});
afterEach(async () => {
  await act(async () => tree.unmount());
  jest.restoreAllMocks();
});

test('denied library permission still allows editing the document-picked photo', async () => {
  await render();
  expect(tree.root.findAllByProps({ testID: 'main-app' })).toHaveLength(0);
  await act(async () => button('Edit a photo').props.onPress());
  expect(native.pickWorkspaceMedia).toHaveBeenCalledWith('IMAGE');
  expect(native.editPhoto).toHaveBeenCalledWith('content://test/chosen');
  expect(native.editVideo).not.toHaveBeenCalled();
  expect(mockRequestAccess).not.toHaveBeenCalled();
  expect(mockReindex).not.toHaveBeenCalled();
});

test('an empty index still allows editing the document-picked video', async () => {
  mockStatus.permissionGranted = true;
  await render();
  expect(tree.root.findAllByProps({ testID: 'main-app' })).toHaveLength(0);
  await act(async () => button('Edit a video').props.onPress());
  expect(native.pickWorkspaceMedia).toHaveBeenCalledWith('VIDEO');
  expect(native.editVideo).toHaveBeenCalledWith('content://test/chosen');
  expect(native.editPhoto).not.toHaveBeenCalled();
  expect(mockReindex).not.toHaveBeenCalled();
});

test('cancelling the direct picker keeps onboarding in place without opening an editor', async () => {
  native.pickWorkspaceMedia.mockResolvedValue(null);
  await render();
  await act(async () => button('Edit a photo').props.onPress());
  expect(native.editPhoto).not.toHaveBeenCalled();
  expect(native.editVideo).not.toHaveBeenCalled();
  expect(tree.root.findAllByProps({ testID: 'main-app' })).toHaveLength(0);
  expect(button('Edit a photo').props.disabled).toBe(false);
});

test('a pending direct picker prevents another editor request', async () => {
  let resolve!: (value: null) => void;
  native.pickWorkspaceMedia.mockImplementation(
    () =>
      new Promise(done => {
        resolve = done;
      }),
  );
  await render();
  let pending: Promise<void>;
  await act(async () => {
    pending = button('Edit a photo').props.onPress();
  });
  expect(button('Edit a video').props.disabled).toBe(true);
  await act(async () => button('Edit a video').props.onPress());
  expect(native.pickWorkspaceMedia).toHaveBeenCalledTimes(1);
  await act(async () => {
    resolve(null);
    await pending;
  });
  expect(button('Edit a video').props.disabled).toBe(false);
});

test('native Studio authorization failures are shown and do not unlock the app', async () => {
  const alert = jest.spyOn(Alert, 'alert').mockImplementation(() => undefined);
  native.editVideo.mockRejectedValue(
    new Error('Reconnect to authorize Studio'),
  );
  await render();
  await act(async () => button('Edit a video').props.onPress());
  expect(alert).toHaveBeenCalledWith(
    'Open workspace',
    'Reconnect to authorize Studio',
  );
  expect(tree.root.findAllByProps({ testID: 'main-app' })).toHaveLength(0);
  expect(button('Edit a video').props.disabled).toBe(false);
});

test('initial status loading does not expose a picker or the main app', async () => {
  mockLoading = true;
  await render();
  expect(button('Edit a photo')).toBeUndefined();
  expect(button('Edit a video')).toBeUndefined();
  expect(tree.root.findAllByProps({ testID: 'main-app' })).toHaveLength(0);
});

test('a permitted indexed library continues to the existing main app', async () => {
  mockStatus.permissionGranted = true;
  mockStatus.indexedCount = 1;
  await render();
  expect(
    tree.root.findAllByProps({ testID: 'main-app' }).length,
  ).toBeGreaterThan(0);
  expect(button('Edit a photo')).toBeUndefined();
  expect(native.pickWorkspaceMedia).not.toHaveBeenCalled();
});
