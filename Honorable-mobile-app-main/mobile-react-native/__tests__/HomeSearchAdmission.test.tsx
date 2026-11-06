import React from 'react';
import Renderer, { act } from 'react-test-renderer';
import { HomeScreen } from '../src/screens/HomeScreen';
const mockStart = jest.fn(),
  mockFinish = jest.fn(),
  mockSelect = jest.fn(),
  mockSearch = jest.fn();
const mockHistory = jest.fn(),
  mockClear = jest.fn();
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
    clearSearchHistory: (...args: unknown[]) => mockClear(...args),
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
    ArrowUp: View,
    Search: View,
    Image: View,
    Film: View,
  };
});
let tree: Renderer.ReactTestRenderer;
const input = () => tree.root.findByProps({ testID: 'search-input' });
beforeEach(async () => {
  jest.resetAllMocks();
  mockHistory.mockResolvedValue({ queries: ['Synthetic old query'] });
  mockClear.mockResolvedValue(undefined);
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

const hasText = (text: string) =>
  tree.root.findAll(node => node.props.children === text).length > 0;
const clearButton = () =>
  tree.root.findAll(
    node =>
      node.props.accessibilityLabel === 'Clear search history' &&
      typeof node.props.onPress === 'function',
  )[0];
test('failed history clear preserves displayed queries, reports failure and permits retry', async () => {
  mockClear.mockRejectedValueOnce(new Error('Synthetic clearing failure'));
  await act(async () => clearButton().props.onPress());
  expect(
    tree.root
      .findAllByProps({ accessibilityRole: 'alert' })
      .some(node =>
        JSON.stringify(node.props.children).includes(
          'Synthetic clearing failure',
        ),
      ),
  ).toBe(true);
  expect(hasText('Synthetic old query')).toBe(true);
  expect(clearButton().props.disabled).toBe(false);
  await act(async () => clearButton().props.onPress());
  expect(clearButton()).toBeUndefined();
  expect(hasText('Synthetic old query')).toBe(false);
  expect(mockClear).toHaveBeenCalledTimes(2);
});
test('same-frame clear taps share one pending native clear and show busy feedback', async () => {
  let release!: () => void;
  const held = new Promise<void>(resolve => {
    release = resolve;
  });
  mockClear.mockReturnValue(held);
  const clear = clearButton().props.onPress;
  let first!: Promise<void>, second!: Promise<void>;
  await act(async () => {
    first = clear();
    second = clear();
  });
  const calls = mockClear.mock.calls.length;
  const busy = clearButton().props.accessibilityState?.busy;
  await act(async () => {
    release();
    await Promise.all([first, second]);
  });
  expect(calls).toBe(1);
  expect(busy).toBe(true);
  expect(clearButton()).toBeUndefined();
});
test('an older pending history read cannot restore queries after a successful clear', async () => {
  const clear = clearButton().props.onPress;
  let release!: (value: { queries: string[] }) => void;
  mockHistory.mockImplementationOnce(
    () =>
      new Promise(resolve => {
        release = resolve;
      }),
  );
  let search!: Promise<void>;
  await act(async () => {
    search = input().props.onSubmit();
  });
  await act(async () => clear());
  await act(async () => {
    release({ queries: ['Synthetic stale query'] });
    await search;
  });
  mockSearch.mockRejectedValueOnce(new Error('Synthetic retry failure'));
  await act(async () => input().props.onSubmit());
  expect(hasText('Synthetic stale query')).toBe(false);
  expect(clearButton()).toBeUndefined();
});
test('a retained Clear callback cannot mutate native history after Home unmounts', async () => {
  const clear = clearButton().props.onPress;
  await act(async () => tree.unmount());
  await act(async () => clear());
  expect(mockClear).not.toHaveBeenCalled();
});

const action = (label: string) =>
  tree.root.findAll(
    node =>
      node.props.accessibilityLabel === label &&
      typeof node.props.onPress === 'function',
  )[0];
test('clearing the completed search field returns Home without another search or credit request', async () => {
  await act(async () => input().props.onSubmit());
  expect(action('Open Studio')).toBeUndefined();
  await act(async () => input().props.onChangeText(''));
  expect(action('Open Studio')).toBeDefined();
  expect(input().props.value).toBe('');
  expect(mockSearch).toHaveBeenCalledTimes(1);
  expect(mockStart).toHaveBeenCalledTimes(1);
  expect(mockFinish).toHaveBeenCalledTimes(1);
});
test('explicit clear results returns Home and retains local history without native mutation', async () => {
  await act(async () => input().props.onSubmit());
  expect(action('Clear search results')).toBeDefined();
  await act(async () => action('Clear search results').props.onPress());
  expect(action('Open Studio')).toBeDefined();
  expect(input().props.value).toBe('');
  expect(hasText('Synthetic old query')).toBe(true);
  expect(mockClear).not.toHaveBeenCalled();
  expect(mockSearch).toHaveBeenCalledTimes(1);
  expect(mockStart).toHaveBeenCalledTimes(1);
});
test('completed query label stays tied to results while drafting another query', async () => {
  await act(async () => input().props.onSubmit());
  await act(async () => input().props.onChangeText('A different draft'));
  expect(hasText('Results for Synthetic query')).toBe(true);
  expect(input().props.value).toBe('A different draft');
  expect(mockSearch).toHaveBeenCalledTimes(1);
});
test('pending search blocks retained result reset and field clear until normal completion', async () => {
  await act(async () => input().props.onSubmit());
  const reset = action('Clear search results').props.onPress;
  let release!: (value: unknown) => void;
  mockSearch.mockImplementationOnce(
    () =>
      new Promise(resolve => {
        release = resolve;
      }),
  );
  await act(async () => input().props.onChangeText('Second query'));
  let pending!: Promise<void>;
  await act(async () => {
    pending = input().props.onSubmit();
    reset();
    input().props.onChangeText('');
  });
  expect(input().props.value).toBe('Second query');
  expect(action('Clear search results').props.disabled).toBe(true);
  await act(async () => {
    release({ results: [] });
    await pending;
  });
  await act(async () => action('Clear search results').props.onPress());
  expect(action('Open Studio')).toBeDefined();
  expect(mockSearch).toHaveBeenCalledTimes(2);
  expect(mockFinish).toHaveBeenCalledTimes(2);
});
test('actual search bar locks editing and clearing while busy and restores both after completion', async () => {
  const RealBar = jest.requireActual(
    '../src/components/HonorableSearchBar',
  ).HonorableSearchBar;
  const change = jest.fn(),
    submit = jest.fn();
  const TextInput = require('react-native').TextInput;
  await act(async () =>
    tree.update(
      <RealBar
        value="Synthetic query"
        busy
        onChangeText={change}
        onSubmit={submit}
      />,
    ),
  );
  expect(tree.root.findByType(TextInput).props.editable).toBe(false);
  expect(action('Clear search').props.disabled).toBe(true);
  await act(async () => {
    action('Clear search').props.onPress();
    tree.root.findByType(TextInput).props.onSubmitEditing();
  });
  expect(change).not.toHaveBeenCalled();
  expect(submit).not.toHaveBeenCalled();
  await act(async () =>
    tree.update(
      <RealBar
        value="Synthetic query"
        busy={false}
        onChangeText={change}
        onSubmit={submit}
      />,
    ),
  );
  expect(tree.root.findByType(TextInput).props.editable).toBe(true);
  await act(async () => {
    action('Clear search').props.onPress();
    tree.root.findByType(TextInput).props.onSubmitEditing();
  });
  expect(change).toHaveBeenCalledWith('');
  expect(submit).toHaveBeenCalledTimes(1);
});
