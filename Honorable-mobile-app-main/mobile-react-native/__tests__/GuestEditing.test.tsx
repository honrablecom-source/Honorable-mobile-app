import React from 'react';
import { Alert, Platform, Text } from 'react-native';
import Renderer, { act } from 'react-test-renderer';
import { AccountGate } from '../src/auth/AccountGate';
import { honorableNative } from '../src/native/HonorableNative';

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
    createImageGuest: jest.fn(),
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
  Object.defineProperty(Platform, 'OS', {
    value: 'android',
    configurable: true,
  });
  mockAuth.signedIn = false;
  mockAuth.status = 'welcome';
  mockAuth.googleConfigured = false;
  mockAuth.error = undefined;
  native.pickWorkspaceMedia.mockResolvedValue({ uri: 'content://test/guest' });
  native.createImageGuest.mockResolvedValue({ opened: true });
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
    'Create an image',
    'Edit a photo',
    'Edit a video',
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

test.each(['Square', 'Portrait', 'Landscape'])(
  'creation opens the %s canvas without a picker or account',
  async format => {
    const alert = jest
      .spyOn(Alert, 'alert')
      .mockImplementation(() => undefined);
    await enterGuest();
    await act(async () => button('Create an image').props.onPress());
    const choices = alert.mock.calls[0][2]!;
    expect(choices).toHaveLength(3);
    expect(alert.mock.calls[0][3]).toEqual({ cancelable: true });
    await act(async () =>
      choices.find(choice => choice.text === format)!.onPress!(),
    );
    expect(native.createImageGuest).toHaveBeenCalledWith(format.toLowerCase());
    expect(native.pickWorkspaceMedia).not.toHaveBeenCalled();
    expect(native.accountSessionAction).not.toHaveBeenCalled();
    expect(mockAccountMounted).not.toHaveBeenCalled();
  },
);
test('failed canvas creation permits retry without spending credits', async () => {
  const alert = jest.spyOn(Alert, 'alert').mockImplementation(() => undefined);
  native.createImageGuest.mockRejectedValue(new Error('Storage unavailable'));
  await enterGuest();
  await act(async () => button('Create an image').props.onPress());
  await act(async () => alert.mock.calls[0][2]![0].onPress!());
  expect(alert).toHaveBeenLastCalledWith(
    'Open workspace',
    'Storage unavailable',
  );
  expect(button('Create an image').props.disabled).toBe(false);
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
