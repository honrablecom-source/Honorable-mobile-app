import React from 'react';
import Renderer, { act } from 'react-test-renderer';
import { StudioScreen } from '../src/screens/StudioScreen';

const mockOpen = jest.fn();
const mockDraw = jest.fn();
const mockCreate = jest.fn();
const mockPhotoVideo = jest.fn();
const mockProjects = jest.fn();
const mockVideoProjects = jest.fn();
const mockEdit = jest.fn();
const mockItems = [
  {
    mediaId: 1,
    mediaUri: 'content://fixture/photo1',
    mediaType: 'IMAGE',
    displayName: 'First.jpg',
  },
  {
    mediaId: 2,
    mediaUri: 'content://fixture/photo2',
    mediaType: 'IMAGE',
    displayName: 'Second.jpg',
  },
  {
    mediaId: 3,
    mediaUri: 'content://fixture/video',
    mediaType: 'VIDEO',
    displayName: 'Clip.mp4',
  },
];
jest.mock('../src/library/LibraryContext', () => ({
  useLibrary: () => ({ items: mockItems, loading: false, error: '' }),
}));
jest.mock('../src/native/openWorkspace', () => ({
  useOpenWorkspace: () => ({
    busy: false,
    open: mockOpen,
    draw: mockDraw,
    create: mockCreate,
    photoVideo: mockPhotoVideo,
    projects: mockProjects,
    videoProjects: mockVideoProjects,
  }),
}));
jest.mock('../src/native/editMemory', () => ({
  editMemory: (...args: unknown[]) => mockEdit(...args),
}));
jest.mock('lucide-react-native', () => {
  const { View } = require('react-native');
  return {
    ArrowUpRight: View,
    PenLine: View,
    Film: View,
    Image: View,
    Plus: View,
    Info: View,
    X: View,
    Check: View,
    ChevronRight: View,
    SlidersHorizontal: View,
    Crop: View,
    Type: View,
    WandSparkles: View,
    Music2: View,
    Download: View,
  };
});
jest.mock('react-native-safe-area-context', () => ({
  SafeAreaView: require('react-native').View,
}));
let tree: Renderer.ReactTestRenderer;
const button = (label: string) =>
  tree.root.findAll(
    node =>
      node.props.accessibilityLabel === label &&
      typeof node.props.onPress === 'function',
  )[0];
beforeEach(async () => {
  jest.clearAllMocks();
  mockEdit.mockResolvedValue({ opened: true });
  for (const action of [
    mockOpen,
    mockDraw,
    mockCreate,
    mockPhotoVideo,
    mockProjects,
    mockVideoProjects,
  ])
    action.mockResolvedValue(undefined);
  await act(async () => {
    tree = Renderer.create(<StudioScreen />);
  });
});
afterEach(async () => {
  await act(async () => tree.unmount());
});

test('opening a selected photo passes that source, not the first library item', async () => {
  await act(async () => button('Edit Second.jpg').props.onPress());
  expect(mockEdit).toHaveBeenCalledWith({
    id: '2',
    mediaUri: 'content://fixture/photo2',
    type: 'IMAGE',
  });
});
test('video workspace selects a video source and imports only the chosen type', async () => {
  await act(async () => button('Video workspace').props.onPress());
  expect(button('Edit Clip.mp4')).toBeDefined();
  expect(button('Edit First.jpg')).toBeUndefined();
  await act(async () => button('Import media').props.onPress());
  expect(mockOpen).toHaveBeenCalledWith('VIDEO');
  await act(async () => button('Edit Clip.mp4').props.onPress());
  expect(mockEdit).toHaveBeenCalledWith({
    id: '3',
    mediaUri: 'content://fixture/video',
    type: 'VIDEO',
  });
});
test('switching back to photo never sends the old video to the photo editor', async () => {
  await act(async () => button('Video workspace').props.onPress());
  await act(async () => button('Photo workspace').props.onPress());
  await act(async () => button('Edit First.jpg').props.onPress());
  expect(mockEdit).toHaveBeenCalledWith({
    id: '1',
    mediaUri: 'content://fixture/photo1',
    type: 'IMAGE',
  });
});

test('tools inspector opens the selected source through the real editor callback', async () => {
  await act(async () => button('Edit Second.jpg').props.onPress());
  await act(async () => button('Open tools inspector').props.onPress());
  await act(async () => button('Open editor from inspector').props.onPress());
  expect(mockEdit).toHaveBeenCalledWith({
    id: '2',
    mediaUri: 'content://fixture/photo2',
    type: 'IMAGE',
  });
});

test('source inspector can switch to tools without changing the selected media', async () => {
  await act(async () => button('Video workspace').props.onPress());
  await act(async () => button('Toggle source details').props.onPress());
  await act(async () => button('tools inspector tab').props.onPress());
  await act(async () => button('Open editor from inspector').props.onPress());
  expect(mockEdit).toHaveBeenCalledWith({
    id: '3',
    mediaUri: 'content://fixture/video',
    type: 'VIDEO',
  });
});

test('source detail icon opens the source inspector rather than the tools tab', async () => {
  await act(async () => button('Toggle source details').props.onPress());
  expect(button('source inspector tab').props.accessibilityState.selected).toBe(
    true,
  );
});

test('Studio launch imports the requested media kind without changing an existing selection', async () => {
  await act(async () => button('Choose a video to edit').props.onPress());
  expect(mockOpen).toHaveBeenLastCalledWith('VIDEO');
  await act(async () => button('Choose a photo to edit').props.onPress());
  expect(mockOpen).toHaveBeenLastCalledWith('IMAGE');
  await act(async () => button('Edit First.jpg').props.onPress());
  expect(mockEdit).toHaveBeenLastCalledWith({
    id: '1',
    mediaUri: 'content://fixture/photo1',
    type: 'IMAGE',
  });
});

test('a same-tick library tap cannot launch an editor while Studio import is pending', async () => {
  let complete!: () => void;
  mockOpen.mockImplementationOnce(
    () =>
      new Promise<void>(resolve => {
        complete = resolve;
      }),
  );
  await act(async () => {
    button('Import media').props.onPress();
    button('Edit Second.jpg').props.onPress();
  });
  try {
    expect(mockOpen).toHaveBeenCalledTimes(1);
    expect(mockEdit).not.toHaveBeenCalled();
  } finally {
    await act(async () => complete());
  }
  await act(async () => button('Edit Second.jpg').props.onPress());
  expect(mockEdit).toHaveBeenCalledTimes(1);
});

test('a same-tick import tap cannot compete with a pending library editor', async () => {
  let complete!: () => void;
  mockEdit.mockImplementationOnce(
    () =>
      new Promise<void>(resolve => {
        complete = resolve;
      }),
  );
  await act(async () => {
    button('Edit Second.jpg').props.onPress();
    button('Import media').props.onPress();
    button('Choose a video to edit').props.onPress();
    button('Choose a photo to edit').props.onPress();
  });
  try {
    expect(mockEdit).toHaveBeenCalledTimes(1);
    expect(mockOpen).not.toHaveBeenCalled();
  } finally {
    await act(async () => complete());
  }
  await act(async () => button('Import media').props.onPress());
  expect(mockOpen).toHaveBeenCalledTimes(1);
});

test.each([
  ['Design with photos & text', mockDraw, undefined],
  ['Turn photos into a video', mockPhotoVideo, undefined],
  ['Create a blank square image', mockCreate, 'square'],
  ['Saved image projects', mockProjects, undefined],
  ['Saved photo videos', mockVideoProjects, undefined],
])(
  'Studio exposes the existing local route: %s',
  async (label, action, argument) => {
    await act(async () => button(label as string).props.onPress());
    expect(action).toHaveBeenCalledTimes(1);
    if (argument) expect(action).toHaveBeenCalledWith(argument);
    else expect(action).toHaveBeenCalledWith();
    expect(mockOpen).not.toHaveBeenCalled();
    expect(mockEdit).not.toHaveBeenCalled();
  },
);

test('pending creation blocks same-frame import, library and other local launches until it settles', async () => {
  let complete!: () => void;
  mockDraw.mockImplementationOnce(
    () =>
      new Promise<void>(resolve => {
        complete = resolve;
      }),
  );
  await act(async () => {
    button('Design with photos & text').props.onPress();
    button('Import media').props.onPress();
    button('Edit First.jpg').props.onPress();
    button('Turn photos into a video').props.onPress();
    button('Create a blank square image').props.onPress();
    button('Saved image projects').props.onPress();
    button('Saved photo videos').props.onPress();
  });
  try {
    expect(mockDraw).toHaveBeenCalledTimes(1);
    for (const action of [
      mockOpen,
      mockEdit,
      mockPhotoVideo,
      mockCreate,
      mockProjects,
      mockVideoProjects,
    ])
      expect(action).not.toHaveBeenCalled();
  } finally {
    await act(async () => complete());
  }
  await act(async () => button('Saved image projects').props.onPress());
  expect(mockProjects).toHaveBeenCalledTimes(1);
});

test('a pending library editor cannot compete with a new local creation or saved shelf', async () => {
  let complete!: () => void;
  mockEdit.mockImplementationOnce(
    () =>
      new Promise<void>(resolve => {
        complete = resolve;
      }),
  );
  await act(async () => {
    button('Edit Second.jpg').props.onPress();
    for (const label of [
      'Design with photos & text',
      'Turn photos into a video',
      'Create a blank square image',
      'Saved image projects',
      'Saved photo videos',
    ])
      button(label).props.onPress();
  });
  try {
    expect(mockEdit).toHaveBeenCalledTimes(1);
    for (const action of [
      mockDraw,
      mockPhotoVideo,
      mockCreate,
      mockProjects,
      mockVideoProjects,
    ])
      expect(action).not.toHaveBeenCalled();
  } finally {
    await act(async () => complete());
  }
});

test('a pending import cannot compete with any new local creation route', async () => {
  let complete!: () => void;
  mockOpen.mockImplementationOnce(
    () =>
      new Promise<void>(resolve => {
        complete = resolve;
      }),
  );
  await act(async () => {
    button('Import media').props.onPress();
    for (const label of [
      'Design with photos & text',
      'Turn photos into a video',
      'Create a blank square image',
      'Saved image projects',
      'Saved photo videos',
    ])
      button(label).props.onPress();
  });
  try {
    expect(mockOpen).toHaveBeenCalledTimes(1);
    for (const action of [
      mockDraw,
      mockPhotoVideo,
      mockCreate,
      mockProjects,
      mockVideoProjects,
    ])
      expect(action).not.toHaveBeenCalled();
  } finally {
    await act(async () => complete());
  }
});
