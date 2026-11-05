import React from 'react';
import { AccessibilityInfo, Animated, Text } from 'react-native';
import Renderer, { act } from 'react-test-renderer';
import { useReducedMotion } from '../src/hooks/useReducedMotion';
import { TabTransition } from '../src/navigation/TabTransition';

jest.mock('@react-navigation/native', () => ({ useIsFocused: () => true }));
let tree: Renderer.ReactTestRenderer;
let change: (value: boolean) => void;
let resolveQuery: (value: boolean) => void;
let rejectQuery: (reason: Error) => void;
const remove = jest.fn(),
  start = jest.fn(),
  stop = jest.fn();
function Probe() {
  return <Text testID="preference">{String(useReducedMotion())}</Text>;
}
const value = () =>
  tree.root.findByProps({ testID: 'preference' }).props.children;
beforeEach(() => {
  jest.spyOn(AccessibilityInfo, 'isReduceMotionEnabled').mockImplementation(
    () =>
      new Promise((resolve, reject) => {
        resolveQuery = resolve;
        rejectQuery = reject;
      }),
  );
  jest
    .spyOn(AccessibilityInfo, 'addEventListener')
    .mockImplementation((_event, listener) => {
      change = listener;
      return { remove };
    });
  jest
    .spyOn(Animated, 'timing')
    .mockReturnValue({ start, stop, reset: jest.fn() });
});
afterEach(async () => {
  if (tree) await act(async () => tree.unmount());
  jest.restoreAllMocks();
  jest.clearAllMocks();
});
async function render(element = <Probe />) {
  await act(async () => {
    tree = Renderer.create(element);
  });
}

test('motion stays disabled until the initial platform query explicitly allows it', async () => {
  await render();
  expect(value()).toBe('true');
  await act(async () => resolveQuery(false));
  expect(value()).toBe('false');
  await act(async () => change(true));
  expect(value()).toBe('true');
});
test('a live preference event wins over a stale initial query', async () => {
  await render();
  await act(async () => change(true));
  await act(async () => resolveQuery(false));
  expect(value()).toBe('true');
  await act(async () => change(false));
  expect(value()).toBe('false');
});
test('a rejected platform query keeps motion disabled and later events still work', async () => {
  await render();
  await act(async () => rejectQuery(Error('Unavailable platform setting')));
  expect(value()).toBe('true');
  await act(async () => change(false));
  expect(value()).toBe('false');
});
test('unmount removes the listener and safely ignores late query and event callbacks', async () => {
  await render();
  await act(async () => tree.unmount());
  expect(remove).toHaveBeenCalledTimes(1);
  await act(async () => {
    resolveQuery(false);
    change(false);
  });
});
test('real tab transition waits for permission, stops active animation when disabled and cleans up', async () => {
  await render(
    <TabTransition>
      <Text>Studio</Text>
    </TabTransition>,
  );
  expect(start).not.toHaveBeenCalled();
  await act(async () => resolveQuery(false));
  expect(start).toHaveBeenCalledTimes(1);
  await act(async () => change(true));
  expect(stop).toHaveBeenCalledTimes(1);
  expect(start).toHaveBeenCalledTimes(1);
  await act(async () => change(false));
  expect(start).toHaveBeenCalledTimes(2);
  await act(async () => tree.unmount());
  expect(stop).toHaveBeenCalledTimes(2);
  expect(remove).toHaveBeenCalledTimes(1);
});
