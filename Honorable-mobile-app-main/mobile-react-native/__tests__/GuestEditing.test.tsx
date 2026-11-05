import React from 'react';
import { AccessibilityInfo, Alert, BackHandler, Platform, Text } from 'react-native';
import Renderer, { act } from 'react-test-renderer';
import { AccountGate } from '../src/auth/AccountGate';
import { honorableNative } from '../src/native/HonorableNative';

// Resolve the lazy native module outside individual event-test timeout windows.
const hardwareBack = BackHandler;
const originalPlatform = Platform.OS;
const mockAuth = {
  signedIn: false,
  status: 'welcome',
  googleConfigured: false,
  error: undefined as string | undefined,
  signInGoogle: jest.fn(),
  refresh: jest.fn(),
  startSearch: jest.fn(),
  charge: jest.fn(),
  buy: jest.fn(),
  restore: jest.fn(),
};
const mockAccountMounted = jest.fn();
jest.mock('../src/passes/MemoryPassContext', () => ({
  useMemoryPass: () => mockAuth,
}));
jest.mock('../src/screens/BetaUpdateNotice', () => ({
  BetaUpdateNotice: () => null,
}));
jest.mock('../src/native/HonorableNative', () => ({
  honorableNative: {
    createPhotoVideoGuest: jest.fn(),
    openPhotoVideoProjectsGuest: jest.fn(),
    openCanvasProjectsGuest: jest.fn(),
    createDrawingGuest: jest.fn(),
    createImageGuest: jest.fn(),
    createCustomImageGuest: jest.fn(),
    canvasSetups: jest.fn(),
    pickWorkspaceMedia: jest.fn(),
    editPhotoGuest: jest.fn(),
    editVideoGuest: jest.fn(),
    editPhoto: jest.fn(),
    editVideo: jest.fn(),
    accountSessionAction: jest.fn(),
    search: jest.fn(),
  },
}));
jest.mock('react-native-safe-area-context', () => ({
  SafeAreaView: require('react-native').View,
}));
jest.mock('lucide-react-native', () => ({
  Image: require('react-native').View,
  Film: require('react-native').View,
  PenLine: require('react-native').View,
  Plus: require('react-native').View,
  ArrowUpRight: require('react-native').View,
  UserRound: require('react-native').View,
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
function AccountContent() {
  mockAccountMounted();
  return <Text testID="account-app">Search and paid account routes</Text>;
}
const render = async () => {
  await act(async () => {
    tree = Renderer.create(
      <AccountGate>
        <AccountContent />
      </AccountGate>,
    );
  });
};
const enterGuest = async () => {
  await render();
  await act(async () => button('Edit without an account').props.onPress());
};
beforeEach(() => {
  jest.resetAllMocks();
  jest.spyOn(AccessibilityInfo, 'isReduceMotionEnabled').mockResolvedValue(true);
  jest.spyOn(AccessibilityInfo, 'addEventListener').mockReturnValue({ remove: jest.fn() });
  Object.defineProperty(Platform, 'OS', {
    value: 'android',
    configurable: true,
  });
  mockAuth.signedIn = false;
  mockAuth.status = 'welcome';
  mockAuth.googleConfigured = false;
  mockAuth.error = undefined;
  native.pickWorkspaceMedia.mockResolvedValue({ uri: 'content://test/guest' });
  native.createPhotoVideoGuest.mockResolvedValue({ opened: true });
  native.openPhotoVideoProjectsGuest.mockResolvedValue({ opened: true });
  native.openCanvasProjectsGuest.mockResolvedValue({ opened: true });
  native.createDrawingGuest.mockResolvedValue({ opened: true });
  native.createImageGuest.mockResolvedValue({ opened: true });
  native.createCustomImageGuest.mockResolvedValue({ opened: true });
  native.canvasSetups.mockResolvedValue('[]');
  native.editPhotoGuest.mockResolvedValue({ opened: true });
  native.editVideoGuest.mockResolvedValue({ opened: true });
});
afterEach(async () => {
  await act(async () => tree.unmount());
  Object.defineProperty(Platform, 'OS', {
    value: originalPlatform,
    configurable: true,
  });
  jest.restoreAllMocks();
});

test('an unconfigured first launch explains sign-in and offers local editing', async () => {
  await render();
  expect(button('Continue with Google').props.disabled).toBe(true);
  expect(button('Edit without an account')).toBeDefined();
  expect(JSON.stringify(tree.toJSON())).toContain(
    'Account sign-in is not configured',
  );
  expect(mockAccountMounted).not.toHaveBeenCalled();
});

test.each(['IMAGE', 'VIDEO'] as const)(
  'guest %s opens only the selected source through the dedicated local bridge',
  async kind => {
    await enterGuest();
    await act(async () =>
      button(
        kind === 'IMAGE' ? 'Edit a photo' : 'Edit a video',
      ).props.onPress(),
    );
    expect(native.pickWorkspaceMedia).toHaveBeenCalledWith(kind);
    expect(
      kind === 'IMAGE' ? native.editPhotoGuest : native.editVideoGuest,
    ).toHaveBeenCalledWith('content://test/guest');
    expect(
      kind === 'IMAGE' ? native.editVideoGuest : native.editPhotoGuest,
    ).not.toHaveBeenCalled();
    expect(native.editPhoto).not.toHaveBeenCalled();
    expect(native.editVideo).not.toHaveBeenCalled();
    expect(mockAccountMounted).not.toHaveBeenCalled();
  },
);

test('guest navigation exposes only local editors and sign-in, never account/search spending', async () => {
  await enterGuest();
  const labels = new Set(
    tree.root
      .findAll(
        node =>
          typeof node.props.onPress === 'function' &&
          !!node.props.accessibilityLabel,
      )
      .map(node => node.props.accessibilityLabel),
  );
  expect([...labels].sort()).toEqual([
    'Back to sign in',
    'Create a video',
    'Create an image',
    'Draw an image',
    'Edit a photo',
    'Edit a video',
    'Help me get started',
    'Saved image projects',
    'Saved photo videos',
  ]);
  expect(mockAccountMounted).not.toHaveBeenCalled();
  expect(native.accountSessionAction).not.toHaveBeenCalled();
  expect(native.search).not.toHaveBeenCalled();
  expect(mockAuth.startSearch).not.toHaveBeenCalled();
  expect(mockAuth.charge).not.toHaveBeenCalled();
  expect(mockAuth.buy).not.toHaveBeenCalled();
  expect(mockAuth.restore).not.toHaveBeenCalled();
  expect(mockAuth.signInGoogle).not.toHaveBeenCalled();
});

test('returning from guest mode retains the honest unavailable sign-in path', async () => {
  mockAuth.status = 'unavailable';
  mockAuth.error = 'Connect to restore your account.';
  await enterGuest();
  await act(async () => button('Back to sign in').props.onPress());
  expect(button('Continue with Google').props.disabled).toBe(true);
  expect(button('Edit without an account')).toBeDefined();
  expect(button('Edit a photo')).toBeUndefined();
  expect(JSON.stringify(tree.toJSON())).toContain(
    'Connect to restore your account.',
  );
  expect(mockAccountMounted).not.toHaveBeenCalled();
});

test('signed-in sessions retain their existing authenticated routes', async () => {
  mockAuth.signedIn = true;
  mockAuth.status = 'online';
  await render();
  expect(mockAccountMounted).toHaveBeenCalled();
  expect(button('Edit without an account')).toBeUndefined();
  expect(native.editPhotoGuest).not.toHaveBeenCalled();
  expect(native.editVideoGuest).not.toHaveBeenCalled();
});

test.each(['restoring', 'signing-in'])(
  '%s still waits for account resolution before displaying entry choices',
  async status => {
    mockAuth.status = status;
    await render();
    expect(button('Edit without an account')).toBeUndefined();
    expect(button('Edit a photo')).toBeUndefined();
    expect(mockAccountMounted).not.toHaveBeenCalled();
  },
);

test('cancelling a guest picker stays in guest mode without opening an editor', async () => {
  native.pickWorkspaceMedia.mockResolvedValue(null);
  await enterGuest();
  await act(async () => button('Edit a photo').props.onPress());
  expect(native.editPhotoGuest).not.toHaveBeenCalled();
  expect(native.editVideoGuest).not.toHaveBeenCalled();
  expect(button('Edit a photo').props.disabled).toBe(false);
  expect(mockAccountMounted).not.toHaveBeenCalled();
});

test('guest picker failures show their action message and permit retry', async () => {
  const alert = jest.spyOn(Alert, 'alert').mockImplementation(() => undefined);
  native.pickWorkspaceMedia.mockRejectedValue(
    new Error('The media picker is unavailable'),
  );
  await enterGuest();
  await act(async () => button('Edit a video').props.onPress());
  expect(alert).toHaveBeenCalledWith(
    'Open workspace',
    'The media picker is unavailable',
  );
  expect(button('Edit a video').props.disabled).toBe(false);
  expect(native.editVideoGuest).not.toHaveBeenCalled();
  expect(mockAccountMounted).not.toHaveBeenCalled();
});

test('a pending guest picker blocks duplicate launches and leaving that request behind', async () => {
  let resolve!: (value: null) => void;
  native.pickWorkspaceMedia.mockImplementation(
    () =>
      new Promise(done => {
        resolve = done;
      }),
  );
  await enterGuest();
  let pending!: Promise<void>;
  await act(async () => {
    pending = button('Edit a photo').props.onPress();
  });
  expect(button('Edit a video').props.disabled).toBe(true);
  expect(button('Back to sign in').props.disabled).toBe(true);
  await act(async () => button('Edit a video').props.onPress());
  expect(native.pickWorkspaceMedia).toHaveBeenCalledTimes(1);
  await act(async () => {
    resolve(null);
    await pending;
  });
  expect(button('Edit a video').props.disabled).toBe(false);
  expect(button('Back to sign in').props.disabled).toBe(false);
});

test.each([
  ['Square', 'square'], ['Portrait', 'portrait'], ['Landscape', 'landscape'],
  ['Feed portrait', 'feed_portrait'], ['Story', 'story'], ['Reels', 'reels'],
  ['Ultrawide', 'ultrawide'], ['Postcard', 'postcard'], ['Banner', 'banner'],
  ['Poster', 'poster'], ['Cover', 'cover'], ['Wallpaper', 'wallpaper'],
])('canvas browser opens %s without account or media access', async (label, format) => {
  await enterGuest();
  await act(async () => button('Create an image').props.onPress());
  await act(async () => button(`Create ${label} canvas`).props.onPress());
  expect(native.createImageGuest).toHaveBeenCalledWith(format);
  expect(native.pickWorkspaceMedia).not.toHaveBeenCalled();
  expect(native.accountSessionAction).not.toHaveBeenCalled();
  expect(mockAccountMounted).not.toHaveBeenCalled();
});

test('closing canvas choices creates nothing and retains guest editing', async () => {
  await enterGuest();
  await act(async () => button('Create an image').props.onPress());
  expect(button('Create Wallpaper canvas')).toBeDefined();
  await act(async () => button('Close canvas choices').props.onPress());
  expect(button('Create Wallpaper canvas')).toBeUndefined();
  expect(native.createImageGuest).not.toHaveBeenCalled();
  expect(button('Edit a photo')).toBeDefined();
});
test('failed canvas creation permits retry without spending credits', async () => {
  const alert = jest.spyOn(Alert, 'alert').mockImplementation(() => undefined);
  native.createImageGuest.mockRejectedValue(new Error('Storage unavailable'));
  await enterGuest();
  await act(async () => button('Create an image').props.onPress());
  await act(async () => button('Create Square canvas').props.onPress());
  expect(alert).toHaveBeenLastCalledWith(
    'Open workspace',
    'Storage unavailable',
  );
  expect(button('Create Square canvas').props.disabled).toBe(false);
  expect(mockAuth.charge).not.toHaveBeenCalled();
});

test('iOS shell explains missing editors without offering broken guest navigation', async () => {
  Object.defineProperty(Platform, 'OS', { value: 'ios', configurable: true });
  await render();
  expect(button('Edit without an account')).toBeUndefined();
  expect(JSON.stringify(tree.toJSON())).toContain(
    'iOS editing is not implemented yet.',
  );
  expect(native.editPhotoGuest).not.toHaveBeenCalled();
  expect(native.editVideoGuest).not.toHaveBeenCalled();
});

test('drawing opens the local guest workspace without a picker or account call', async () => {
  await enterGuest();
  await act(async () => button('Draw an image').props.onPress());
  expect(native.createDrawingGuest).toHaveBeenCalledTimes(1);
  expect(native.pickWorkspaceMedia).not.toHaveBeenCalled();
  expect(native.accountSessionAction).not.toHaveBeenCalled();
  expect(mockAuth.charge).not.toHaveBeenCalled();
});
test('drawing launch failure permits retry', async () => {
  const alert = jest.spyOn(Alert, 'alert').mockImplementation(() => undefined);
  native.createDrawingGuest.mockRejectedValue(new Error('Drawing unavailable'));
  await enterGuest();
  await act(async () => button('Draw an image').props.onPress());
  expect(alert).toHaveBeenCalledWith('Open workspace', 'Drawing unavailable');
  expect(button('Draw an image').props.disabled).toBe(false);
});
test('pending drawing blocks a second editor launch', async () => {
  let resolve!: (value: { opened: boolean }) => void;
  native.createDrawingGuest.mockImplementation(
    () =>
      new Promise(done => {
        resolve = done;
      }),
  );
  await enterGuest();
  let pending!: Promise<void>;
  await act(async () => {
    pending = button('Draw an image').props.onPress();
  });
  await act(async () => button('Edit a video').props.onPress());
  expect(native.pickWorkspaceMedia).not.toHaveBeenCalled();
  expect(button('Back to sign in').props.disabled).toBe(true);
  await act(async () => {
    resolve({ opened: true });
    await pending;
  });
  expect(button('Edit a video').props.disabled).toBe(false);
});

 test('canvas search filters by name and dimensions and recovers from no matches', async () => {
  await enterGuest();
  await act(async () => button('Create an image').props.onPress());
  const search = tree.root.findAll(node => node.props.accessibilityLabel === 'Search canvas formats' && typeof node.props.onChangeText === 'function')[0];
  await act(async () => search.props.onChangeText('  BANNER  '));
  expect(button('Create Banner canvas')).toBeDefined();
  expect(button('Create Square canvas')).toBeUndefined();
  await act(async () => search.props.onChangeText('2560'));
  expect(button('Create Ultrawide canvas')).toBeDefined();
  expect(button('Create Banner canvas')).toBeUndefined();
  await act(async () => search.props.onChangeText('missing'));
  expect(button('Create Ultrawide canvas')).toBeUndefined();
  expect(native.createImageGuest).not.toHaveBeenCalled();
  await act(async () => search.props.onChangeText(''));
  expect(button('Create Square canvas')).toBeDefined();
 });

const input = (label: string) => tree.root.findAll(node => node.props.accessibilityLabel === label && typeof node.props.onChangeText === 'function')[0];
test('custom canvas validates allocation limits and forwards exact dimensions and alpha choice', async () => {
  await enterGuest();
  await act(async () => button('Create an image').props.onPress());
  await act(async () => button('Custom canvas and setups').props.onPress());
  await act(async () => input('Canvas width in pixels').props.onChangeText('4096'));
  await act(async () => input('Canvas height in pixels').props.onChangeText('4096'));
  expect(button('Create custom canvas').props.disabled).toBe(true);
  for (const invalid of ['0', '63', '4097', '1.5', '-100', '1e3', '']) {
    await act(async () => input('Canvas width in pixels').props.onChangeText(invalid));
    expect(button('Create custom canvas').props.disabled).toBe(true);
  }
  await act(async () => input('Canvas width in pixels').props.onChangeText('800'));
  await act(async () => input('Canvas height in pixels').props.onChangeText('600'));
  await act(async () => button('transparent canvas background').props.onPress());
  expect(button('Create custom canvas').props.disabled).toBe(false);
  await act(async () => button('Create custom canvas').props.onPress());
  expect(native.createCustomImageGuest).toHaveBeenCalledWith(800, 600, 'transparent');
  expect(native.pickWorkspaceMedia).not.toHaveBeenCalled();
  expect(mockAuth.charge).not.toHaveBeenCalled();
});

test('named canvas setups restore dimensions and background without creating media', async () => {
  native.canvasSetups.mockResolvedValue(JSON.stringify([{name:'My cover',width:1600,height:900,background:'black'}]));
  await enterGuest();
  await act(async () => button('Create an image').props.onPress());
  await act(async () => button('Custom canvas and setups').props.onPress());
  await act(async () => button('Load canvas setup My cover').props.onPress());
  expect(input('Canvas width in pixels').props.value).toBe('1600');
  expect(input('Canvas height in pixels').props.value).toBe('900');
  expect(native.createCustomImageGuest).not.toHaveBeenCalled();
  await act(async () => button('Save canvas setup').props.onPress());
  expect(native.canvasSetups).toHaveBeenLastCalledWith('save','My cover',1600,900,'black');
  await act(async () => button('Create custom canvas').props.onPress());
  expect(native.createCustomImageGuest).toHaveBeenCalledWith(1600,900,'black');
  native.canvasSetups.mockResolvedValue('[]');
  await act(async () => button('Delete canvas setup My cover').props.onPress());
  expect(button('Load canvas setup My cover')).toBeDefined();
  await act(async () => button('Confirm delete saved canvas setup').props.onPress());
  expect(button('Load canvas setup My cover')).toBeUndefined();
});
test('malformed saved setups show an error and leave custom creation available', async () => {
  native.canvasSetups.mockResolvedValue('[{"name":"bad","width":900000,"height":900000,"background":"white"}]');
  await enterGuest();
  await act(async () => button('Create an image').props.onPress());
  await act(async () => button('Custom canvas and setups').props.onPress());
  expect(button('Load canvas setup bad')).toBeUndefined();
  expect(button('Create custom canvas').props.disabled).toBe(false);
  expect(native.createCustomImageGuest).not.toHaveBeenCalled();
});

test('saved image projects open the private guest shelf without picking media or mounting account routes', async () => {
  await enterGuest();
  await act(async () => button('Saved image projects').props.onPress());
  expect(native.openCanvasProjectsGuest).toHaveBeenCalledTimes(1);
  expect(native.pickWorkspaceMedia).not.toHaveBeenCalled();
  expect(native.accountSessionAction).not.toHaveBeenCalled();
  expect(mockAuth.charge).not.toHaveBeenCalled();
  expect(mockAccountMounted).not.toHaveBeenCalled();
});

test('saved image project entry shares the guard with all other guest editors', async () => {
  let complete: (value: { opened: boolean }) => void = () => {};
  native.openCanvasProjectsGuest.mockImplementation(() => new Promise(resolve => { complete = resolve; }));
  await enterGuest();
  await act(async () => { button('Saved image projects').props.onPress(); });
  expect(button('Saved image projects').props.disabled).toBe(true);
  expect(button('Edit a photo').props.disabled).toBe(true);
  await act(async () => { button('Saved image projects').props.onPress(); });
  expect(native.openCanvasProjectsGuest).toHaveBeenCalledTimes(1);
  await act(async () => complete({ opened: true }));
  expect(button('Saved image projects').props.disabled).toBe(false);
});


it.each([
  ['Create a video', 'createPhotoVideoGuest'],
  ['Saved photo videos', 'openPhotoVideoProjectsGuest'],
] as const)('opens %s through the guest boundary with a shared busy guard', async (label, method) => {
  let complete: (value: { opened: boolean }) => void = () => {};
  native[method].mockImplementation(() => new Promise(resolve => { complete = resolve; }));
  await enterGuest();
  await act(async () => { button(label).props.onPress(); });
  expect(button(label).props.disabled).toBe(true);
  await act(async () => { button(label).props.onPress(); });
  expect(native[method]).toHaveBeenCalledTimes(1);
  complete({ opened: true });
  await act(async () => {});
  expect(button(label).props.disabled).toBe(false);
});


test('canvas modes hide unrelated routes and retain custom settings when switching', async () => {
  await enterGuest();
  await act(async () => button('Create an image').props.onPress());
  expect(button('Edit a photo')).toBeUndefined();
  expect(button('Create a video')).toBeUndefined();
  expect(input('Canvas width in pixels')).toBeUndefined();
  expect(button('Canvas formats').props.accessibilityState.selected).toBe(true);
  await act(async () => button('Custom canvas and setups').props.onPress());
  expect(input('Search canvas formats')).toBeUndefined();
  await act(async () => input('Canvas width in pixels').props.onChangeText('800'));
  await act(async () => button('Canvas formats').props.onPress());
  await act(async () => button('Custom canvas and setups').props.onPress());
  expect(input('Canvas width in pixels').props.value).toBe('800');
  await act(async () => button('Close canvas choices').props.onPress());
  expect(button('Edit a photo')).toBeDefined();
  expect(native.createImageGuest).not.toHaveBeenCalled();
  expect(native.createCustomImageGuest).not.toHaveBeenCalled();
});


test.each([
  ['Polish a photo', 'Choose my photo', 'editPhotoGuest'],
  ['Edit a video', 'Choose my video', 'editVideoGuest'],
  ['Turn photos into a video', 'Choose my story photos', 'createPhotoVideoGuest'],
  ['Make an original design', 'Open my drawing canvas', 'createDrawingGuest'],
] as const)('beginner guide launches %s locally only after explicit start', async (goal, action, method) => {
  await enterGuest();
  await act(async () => button('Help me get started').props.onPress());
  const choice = tree.root.findAll(node => node.props.accessibilityRole === 'radio' && node.props.accessibilityLabel === goal)[0];
  await act(async () => choice.props.onPress());
  expect(native.pickWorkspaceMedia).not.toHaveBeenCalled();
  expect(native[method]).not.toHaveBeenCalled();
  expect(choice.props.accessibilityState.checked).toBe(true);
  await act(async () => button(action).props.onPress());
  expect(native[method]).toHaveBeenCalledTimes(1);
  expect(native.accountSessionAction).not.toHaveBeenCalled();
  expect(mockAuth.charge).not.toHaveBeenCalled();
});

test('beginner guide retains its choice and shares the existing launch guard', async () => {
  let complete: (value: { opened: boolean }) => void = () => {};
  native.createPhotoVideoGuest.mockImplementation(() => new Promise(resolve => { complete = resolve; }));
  await enterGuest();
  await act(async () => button('Help me get started').props.onPress());
  await act(async () => button('Turn photos into a video').props.onPress());
  await act(async () => button('Help me get started').props.onPress());
  expect(button('Choose my story photos')).toBeUndefined();
  await act(async () => button('Help me get started').props.onPress());
  expect(button('Turn photos into a video').props.accessibilityState.checked).toBe(true);
  await act(async () => { button('Choose my story photos').props.onPress(); });
  expect(button('Choose my story photos').props.disabled).toBe(true);
  expect(button('Help me get started').props.disabled).toBe(true);
  await act(async () => { button('Choose my story photos').props.onPress(); });
  await act(async () => { button('Edit a photo').props.onPress(); });
  expect(native.createPhotoVideoGuest).toHaveBeenCalledTimes(1);
  expect(native.pickWorkspaceMedia).not.toHaveBeenCalled();
  await act(async () => complete({ opened: true }));
  expect(button('Choose my story photos').props.disabled).toBe(false);
});


test('video guide cancellation preserves the choice and permits an explicit retry', async () => {
  native.pickWorkspaceMedia.mockResolvedValueOnce(null);
  await enterGuest();
  await act(async () => button('Help me get started').props.onPress());
  // The guide radio and the studio card share the same plain-language title.
  const choice = () => tree.root.findAll(node => node.props.accessibilityRole === 'radio' && node.props.accessibilityLabel === 'Edit a video')[0];
  await act(async () => choice().props.onPress());
  await act(async () => button('Choose my video').props.onPress());
  expect(native.pickWorkspaceMedia).toHaveBeenLastCalledWith('VIDEO');
  expect(native.editVideoGuest).not.toHaveBeenCalled();
  expect(choice().props.accessibilityState.checked).toBe(true);
  expect(button('Choose my video').props.disabled).toBe(false);
  await act(async () => button('Choose my video').props.onPress());
  expect(native.editVideoGuest).toHaveBeenCalledWith('content://test/guest');
  expect(native.editPhotoGuest).not.toHaveBeenCalled();
  expect(mockAuth.charge).not.toHaveBeenCalled();
});

test('a pending video guide picker blocks other launches until cancellation', async () => {
  let complete!: (value: null) => void;
  native.pickWorkspaceMedia.mockImplementation(() => new Promise(resolve => { complete = resolve; }));
  await enterGuest();
  await act(async () => button('Help me get started').props.onPress());
  const choice = tree.root.findAll(node => node.props.accessibilityRole === 'radio' && node.props.accessibilityLabel === 'Edit a video')[0];
  await act(async () => choice.props.onPress());
  await act(async () => { button('Choose my video').props.onPress(); });
  expect(button('Choose my video').props.disabled).toBe(true);
  expect(button('Back to sign in').props.disabled).toBe(true);
  await act(async () => button('Choose my video').props.onPress());
  await act(async () => button('Edit a photo').props.onPress());
  expect(native.pickWorkspaceMedia).toHaveBeenCalledTimes(1);
  await act(async () => complete(null));
  expect(button('Choose my video').props.disabled).toBe(false);
  expect(native.editVideoGuest).not.toHaveBeenCalled();
});


test('custom canvas swaps orientation without losing the background or creating media', async () => {
  await enterGuest();
  await act(async () => button('Create an image').props.onPress());
  await act(async () => button('Custom canvas and setups').props.onPress());
  await act(async () => input('Canvas width in pixels').props.onChangeText('800'));
  await act(async () => input('Canvas height in pixels').props.onChangeText('600'));
  await act(async () => button('transparent canvas background').props.onPress());
  await act(async () => button('Swap width and height').props.onPress());
  expect(input('Canvas width in pixels').props.value).toBe('600');
  expect(input('Canvas height in pixels').props.value).toBe('800');
  expect(JSON.stringify(tree.toJSON())).toContain('480,000 of 4,194,304 pixels used');
  expect(native.createCustomImageGuest).not.toHaveBeenCalled();
  await act(async () => button('Create custom canvas').props.onPress());
  expect(native.createCustomImageGuest).toHaveBeenCalledWith(600, 800, 'transparent');
});

test('custom canvas explains the pixel budget and retains invalid input when swapped', async () => {
  await enterGuest();
  await act(async () => button('Create an image').props.onPress());
  await act(async () => button('Custom canvas and setups').props.onPress());
  await act(async () => input('Canvas width in pixels').props.onChangeText('4096'));
  await act(async () => input('Canvas height in pixels').props.onChangeText('4096'));
  expect(button('Create custom canvas').props.disabled).toBe(true);
  expect(JSON.stringify(tree.toJSON())).toContain('16,777,216 of 4,194,304 pixels used');
  expect(JSON.stringify(tree.toJSON())).toContain('Reduce the width or height');
  await act(async () => input('Canvas width in pixels').props.onChangeText(''));
  await act(async () => button('Swap width and height').props.onPress());
  expect(input('Canvas width in pixels').props.value).toBe('4096');
  expect(input('Canvas height in pixels').props.value).toBe('');
  expect(JSON.stringify(tree.toJSON())).toContain('Enter a whole number from 64 to 4096');
  expect(JSON.stringify(tree.toJSON())).not.toContain('pixels used');
  expect(button('Create custom canvas').props.disabled).toBe(true);
  expect(native.createCustomImageGuest).not.toHaveBeenCalled();
});


test('saved setup load failure retries in place while preserving the custom canvas', async () => {
  native.canvasSetups.mockRejectedValueOnce(new Error('Saved setups temporarily unavailable'));
  await enterGuest();
  await act(async () => button('Create an image').props.onPress());
  await act(async () => button('Custom canvas and setups').props.onPress());
  await act(async () => input('Canvas width in pixels').props.onChangeText('800'));
  await act(async () => input('Canvas height in pixels').props.onChangeText('600'));
  await act(async () => button('black canvas background').props.onPress());
  expect(JSON.stringify(tree.toJSON())).toContain('Saved setups temporarily unavailable');
  expect(JSON.stringify(tree.toJSON())).not.toContain('No saved setups yet.');
  native.canvasSetups.mockResolvedValue(JSON.stringify([{name:'Recovered',width:1600,height:900,background:'white'}]));
  await act(async () => button('Refresh saved setups').props.onPress());
  expect(button('Load canvas setup Recovered')).toBeDefined();
  expect(JSON.stringify(tree.toJSON())).not.toContain('Saved setups temporarily unavailable');
  expect(input('Canvas width in pixels').props.value).toBe('800');
  expect(input('Canvas height in pixels').props.value).toBe('600');
  expect(button('black canvas background').props.accessibilityState.checked).toBe(true);
  expect(native.createCustomImageGuest).not.toHaveBeenCalled();
  expect(mockAuth.charge).not.toHaveBeenCalled();
});

test('pending setup refresh prevents duplicate reads and does not report an empty shelf', async () => {
  await enterGuest();
  await act(async () => button('Create an image').props.onPress());
  await act(async () => button('Custom canvas and setups').props.onPress());
  expect(JSON.stringify(tree.toJSON())).toContain('No saved setups yet.');
  let complete!: (value: string) => void;
  native.canvasSetups.mockImplementation(() => new Promise(resolve => { complete = resolve; }));
  await act(async () => { button('Refresh saved setups').props.onPress(); });
  expect(button('Refresh saved setups').props.disabled).toBe(true);
  expect(button('Refresh saved setups').props.accessibilityState.busy).toBe(true);
  expect(JSON.stringify(tree.toJSON())).toContain('Updating saved setups');
  expect(JSON.stringify(tree.toJSON())).not.toContain('No saved setups yet.');
  await act(async () => { button('Refresh saved setups').props.onPress(); });
  expect(native.canvasSetups).toHaveBeenCalledTimes(2);
  await act(async () => complete('[]'));
  expect(button('Refresh saved setups').props.disabled).toBe(false);
  expect(JSON.stringify(tree.toJSON())).toContain('No saved setups yet.');
});


test.each([
  ['4096', '4096', '2048', '2048'],
  ['4096', '2048', '2896', '1448'],
  ['2048', '4096', '1448', '2896'],
])('over-budget %s by %s canvas fits proportionally before explicit creation', async (width, height, fittedWidth, fittedHeight) => {
  await enterGuest();
  await act(async () => button('Create an image').props.onPress());
  await act(async () => button('Custom canvas and setups').props.onPress());
  await act(async () => input('Canvas width in pixels').props.onChangeText(width));
  await act(async () => input('Canvas height in pixels').props.onChangeText(height));
  await act(async () => input('Canvas setup name').props.onChangeText('My design'));
  await act(async () => button('transparent canvas background').props.onPress());
  expect(button('Create custom canvas').props.disabled).toBe(true);
  await act(async () => button('Fit within pixel limit').props.onPress());
  expect(input('Canvas width in pixels').props.value).toBe(fittedWidth);
  expect(input('Canvas height in pixels').props.value).toBe(fittedHeight);
  expect(Number(fittedWidth) * Number(fittedHeight)).toBeLessThanOrEqual(4194304);
  expect(input('Canvas setup name').props.value).toBe('My design');
  expect(button('transparent canvas background').props.accessibilityState.checked).toBe(true);
  expect(button('Fit within pixel limit')).toBeUndefined();
  expect(button('Create custom canvas').props.disabled).toBe(false);
  expect(native.createCustomImageGuest).not.toHaveBeenCalled();
  await act(async () => button('Create custom canvas').props.onPress());
  expect(native.createCustomImageGuest).toHaveBeenCalledWith(Number(fittedWidth), Number(fittedHeight), 'transparent');
  expect(mockAuth.charge).not.toHaveBeenCalled();
});


test('Android Back closes canvas choices and retains custom settings for the next visit', async () => {
  let back: () => boolean | null | undefined = () => false;
  const remove = jest.fn();
  const subscribe = jest.spyOn(hardwareBack, 'addEventListener').mockImplementation((_event, listener) => {
    back = () => listener({ type: 'hardwareBackPress', timeStamp: 0 }); return { remove };
  });
  await enterGuest();
  expect(subscribe).not.toHaveBeenCalled();
  await act(async () => button('Create an image').props.onPress());
  await act(async () => button('Custom canvas and setups').props.onPress());
  await act(async () => input('Canvas width in pixels').props.onChangeText('800'));
  await act(async () => input('Canvas height in pixels').props.onChangeText('600'));
  await act(async () => button('black canvas background').props.onPress());
  await act(async () => { expect(back()).toBe(true); });
  expect(button('Close canvas choices')).toBeUndefined();
  expect(button('Edit a photo')).toBeDefined();
  expect(remove).toHaveBeenCalledTimes(1);
  expect(native.createCustomImageGuest).not.toHaveBeenCalled();
  await act(async () => button('Create an image').props.onPress());
  await act(async () => button('Custom canvas and setups').props.onPress());
  expect(input('Canvas width in pixels').props.value).toBe('800');
  expect(input('Canvas height in pixels').props.value).toBe('600');
  expect(button('black canvas background').props.accessibilityState.checked).toBe(true);
  await act(async () => tree.unmount());
  expect(remove).toHaveBeenCalledTimes(2);
});

test('Android Back is consumed while canvas creation is pending, then closes after completion', async () => {
  let back: () => boolean | null | undefined = () => false;
  jest.spyOn(hardwareBack, 'addEventListener').mockImplementation((_event, listener) => {
    back = () => listener({ type: 'hardwareBackPress', timeStamp: 0 }); return { remove: jest.fn() };
  });
  let complete!: (result: { opened: boolean }) => void;
  native.createCustomImageGuest.mockImplementation(() => new Promise(resolve => { complete = resolve; }));
  await enterGuest();
  await act(async () => button('Create an image').props.onPress());
  await act(async () => button('Custom canvas and setups').props.onPress());
  await act(async () => { button('Create custom canvas').props.onPress(); });
  await act(async () => { expect(back()).toBe(true); });
  expect(button('Close canvas choices')).toBeDefined();
  expect(button('Create custom canvas').props.disabled).toBe(true);
  expect(button('Edit a photo')).toBeUndefined();
  await act(async () => complete({ opened: true }));
  await act(async () => { expect(back()).toBe(true); });
  expect(button('Close canvas choices')).toBeUndefined();
  expect(button('Edit a photo')).toBeDefined();
});


test('blank image guide opens choices without creating media and uses explicit canvas selection', async () => {
  await enterGuest();
  await act(async () => button('Help me get started').props.onPress());
  await act(async () => button('Start with a blank image').props.onPress());
  expect(native.createImageGuest).not.toHaveBeenCalled();
  expect(native.canvasSetups).not.toHaveBeenCalled();
  await act(async () => button('Choose my canvas size').props.onPress());
  expect(button('Create Square canvas')).toBeDefined();
  expect(native.canvasSetups).toHaveBeenLastCalledWith('list', '', 1080, 1080, 'white');
  expect(native.createImageGuest).not.toHaveBeenCalled();
  expect(native.createCustomImageGuest).not.toHaveBeenCalled();
  expect(native.createDrawingGuest).not.toHaveBeenCalled();
  expect(native.pickWorkspaceMedia).not.toHaveBeenCalled();
  await act(async () => button('Close canvas choices').props.onPress());
  expect(native.createImageGuest).not.toHaveBeenCalled();
  await act(async () => button('Help me get started').props.onPress());
  await act(async () => button('Start with a blank image').props.onPress());
  await act(async () => button('Choose my canvas size').props.onPress());
  await act(async () => button('Create Square canvas').props.onPress());
  expect(native.createImageGuest).toHaveBeenCalledTimes(1);
  expect(native.createImageGuest).toHaveBeenCalledWith('square');
  expect(native.accountSessionAction).not.toHaveBeenCalled();
  expect(mockAuth.charge).not.toHaveBeenCalled();
});

test('pending native launch blocks the blank canvas guide and direct canvas card', async () => {
  let complete!: (value: null) => void;
  native.pickWorkspaceMedia.mockImplementation(() => new Promise(resolve => { complete = resolve; }));
  await enterGuest();
  await act(async () => button('Help me get started').props.onPress());
  await act(async () => button('Start with a blank image').props.onPress());
  await act(async () => { button('Edit a photo').props.onPress(); });
  expect(button('Choose my canvas size').props.disabled).toBe(true);
  await act(async () => button('Choose my canvas size').props.onPress());
  await act(async () => button('Create an image').props.onPress());
  expect(button('Create Square canvas')).toBeUndefined();
  expect(native.canvasSetups).not.toHaveBeenCalled();
  await act(async () => complete(null));
  await act(async () => button('Choose my canvas size').props.onPress());
  expect(button('Create Square canvas')).toBeDefined();
});

test('custom canvas preview tracks dimensions, orientation and background without creating media', async () => {
  await enterGuest();
  await act(async () => button('Create an image').props.onPress());
  await act(async () => button('Custom canvas and setups').props.onPress());
  const preview = () => tree.root.findAll(node => node.props.accessibilityRole === 'image' && String(node.props.accessibilityLabel).includes('aspect ratio'))[0];
  expect(preview().props.accessibilityLabel).toContain('1080 by 1080 pixels, 1:1 aspect ratio, square, white background');
  await act(async () => input('Canvas width in pixels').props.onChangeText('800'));
  await act(async () => input('Canvas height in pixels').props.onChangeText('600'));
  await act(async () => button('transparent canvas background').props.onPress());
  expect(preview().props.accessibilityLabel).toContain('800 by 600 pixels, 4:3 aspect ratio, landscape, transparent background');
  expect(JSON.stringify(tree.toJSON())).toContain('Checkerboard means transparent');
  await act(async () => button('Swap width and height').props.onPress());
  expect(preview().props.accessibilityLabel).toContain('600 by 800 pixels, 3:4 aspect ratio, portrait');
  await act(async () => input('Canvas width in pixels').props.onChangeText(''));
  expect(preview()).toBeUndefined();
  expect(button('Create custom canvas').props.disabled).toBe(true);
  expect(native.createCustomImageGuest).not.toHaveBeenCalled();
  expect(native.pickWorkspaceMedia).not.toHaveBeenCalled();
  expect(mockAuth.charge).not.toHaveBeenCalled();
});


test('saved canvas replacement requires review and cancels when settings change', async () => {
  native.canvasSetups.mockResolvedValue(JSON.stringify([{name:'Cover',width:1600,height:900,background:'white'}]));
  await enterGuest();
  await act(async () => button('Create an image').props.onPress());
  await act(async () => button('Custom canvas and setups').props.onPress());
  await act(async () => button('Load canvas setup Cover').props.onPress());
  await act(async () => input('Canvas width in pixels').props.onChangeText('1200'));
  const calls = native.canvasSetups.mock.calls.length;
  await act(async () => button('Save canvas setup').props.onPress());
  expect(native.canvasSetups).toHaveBeenCalledTimes(calls);
  expect(JSON.stringify(tree.toJSON())).toContain('Replace with 1200 × 900');
  const stale = button('Replace saved canvas setup').props.onPress;
  await act(async () => input('Canvas height in pixels').props.onChangeText('800'));
  expect(button('Replace saved canvas setup')).toBeUndefined();
  await act(async () => stale());
  expect(native.canvasSetups).toHaveBeenCalledTimes(calls);
  await act(async () => button('Save canvas setup').props.onPress());
  await act(async () => button('Keep saved canvas setup').props.onPress());
  expect(native.canvasSetups).toHaveBeenCalledTimes(calls);
  await act(async () => button('Save canvas setup').props.onPress());
  await act(async () => button('Replace saved canvas setup').props.onPress());
  expect(native.canvasSetups).toHaveBeenLastCalledWith('save','Cover',1200,800,'white');
  expect(native.createCustomImageGuest).not.toHaveBeenCalled();
});

test('canvas setup deletion supports cancel and only submits the reviewed target once', async () => {
  native.canvasSetups.mockResolvedValue(JSON.stringify([{name:'Cover',width:1600,height:900,background:'black'}]));
  await enterGuest();
  await act(async () => button('Create an image').props.onPress());
  await act(async () => button('Custom canvas and setups').props.onPress());
  const calls = native.canvasSetups.mock.calls.length;
  await act(async () => button('Delete canvas setup Cover').props.onPress());
  expect(native.canvasSetups).toHaveBeenCalledTimes(calls);
  await act(async () => button('Keep saved canvas setup').props.onPress());
  expect(button('Load canvas setup Cover')).toBeDefined();
  await act(async () => button('Delete canvas setup Cover').props.onPress());
  const confirm = button('Confirm delete saved canvas setup').props.onPress;
  native.canvasSetups.mockResolvedValue('[]');
  await act(async () => { confirm(); confirm(); });
  expect(native.canvasSetups).toHaveBeenCalledTimes(calls + 1);
  expect(native.canvasSetups).toHaveBeenLastCalledWith('delete','Cover',1080,1080,'white');
  expect(button('Load canvas setup Cover')).toBeUndefined();
  expect(input('Canvas width in pixels').props.value).toBe('1080');
  expect(native.createCustomImageGuest).not.toHaveBeenCalled();
  expect(mockAuth.charge).not.toHaveBeenCalled();
});

test('refresh invalidates setup review and mutation failure keeps the displayed saved setup', async () => {
  native.canvasSetups.mockResolvedValue(JSON.stringify([{name:'Cover',width:1600,height:900,background:'white'}]));
  await enterGuest();
  await act(async () => button('Create an image').props.onPress());
  await act(async () => button('Custom canvas and setups').props.onPress());
  await act(async () => button('Delete canvas setup Cover').props.onPress());
  const stale = button('Confirm delete saved canvas setup').props.onPress;
  native.canvasSetups.mockRejectedValueOnce(new Error('Setup refresh unavailable'));
  await act(async () => button('Refresh saved setups').props.onPress());
  expect(JSON.stringify(tree.toJSON())).toContain('Setup refresh unavailable');
  expect(button('Confirm delete saved canvas setup')).toBeUndefined();
  const calls = native.canvasSetups.mock.calls.length;
  await act(async () => stale());
  expect(native.canvasSetups).toHaveBeenCalledTimes(calls);
  await act(async () => button('Delete canvas setup Cover').props.onPress());
  native.canvasSetups.mockRejectedValueOnce(new Error('Setup save unavailable'));
  await act(async () => button('Confirm delete saved canvas setup').props.onPress());
  expect(button('Load canvas setup Cover')).toBeDefined();
  expect(JSON.stringify(tree.toJSON())).toContain('Setup save unavailable');
});
