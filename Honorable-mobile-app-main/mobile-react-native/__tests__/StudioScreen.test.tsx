import React from 'react';
import Renderer, { act } from 'react-test-renderer';
import { Alert } from 'react-native';
import { StudioScreen } from '../src/screens/StudioScreen';

const mockOpen = jest.fn();
const mockDraw = jest.fn();
const mockCreate = jest.fn();
const mockPhotoVideo = jest.fn();
const mockProjects = jest.fn();
const mockVideoProjects = jest.fn();
const mockEdit = jest.fn();
const initialItems = [
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
let mockItems = initialItems;
let mockTotal = initialItems.length;
const mockLoadMore = jest.fn();
jest.mock('../src/library/LibraryContext', () => ({
  useLibrary: () => ({
    items: mockItems,
    total: mockTotal,
    loading: false,
    error: '',
    loadMore: mockLoadMore,
  }),
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
  mockItems = initialItems;
  mockTotal = initialItems.length;
  mockLoadMore.mockResolvedValue(undefined);
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

const filterInput = () =>
  tree.root.findAll(
    node =>
      node.props.accessibilityLabel === 'Filter loaded source filenames' &&
      typeof node.props.onChangeText === 'function',
  )[0];
async function updateLibrary() {
  await act(async () => tree.update(<StudioScreen />));
}
test('loaded sources beyond the first twelve become selectable without another native page', async () => {
  mockItems = Array.from({ length: 25 }, (_, index) => ({
    ...initialItems[0],
    mediaId: index + 10,
    mediaUri: `content://synthetic/${index}`,
    displayName: `Source ${index}.jpg`,
  }));
  mockTotal = 25;
  await updateLibrary();
  expect(button('Edit Source 12.jpg')).toBeUndefined();
  await act(async () => button('Next loaded sources').props.onPress());
  expect(button('Edit Source 12.jpg')).toBeDefined();
  expect(button('Edit Source 0.jpg')).toBeUndefined();
  const cards = new Set(
    tree.root
      .findAll(
        node =>
          typeof node.props.onPress === 'function' &&
          node.props.accessibilityLabel?.startsWith('Edit Source '),
      )
      .map(node => node.props.accessibilityLabel),
  );
  expect(cards.size).toBe(12);
  await act(async () => button('Next loaded sources').props.onPress());
  await act(async () => button('Edit Source 24.jpg').props.onPress());
  expect(mockEdit).toHaveBeenCalledWith({
    id: '34',
    mediaUri: 'content://synthetic/24',
    type: 'IMAGE',
  });
  expect(mockLoadMore).not.toHaveBeenCalled();
  await act(async () => button('Previous loaded sources').props.onPress());
  await act(async () => button('Previous loaded sources').props.onPress());
  expect(button('Edit Source 0.jpg')).toBeDefined();
  expect(button('Previous loaded sources')).toBeUndefined();
  await act(async () => button('Next loaded sources').props.onPress());
  mockItems = mockItems.slice(0, 3);
  mockTotal = 3;
  await updateLibrary();
  expect(button('Edit Source 0.jpg')).toBeDefined();
  expect(button('Previous loaded sources')).toBeUndefined();
});
test('filename filtering finds loaded media outside the visible page and clear restores the bounded list', async () => {
  mockItems = Array.from({ length: 20 }, (_, index) => ({
    ...initialItems[0],
    mediaId: index + 10,
    displayName: index === 19 ? 'TARGET.jpg' : `Other ${index}.jpg`,
  }));
  mockTotal = 20;
  await updateLibrary();
  expect(button('Edit TARGET.jpg')).toBeUndefined();
  await act(async () => filterInput().props.onChangeText('  target  '));
  expect(button('Edit TARGET.jpg')).toBeDefined();
  expect(button('Edit Other 0.jpg')).toBeUndefined();
  await act(async () => button('Clear source filter').props.onPress());
  expect(button('Edit Other 0.jpg')).toBeDefined();
  expect(button('Edit TARGET.jpg')).toBeUndefined();
  expect(mockLoadMore).not.toHaveBeenCalled();
  await act(async () => filterInput().props.onChangeText('Other'));
  await act(async () => button('Video workspace').props.onPress());
  expect(filterInput().props.value).toBe('');
  expect(button('Edit Other 0.jpg')).toBeUndefined();
});
test('loading another library page preserves filename/type filtering and admits same-tick taps once', async () => {
  mockTotal = 5;
  await updateLibrary();
  await act(async () => button('Video workspace').props.onPress());
  await act(async () => filterInput().props.onChangeText('Later'));
  let finish!: () => void;
  mockLoadMore.mockImplementationOnce(
    () =>
      new Promise<void>(resolve => {
        finish = resolve;
      }),
  );
  let pending!: Promise<void>;
  await act(async () => {
    const more = button('Load more library sources');
    pending = more.props.onPress();
    more.props.onPress();
  });
  expect(mockLoadMore).toHaveBeenCalledTimes(1);
  expect(button('Load more library sources').props.disabled).toBe(true);
  mockItems = [
    ...initialItems,
    { ...initialItems[0], mediaId: 4, displayName: 'Later photo.jpg' },
    {
      ...initialItems[2],
      mediaId: 5,
      mediaUri: 'content://synthetic/later-video',
      displayName: 'Later video.mp4',
    },
  ];
  await act(async () => {
    finish();
    await pending;
    tree.update(<StudioScreen />);
  });
  expect(filterInput().props.value).toBe('Later');
  expect(button('Edit Later photo.jpg')).toBeUndefined();
  expect(button('Load more library sources')).toBeUndefined();
  await act(async () => button('Edit Later video.mp4').props.onPress());
  expect(mockEdit).toHaveBeenCalledWith({
    id: '5',
    mediaUri: 'content://synthetic/later-video',
    type: 'VIDEO',
  });
});
test('page failure remains retryable and direct import stays available with no filename matches', async () => {
  mockTotal = 10;
  await updateLibrary();
  await act(async () => filterInput().props.onChangeText('No match'));
  mockLoadMore.mockRejectedValueOnce(new Error('Synthetic page failure'));
  await act(async () => button('Load more library sources').props.onPress());
  expect(JSON.stringify(tree.toJSON())).toContain('Synthetic page failure');
  expect(button('Load more library sources').props.disabled).toBe(false);
  await act(async () => button('Load more library sources').props.onPress());
  expect(mockLoadMore).toHaveBeenCalledTimes(2);
  expect(JSON.stringify(tree.toJSON())).not.toContain('Synthetic page failure');
  await act(async () => button('Import another source').props.onPress());
  expect(mockOpen).toHaveBeenCalledWith('IMAGE');
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

test('leaving Studio suppresses a delayed library editor failure alert', async () => {
  let reject!: (reason: Error) => void;
  mockEdit.mockImplementationOnce(
    () =>
      new Promise((_, fail) => {
        reject = fail;
      }),
  );
  const alert = jest.spyOn(Alert, 'alert').mockImplementation(() => {});
  try {
    let pending!: Promise<void>;
    await act(async () => {
      pending = button('Edit First.jpg').props.onPress();
    });
    await act(async () => tree.unmount());
    await act(async () => {
      reject(new Error('Source unavailable'));
      await pending;
    });
    expect(alert).not.toHaveBeenCalled();
  } finally {
    alert.mockRestore();
  }
});

test('retained Studio source and creation callbacks cannot launch after unmount', async () => {
  const edit = button('Edit First.jpg').props.onPress;
  const importMedia = button('Import media').props.onPress;
  await act(async () => tree.unmount());
  await act(async () => {
    await edit();
    await importMedia();
  });
  expect(mockEdit).not.toHaveBeenCalled();
  expect(mockOpen).not.toHaveBeenCalled();
});

test('a current Studio library failure still alerts and permits an exact-source retry', async () => {
  mockEdit.mockRejectedValueOnce(new Error('Source unavailable'));
  const alert = jest.spyOn(Alert, 'alert').mockImplementation(() => {});
  try {
    await act(async () => button('Edit Second.jpg').props.onPress());
    expect(alert).toHaveBeenCalledWith('Open editor', 'Source unavailable');
    await act(async () => button('Edit Second.jpg').props.onPress());
    expect(mockEdit).toHaveBeenCalledTimes(2);
    expect(mockEdit.mock.calls[1][0]).toEqual({
      id: '2',
      mediaUri: 'content://fixture/photo2',
      type: 'IMAGE',
    });
  } finally {
    alert.mockRestore();
  }
});
