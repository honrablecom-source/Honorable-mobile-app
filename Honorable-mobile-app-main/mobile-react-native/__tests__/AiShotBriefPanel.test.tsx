import React from 'react';
import Renderer, { act } from 'react-test-renderer';
// Resolve React Native's lazy component modules during fixture setup, before
// timed UI hooks. Standard Jest deadlines and all behavior assertions stay intact.
const nativePrimitives = require('react-native');
for (const name of [
  'View',
  'Text',
  'Pressable',
  'TextInput',
  'Image',
  'ScrollView',
  'Modal',
  'ActivityIndicator',
  'Keyboard',
  'AppState',
]) {
  Reflect.get(nativePrimitives, name);
}

import { AppState } from 'react-native';
import { AiShotBriefPanel } from '../src/search/AiShotBriefPanel';
import type { SearchResponse } from '../src/native/HonorableNative';
const mockCollections = jest.fn();
jest.mock('../src/native/HonorableNative', () => ({
  honorableNative: {
    libraryCollections: (...args: unknown[]) => mockCollections(...args),
  },
}));
const candidate = {
  rank: 4,
  mediaId: 19,
  mediaUri: 'content://synthetic/19',
  mediaType: 'VIDEO' as const,
  displayName: 'Synthetic clip',
  score: 0.8,
  semantic: 0.8,
  confidence: 'POSSIBLE' as const,
  evidence: ['synthetic'],
  bestTimestampMs: 4200,
};
const response: SearchResponse = {
  confident: true,
  decision: 'MATCH',
  semantic: 0.8,
  margin: 0.1,
  results: [candidate],
};
let tree: Renderer.ReactTestRenderer;
let props: React.ComponentProps<typeof AiShotBriefPanel>;
let appChange: (state: string) => void;
const button = (label: string) =>
  tree.root.findAll(
    node =>
      node.props.accessibilityLabel === label &&
      typeof node.props.onPress === 'function',
  )[0];
const press = async (label: string) => {
  await act(async () => button(label).props.onPress());
};
const input = (label: string) =>
  tree.root.findAll(
    node =>
      node.props.accessibilityLabel === label &&
      typeof node.props.onChangeText === 'function',
  )[0];
const text = () => JSON.stringify(tree.toJSON());
async function prepare() {
  await press('AI shot brief: find shots for an idea');
  await act(async () =>
    input('AI shot brief').props.onChangeText(
      'Candles glowing\nFriends laughing',
    ),
  );
  await press('Prepare AI shot brief');
}
beforeEach(async () => {
  jest.resetAllMocks();
  jest
    .spyOn(AppState, 'addEventListener')
    .mockImplementation((_type, listener) => {
      appChange = listener as typeof appChange;
      return { remove: jest.fn() };
    });
  props = {
    owner: 'synthetic-owner',
    connected: true,
    model: 'SERAN_V1',
    busy: false,
    phase: 'IDLE',
    onSearch: jest.fn().mockResolvedValue(response),
    onCancel: jest.fn().mockReturnValue(true),
    onPreview: jest.fn(),
  };
  mockCollections.mockResolvedValue({
    owner: 'opaque-native-shelf',
    collections: [],
  });
  await act(async () => {
    tree = Renderer.create(<AiShotBriefPanel {...props} />);
  });
});
afterEach(async () => {
  await act(async () => tree.unmount());
  jest.restoreAllMocks();
});
test('prepare does no inference; explicit per-shot AI search preserves query, order and manual review', async () => {
  await prepare();
  expect(props.onSearch).not.toHaveBeenCalled();
  expect(mockCollections).not.toHaveBeenCalled();
  expect(text()).toContain('per successful search');
  expect(text()).toContain('local search history');
  await press('Find AI candidates for shot 1');
  expect(props.onSearch).toHaveBeenCalledWith('Candles glowing');
  expect(text()).toContain('possible');
  expect(text()).toContain('4.2');
  expect(button('Save chosen originals as collection').props.disabled).toBe(
    true,
  );
  await press('Preview candidate 1 for shot 1');
  expect(props.onPreview).toHaveBeenCalledWith(candidate);
  await press('Choose candidate 1 for shot 1');
  expect(button('Save chosen originals as collection').props.disabled).toBe(
    false,
  );
});
test('reuse across shots deduplicates exact originals and creates with opaque native shelf owner only on Save', async () => {
  await prepare();
  await press('Find AI candidates for shot 1');
  await press('Choose candidate 1 for shot 1');
  await press('Find AI candidates for shot 2');
  await press('Choose candidate 1 for shot 2');
  expect(mockCollections).not.toHaveBeenCalled();
  await press('Save chosen originals as collection');
  expect(mockCollections.mock.calls).toEqual([
    ['list'],
    [
      'create',
      'opaque-native-shelf',
      '',
      'AI shot picks',
      [{ id: '19', uri: 'content://synthetic/19', kind: 'VIDEO' }],
    ],
  ]);
  expect(text()).toContain('collection stores original files');
});
test('same-frame find taps start only one search and accepted stop suppresses late candidates', async () => {
  await prepare();
  let resolve!: (value: SearchResponse) => void;
  (props.onSearch as jest.Mock).mockReturnValue(
    new Promise(done => {
      resolve = done;
    }),
  );
  const find = button('Find AI candidates for shot 1').props.onPress;
  await act(async () => {
    find();
    find();
  });
  expect(props.onSearch).toHaveBeenCalledTimes(1);
  await press('Stop AI shot search');
  expect(props.onCancel).toHaveBeenCalledTimes(1);
  await act(async () => resolve(response));
  expect(button('Choose candidate 1 for shot 1')).toBeUndefined();
  expect(text()).toContain('Stopping this search');
});
test('account change clears private brief and retained old Find cannot send a query as the new owner', async () => {
  await prepare();
  const retained = button('Find AI candidates for shot 1').props.onPress;
  await act(async () => {
    props = { ...props, owner: 'different-owner' };
    tree.update(<AiShotBriefPanel {...props} />);
  });
  await act(async () => retained());
  expect(props.onSearch).not.toHaveBeenCalled();
  expect(text()).not.toContain('Candles glowing');
  expect(input('AI shot brief')).toBeUndefined();
});
test('background invalidates pending result and requests cancellation', async () => {
  await prepare();
  let resolve!: (value: SearchResponse) => void;
  (props.onSearch as jest.Mock).mockReturnValue(
    new Promise(done => {
      resolve = done;
    }),
  );
  await press('Find AI candidates for shot 1');
  await act(async () => appChange('background'));
  await act(async () => resolve(response));
  expect(props.onCancel).toHaveBeenCalled();
  expect(button('Choose candidate 1 for shot 1')).toBeUndefined();
});
test('account change during shelf lookup prevents create; invalid title performs no collection IO', async () => {
  await prepare();
  await press('Find AI candidates for shot 1');
  await press('Choose candidate 1 for shot 1');
  await act(async () =>
    input('AI picks collection name').props.onChangeText('   '),
  );
  await press('Save chosen originals as collection');
  expect(mockCollections).not.toHaveBeenCalled();
  await act(async () =>
    input('AI picks collection name').props.onChangeText('Reviewed picks'),
  );
  let resolve!: (value: unknown) => void;
  mockCollections.mockReturnValue(
    new Promise(done => {
      resolve = done;
    }),
  );
  await press('Save chosen originals as collection');
  await act(async () => {
    props = { ...props, owner: 'different-owner' };
    tree.update(<AiShotBriefPanel {...props} />);
  });
  await act(async () => resolve({ owner: 'old-shelf', collections: [] }));
  expect(mockCollections).toHaveBeenCalledTimes(1);
  expect(text()).not.toContain('Saved chosen originals');
});
test('finalizing search cannot advertise an accepted cancel; VIDEO displays existing three-credit cost', async () => {
  props = { ...props, model: 'SERAN_V2' };
  await act(async () => tree.update(<AiShotBriefPanel {...props} />));
  await prepare();
  let resolve!: (value: SearchResponse) => void;
  (props.onSearch as jest.Mock).mockReturnValue(
    new Promise(done => {
      resolve = done;
    }),
  );
  await press('Find AI candidates for shot 1');
  props = { ...props, phase: 'FINALIZING', busy: true };
  await act(async () => tree.update(<AiShotBriefPanel {...props} />));
  expect(button('Finishing completed AI search').props.disabled).toBe(true);
  await press('Finishing completed AI search');
  expect(props.onCancel).not.toHaveBeenCalled();
  expect(text()).toContain('per successful search');
  await act(async () => resolve(response));
});

test('retained clear callback cannot replace the brief while another native criterion is pending', async () => {
  await prepare();
  const clear = button('Clear AI brief').props.onPress;
  let resolve!: (value: SearchResponse) => void;
  (props.onSearch as jest.Mock).mockReturnValue(
    new Promise(done => {
      resolve = done;
    }),
  );
  await press('Find AI candidates for shot 1');
  await act(async () => clear());
  expect(text()).toContain('Candles glowing');
  await act(async () => resolve(response));
  expect(button('Choose candidate 1 for shot 1')).toBeDefined();
});
test('collection name matches native forty-codepoint and control-character bounds before IO', async () => {
  await prepare();
  await press('Find AI candidates for shot 1');
  await press('Choose candidate 1 for shot 1');
  for (const invalid of ['x'.repeat(41), 'line\nbreak']) {
    await act(async () =>
      input('AI picks collection name').props.onChangeText(invalid),
    );
    await press('Save chosen originals as collection');
    expect(mockCollections).not.toHaveBeenCalled();
  }
});
