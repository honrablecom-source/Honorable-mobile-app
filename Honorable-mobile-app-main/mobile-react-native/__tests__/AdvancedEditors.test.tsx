import React from 'react';
import Renderer, {act} from 'react-test-renderer';
import {NativeModules} from 'react-native';
import {AdvancedEditors} from '../src/components/AdvancedEditors';

let tree: Renderer.ReactTestRenderer | null;
const original = NativeModules.NobleCreative;
const getCapabilities = jest.fn(), openEditor = jest.fn();
const button = (label: string) => tree!.root.findAll(node => node.props.accessibilityLabel === label && typeof node.props.onPress === 'function')[0];
beforeEach(() => {
  tree = null; getCapabilities.mockReset(); openEditor.mockReset();
  NativeModules.NobleCreative = {getCapabilities, openEditor};
});
afterEach(async () => {if (tree) await act(async () => tree!.unmount()); NativeModules.NobleCreative = original;});
test('absent native module and missing verified bundles expose no fake editor entries', async () => {
  NativeModules.NobleCreative = undefined;
  await act(async () => {tree = Renderer.create(<AdvancedEditors />);});
  expect(tree!.toJSON()).toBeNull();
  await act(async () => {tree!.unmount();}); tree = null;
  NativeModules.NobleCreative = {getCapabilities, openEditor};getCapabilities.mockResolvedValue({image: false, video: false});
  await act(async () => {tree = Renderer.create(<AdvancedEditors />);});
  expect(tree!.toJSON()).toBeNull();expect(openEditor).not.toHaveBeenCalled();
});
test('only strict verified capabilities create entries and parent busy blocks opening', async () => {
  getCapabilities.mockResolvedValue({image: true, video: 'true'});
  await act(async () => {tree = Renderer.create(<AdvancedEditors busy />);});
  expect(button('Open image studio')).toBeTruthy();expect(button('Open video studio')).toBeUndefined();
  await act(async () => {button('Open image studio').props.onPress();});expect(openEditor).not.toHaveBeenCalled();
});
test('correct editor opens once even when the handler is replayed before settlement', async () => {
  getCapabilities.mockResolvedValue({image: true, video: true});
  let finish!: (value: boolean) => void;openEditor.mockReturnValue(new Promise(resolve => {finish = resolve;}));
  await act(async () => {tree = Renderer.create(<AdvancedEditors />);});
  const press = button('Open video studio').props.onPress;
  await act(async () => {press();press();});expect(openEditor).toHaveBeenCalledTimes(1);expect(openEditor).toHaveBeenCalledWith('video');
  await act(async () => {finish(true);});
  await act(async () => {press();});expect(openEditor).toHaveBeenCalledTimes(1);
});
test('failed opening preserves a retry and reports the failure without claiming a workspace opened', async () => {
  getCapabilities.mockResolvedValue({image: true, video: false});openEditor.mockRejectedValue(new Error('Editor unavailable'));
  await act(async () => {tree = Renderer.create(<AdvancedEditors />);});
  await act(async () => {button('Open image studio').props.onPress();});
  expect(tree!.root.findAll(node => node.props.accessibilityRole === 'alert').length).toBeGreaterThan(0);
  expect(button('Open image studio').props.disabled).toBe(false);
});
test('late capability reply after unmount cannot expose an editor or open an activity', async () => {
  let finish!: (value: object) => void;getCapabilities.mockReturnValue(new Promise(resolve => {finish = resolve;}));
  await act(async () => {tree = Renderer.create(<AdvancedEditors />);});
  await act(async () => {tree!.unmount();});tree = null;
  await act(async () => {finish({image: true, video: true});});expect(openEditor).not.toHaveBeenCalled();
});
