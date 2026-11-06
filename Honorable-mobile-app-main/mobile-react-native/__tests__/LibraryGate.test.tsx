import React from 'react';
import {
  AccessibilityInfo,
  Alert,
  BackHandler,
  Linking,
  Text,
} from 'react-native';
import Renderer, { act } from 'react-test-renderer';
import { LibraryGate } from '../src/library/LibraryGate';
import {
  honorableNative,
  type IndexStatus,
} from '../src/native/HonorableNative';

let mockStatus: IndexStatus;
let mockLoading = false;
let mockIndexing = false;
let mockError = '';
let mockBack: Parameters<typeof BackHandler.addEventListener>[1] | undefined;
const mockBackRemove = jest.fn();
const mockRequestAccess = jest.fn();
const mockReindex = jest.fn();
const mockReload = jest.fn();
jest.mock('../src/library/LibraryContext', () => ({
  useLibrary: () => ({
    status: mockStatus,
    loading: mockLoading,
    indexing: mockIndexing,
    error: mockError,
    items: [],
    total: 0,
    loadMore: jest.fn().mockResolvedValue(undefined),
    requestAccess: mockRequestAccess,
    reindex: mockReindex,
    reload: mockReload,
  }),
}));
jest.mock('../src/native/HonorableNative', () => ({
  honorableNative: {
    pickWorkspaceMedia: jest.fn(),
    editPhoto: jest.fn(),
    editVideo: jest.fn(),
    createDrawingGuest: jest.fn(),
    createImageGuest: jest.fn(),
    createPhotoVideoGuest: jest.fn(),
    openCanvasProjectsGuest: jest.fn(),
    openPhotoVideoProjectsGuest: jest.fn(),
  },
}));
jest.mock('react-native-safe-area-context', () => ({
  SafeAreaView: require('react-native').View,
}));
jest.mock('lucide-react-native', () => {
  const { View } = require('react-native');
  return Object.fromEntries(
    [
      'Images',
      'ShieldCheck',
      'ArrowUpRight',
      'ChevronRight',
      'Film',
      'Image',
      'Plus',
      'Info',
      'X',
      'SlidersHorizontal',
      'Crop',
      'Type',
      'WandSparkles',
      'Music2',
      'Download',
      'PenLine',
    ].map(name => [name, View]),
  );
});

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
  mockReload.mockResolvedValue(undefined);
  jest.spyOn(Linking, 'openSettings').mockResolvedValue(undefined);
  mockIndexing = false;
  mockError = '';
  jest
    .spyOn(AccessibilityInfo, 'isReduceMotionEnabled')
    .mockResolvedValue(false);
  jest
    .spyOn(AccessibilityInfo, 'addEventListener')
    .mockReturnValue({ remove: jest.fn() });
  jest
    .spyOn(BackHandler, 'addEventListener')
    .mockImplementation((_, handler) => {
      mockBack = handler;
      return { remove: mockBackRemove };
    });
  mockStatus = {
    engine: 'REAL',
    permissionGranted: false,
    indexedCount: 0,
    status: 'IDLE',
  };
  native.pickWorkspaceMedia.mockResolvedValue({ uri: 'content://test/chosen' });
  native.editPhoto.mockResolvedValue({ opened: true });
  native.editVideo.mockResolvedValue({ opened: true });
  native.createDrawingGuest.mockResolvedValue({ opened: true });
  native.createImageGuest.mockResolvedValue({ opened: true });
  native.createPhotoVideoGuest.mockResolvedValue({ opened: true });
  native.openCanvasProjectsGuest.mockResolvedValue({ opened: true });
  native.openPhotoVideoProjectsGuest.mockResolvedValue({ opened: true });
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

test.each(['loading', 'denied', 'empty', 'indexing', 'error'] as const)(
  'existing Studio creation routes stay reachable during %s library setup without unlocking search',
  async state => {
    mockLoading = state === 'loading';
    mockIndexing = state === 'indexing';
    mockStatus.permissionGranted = state === 'empty' || state === 'indexing';
    if (state === 'error') mockError = 'Synthetic library failure';
    await render();
    await act(async () =>
      button('Open Studio without library setup').props.onPress(),
    );
    expect(button('Back to library setup')).toBeDefined();
    for (const label of [
      'Design with photos & text',
      'Create a blank square image',
      'Turn photos into a video',
      'Saved image projects',
      'Saved photo videos',
    ])
      await act(async () => button(label).props.onPress());
    expect(native.createDrawingGuest).toHaveBeenCalledTimes(1);
    expect(native.createImageGuest).toHaveBeenCalledWith('square');
    expect(native.createPhotoVideoGuest).toHaveBeenCalledTimes(1);
    expect(native.openCanvasProjectsGuest).toHaveBeenCalledTimes(1);
    expect(native.openPhotoVideoProjectsGuest).toHaveBeenCalledTimes(1);
    expect(tree.root.findAllByProps({ testID: 'main-app' })).toHaveLength(0);
    expect(mockRequestAccess).not.toHaveBeenCalled();
    expect(mockReindex).not.toHaveBeenCalled();
    expect(native.pickWorkspaceMedia).not.toHaveBeenCalled();
    await act(async () => button('Back to library setup').props.onPress());
    expect(button('Open Studio without library setup')).toBeDefined();
    expect(tree.root.findAllByProps({ testID: 'main-app' })).toHaveLength(0);
  },
);
test('hardware Back closes the inspector before returning to library setup', async () => {
  await render();
  await act(async () =>
    button('Open Studio without library setup').props.onPress(),
  );
  await act(async () => button('Open tools inspector').props.onPress());
  await act(async () => {
    expect(mockBack?.({ type: 'hardwareBackPress', timeStamp: 0 })).toBe(true);
  });
  expect(button('Back to library setup')).toBeDefined();
  await act(async () => {
    expect(mockBack?.({ type: 'hardwareBackPress', timeStamp: 0 })).toBe(true);
  });
  expect(button('Open Studio without library setup')).toBeDefined();
  expect(mockBackRemove).toHaveBeenCalled();
});
test('returning from Studio continues to the main app only after library readiness changes', async () => {
  await render();
  await act(async () =>
    button('Open Studio without library setup').props.onPress(),
  );
  mockStatus.permissionGranted = true;
  mockStatus.indexedCount = 1;
  await act(async () =>
    tree.update(
      <LibraryGate>
        <Text testID="main-app">Main app</Text>
      </LibraryGate>,
    ),
  );
  expect(button('Back to library setup')).toBeDefined();
  expect(tree.root.findAllByProps({ testID: 'main-app' })).toHaveLength(0);
  await act(async () => button('Back to library setup').props.onPress());
  expect(
    tree.root.findAllByProps({ testID: 'main-app' }).length,
  ).toBeGreaterThan(0);
});
test('leaving setup Studio invalidates a pending picker instead of opening its late result', async () => {
  let finish!: (value: { uri: string }) => void;
  native.pickWorkspaceMedia.mockImplementationOnce(
    () =>
      new Promise(resolve => {
        finish = resolve;
      }),
  );
  await render();
  await act(async () =>
    button('Open Studio without library setup').props.onPress(),
  );
  let pending!: Promise<void>;
  await act(async () => {
    pending = button('Choose a photo to edit').props.onPress();
  });
  await act(async () => button('Back to library setup').props.onPress());
  await act(async () => {
    finish({ uri: 'content://synthetic/late' });
    await pending;
  });
  expect(native.editPhoto).not.toHaveBeenCalled();
  expect(button('Open Studio without library setup')).toBeDefined();
  expect(tree.root.findAllByProps({ testID: 'main-app' })).toHaveLength(0);
});
async function attemptAccess() {
  await render();
  await act(async () => button('Choose photos & videos').props.onPress());
}
test('settings recovery appears after a denied access attempt and explicit checking never prompts or indexes', async () => {
  await render();
  expect(button('Open device settings')).toBeUndefined();
  await act(async () => button('Choose photos & videos').props.onPress());
  expect(mockRequestAccess).toHaveBeenCalledTimes(1);
  await act(async () => button('Open device settings').props.onPress());
  expect(Linking.openSettings).toHaveBeenCalledTimes(1);
  await act(async () => button('Check photo access again').props.onPress());
  expect(mockReload).toHaveBeenCalledTimes(1);
  expect(mockRequestAccess).toHaveBeenCalledTimes(1);
  expect(mockReindex).not.toHaveBeenCalled();
  expect(tree.root.findAllByProps({ testID: 'main-app' })).toHaveLength(0);
});
test('settings and access checking share same-tick admission until the active action settles', async () => {
  let finish!: () => void;
  jest.mocked(Linking.openSettings).mockImplementationOnce(
    () =>
      new Promise(resolve => {
        finish = resolve;
      }),
  );
  await attemptAccess();
  let pending!: Promise<void>;
  await act(async () => {
    const settings = button('Open device settings');
    const check = button('Check photo access again');
    pending = settings.props.onPress();
    settings.props.onPress();
    check.props.onPress();
  });
  expect(Linking.openSettings).toHaveBeenCalledTimes(1);
  expect(mockReload).not.toHaveBeenCalled();
  expect(button('Check photo access again').props.disabled).toBe(true);
  await act(async () => {
    finish();
    await pending;
  });
  expect(button('Check photo access again').props.disabled).toBe(false);
  await act(async () => button('Check photo access again').props.onPress());
  expect(mockReload).toHaveBeenCalledTimes(1);
});
test('settings failure gives actionable feedback and remains retryable without unlocking search', async () => {
  jest
    .mocked(Linking.openSettings)
    .mockRejectedValueOnce(new Error('Synthetic settings failure'));
  await attemptAccess();
  await act(async () => button('Open device settings').props.onPress());
  expect(
    tree.root.findAllByProps({ accessibilityRole: 'alert' }).length,
  ).toBeGreaterThan(0);
  await act(async () => button('Open device settings').props.onPress());
  expect(Linking.openSettings).toHaveBeenCalledTimes(2);
  expect(tree.root.findAllByProps({ accessibilityRole: 'alert' })).toHaveLength(
    0,
  );
  expect(tree.root.findAllByProps({ testID: 'main-app' })).toHaveLength(0);
});
test('late settings failure and retained actions cannot affect Studio after leaving recovery', async () => {
  let fail!: (reason: Error) => void;
  jest.mocked(Linking.openSettings).mockImplementationOnce(
    () =>
      new Promise((_, reject) => {
        fail = reject;
      }),
  );
  await attemptAccess();
  const retained = button('Open device settings').props.onPress;
  let pending!: Promise<void>;
  await act(async () => {
    pending = retained();
  });
  await act(async () =>
    button('Open Studio without library setup').props.onPress(),
  );
  await act(async () => {
    fail(new Error('Late failure'));
    await pending;
    await retained();
  });
  expect(Linking.openSettings).toHaveBeenCalledTimes(1);
  expect(tree.root.findAllByProps({ accessibilityRole: 'alert' })).toHaveLength(
    0,
  );
  expect(button('Back to library setup')).toBeDefined();
  await act(async () => button('Back to library setup').props.onPress());
  expect(button('Open device settings').props.disabled).toBe(false);
  expect(mockRequestAccess).toHaveBeenCalledTimes(1);
});
test('access recheck follows the existing readiness gate instead of granting access itself', async () => {
  await attemptAccess();
  await act(async () => button('Check photo access again').props.onPress());
  expect(tree.root.findAllByProps({ testID: 'main-app' })).toHaveLength(0);
  mockStatus.permissionGranted = true;
  mockStatus.indexedCount = 1;
  await act(async () =>
    tree.update(
      <LibraryGate>
        <Text testID="main-app">Main app</Text>
      </LibraryGate>,
    ),
  );
  expect(
    tree.root.findAllByProps({ testID: 'main-app' }).length,
  ).toBeGreaterThan(0);
  expect(button('Open device settings')).toBeUndefined();
  expect(mockReload).toHaveBeenCalledTimes(1);
  expect(mockReindex).not.toHaveBeenCalled();
});
