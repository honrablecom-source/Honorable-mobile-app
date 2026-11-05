import React from 'react';
import { AccessibilityInfo, Animated } from 'react-native';
import Renderer, { act } from 'react-test-renderer';
import { StudioStartGuide } from '../src/components/StudioStartGuide';

let tree: Renderer.ReactTestRenderer;
let change: (enabled: boolean) => void;
const remove = jest.fn();
const start = jest.fn();
const stop = jest.fn();
const button = (label: string) => tree.root.findAll(node => node.props.accessibilityLabel === label && typeof node.props.onPress === 'function')[0];
beforeEach(() => {
  jest.spyOn(AccessibilityInfo, 'addEventListener').mockImplementation((_event, listener) => {
    change = listener;
    return { remove };
  });
  jest.spyOn(Animated, 'timing').mockReturnValue({ start, stop, reset: jest.fn() });
});
afterEach(async () => {
  await act(async () => tree.unmount());
  jest.restoreAllMocks();
  jest.clearAllMocks();
});
async function render() {
  await act(async () => { tree = Renderer.create(<StudioStartGuide busy={false} onPhoto={jest.fn()} onVideo={jest.fn()} onStory={jest.fn()} onDrawing={jest.fn()} onCanvas={jest.fn()} />); });
  await act(async () => button('Help me get started').props.onPress());
}
test('reduced motion keeps guide still; live preference changes stop animation', async () => {
  jest.spyOn(AccessibilityInfo, 'isReduceMotionEnabled').mockResolvedValue(true);
  await render();
  expect(Animated.timing).not.toHaveBeenCalled();
  await act(async () => change(false));
  expect(start).toHaveBeenCalledTimes(1);
  await act(async () => change(true));
  expect(stop).toHaveBeenCalledTimes(1);
  await act(async () => button('Turn photos into a video').props.onPress());
  expect(start).toHaveBeenCalledTimes(1);
});
test('a late preference query cannot override a newer reduced-motion event', async () => {
  let resolve: (value: boolean) => void = () => {};
  jest.spyOn(AccessibilityInfo, 'isReduceMotionEnabled').mockImplementation(() => new Promise(done => { resolve = done; }));
  await render();
  await act(async () => change(true));
  await act(async () => resolve(false));
  expect(Animated.timing).not.toHaveBeenCalled();
});
test('unavailable preference query leaves guide usable without animation', async () => {
  jest.spyOn(AccessibilityInfo, 'isReduceMotionEnabled').mockRejectedValue(new Error('Unavailable'));
  await render();
  expect(button('Choose my photo')).toBeDefined();
  expect(Animated.timing).not.toHaveBeenCalled();
  await act(async () => tree.unmount());
  expect(remove).toHaveBeenCalledTimes(1);
});
