import React from 'react';
import Renderer, { act } from 'react-test-renderer';
import { AppState, Modal } from 'react-native';
import {
  PhotoPrivacyAuditPanel,
  validPhotoPrivacyReport,
} from '../src/library/PhotoPrivacyAudit';
import type {
  LibraryCollectionItem,
  PhotoPrivacyReport,
} from '../src/native/HonorableNative';

const mockInspect = jest.fn(),
  mockCancel = jest.fn();
let mockOwner = 'synthetic-owner';
jest.mock('../src/native/HonorableNative', () => ({
  honorableNative: {
    inspectPhotoPrivacy: (...args: unknown[]) => mockInspect(...args),
    cancelLibraryDuplicateCheck: (...args: unknown[]) => mockCancel(...args),
  },
}));
jest.mock('../src/passes/MemoryPassContext', () => ({
  useMemoryPass: () => ({
    signedIn: !!mockOwner,
    account: { accountId: mockOwner },
  }),
}));
const items: LibraryCollectionItem[] = [
  { id: 'one', uri: 'content://synthetic/one', kind: 'IMAGE' },
  { id: 'two', uri: 'content://synthetic/two', kind: 'IMAGE' },
];
const report: PhotoPrivacyReport = {
  entries: [
    {
      id: 'one',
      uri: items[0].uri,
      status: 'READ',
      fields: ['GPS_LATITUDE', 'BODY_SERIAL', 'DATE_ORIGINAL'],
    },
    { id: 'two', uri: items[1].uri, status: 'UNKNOWN', fields: [] },
  ],
};
let tree: Renderer.ReactTestRenderer,
  props: React.ComponentProps<typeof PhotoPrivacyAuditPanel>;
let canStart: jest.Mock,
  selectionCurrent: jest.Mock,
  onBusy: jest.Mock,
  onSelectLocation: jest.Mock;
let changed: (state: string) => void;
const button = (label: string) =>
  tree.root.findAll(
    n =>
      n.props.accessibilityLabel === label &&
      typeof n.props.onPress === 'function',
  )[0];
const press = async (label: string) => {
  await act(async () => {
    await button(label).props.onPress();
  });
};
const mount = async (extra: Partial<typeof props> = {}) => {
  props = {
    items,
    names: { one: 'First photo', two: 'Second photo' },
    disabled: false,
    canStart,
    selectionCurrent,
    onBusy,
    onSelectLocation,
    ...extra,
  };
  await act(async () => {
    tree = Renderer.create(<PhotoPrivacyAuditPanel {...props} />);
  });
};
const update = async (extra: Partial<typeof props> = {}) => {
  props = { ...props, ...extra };
  await act(async () => tree.update(<PhotoPrivacyAuditPanel {...props} />));
};
const visible = () => tree.root.findByType(Modal).props.visible;
const inspect = () => press('Inspect photo EXIF privacy');
beforeEach(() => {
  jest.clearAllMocks();
  mockOwner = 'synthetic-owner';
  canStart = jest.fn(() => true);
  selectionCurrent = jest.fn(() => true);
  onBusy = jest.fn();
  onSelectLocation = jest.fn(() => true);
  mockInspect.mockResolvedValue(report);
  jest.spyOn(AppState, 'addEventListener').mockImplementation((_, callback) => {
    changed = callback as (s: string) => void;
    return { remove: jest.fn() };
  });
});
afterEach(async () => {
  if (tree) await act(async () => tree.unmount());
  jest.restoreAllMocks();
});

test('explicit inspection reads exact ordered bindings and shows only presence labels and truthful unknowns', async () => {
  mockInspect.mockResolvedValueOnce({
    entries: report.entries.map(e => ({ ...e, raw: 'private-value-51.5007' })),
  });
  await mount();
  expect(mockInspect).not.toHaveBeenCalled();
  await inspect();
  expect(mockInspect).toHaveBeenCalledWith(items, expect.any(String));
  expect(mockInspect.mock.calls[0][0][0]).not.toBe(items[0]);
  const output = JSON.stringify(tree.toJSON());
  expect(output).toContain('GPS latitude');
  expect(output).toContain('Camera body serial number');
  expect(output).toContain('Unknown');
  expect(output).not.toContain('private-value-51.5007');
  expect(JSON.stringify(mockInspect.mock.calls[0][0])).not.toContain(
    'First photo',
  );
  expect(onBusy.mock.calls.map(c => c[0])).toEqual([true, false]);
});

test.each([
  'guest',
  'disabled',
  'empty',
  'twenty-one',
  'video',
  'duplicate',
  'admission',
  'selection',
] as const)('%s cannot start reading EXIF', async kind => {
  if (kind === 'guest') mockOwner = '';
  if (kind === 'admission') canStart.mockReturnValue(false);
  if (kind === 'selection') selectionCurrent.mockReturnValue(false);
  await mount(
    kind === 'disabled'
      ? { disabled: true }
      : kind === 'empty'
      ? { items: [] }
      : kind === 'twenty-one'
      ? {
          items: Array.from({ length: 21 }, (_, i) => ({
            id: `${i}`,
            uri: `content://synthetic/${i}`,
            kind: 'IMAGE',
          })),
        }
      : kind === 'video'
      ? { items: [items[0], { ...items[1], kind: 'VIDEO' }] }
      : kind === 'duplicate'
      ? { items: [items[0], items[0]] }
      : {},
  );
  await inspect();
  expect(mockInspect).not.toHaveBeenCalled();
  expect(visible()).toBe(false);
});

test('one photo can be audited and no listed fields is not labelled safe', async () => {
  mockInspect.mockResolvedValueOnce({
    entries: [{ ...report.entries[0], fields: [] }],
  });
  await mount({ items: items.slice(0, 1) });
  await inspect();
  const output = JSON.stringify(tree.toJSON());
  expect(output).toContain('Other metadata may still exist');
  expect(button('Select location-flagged photos')).toBeUndefined();
});

test('same-tick repeated inspection claims admission before re-render', async () => {
  let finish!: (r: PhotoPrivacyReport) => void;
  mockInspect.mockImplementation(
    () =>
      new Promise(resolve => {
        finish = resolve;
      }),
  );
  await mount();
  const handler = button('Inspect photo EXIF privacy').props.onPress;
  act(() => {
    handler();
    handler();
  });
  expect(mockInspect).toHaveBeenCalledTimes(1);
  await act(async () => finish(report));
  expect(onBusy.mock.calls.map(c => c[0])).toEqual([true, false]);
});

test('Stop cancels exact request and ignores late report', async () => {
  let finish!: (r: PhotoPrivacyReport) => void;
  mockInspect.mockImplementation(
    () =>
      new Promise(resolve => {
        finish = resolve;
      }),
  );
  await mount();
  act(() => {
    button('Inspect photo EXIF privacy').props.onPress();
  });
  await press('Stop EXIF inspection');
  expect(mockCancel).toHaveBeenCalledWith(mockInspect.mock.calls[0][1]);
  await act(async () => finish(report));
  expect(visible()).toBe(false);
  expect(button('Select location-flagged photos')).toBeUndefined();
  expect(onBusy.mock.calls.map(c => c[0])).toEqual([true, false]);
});

test.each(['owner', 'order', 'source', 'background'] as const)(
  '%s change discards report and rejects retained selection',
  async kind => {
    await mount();
    await inspect();
    const retained = button('Select location-flagged photos').props.onPress;
    if (kind === 'owner') {
      mockOwner = 'other';
      await update();
    }
    if (kind === 'order') await update({ items: [...items].reverse() });
    if (kind === 'source')
      await update({
        items: [items[0], { ...items[1], uri: 'content://synthetic/replaced' }],
      });
    if (kind === 'background') act(() => changed('background'));
    await act(async () => retained());
    expect(onSelectLocation).not.toHaveBeenCalled();
    expect(visible()).toBe(false);
  },
);

test('location focus includes only location-related fields and changes no files', async () => {
  mockInspect.mockResolvedValueOnce({
    entries: [
      report.entries[0],
      {
        ...report.entries[1],
        status: 'READ',
        fields: ['DATE_ORIGINAL', 'CAMERA_OWNER'],
      },
    ],
  });
  await mount();
  await inspect();
  await press('Select location-flagged photos');
  expect(onSelectLocation).toHaveBeenCalledWith(['one']);
  expect(visible()).toBe(false);
  expect(mockInspect).toHaveBeenCalledTimes(1);
});

test('a same-tick selection change prevents report-based focusing', async () => {
  await mount();
  await inspect();
  const retained = button('Select location-flagged photos').props.onPress;
  selectionCurrent.mockReturnValue(false);
  await act(async () => retained());
  expect(onSelectLocation).not.toHaveBeenCalled();
});

test('failed parent admission does not pretend selection was changed', async () => {
  onSelectLocation.mockReturnValue(false);
  await mount();
  await inspect();
  await press('Select location-flagged photos');
  expect(visible()).toBe(true);
  expect(
    tree.root.findAllByProps({ accessibilityRole: 'alert' }).length,
  ).toBeGreaterThan(0);
});

test('closed/reopened review rejects a retained Inspect-again callback', async () => {
  await mount();
  await inspect();
  const retained = button('Inspect photo EXIF again').props.onPress;
  await press('Close EXIF privacy report');
  await inspect();
  await act(async () => retained());
  expect(mockInspect).toHaveBeenCalledTimes(2);
});

test('native failure gives no clean verdict and can retry', async () => {
  mockInspect.mockRejectedValueOnce(Error('Synthetic read limit'));
  await mount();
  await inspect();
  expect(button('Select location-flagged photos')).toBeUndefined();
  expect(
    tree.root.findAllByProps({ accessibilityRole: 'alert' }).length,
  ).toBeGreaterThan(0);
  await press('Inspect photo EXIF again');
  expect(mockInspect).toHaveBeenCalledTimes(2);
});

test('a report for another binding is refused rather than applied to selected originals', async () => {
  mockInspect.mockResolvedValueOnce({
    entries: [
      { ...report.entries[0], uri: 'content://synthetic/other' },
      report.entries[1],
    ],
  });
  await mount();
  await inspect();
  expect(button('Select location-flagged photos')).toBeUndefined();
  expect(
    tree.root.findAllByProps({ accessibilityRole: 'alert' }).length,
  ).toBeGreaterThan(0);
});

test('unmount cancels pending read without publishing a late report', async () => {
  let finish!: (r: PhotoPrivacyReport) => void;
  mockInspect.mockImplementation(
    () =>
      new Promise(resolve => {
        finish = resolve;
      }),
  );
  await mount();
  act(() => {
    button('Inspect photo EXIF privacy').props.onPress();
  });
  await act(async () => tree.unmount());
  await act(async () => finish(report));
  expect(mockCancel).toHaveBeenCalledWith(mockInspect.mock.calls[0][1]);
  expect(onBusy.mock.calls.map(c => c[0])).toEqual([true, false]);
});

test('schema rejects unknown/value-like fields, duplicates, reordered IDs and unknown-status findings', () => {
  expect(validPhotoPrivacyReport(report, items)).toBe(true);
  const invalid = [
    { entries: [...report.entries].reverse() },
    {
      entries: [
        { ...report.entries[0], fields: ['51.5007'] },
        report.entries[1],
      ],
    },
    {
      entries: [
        { ...report.entries[0], fields: ['constructor'] },
        report.entries[1],
      ],
    },
    {
      entries: [
        { ...report.entries[0], fields: ['GPS_LATITUDE', 'GPS_LATITUDE'] },
        report.entries[1],
      ],
    },
    {
      entries: [
        report.entries[0],
        { ...report.entries[1], fields: ['GPS_TIME'] },
      ],
    },
  ];
  for (const value of invalid)
    expect(validPhotoPrivacyReport(value as PhotoPrivacyReport, items)).toBe(
      false,
    );
});
