import React from 'react';
import { Animated, Text, View } from 'react-native';
import Renderer, { act } from 'react-test-renderer';
import { AppNavigator } from '../src/navigation/AppNavigator';
import { TabTransition } from '../src/navigation/TabTransition';

let mockReduced = true;
let mockFocused = true;
jest.mock('../src/hooks/useReducedMotion', () => ({
  useReducedMotion: () => mockReduced,
}));
jest.mock('@react-navigation/native', () => ({
  useIsFocused: () => mockFocused,
}));
jest.mock('@react-navigation/native-stack', () => ({
  createNativeStackNavigator: () => ({
    Navigator: ({ children, ...props }: any) =>
      require('react').createElement(
        require('react-native').View,
        { ...props, testID: 'stack' },
        children,
      ),
    Screen: () => null,
  }),
}));
jest.mock('@react-navigation/bottom-tabs', () => ({
  createBottomTabNavigator: () => ({
    Navigator: ({ children, ...props }: any) =>
      require('react').createElement(
        require('react-native').View,
        { ...props, testID: 'tabs' },
        children,
      ),
    Screen: () => null,
  }),
}));
jest.mock('react-native-safe-area-context', () => ({
  useSafeAreaInsets: () => ({ top: 0, right: 0, bottom: 12, left: 0 }),
}));
jest.mock('../src/screens/HomeScreen', () => ({
  HomeScreen: () =>
    require('react').createElement(
      require('react-native').Text,
      null,
      'Home screen',
    ),
}));
jest.mock('../src/screens/MemoriesScreen', () => ({
  MemoriesScreen: () =>
    require('react').createElement(
      require('react-native').Text,
      null,
      'Library screen',
    ),
}));
jest.mock('../src/screens/ProductScreens', () => ({
  StudioScreen: () =>
    require('react').createElement(
      require('react-native').Text,
      null,
      'Studio screen',
    ),
}));
jest.mock('../src/screens/SettingsScreen', () => ({
  SettingsScreen: () =>
    require('react').createElement(
      require('react-native').Text,
      null,
      'Account screen',
    ),
}));
let tree: Renderer.ReactTestRenderer;
const start = jest.fn(),
  stop = jest.fn();
beforeEach(() => {
  mockReduced = true;
  mockFocused = true;
  jest
    .spyOn(Animated, 'timing')
    .mockReturnValue({ start, stop, reset: jest.fn() });
});
afterEach(async () => {
  if (tree) await act(async () => tree.unmount());
  jest.restoreAllMocks();
  jest.clearAllMocks();
});
async function render() {
  await act(async () => {
    tree = Renderer.create(<AppNavigator />);
  });
}
async function tabScreens() {
  await render();
  const MainTabs = tree.root.findByProps({ name: 'MainTabs' }).props.component;
  await act(async () => tree.update(<MainTabs />));
  return Object.fromEntries(
    ['Home', 'Memories', 'Studio', 'Account'].map(name => [
      name,
      tree.root.findByProps({ name }).props.component,
    ]),
  );
}
test('stack navigation stays still until motion is allowed and responds to preference changes', async () => {
  await render();
  expect(
    tree.root.findByProps({ testID: 'stack' }).props.screenOptions.animation,
  ).toBe('none');
  mockReduced = false;
  await act(async () => tree.update(<AppNavigator />));
  expect(
    tree.root.findByProps({ testID: 'stack' }).props.screenOptions.animation,
  ).toBe('slide_from_right');
  mockReduced = true;
  await act(async () => tree.update(<AppNavigator />));
  expect(
    tree.root.findByProps({ testID: 'stack' }).props.screenOptions.animation,
  ).toBe('none');
});
test('all four phone tabs use the actual transition without changing their screen content', async () => {
  const screens = await tabScreens();
  for (const [name, label] of [
    ['Home', 'Home'],
    ['Memories', 'Library'],
    ['Studio', 'Studio'],
    ['Account', 'Account'],
  ]) {
    const Screen = screens[name];
    await act(async () => tree.update(<Screen />));
    expect(tree.root.findAllByType(TabTransition)).toHaveLength(1);
    expect(tree.root.findByType(Text).props.children).toBe(`${label} screen`);
  }
  expect(start).not.toHaveBeenCalled();
});
test('Studio focus animations stop on blur or reduced motion and keep inactive content untouchable', async () => {
  const screens = await tabScreens();
  const Studio = screens.Studio;
  mockReduced = false;
  await act(async () => tree.update(<Studio />));
  expect(start).toHaveBeenCalledTimes(1);
  mockFocused = false;
  await act(async () => tree.update(<Studio />));
  expect(stop).toHaveBeenCalledTimes(1);
  expect(
    tree.root
      .findAllByType(View)
      .some(node => node.props.pointerEvents === 'none'),
  ).toBe(true);
  mockFocused = true;
  await act(async () => tree.update(<Studio />));
  expect(start).toHaveBeenCalledTimes(2);
  mockReduced = true;
  await act(async () => tree.update(<Studio />));
  expect(stop).toHaveBeenCalledTimes(2);
  expect(start).toHaveBeenCalledTimes(2);
});
