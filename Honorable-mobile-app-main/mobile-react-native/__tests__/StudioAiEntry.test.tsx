import React from 'react';
import { NativeModules } from 'react-native';
import Renderer, { act } from 'react-test-renderer';
import { AdvancedEditors } from '../src/components/AdvancedEditors';

const original = NativeModules.NobleCreative;
let tree: Renderer.ReactTestRenderer | undefined;

afterEach(async () => {
  if (tree) await act(async () => tree?.unmount());
  tree = undefined;
  NativeModules.NobleCreative = original;
});

test('verified featured tools open the exact AI workflows and own one launch', async () => {
  const openEditor = jest.fn();
  const openImageAi = jest.fn().mockResolvedValue(true);
  NativeModules.NobleCreative = {
    getCapabilities: jest.fn().mockResolvedValue({ image: true }),
    openEditor,
    openImageAi,
  };
  await act(async () => { tree = Renderer.create(<AdvancedEditors featured />); });
  const button = (label: string) => tree!.root.findAll(node =>
    node.props.accessibilityLabel === label && typeof node.props.onPress === 'function',
  )[0];
  expect(button('Remove background')).toBeDefined();
  expect(button('Color just the subject')).toBeDefined();
  expect(button('Reduce photo noise')).toBeDefined();
  expect(button('Turn a photo into a painting')).toBeDefined();
  const color = button('Color just the subject').props.onPress;
  await act(async () => { color(); color(); });
  expect(openImageAi).toHaveBeenCalledTimes(1);
  expect(openImageAi).toHaveBeenCalledWith('subject');
  expect(openEditor).not.toHaveBeenCalled();
  expect(button('Remove background').props.disabled).toBe(true);
});

test('an older or incomplete native bundle does not advertise featured AI', async () => {
  NativeModules.NobleCreative = {
    getCapabilities: jest.fn().mockResolvedValue({ image: true }),
    openEditor: jest.fn(),
  };
  await act(async () => { tree = Renderer.create(<AdvancedEditors featured />); });
  expect(tree!.toJSON()).toBeNull();
});
