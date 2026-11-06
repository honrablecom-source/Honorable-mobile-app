import {
  memoryRows,
  prepareMemoryEntries,
  type MemoryRow,
} from '../src/library/memoryRows';
import type { LibraryItem } from '../src/native/HonorableNative';
const photo = (id: number, month = 10): LibraryItem => ({
  mediaId: id,
  mediaUri: `content://synthetic/original/${id}`,
  thumbnailUri: `content://synthetic/thumbnail/${id}`,
  mediaType: 'IMAGE',
  displayName: `Photo ${id}.jpg`,
  capturedAt: Date.UTC(2026, month, 15, 12),
});
const media = (rows: MemoryRow[]) =>
  rows.flatMap(row => (row.kind === 'media' ? row.items : []));
test('first-seen months and native order within each month survive interleaved metadata', () => {
  const rows = memoryRows(
    prepareMemoryEntries([
      photo(1, 10),
      photo(2, 9),
      photo(3, 10),
      photo(4, 9),
    ]),
    '',
    'All',
  );
  expect(rows.filter(row => row.kind === 'month')).toHaveLength(2);
  expect(media(rows).map(item => item.id)).toEqual(['1', '3', '2', '4']);
});
test('original URIs stay separate from thumbnails and video metadata is preserved', () => {
  const video = {
    ...photo(2),
    mediaType: 'VIDEO' as const,
    displayName: 'Clip.mp4',
    durationMs: 125000,
  };
  const input = [photo(1), video],
    before = JSON.stringify(input);
  const result = media(memoryRows(prepareMemoryEntries(input), '', 'All'));
  expect(result[0]).toMatchObject({
    id: '1',
    mediaUri: input[0].mediaUri,
    uri: input[0].thumbnailUri,
  });
  expect(result[1]).toMatchObject({
    type: 'VIDEO',
    mediaUri: video.mediaUri,
    duration: '2:05',
    capturedAt: video.capturedAt,
  });
  expect(JSON.stringify(input)).toBe(before);
});
test('filename and type filtering retain existing case/whitespace and screenshot behavior', () => {
  const entries = prepareMemoryEntries([
    photo(1),
    { ...photo(2), mediaType: 'VIDEO', displayName: 'SCREENSHOT clip.mp4' },
    { ...photo(3), displayName: 'Screenshot.png' },
  ]);
  expect(
    media(memoryRows(entries, '  SCREENSHOT ', 'All')).map(item => item.id),
  ).toEqual(['2', '3']);
  expect(media(memoryRows(entries, '', 'Photos')).map(item => item.id)).toEqual(
    ['1', '3'],
  );
  expect(media(memoryRows(entries, '', 'Videos')).map(item => item.id)).toEqual(
    ['2'],
  );
  expect(
    media(memoryRows(entries, '', 'Screenshots')).map(item => item.id),
  ).toEqual(['2', '3']);
});
test('each month is split into at-most-three-item rows with no empty rows or duplicate keys', () => {
  const rows = memoryRows(
    prepareMemoryEntries(Array.from({ length: 10 }, (_, i) => photo(i))),
    '',
    'All',
  );
  expect(
    rows
      .filter(row => row.kind === 'media')
      .map(row => (row.kind === 'media' ? row.items.length : 0)),
  ).toEqual([3, 3, 3, 1]);
  expect(new Set(rows.map(row => row.key)).size).toBe(rows.length);
  expect(media(rows).map(item => item.id)).toEqual(
    Array.from({ length: 10 }, (_, i) => String(i)),
  );
});
test('no-match and empty input produce no orphan month headings', () => {
  expect(memoryRows(prepareMemoryEntries([photo(1)]), 'absent', 'All')).toEqual(
    [],
  );
  expect(memoryRows([], '', 'All')).toEqual([]);
});
test('filtering reuses prepared media metadata without modifying entries or rows from another query', () => {
  const entries = prepareMemoryEntries([photo(1), photo(2)]),
    before = JSON.stringify(entries);
  const all = memoryRows(entries, '', 'All'),
    one = memoryRows(entries, 'Photo 2', 'All');
  expect(media(one)[0]).toBe(entries[1].media);
  expect(media(all)).toHaveLength(2);
  expect(JSON.stringify(entries)).toBe(before);
});
test('hundreds of synthetic records remain complete while render rows stay small', () => {
  const input = Array.from({ length: 601 }, (_, i) => photo(i));
  const rows = memoryRows(prepareMemoryEntries(input), '', 'All');
  expect(media(rows)).toHaveLength(601);
  expect(new Set(media(rows).map(item => item.id)).size).toBe(601);
  expect(
    rows
      .filter(row => row.kind === 'media')
      .every(row => row.kind === 'media' && row.items.length <= 3),
  ).toBe(true);
});
