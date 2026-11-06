import React from 'react';
import Renderer, { act } from 'react-test-renderer';
import { MemoriesScreen } from '../src/screens/MemoriesScreen';
import type { LibraryItem } from '../src/native/HonorableNative';

const fixtures: LibraryItem[] = [
  {
    mediaId: 1,
    mediaUri: 'content://synthetic/photo',
    thumbnailUri: 'content://synthetic/thumb',
    mediaType: 'IMAGE',
    displayName: 'Photo.jpg',
    capturedAt: Date.UTC(2026, 10, 6),
  },
  {
    mediaId: 2,
    mediaUri: 'content://synthetic/clip',
    mediaType: 'VIDEO',
    displayName: 'Clip.mp4',
    capturedAt: Date.UTC(2026, 10, 6),
    durationMs: 12000,
  },
];
let mockItems = fixtures,
  mockTotal = 4,
  mockLoading = false,
  mockIndexing = false,
  mockError = '';
const mockShare = jest.fn(),
  mockCreateVideo = jest.fn();
jest.mock('../src/native/HonorableNative', () => ({
  honorableNative: {
    shareMediaBatch: (...args: unknown[]) => mockShare(...args),
    createPhotoVideoFromLibraryGuest: (...args: unknown[]) =>
      mockCreateVideo(...args),
  },
}));
const mockLoadMore = jest.fn(),
  mockReload = jest.fn();
jest.mock('../src/library/LibraryContext', () => ({
  useLibrary: () => ({
    items: mockItems,
    total: mockTotal,
    loading: mockLoading,
    indexing: mockIndexing,
    error: mockError,
    loadMore: mockLoadMore,
    reload: mockReload,
  }),
}));
jest.mock('react-native-safe-area-context', () => ({
  SafeAreaView: require('react-native').View,
}));
jest.mock('@/components/ui/text', () => ({
  Text: require('react-native').Text,
}));
jest.mock('../src/design-system', () => ({
  colors: jest.requireActual('../src/design-system/tokens').colors,
  WorkspaceHeader: ({ title }: { title: string }) => {
    const create = require('react').createElement;
    return create(require('react-native').Text, null, title);
  },
}));
jest.mock('../src/design-system/CreativeChrome', () => ({
  FilterTabs: ({
    items,
    onChange,
  }: {
    items: string[];
    onChange: (value: string) => void;
  }) => {
    const create = require('react').createElement,
      { View, Pressable, Text } = require('react-native');
    return create(
      View,
      null,
      items.map(value =>
        create(
          Pressable,
          {
            key: value,
            accessibilityLabel: `Filter ${value}`,
            onPress: () => onChange(value),
          },
          create(Text, null, value),
        ),
      ),
    );
  },
}));
jest.mock('../src/components/HonorableSearchBar', () => ({
  HonorableSearchBar: ({
    value,
    onChangeText,
  }: {
    value: string;
    onChangeText: (value: string) => void;
  }) => {
    const create = require('react').createElement;
    return create(require('react-native').TextInput, {
      accessibilityLabel: 'Filename filter',
      value,
      onChangeText,
    });
  },
}));
jest.mock('../src/components/MediaGrid', () => ({
  MediaGridRow: (props: Record<string, unknown>) => {
    const create = require('react').createElement;
    return create(require('react-native').View, {
      ...props,
      testID: 'media-grid',
    });
  },
}));
jest.mock('../src/components/MediaViewer', () => ({
  MediaViewer: (props: Record<string, unknown>) => {
    const create = require('react').createElement;
    return create(require('react-native').View, {
      ...props,
      testID: 'media-viewer',
    });
  },
}));
let tree: Renderer.ReactTestRenderer;
const button = (label: string) =>
  tree.root.findAll(
    node =>
      node.props.accessibilityLabel === label &&
      typeof node.props.onPress === 'function',
  )[0];
const refresh = () =>
  tree.root.findAll(
    node =>
      node.props.refreshControl?.props?.accessibilityLabel ===
      'Refresh library',
  )[0].props.refreshControl.props;
const grid = () => tree.root.findAllByProps({ testID: 'media-grid' })[0];
const viewer = () => tree.root.findAllByProps({ testID: 'media-viewer' })[0];
const filter = () =>
  tree.root.findAll(
    node =>
      node.props.accessibilityLabel === 'Filename filter' &&
      typeof node.props.onChangeText === 'function',
  )[0];
async function mount() {
  await act(async () => {
    tree = Renderer.create(<MemoriesScreen />);
  });
}
async function update() {
  await act(async () => tree.update(<MemoriesScreen />));
}
beforeEach(() => {
  jest.resetAllMocks();
  mockItems = fixtures;
  mockTotal = 4;
  mockLoading = false;
  mockIndexing = false;
  mockError = '';
  mockShare.mockResolvedValue({ opened: true });
  mockCreateVideo.mockResolvedValue({ opened: true });
  mockLoadMore.mockResolvedValue(undefined);
  mockReload.mockResolvedValue(undefined);
});
afterEach(async () => {
  await act(async () => tree?.unmount());
});

test('library failure is distinct from no matches and retry reloads metadata', async () => {
  mockItems = [];
  mockError = 'Synthetic library read failure';
  await mount();
  expect(
    tree.root.findAllByProps({ accessibilityRole: 'alert' }).length,
  ).toBeGreaterThan(0);
  expect(
    tree.root.findAll(
      node => node.props.children === 'No loaded memories match',
    ),
  ).toHaveLength(0);
  await act(async () => button('Retry loading library').props.onPress());
  expect(mockReload).toHaveBeenCalledTimes(1);
  expect(mockLoadMore).not.toHaveBeenCalled();
  mockError = '';
  mockItems = fixtures;
  await update();
  expect(grid().props.items).toHaveLength(2);
});
test('initial loading does not report that no memories exist', async () => {
  mockLoading = true;
  mockItems = [];
  await mount();
  expect(
    tree.root.findAllByProps({ accessibilityLabel: 'Loading library' }).length,
  ).toBeGreaterThan(0);
  expect(
    tree.root.findAll(
      node => node.props.children === 'No loaded memories match',
    ),
  ).toHaveLength(0);
  expect(button('Load more memories').props.disabled).toBe(true);
  expect(mockLoadMore).not.toHaveBeenCalled();
});
test('same-tick paging and refresh cannot compete; explicit refresh closes the viewer', async () => {
  let finish!: () => void;
  mockLoadMore.mockImplementationOnce(
    () =>
      new Promise<void>(resolve => {
        finish = resolve;
      }),
  );
  await mount();
  await act(async () => grid().props.onPress(grid().props.items[0]));
  expect(viewer().props.item.mediaUri).toBe('content://synthetic/photo');
  let pending!: Promise<void>;
  await act(async () => {
    const more = button('Load more memories');
    const pull = refresh();
    pending = more.props.onPress();
    more.props.onPress();
    pull.onRefresh();
  });
  expect(mockLoadMore).toHaveBeenCalledTimes(1);
  expect(mockReload).not.toHaveBeenCalled();
  expect(button('Load more memories').props.disabled).toBe(true);
  expect(viewer().props.item).toBeDefined();
  await act(async () => {
    finish();
    await pending;
  });
  await act(async () => refresh().onRefresh());
  expect(mockReload).toHaveBeenCalledTimes(1);
  expect(viewer().props.item).toBeUndefined();
});
test('paging errors remain retryable and filters survive a readonly reload', async () => {
  await mount();
  await act(async () => {
    button('Filter Videos').props.onPress();
    filter().props.onChangeText('Clip');
  });
  mockLoadMore.mockImplementationOnce(async () => {
    mockError = 'Synthetic page failure';
  });
  await act(async () => button('Load more memories').props.onPress());
  await update();
  expect(
    tree.root.findAllByProps({ accessibilityRole: 'alert' }).length,
  ).toBeGreaterThan(0);
  mockReload.mockImplementationOnce(async () => {
    mockError = '';
  });
  await act(async () => button('Retry loading library').props.onPress());
  await update();
  expect(filter().props.value).toBe('Clip');
  expect(grid().props.items.map((item: { type: string }) => item.type)).toEqual(
    ['VIDEO'],
  );
  expect(mockReload).toHaveBeenCalledTimes(1);
  expect(button('Load more memories').props.disabled).toBe(false);
});
test('failed callbacks are caught and late completion cannot revive an unmounted screen', async () => {
  let fail!: (reason: Error) => void;
  mockReload.mockImplementationOnce(
    () =>
      new Promise<void>((_, reject) => {
        fail = reject;
      }),
  );
  await mount();
  const retained = refresh().onRefresh;
  let pending!: Promise<void>;
  await act(async () => {
    pending = retained();
  });
  await act(async () => tree.unmount());
  await act(async () => {
    fail(new Error('Late failure'));
    await pending;
    await retained();
  });
  expect(mockReload).toHaveBeenCalledTimes(1);
  await mount();
  expect(tree.root.findAllByProps({ accessibilityRole: 'alert' })).toHaveLength(
    0,
  );
});
test('a later page can satisfy a loaded filename filter and preserves the selected original URI', async () => {
  await mount();
  await act(async () => filter().props.onChangeText('Later'));
  expect(tree.root.findAllByProps({ testID: 'media-grid' })).toHaveLength(0);
  mockLoadMore.mockImplementationOnce(async () => {
    mockItems = [
      ...fixtures,
      {
        ...fixtures[0],
        mediaId: 3,
        displayName: 'Later.jpg',
        mediaUri: 'content://synthetic/later-original',
      },
    ];
  });
  await act(async () => button('Load more memories').props.onPress());
  await update();
  expect(filter().props.value).toBe('Later');
  await act(async () => grid().props.onPress(grid().props.items[0]));
  expect(viewer().props.item.mediaUri).toBe(
    'content://synthetic/later-original',
  );
});
test('indexing blocks page loading but does not trigger a new scan on refresh', async () => {
  mockIndexing = true;
  await mount();
  expect(button('Load more memories').props.disabled).toBe(true);
  await act(async () => button('Load more memories').props.onPress());
  expect(mockLoadMore).not.toHaveBeenCalled();
  await act(async () => refresh().onRefresh());
  expect(mockReload).toHaveBeenCalledTimes(1);
  expect(mockLoadMore).not.toHaveBeenCalled();
});
test('one owning virtualized list receives complete small rows for a larger library', async () => {
  mockItems = Array.from({ length: 90 }, (_, i) => ({
    ...fixtures[0],
    mediaId: i + 1,
  }));
  mockTotal = 90;
  await mount();
  const list = tree.root.findAll(
    node =>
      node.props.accessibilityLabel === 'Library media' &&
      Array.isArray(node.props.data),
  )[0];
  const rows = list.props.data as Array<{ kind: string; items?: unknown[] }>;
  expect(list.props.initialNumToRender).toBeLessThan(rows.length);
  expect(
    rows.filter(row => row.kind === 'media').flatMap(row => row.items ?? []),
  ).toHaveLength(90);
  expect(
    rows.every(
      row =>
        row.kind === 'month' ||
        (row.items!.length > 0 && row.items!.length <= 3),
    ),
  ).toBe(true);
  expect(list.props.ListHeaderComponent).toBeDefined();
  expect(list.props.ListFooterComponent).toBeDefined();
});

test('viewer browses loaded filtered originals in displayed order without wrapping or loading', async () => {
  await mount();
  await act(async () => grid().props.onPress(grid().props.items[0]));
  expect(viewer().props.navigation).toMatchObject({
    position: 1,
    total: 2,
    onPrevious: undefined,
  });
  await act(async () => viewer().props.navigation.onNext());
  expect(viewer().props.item).toMatchObject({
    id: '2',
    type: 'VIDEO',
    mediaUri: fixtures[1].mediaUri,
  });
  expect(viewer().props.navigation).toMatchObject({
    position: 2,
    total: 2,
    onNext: undefined,
  });
  await act(async () => viewer().props.navigation.onPrevious());
  expect(viewer().props.item.mediaUri).toBe(fixtures[0].mediaUri);
  expect(mockLoadMore).not.toHaveBeenCalled();
  expect(mockReload).not.toHaveBeenCalled();
});

test('viewer navigation uses only filename matches and extends after an explicit page load', async () => {
  await mount();
  await act(async () => filter().props.onChangeText('Photo'));
  await act(async () => grid().props.onPress(grid().props.items[0]));
  expect(viewer().props.navigation).toMatchObject({
    position: 1,
    total: 1,
    onNext: undefined,
  });
  mockItems = [
    ...fixtures,
    {
      ...fixtures[0],
      mediaId: 3,
      mediaUri: 'content://synthetic/photo3',
      displayName: 'Photo3.jpg',
    },
  ];
  await update();
  expect(viewer().props.navigation.total).toBe(2);
  await act(async () => viewer().props.navigation.onNext());
  expect(viewer().props.item.mediaUri).toBe('content://synthetic/photo3');
  expect(mockLoadMore).not.toHaveBeenCalled();
});

test('stale next callbacks cannot skip the current item and refresh closes viewer navigation', async () => {
  await mount();
  await act(async () => grid().props.onPress(grid().props.items[0]));
  const next = viewer().props.navigation.onNext;
  await act(async () => {
    next();
    next();
  });
  expect(viewer().props.item.id).toBe('2');
  await act(async () => next());
  expect(viewer().props.item.id).toBe('2');
  await act(async () => refresh().onRefresh());
  expect(viewer().props.item).toBeUndefined();
  expect(viewer().props.navigation).toBeUndefined();
});

const chooseTile = (index: number) => {
  const rows = tree.root
    .findAllByProps({ testID: 'media-grid' })
    .filter(row => typeof row.type === 'string');
  const values = rows.flatMap(row =>
    row.props.items.map((item: unknown) => ({ row, item })),
  );
  values[index].row.props.onPress(values[index].item);
};
test('selection shares exact photo and video originals in chosen order without opening a viewer', async () => {
  await mount();
  await act(async () => button('Select library originals').props.onPress());
  expect(button('Share selected originals').props.disabled).toBe(true);
  await act(async () => {
    chooseTile(1);
    chooseTile(0);
  });
  expect(viewer().props.item).toBeUndefined();
  expect(mockShare).not.toHaveBeenCalled();
  await act(async () => button('Share selected originals').props.onPress());
  expect(mockShare).toHaveBeenCalledWith([
    { mediaUri: fixtures[1].mediaUri, mediaType: 'VIDEO' },
    { mediaUri: fixtures[0].mediaUri, mediaType: 'IMAGE' },
  ]);
  expect(
    tree.root.findAll(
      node =>
        typeof node.props.children === 'string' &&
        node.props.children.includes('Sending depends'),
    ).length,
  ).toBeGreaterThan(0);
});
test('selection is capped synchronously at twenty and survives a filename filter', async () => {
  mockItems = Array.from({ length: 21 }, (_, i) => ({
    ...fixtures[0],
    mediaId: i + 1,
    displayName: `Photo${i}.jpg`,
    mediaUri: `content://synthetic/original/${i}`,
  }));
  mockTotal = 21;
  await mount();
  await act(async () => button('Select library originals').props.onPress());
  await act(async () => {
    for (let i = 0; i < 21; i++) chooseTile(i);
  });
  await act(async () => filter().props.onChangeText('Photo0.jpg'));
  expect(
    tree.root.findAll(
      node =>
        typeof node.props.children === 'string' &&
        node.props.children.includes('19 hidden by filters'),
    ).length,
  ).toBeGreaterThan(0);
  await act(async () => button('Share selected originals').props.onPress());
  expect(mockShare.mock.calls[0][0]).toEqual(
    mockItems
      .slice(0, 20)
      .map(item => ({ mediaUri: item.mediaUri, mediaType: 'IMAGE' })),
  );
});
test('pending share blocks duplicate launch and selection changes, then failure preserves retry', async () => {
  let reject!: (reason: Error) => void;
  mockShare.mockImplementationOnce(
    () =>
      new Promise((_, fail) => {
        reject = fail;
      }),
  );
  await mount();
  await act(async () => button('Select library originals').props.onPress());
  await act(async () => chooseTile(0));
  let pending!: Promise<void>;
  await act(async () => {
    pending = button('Share selected originals').props.onPress();
    button('Share selected originals').props.onPress();
    chooseTile(1);
    button('Clear library selection').props.onPress();
  });
  expect(mockShare).toHaveBeenCalledTimes(1);
  expect(button('Share selected originals').props.accessibilityState.busy).toBe(
    true,
  );
  await act(async () => {
    reject(new Error('Synthetic provider denied'));
    await pending;
  });
  expect(
    tree.root
      .findAllByProps({ accessibilityRole: 'alert' })
      .some(node => node.props.children === 'Synthetic provider denied'),
  ).toBe(true);
  await act(async () => button('Share selected originals').props.onPress());
  expect(mockShare).toHaveBeenCalledTimes(2);
  expect(mockShare.mock.calls[1][0]).toEqual([
    { mediaUri: fixtures[0].mediaUri, mediaType: 'IMAGE' },
  ]);
});
test('removed sources are pruned before share and explicit refresh exits selection', async () => {
  await mount();
  await act(async () => button('Select library originals').props.onPress());
  await act(async () => {
    chooseTile(0);
    chooseTile(1);
  });
  mockItems = [fixtures[1]];
  await update();
  await act(async () => button('Share selected originals').props.onPress());
  expect(mockShare).toHaveBeenCalledWith([
    { mediaUri: fixtures[1].mediaUri, mediaType: 'VIDEO' },
  ]);
  await act(async () => refresh().onRefresh());
  expect(button('Done selecting originals')).toBeUndefined();
  expect(button('Select library originals')).toBeDefined();
});
test('retained Share cannot invoke native storage after Library unmount', async () => {
  await mount();
  await act(async () => button('Select library originals').props.onPress());
  await act(async () => chooseTile(0));
  const share = button('Share selected originals').props.onPress;
  await act(async () => tree.unmount());
  await act(async () => share());
  expect(mockShare).not.toHaveBeenCalled();
});
test('Done discards only selection and restores normal original viewer opening', async () => {
  await mount();
  await act(async () => button('Select library originals').props.onPress());
  await act(async () => chooseTile(0));
  await act(async () => button('Done selecting originals').props.onPress());
  await act(async () => chooseTile(1));
  expect(viewer().props.item.mediaUri).toBe(fixtures[1].mediaUri);
  expect(mockShare).not.toHaveBeenCalled();
});

test('a reused library ID cannot silently substitute a different original into a selected share', async () => {
  await mount();
  await act(async () => button('Select library originals').props.onPress());
  await act(async () => chooseTile(0));
  mockItems = [
    {
      ...fixtures[0],
      mediaUri: 'content://synthetic/replacement',
      mediaType: 'VIDEO',
    },
  ];
  await update();
  expect(button('Share selected originals').props.disabled).toBe(true);
  await act(async () => button('Share selected originals').props.onPress());
  expect(mockShare).not.toHaveBeenCalled();
  await act(async () => chooseTile(0));
  await act(async () => button('Share selected originals').props.onPress());
  expect(mockShare).toHaveBeenCalledWith([
    { mediaUri: 'content://synthetic/replacement', mediaType: 'VIDEO' },
  ]);
});

test('selected photos open the existing creator in chosen order across filters without another picker', async () => {
  mockItems = [
    fixtures[0],
    {
      ...fixtures[0],
      mediaId: 3,
      mediaUri: 'content://synthetic/photo3',
      displayName: 'Photo3.jpg',
    },
  ];
  await mount();
  await act(async () => button('Select library originals').props.onPress());
  await act(async () => {
    chooseTile(1);
    chooseTile(0);
  });
  await act(async () => filter().props.onChangeText('Photo3'));
  await act(async () =>
    button('Make video from selected photos').props.onPress(),
  );
  expect(mockCreateVideo).toHaveBeenCalledWith([
    { mediaUri: 'content://synthetic/photo3', mediaType: 'IMAGE' },
    { mediaUri: fixtures[0].mediaUri, mediaType: 'IMAGE' },
  ]);
  expect(mockShare).not.toHaveBeenCalled();
  expect(mockLoadMore).not.toHaveBeenCalled();
  expect(mockReload).not.toHaveBeenCalled();
  expect(viewer().props.item).toBeUndefined();
  expect(
    tree.root.findAll(
      node =>
        typeof node.props.children === 'string' &&
        node.props.children.includes('Review order, holds and format'),
    ).length,
  ).toBeGreaterThan(0);
});
test('video or mixed selection refuses photo-video creation without silently dropping any chosen file', async () => {
  await mount();
  await act(async () => button('Select library originals').props.onPress());
  await act(async () => {
    chooseTile(0);
    chooseTile(1);
  });
  expect(button('Make video from selected photos').props.disabled).toBe(true);
  await act(async () =>
    button('Make video from selected photos').props.onPress(),
  );
  expect(mockCreateVideo).not.toHaveBeenCalled();
  await act(async () => button('Share selected originals').props.onPress());
  expect(mockShare.mock.calls[0][0]).toHaveLength(2);
});
test('pending creator opening shares admission with Share and selection changes, then failure permits exact retry', async () => {
  let reject!: (error: Error) => void;
  mockCreateVideo.mockImplementationOnce(
    () =>
      new Promise((_, fail) => {
        reject = fail;
      }),
  );
  await mount();
  await act(async () => button('Select library originals').props.onPress());
  await act(async () => chooseTile(0));
  let pending!: Promise<void>;
  await act(async () => {
    pending = button('Make video from selected photos').props.onPress();
    button('Make video from selected photos').props.onPress();
    button('Share selected originals').props.onPress();
    button('Done selecting originals').props.onPress();
    chooseTile(1);
  });
  expect(mockCreateVideo).toHaveBeenCalledTimes(1);
  expect(mockShare).not.toHaveBeenCalled();
  expect(
    button('Make video from selected photos').props.accessibilityState.busy,
  ).toBe(true);
  await act(async () => {
    reject(new Error('Synthetic creator access failure'));
    await pending;
  });
  await act(async () =>
    button('Make video from selected photos').props.onPress(),
  );
  expect(mockCreateVideo).toHaveBeenCalledTimes(2);
  expect(mockCreateVideo.mock.calls[1][0]).toEqual([
    { mediaUri: fixtures[0].mediaUri, mediaType: 'IMAGE' },
  ]);
});
test('retained Make video cannot launch a creator after the Library closes', async () => {
  await mount();
  await act(async () => button('Select library originals').props.onPress());
  await act(async () => chooseTile(0));
  const create = button('Make video from selected photos').props.onPress;
  await act(async () => tree.unmount());
  await act(async () => create());
  expect(mockCreateVideo).not.toHaveBeenCalled();
});
test('a refused creator opening is reported rather than claiming a prepared video', async () => {
  mockCreateVideo.mockResolvedValueOnce({ opened: false });
  await mount();
  await act(async () => button('Select library originals').props.onPress());
  await act(async () => chooseTile(0));
  await act(async () =>
    button('Make video from selected photos').props.onPress(),
  );
  expect(
    tree.root
      .findAllByProps({ accessibilityRole: 'alert' })
      .some(
        node =>
          node.props.children ===
          'The photo video creator could not open. Try again.',
      ),
  ).toBe(true);
  expect(button('Make video from selected photos').props.disabled).toBe(false);
});
