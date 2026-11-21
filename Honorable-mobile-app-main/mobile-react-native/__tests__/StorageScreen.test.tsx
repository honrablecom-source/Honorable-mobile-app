import React from 'react';
import { Alert, Linking } from 'react-native';
import Renderer, { act } from 'react-test-renderer';
import { StorageScreen } from '../src/screens/StorageScreen';
import type { IndexStatus } from '../src/native/HonorableNative';

let mockStatus: IndexStatus;
const mockReindex = jest.fn();
jest.mock('../src/library/LibraryContext', () => ({
  useLibrary: () => ({ status: mockStatus, reindex: mockReindex }),
}));
jest.mock('@react-navigation/native', () => ({
  useNavigation: () => ({ goBack: jest.fn() }),
}));
jest.mock('react-native-safe-area-context', () => ({
  SafeAreaView: require('react-native').View,
}));
jest.mock('lucide-react-native', () => ({
  ChevronLeft: require('react-native').View,
  ChevronRight: require('react-native').View,
  Images: require('react-native').View,
  RefreshCw: require('react-native').View,
}));

let tree: Renderer.ReactTestRenderer;
const action = (label: string) =>
  tree.root.findAll(
    node =>
      node.props.accessibilityLabel === label &&
      typeof node.props.onPress === 'function',
  )[0];
const hasText = (value: string) =>
  tree.root.findAll(node => node.props.children === value).length > 0;

async function render(overrides: Partial<IndexStatus>) {
  mockStatus = {
    engine: 'REAL',
    permissionGranted: true,
    indexedCount: 32,
    status: 'READY',
    ...overrides,
  };
  await act(async () => {
    tree = Renderer.create(<StorageScreen />);
  });
}

afterEach(async () => {
  await act(async () => tree?.unmount());
  jest.clearAllMocks();
  jest.restoreAllMocks();
});

test('a failed index never shows a prior job’s progress or a ready label', async () => {
  await render({ status: 'FAILED', processed: 100, total: 100 });
  expect(hasText('Indexing stopped with an error')).toBe(true);
  expect(hasText('Ready to search')).toBe(false);
  expect(hasText('Indexing stopped with an error · 100%')).toBe(false);
  expect(action('Reindex library').props.disabled).toBe(false);
});

test('a running index reports bounded progress and blocks a competing rebuild', async () => {
  await render({ status: 'RUNNING', processed: 4, total: 10 });
  expect(hasText('Indexing your library · 40%')).toBe(true);
  expect(action('Reindex library').props.disabled).toBe(true);
});

test('permission recovery remains available when reindexing is blocked', async () => {
  const settings = jest
    .spyOn(Linking, 'openSettings')
    .mockRejectedValue(new Error('Unavailable'));
  await render({ status: 'PERMISSION_REQUIRED', permissionGranted: false });
  expect(hasText('Photo access needed')).toBe(true);
  expect(action('Reindex library').props.disabled).toBe(true);
  await act(async () => action('Manage photo access').props.onPress());
  expect(settings).toHaveBeenCalledTimes(1);
  expect(hasText('Open your device settings to change photo access.')).toBe(
    true,
  );
});

test('reindex waits for explicit confirmation', async () => {
  const alert = jest.spyOn(Alert, 'alert').mockImplementation(() => {});
  mockReindex.mockResolvedValue(undefined);
  await render({});
  act(() => action('Reindex library').props.onPress());
  expect(mockReindex).not.toHaveBeenCalled();
  await act(async () =>
    alert.mock.calls[0][2]?.find(item => item.text === 'Reindex')?.onPress?.(),
  );
  expect(mockReindex).toHaveBeenCalledTimes(1);
});
