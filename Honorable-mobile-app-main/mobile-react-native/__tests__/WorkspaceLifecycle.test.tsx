import React from 'react';
import { Alert } from 'react-native';
import Renderer, { act } from 'react-test-renderer';
import { useOpenWorkspace, WorkspaceAccess } from '../src/native/openWorkspace';
import { honorableNative } from '../src/native/HonorableNative';
jest.mock('../src/native/HonorableNative', () => ({
  honorableNative: {
    pickWorkspaceMedia: jest.fn(),
    editPhoto: jest.fn(),
    editVideo: jest.fn(),
    editPhotoGuest: jest.fn(),
    editVideoGuest: jest.fn(),
  },
}));
const native = honorableNative as jest.Mocked<typeof honorableNative>;
let tree: Renderer.ReactTestRenderer;
let hook: ReturnType<typeof useOpenWorkspace>;
let resolvePicker: (value: { uri: string } | null) => void;
let rejectPicker: (reason: Error) => void;
function Probe({ access = 'account' }: { access?: WorkspaceAccess }) {
  hook = useOpenWorkspace(access);
  return null;
}
beforeEach(() => {
  jest.resetAllMocks();
  jest.spyOn(Alert, 'alert').mockImplementation(() => {});
  native.pickWorkspaceMedia.mockImplementation(
    () =>
      new Promise((resolve, reject) => {
        resolvePicker = resolve;
        rejectPicker = reject;
      }),
  );
  native.editPhoto.mockResolvedValue({ opened: true });
  native.editVideo.mockResolvedValue({ opened: true });
  native.editPhotoGuest.mockResolvedValue({ opened: true });
  native.editVideoGuest.mockResolvedValue({ opened: true });
});
afterEach(async () => {
  if (tree) await act(async () => tree.unmount());
  jest.restoreAllMocks();
});
async function render() {
  await act(async () => {
    tree = Renderer.create(<Probe />);
  });
}
test('a pending picker cannot launch an editor after its screen unmounts', async () => {
  await render();
  let task: Promise<void>;
  await act(async () => {
    task = hook.open('IMAGE');
  });
  await act(async () => tree.unmount());
  await act(async () => {
    resolvePicker({ uri: 'content://synthetic/chosen' });
    await task;
  });
  expect(native.editPhoto).not.toHaveBeenCalled();
  expect(native.editPhotoGuest).not.toHaveBeenCalled();
  expect(Alert.alert).not.toHaveBeenCalled();
});
test('switching access while a picker is pending rejects the stale route and allows a fresh guest retry', async () => {
  await render();
  let task: Promise<void>;
  await act(async () => {
    task = hook.open('VIDEO');
  });
  await act(async () => tree.update(<Probe access="guest" />));
  await act(async () => {
    resolvePicker({ uri: 'content://synthetic/stale' });
    await task;
  });
  expect(native.editVideo).not.toHaveBeenCalled();
  expect(native.editVideoGuest).not.toHaveBeenCalled();
  expect(hook.busy).toBe(false);
  await act(async () => {
    task = hook.open('VIDEO');
  });
  await act(async () => {
    resolvePicker({ uri: 'content://synthetic/current' });
    await task;
  });
  expect(native.editVideoGuest).toHaveBeenCalledWith(
    'content://synthetic/current',
  );
  expect(native.editVideo).not.toHaveBeenCalled();
});
test('same-frame taps share a single picker and cancellation restores idle state', async () => {
  await render();
  let task: Promise<void>;
  await act(async () => {
    task = hook.open('IMAGE');
    await hook.open('VIDEO');
  });
  expect(native.pickWorkspaceMedia).toHaveBeenCalledTimes(1);
  expect(hook.busy).toBe(true);
  await act(async () => {
    resolvePicker(null);
    await task;
  });
  expect(hook.busy).toBe(false);
  expect(native.editPhoto).not.toHaveBeenCalled();
  expect(Alert.alert).not.toHaveBeenCalled();
});
test('picker error reports once while mounted and permits a new attempt', async () => {
  await render();
  let task: Promise<void>;
  await act(async () => {
    task = hook.open('IMAGE');
  });
  await act(async () => {
    rejectPicker(Error('Synthetic picker failure'));
    await task;
  });
  expect(Alert.alert).toHaveBeenCalledWith(
    'Open workspace',
    'Synthetic picker failure',
  );
  expect(hook.busy).toBe(false);
  await act(async () => {
    task = hook.open('IMAGE');
  });
  await act(async () => {
    resolvePicker({ uri: 'content://synthetic/retry' });
    await task;
  });
  expect(native.editPhoto).toHaveBeenCalledTimes(1);
});
test('late picker errors after unmount do not show alerts', async () => {
  await render();
  let task: Promise<void>;
  await act(async () => {
    task = hook.open('IMAGE');
  });
  await act(async () => tree.unmount());
  await act(async () => {
    rejectPicker(Error('Late picker failure'));
    await task;
  });
  expect(Alert.alert).not.toHaveBeenCalled();
  expect(native.editPhoto).not.toHaveBeenCalled();
});

test('leaving and returning to the same access mode does not revive an older picker', async () => {
  await render();
  let task: Promise<void>;
  await act(async () => {
    task = hook.open('IMAGE');
  });
  await act(async () => tree.update(<Probe access="guest" />));
  await act(async () => tree.update(<Probe access="account" />));
  await act(async () => {
    resolvePicker({ uri: 'content://synthetic/obsolete' });
    await task;
  });
  expect(native.editPhoto).not.toHaveBeenCalled();
  expect(native.editPhotoGuest).not.toHaveBeenCalled();
  expect(hook.busy).toBe(false);
});

test('an already-started native editor failure after unmount does not alert a different screen', async () => {
  let failEditor: (reason: Error) => void;
  native.editVideo.mockImplementation(
    () =>
      new Promise((_resolve, reject) => {
        failEditor = reject;
      }),
  );
  await render();
  let task: Promise<void>;
  await act(async () => {
    task = hook.open('VIDEO');
  });
  await act(async () => resolvePicker({ uri: 'content://synthetic/opened' }));
  expect(native.editVideo).toHaveBeenCalledTimes(1);
  await act(async () => tree.unmount());
  await act(async () => {
    failEditor(Error('Late native failure'));
    await task;
  });
  expect(Alert.alert).not.toHaveBeenCalled();
});
