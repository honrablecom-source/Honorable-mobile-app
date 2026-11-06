import React from 'react';
import { FlatList, StyleSheet } from 'react-native';
import Renderer, { act } from 'react-test-renderer';
import { MediaGridRow, type MediaItem } from '../src/components/MediaGrid';
jest.mock('lucide-react-native', () => ({
  Play: require('react-native').View,
}));
const items: MediaItem[] = [
  {
    id: '1',
    mediaUri: 'content://synthetic/original',
    uri: 'content://synthetic/thumb',
    type: 'IMAGE',
    displayName: 'Photo',
  },
  {
    id: '2',
    mediaUri: 'content://synthetic/video',
    type: 'VIDEO',
    displayName: 'Clip',
    duration: '0:12',
  },
  {
    id: '3',
    mediaUri: 'content://synthetic/last',
    type: 'IMAGE',
    displayName: 'Last',
  },
];
let tree: Renderer.ReactTestRenderer;
const open = jest.fn();
const tile = (label: string) =>
  tree.root.findAll(
    node =>
      node.props.accessibilityLabel === label &&
      typeof node.props.onPress === 'function',
  )[0];
beforeEach(() => jest.clearAllMocks());
afterEach(async () => {
  await act(async () => tree?.unmount());
});
test('a row has no nested list and routes the selected original item rather than its thumbnail', async () => {
  await act(async () => {
    tree = Renderer.create(
      <MediaGridRow items={items} onPress={open} edgeToEdge />,
    );
  });
  expect(tree.root.findAllByType(FlatList)).toHaveLength(0);
  await act(async () => tile('image Photo').props.onPress());
  expect(open).toHaveBeenLastCalledWith(items[0]);
  expect(open.mock.calls[0][0].mediaUri).toBe('content://synthetic/original');
  await act(async () => tile('video Clip').props.onPress());
  expect(open).toHaveBeenLastCalledWith(items[1]);
});
test('row thumbnails keep square geometry and the existing edge spacing', async () => {
  await act(async () => {
    tree = Renderer.create(
      <MediaGridRow items={items} onPress={open} edgeToEdge />,
    );
  });
  for (const label of ['image Photo', 'video Clip', 'image Last']) {
    const style = StyleSheet.flatten(tile(label).props.style);
    expect(style.width).toBeGreaterThan(0);
    expect(style.height).toBe(style.width);
    expect(style.borderRadius).toBe(2);
  }
});
test('a final partial row updates without retaining or opening removed tiles', async () => {
  await act(async () => {
    tree = Renderer.create(<MediaGridRow items={items} onPress={open} />);
  });
  await act(async () =>
    tree.update(<MediaGridRow items={[items[2]]} onPress={open} />),
  );
  expect(tile('image Photo')).toBeUndefined();
  expect(tile('video Clip')).toBeUndefined();
  await act(async () => tile('image Last').props.onPress());
  expect(open).toHaveBeenCalledTimes(1);
  expect(open).toHaveBeenLastCalledWith(items[2]);
});
