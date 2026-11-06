import React from 'react';
import { Alert, Modal } from 'react-native';
import Renderer, { act } from 'react-test-renderer';
import { MediaViewer } from '../src/components/MediaViewer';
import type { MediaItem } from '../src/components/MediaGrid';
import type { SearchResult } from '../src/native/HonorableNative';
import { honorableNative } from '../src/native/HonorableNative';
jest.mock('../src/native/HonorableNative', () => ({
  honorableNative: {
    openMedia: jest.fn(),
    shareMedia: jest.fn(),
    editPhoto: jest.fn(),
    editVideo: jest.fn(),
  },
}));
jest.mock('../src/hooks/useReducedMotion', () => ({
  useReducedMotion: () => true,
}));
jest.mock('react-native-safe-area-context', () => ({
  useSafeAreaInsets: () => ({ top: 0, bottom: 0, left: 0, right: 0 }),
}));
jest.mock('lucide-react-native', () => ({
  ChevronLeft: 'ChevronLeft',
  Play: 'Play',
}));
// Resolve the lazy Modal module before individual test timers start.
const ViewerModal = Modal;
const native = honorableNative as jest.Mocked<typeof honorableNative>;
const source: MediaItem = {
  id: 'found',
  type: 'VIDEO',
  mediaUri: 'content://synthetic/fallback',
  result: {
    mediaUri: 'content://synthetic/selected',
    bestTimestampMs: 1234,
  } as SearchResult,
};
let tree: Renderer.ReactTestRenderer;
const button = (label: string) =>
  tree.root.findAll(
    node =>
      node.props.accessibilityLabel === label &&
      typeof node.props.onPress === 'function',
  )[0];
async function render(item = source) {
  await act(async () => {
    tree = Renderer.create(<MediaViewer item={item} onClose={() => {}} />);
  });
}
beforeEach(() => {
  jest.resetAllMocks();
  jest.spyOn(Alert, 'alert').mockImplementation(() => {});
  native.openMedia.mockResolvedValue({ opened: true });
  native.editVideo.mockResolvedValue({ opened: true });
  native.editPhoto.mockResolvedValue({ opened: true });
  native.shareMedia.mockResolvedValue({ opened: true });
});
afterEach(async () => {
  if (tree) await act(async () => tree.unmount());
  jest.restoreAllMocks();
});
test('play failure gives a recoverable message and a retry succeeds with the selected moment', async () => {
  native.openMedia.mockRejectedValueOnce(Error('Synthetic playback failure'));
  await render();
  await act(async () => button('Play video').props.onPress());
  expect(Alert.alert).toHaveBeenCalledWith(
    'Open media',
    'Synthetic playback failure',
  );
  expect(button('Play video').props.disabled).toBe(false);
  await act(async () => button('Play video').props.onPress());
  expect(native.openMedia).toHaveBeenLastCalledWith({
    mediaUri: 'content://synthetic/selected',
    mediaType: 'VIDEO',
    bestTimestampMs: 1234,
  });
});
test('same-frame Open and Edit taps share one synchronous guard', async () => {
  let finish: (value: { opened: boolean }) => void;
  native.openMedia.mockImplementation(
    () =>
      new Promise(resolve => {
        finish = resolve;
      }),
  );
  await render();
  let task: Promise<void>;
  await act(async () => {
    task = button('Play video').props.onPress();
    await button('Edit media').props.onPress();
  });
  expect(native.openMedia).toHaveBeenCalledTimes(1);
  expect(native.editVideo).not.toHaveBeenCalled();
  expect(button('Share media').props.accessibilityState).toEqual({
    disabled: true,
    busy: true,
  });
  await act(async () => {
    finish({ opened: true });
    await task;
  });
  expect(button('Edit media').props.disabled).toBe(false);
});
test('share rejection is handled and uses the selected result rather than a fallback source', async () => {
  native.shareMedia.mockRejectedValueOnce(Error('Synthetic sharing failure'));
  await render();
  await act(async () => button('Share media').props.onPress());
  expect(native.shareMedia).toHaveBeenCalledWith({
    mediaUri: 'content://synthetic/selected',
    mediaType: 'VIDEO',
  });
  expect(Alert.alert).toHaveBeenCalledWith(
    'Share media',
    'Synthetic sharing failure',
  );
  expect(button('Share media').props.disabled).toBe(false);
});
test('editing uses the existing authorized found-moment entry and catches non-Error failures', async () => {
  native.editVideo.mockRejectedValueOnce('Unavailable');
  await render();
  await act(async () => button('Edit media').props.onPress());
  expect(native.editVideo).toHaveBeenCalledWith(
    'content://synthetic/selected',
    1234,
  );
  expect(Alert.alert).toHaveBeenCalledWith(
    'Edit video',
    'This action could not complete. Try again.',
  );
});
test('late failures after source replacement or unmount do not alert a different view', async () => {
  let fail: (error: Error) => void;
  native.openMedia.mockImplementation(
    () =>
      new Promise((_resolve, reject) => {
        fail = reject;
      }),
  );
  await render();
  let task: Promise<void>;
  await act(async () => {
    task = button('Open media').props.onPress();
  });
  await act(async () =>
    tree.update(
      <MediaViewer
        item={{
          id: 'another',
          type: 'IMAGE',
          mediaUri: 'content://synthetic/another',
        }}
        onClose={() => {}}
      />,
    ),
  );
  await act(async () => {
    fail(Error('Obsolete source'));
    await task;
  });
  expect(Alert.alert).not.toHaveBeenCalled();
  expect(button('Open media').props.disabled).toBe(false);
  await act(async () => {
    task = button('Open media').props.onPress();
  });
  await act(async () => tree.unmount());
  await act(async () => {
    fail(Error('Closed view'));
    await task;
  });
  expect(Alert.alert).not.toHaveBeenCalled();
});
test('a video thumbnail cannot substitute for a missing playable source', async () => {
  await render({
    id: 'thumbnail',
    type: 'VIDEO',
    uri: 'content://synthetic/thumbnail',
  });
  await act(async () => button('Play video').props.onPress());
  expect(native.openMedia).not.toHaveBeenCalled();
  expect(Alert.alert).toHaveBeenCalledWith(
    'Open media',
    expect.stringContaining('source is unavailable'),
  );
});

test.each([NaN, Infinity, -1])(
  'invalid playback moment %s is not sent to native playback',
  async timestamp => {
    await render({
      ...source,
      result: { ...source.result, bestTimestampMs: timestamp } as SearchResult,
    });
    await act(async () => button('Play video').props.onPress());
    expect(native.openMedia).not.toHaveBeenCalled();
    expect(Alert.alert).toHaveBeenCalledWith(
      'Open media',
      expect.stringContaining('video moment is unavailable'),
    );
  },
);

test('viewer actions are not nested inside another interactive touch target', async () => {
  await render();
  for (const label of [
    'Close viewer',
    'Open media',
    'Edit media',
    'Share media',
  ]) {
    let parent = button(label).parent;
    while (parent) {
      expect(typeof parent.props.onPress).not.toBe('function');
      parent = parent.parent;
    }
  }
});

test('screen-reader control toggle exposes show and hide actions without trapping the viewer', async () => {
  await render();
  expect(button('Hide viewer controls').props.accessibilityRole).toBe('button');
  await act(async () => button('Hide viewer controls').props.onPress());
  expect(button('Edit media')).toBeUndefined();
  expect(button('Show viewer controls').props.accessibilityRole).toBe('button');
  await act(async () => button('Show viewer controls').props.onPress());
  expect(button('Close viewer').props.accessibilityRole).toBe('button');
  expect(button('Edit media')).toBeDefined();
});

test('accessibility escape uses the existing viewer close callback', async () => {
  const close = jest.fn();
  await act(async () => {
    tree = Renderer.create(<MediaViewer item={source} onClose={close} />);
  });
  const canvas = tree.root.findAll(
    node => typeof node.props.onAccessibilityEscape === 'function',
  )[0];
  await act(async () => canvas.props.onAccessibilityEscape());
  expect(close).toHaveBeenCalledTimes(1);
  await act(async () =>
    tree.root.findByType(ViewerModal).props.onRequestClose(),
  );
  expect(close).toHaveBeenCalledTimes(2);
});

test('optional navigation exposes position and disables the loaded boundary', async () => {
  const next = jest.fn();
  await act(async () => {
    tree = Renderer.create(
      <MediaViewer
        item={source}
        onClose={() => {}}
        navigation={{ position: 1, total: 2, onNext: next }}
      />,
    );
  });
  expect(
    button('Previous library item').props.accessibilityState.disabled,
  ).toBe(true);
  await act(async () => button('Previous library item').props.onPress());
  await act(async () => button('Next library item').props.onPress());
  expect(next).toHaveBeenCalledTimes(1);
  expect(JSON.stringify(tree.toJSON())).toContain('loaded');
});

test('pending native actions synchronously block viewer navigation until completion', async () => {
  const next = jest.fn();
  let finish!: (value: { opened: boolean }) => void;
  native.openMedia.mockImplementationOnce(
    () =>
      new Promise(resolve => {
        finish = resolve;
      }),
  );
  await act(async () => {
    tree = Renderer.create(
      <MediaViewer
        item={source}
        onClose={() => {}}
        navigation={{ position: 1, total: 2, onNext: next }}
      />,
    );
  });
  const move = button('Next library item').props.onPress;
  let pending!: Promise<void>;
  await act(async () => {
    pending = button('Open media').props.onPress();
    move();
  });
  expect(next).not.toHaveBeenCalled();
  expect(button('Next library item').props.disabled).toBe(true);
  await act(async () => {
    finish({ opened: true });
    await pending;
  });
  await act(async () => button('Next library item').props.onPress());
  expect(next).toHaveBeenCalledTimes(1);
});

test('changed viewer item rejects stale actions and uses the displayed original for open edit and share', async () => {
  const next = jest.fn();
  await act(async () => {
    tree = Renderer.create(
      <MediaViewer
        item={source}
        onClose={() => {}}
        navigation={{ position: 1, total: 2, onNext: next }}
      />,
    );
  });
  const oldOpen = button('Open media').props.onPress;
  const oldNext = button('Next library item').props.onPress;
  const photo: MediaItem = {
    id: 'photo-next',
    type: 'IMAGE',
    uri: 'content://synthetic/thumb-next',
    mediaUri: 'content://synthetic/original-next',
  };
  await act(async () =>
    tree.update(
      <MediaViewer
        item={photo}
        onClose={() => {}}
        navigation={{ position: 2, total: 2 }}
      />,
    ),
  );
  await act(async () => {
    await oldOpen();
    oldNext();
  });
  expect(native.openMedia).not.toHaveBeenCalled();
  expect(next).not.toHaveBeenCalled();
  await act(async () => button('Open media').props.onPress());
  await act(async () => button('Edit media').props.onPress());
  await act(async () => button('Share media').props.onPress());
  expect(native.openMedia).toHaveBeenCalledWith({
    mediaUri: photo.mediaUri,
    mediaType: 'IMAGE',
    bestTimestampMs: undefined,
  });
  expect(native.editPhoto).toHaveBeenCalledWith(photo.mediaUri);
  expect(native.shareMedia).toHaveBeenCalledWith({
    mediaUri: photo.mediaUri,
    mediaType: 'IMAGE',
  });
});

test('single-item and search viewers retain their existing controls without library navigation', async () => {
  await render();
  expect(button('Previous library item')).toBeUndefined();
  expect(button('Next library item')).toBeUndefined();
  expect(button('Edit media')).toBeDefined();
});
