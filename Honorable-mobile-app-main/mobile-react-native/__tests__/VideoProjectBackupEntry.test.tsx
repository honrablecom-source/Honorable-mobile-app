import React from 'react';
import Renderer, {act} from 'react-test-renderer';
import {AppState, Platform} from 'react-native';
import {VideoProjectBackupEntry} from '../src/library/VideoProjectBackupEntry';
const mockOpen = jest.fn();
jest.mock('../src/native/HonorableNative', () => ({honorableNative: {openVideoProjectBackups: (...args: unknown[]) => mockOpen(...args)}}));
let tree: Renderer.ReactTestRenderer;
let changed: (state: string) => void;
let originalState: PropertyDescriptor | undefined;
const button = () => tree.root.findAll(node => node.props.accessibilityRole === 'button' && typeof node.props.onPress === 'function')[0];
const labels = () => tree.root.findAll(node => String(node.type) === 'Text').map(node => node.props.children).join(' ');
beforeEach(() => {
  mockOpen.mockReset();
  jest.replaceProperty(Platform, 'OS', 'android');
  originalState = Object.getOwnPropertyDescriptor(AppState, 'currentState');
  Object.defineProperty(AppState, 'currentState', {configurable: true, writable: true, value: 'active'});
  jest.spyOn(AppState, 'addEventListener').mockImplementation((_, callback) => {changed = callback as (value: string) => void; return {remove: jest.fn()};});
});
afterEach(async () => {if (tree) await act(async () => tree.unmount()); jest.restoreAllMocks(); if (originalState) Object.defineProperty(AppState, 'currentState', originalState);});
const render = async (disabled = false, canStart = () => true) => {await act(async () => {tree = Renderer.create(<VideoProjectBackupEntry disabled={disabled} canStart={canStart} />);});};
it('does not offer the Android-only native screen on iOS', async () => {
  jest.replaceProperty(Platform, 'OS', 'ios'); await render(); expect(tree.toJSON()).toBeNull(); expect(mockOpen).not.toHaveBeenCalled();
});
it('opens the standalone backup screen without requiring any media selection', async () => {
  mockOpen.mockResolvedValue({opened: true}); await render(); await act(async () => {await button().props.onPress();}); expect(mockOpen).toHaveBeenCalledTimes(1);
  expect(labels()).toContain('separate local guest copy'); expect(button().props.accessibilityState.disabled).toBe(false);
});
it('refuses disabled, denied-admission and background launches', async () => {
  await render(true); await act(async () => {await button().props.onPress();}); expect(mockOpen).not.toHaveBeenCalled();
  await act(async () => tree.update(<VideoProjectBackupEntry disabled={false} canStart={() => false} />)); await act(async () => {await button().props.onPress();}); expect(mockOpen).not.toHaveBeenCalled();
  await act(async () => tree.update(<VideoProjectBackupEntry disabled={false} canStart={() => true} />)); await act(async () => {changed('background'); await button().props.onPress();}); expect(mockOpen).not.toHaveBeenCalled();
});
it('prevents duplicate presses before React commits a busy render', async () => {
  let finish!: (value: {opened: boolean}) => void; mockOpen.mockReturnValue(new Promise(resolve => {finish = resolve;})); await render();
  let pending!: Promise<void>; await act(async () => {pending = button().props.onPress(); button().props.onPress();}); expect(mockOpen).toHaveBeenCalledTimes(1); expect(button().props.accessibilityState.disabled).toBe(true);
  await act(async () => {finish({opened: true}); await pending;}); expect(button().props.accessibilityState.disabled).toBe(false);
});
it('shows recoverable failure for a missing or failed native entry, and permits retry', async () => {
  mockOpen.mockRejectedValueOnce(new Error('synthetic unavailable')); await render(); await act(async () => {await button().props.onPress();}); expect(labels()).toContain('your files and projects were kept');
  mockOpen.mockResolvedValueOnce({opened: false}); await act(async () => {await button().props.onPress();}); expect(labels()).toContain('could not open');
  mockOpen.mockResolvedValueOnce({opened: true}); await act(async () => {await button().props.onPress();}); expect(labels()).not.toContain('could not open'); expect(mockOpen).toHaveBeenCalledTimes(3);
});
it('drops an old result after background/resume and cannot clear a newer request', async () => {
  let rejectFirst!: (error: Error) => void; let finishSecond!: (value: {opened: boolean}) => void;
  mockOpen.mockReturnValueOnce(new Promise((_, reject) => {rejectFirst = reject;})).mockReturnValueOnce(new Promise(resolve => {finishSecond = resolve;})); await render();
  let first!: Promise<void>; let second!: Promise<void>;
  await act(async () => {first = button().props.onPress();}); await act(async () => {changed('background'); changed('active');}); await act(async () => {second = button().props.onPress();}); expect(mockOpen).toHaveBeenCalledTimes(2);
  await act(async () => {rejectFirst(new Error('late old result')); await first;}); expect(labels()).not.toContain('could not open'); expect(button().props.accessibilityState.disabled).toBe(true);
  await act(async () => {finishSecond({opened: true}); await second;}); expect(button().props.accessibilityState.disabled).toBe(false);
});
it('ignores native completion after unmount', async () => {
  let finish!: (value: {opened: boolean}) => void; mockOpen.mockReturnValue(new Promise(resolve => {finish = resolve;})); await render(); let pending!: Promise<void>;
  await act(async () => {pending = button().props.onPress(); tree.unmount();}); await act(async () => {finish({opened: true}); await pending;}); expect(mockOpen).toHaveBeenCalledTimes(1);
});
