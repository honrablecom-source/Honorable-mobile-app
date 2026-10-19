import React from 'react';
import Renderer, { act } from 'react-test-renderer';
import { StudioScreen } from '../src/screens/StudioScreen';

const mockOpen = jest.fn();
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
  useOpenWorkspace: () => ({ busy: false, open: mockOpen }),
}));
jest.mock('../src/native/editMemory', () => ({
  editMemory: (...args: unknown[]) => mockEdit(...args),
}));
jest.mock('lucide-react-native', () => {
  const { View } = require('react-native');
  return {
    ArrowUpRight: View,
    Film: View,
    Image: View,
    Plus: View,
    Info: View,
    X: View,
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
  await act(async () => {
    tree = Renderer.create(<StudioScreen />);
  });
});
afterEach(async () => {
  await act(async () => tree.unmount());
});

test('opening a selected photo passes that source, not the first library item', async () => {
  await act(async () => button('Select Second.jpg').props.onPress());
  await act(async () =>
    button('Open selected media in editor').props.onPress(),
  );
  expect(mockEdit).toHaveBeenCalledWith({
    id: '2',
    mediaUri: 'content://fixture/photo2',
    type: 'IMAGE',
  });
});
test('video workspace selects a video source and imports only the chosen type', async () => {
  await act(async () => button('Video workspace').props.onPress());
  expect(button('Select Clip.mp4')).toBeDefined();
  expect(button('Select First.jpg')).toBeUndefined();
  await act(async () => button('Import media').props.onPress());
  expect(mockOpen).toHaveBeenCalledWith('VIDEO');
  await act(async () =>
    button('Open selected media in editor').props.onPress(),
  );
  expect(mockEdit).toHaveBeenCalledWith({
    id: '3',
    mediaUri: 'content://fixture/video',
    type: 'VIDEO',
  });
});
test('switching back to photo never sends the old video to the photo editor', async () => {
  await act(async () => button('Video workspace').props.onPress());
  await act(async () => button('Photo workspace').props.onPress());
  await act(async () =>
    button('Open selected media in editor').props.onPress(),
  );
  expect(mockEdit).toHaveBeenCalledWith({
    id: '1',
    mediaUri: 'content://fixture/photo1',
    type: 'IMAGE',
  });
});
