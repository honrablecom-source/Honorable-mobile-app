import React from 'react';
import Renderer, { act } from 'react-test-renderer';
import { HomeScreen } from '../src/screens/HomeScreen';
const mockStart = jest.fn(),
  mockFinish = jest.fn(),
  mockSelect = jest.fn(),
  mockSearch = jest.fn();
const mockHistory = jest.fn();
jest.mock('../src/passes/MemoryPassContext', () => ({
  useMemoryPass: () => ({
    connected: true,
    startSearch: mockStart,
    finishSearch: mockFinish,
  }),
}));
jest.mock('../src/library/LibraryContext', () => ({
  useLibrary: () => ({ items: [], loading: false, error: '' }),
}));
jest.mock('../src/native/HonorableNative', () => ({
  honorableNative: {
    getSearchHistory: (...args: unknown[]) => mockHistory(...args),
    selectSeranModel: (...args: unknown[]) => mockSelect(...args),
    search: (...args: unknown[]) => mockSearch(...args),
  },
}));
jest.mock('../src/native/openWorkspace', () => ({
  useOpenWorkspace: () => ({ busy: false, open: jest.fn() }),
}));
jest.mock('@react-navigation/native', () => ({
  useNavigation: () => ({ getParent: () => ({ navigate: jest.fn() }) }),
}));
jest.mock('react-native-safe-area-context', () => ({
  SafeAreaView: require('react-native').View,
}));
jest.mock('../src/hooks/useReducedMotion', () => ({
  useReducedMotion: () => true,
}));
jest.mock('@/components/ui/text', () => ({
  Text: require('react-native').Text,
}));
jest.mock('@/components/ui/button', () => ({
  Button: require('react-native').Pressable,
}));
jest.mock('../src/components/HonorableSearchBar', () => ({
  HonorableSearchBar: (props: Record<string, unknown>) =>
    require('react').createElement(require('react-native').View, {
      ...props,
      testID: 'search-input',
    }),
}));
jest.mock('../src/components/MediaGrid', () => ({ MediaGrid: () => null }));
jest.mock('../src/components/MediaShelf', () => ({ MediaShelf: () => null }));
jest.mock('../src/components/MediaViewer', () => ({ MediaViewer: () => null }));
jest.mock('../src/design-system/CreativeChrome', () => ({
  FilterTabs: () => null,
  SectionHeading: () => null,
}));
jest.mock('lucide-react-native', () => {
  const { View } = require('react-native');
  return {
    Clock3: View,
    X: View,
    UserRound: View,
    ChevronDown: View,
    ShieldCheck: View,
    ArrowUpRight: View,
    Image: View,
    Film: View,
  };
});
let tree: Renderer.ReactTestRenderer;
const input = () => tree.root.findByProps({ testID: 'search-input' });
beforeEach(async () => {
  jest.resetAllMocks();
  mockHistory.mockResolvedValue({ queries: [] });
  mockSelect.mockResolvedValue(undefined);
  mockStart.mockResolvedValue(undefined);
  mockFinish.mockResolvedValue(undefined);
  mockSearch.mockResolvedValue({
    results: [
      {
        mediaId: 1,
        rank: 1,
        mediaType: 'IMAGE',
        mediaUri: 'content://synthetic/result',
      },
    ],
  });
  await act(async () => {
    tree = Renderer.create(<HomeScreen />);
  });
  await act(async () => input().props.onChangeText('Synthetic query'));
});
afterEach(async () => {
  await act(async () => tree.unmount());
});
test('same-frame submissions share one search admission and one completion request', async () => {
  let release!: () => void;
  const held = new Promise<void>(resolve => {
    release = resolve;
  });
  mockSelect.mockReturnValue(held);
  const submit = input().props.onSubmit;
  let first!: Promise<void>, second!: Promise<void>;
  await act(async () => {
    first = submit();
    second = submit();
  });
  await act(async () => {
    release();
    await Promise.all([first, second]);
  });
  expect(mockStart).toHaveBeenCalledTimes(1);
  expect(mockSelect).toHaveBeenCalledTimes(1);
  expect(mockSearch).toHaveBeenCalledTimes(1);
  expect(mockFinish).toHaveBeenCalledTimes(1);
  expect(mockSearch).toHaveBeenCalledWith('Synthetic query');
  expect(mockFinish).toHaveBeenCalledWith(
    mockStart.mock.calls[0][1],
    'SUCCESS',
  );
});
test('native search failure completes the same request as failed and allows a new successful retry', async () => {
  mockSearch.mockRejectedValueOnce(new Error('Synthetic native failure'));
  await act(async () => input().props.onSubmit());
  const failedId = mockStart.mock.calls[0][1];
  expect(mockFinish).toHaveBeenCalledWith(failedId, 'FAILED');
  expect(input().props.busy).toBe(false);
  await act(async () => input().props.onSubmit());
  expect(mockStart).toHaveBeenCalledTimes(2);
  expect(mockSearch).toHaveBeenCalledTimes(2);
  expect(mockStart.mock.calls[1][1]).not.toBe(failedId);
  expect(mockFinish).toHaveBeenLastCalledWith(
    mockStart.mock.calls[1][1],
    'SUCCESS',
  );
});
test('an empty native result completes as failed rather than success', async () => {
  mockSearch.mockResolvedValueOnce({ results: [] });
  await act(async () => input().props.onSubmit());
  expect(mockFinish).toHaveBeenCalledTimes(1);
  expect(mockFinish).toHaveBeenCalledWith(mockStart.mock.calls[0][1], 'FAILED');
  expect(input().props.busy).toBe(false);
});
test('a failed start never reaches native search and releases admission for retry', async () => {
  mockStart.mockRejectedValueOnce(new Error('Synthetic start unavailable'));
  await act(async () => input().props.onSubmit());
  expect(mockSearch).not.toHaveBeenCalled();
  expect(input().props.busy).toBe(false);
  await act(async () => input().props.onSubmit());
  expect(mockSearch).toHaveBeenCalledTimes(1);
});
test('blank input does not reserve admission or call model, credit or native search boundaries', async () => {
  await act(async () => input().props.onChangeText('  '));
  await act(async () => input().props.onSubmit());
  expect(mockSelect).not.toHaveBeenCalled();
  expect(mockStart).not.toHaveBeenCalled();
  expect(mockSearch).not.toHaveBeenCalled();
  expect(mockFinish).not.toHaveBeenCalled();
  await act(async () => input().props.onChangeText('Retry'));
  await act(async () => input().props.onSubmit());
  expect(mockSearch).toHaveBeenCalledWith('Retry');
});
