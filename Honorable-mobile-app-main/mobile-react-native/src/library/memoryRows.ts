import type { LibraryItem } from '../native/HonorableNative';
import type { MediaItem } from '../components/MediaGrid';

export type MemoryFilter = 'All' | 'Photos' | 'Videos' | 'Screenshots';
export type MemoryEntry = { month: string; filename: string; media: MediaItem };
export type MemoryRow =
  | { kind: 'month'; key: string; label: string }
  | { kind: 'media'; key: string; items: MediaItem[] };

/** Prepare date labels/filename metadata once per library page change, not each keystroke. */
export function prepareMemoryEntries(
  items: readonly LibraryItem[],
): MemoryEntry[] {
  return items.map(item => {
    const seconds =
      item.durationMs === undefined
        ? undefined
        : Math.floor(item.durationMs / 1000);
    return {
      month: new Date(item.capturedAt).toLocaleDateString(undefined, {
        month: 'long',
        year: 'numeric',
      }),
      filename: item.displayName.toLowerCase(),
      media: {
        id: String(item.mediaId),
        uri:
          item.thumbnailUri ??
          (item.mediaType === 'IMAGE' ? item.mediaUri : undefined),
        mediaUri: item.mediaUri,
        type: item.mediaType,
        duration:
          seconds === undefined
            ? undefined
            : `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(
                2,
                '0',
              )}`,
        capturedAt: item.capturedAt,
        displayName: item.displayName,
      },
    };
  });
}

/** Preserve first-seen month order and native item order within each month. */
export function memoryRows(
  entries: readonly MemoryEntry[],
  query: string,
  filter: MemoryFilter,
): MemoryRow[] {
  const clean = query.trim().toLowerCase();
  const grouped = new Map<string, MediaItem[]>();
  for (const entry of entries) {
    const kindMatches =
      filter === 'All' ||
      (filter === 'Photos' && entry.media.type === 'IMAGE') ||
      (filter === 'Videos' && entry.media.type === 'VIDEO') ||
      (filter === 'Screenshots' && entry.filename.includes('screenshot'));
    if (!kindMatches || (clean && !entry.filename.includes(clean))) continue;
    const month = grouped.get(entry.month);
    if (month) month.push(entry.media);
    else grouped.set(entry.month, [entry.media]);
  }
  const rows: MemoryRow[] = [];
  for (const [label, media] of grouped) {
    rows.push({ kind: 'month', key: `month:${label}`, label });
    for (let start = 0; start < media.length; start += 3) {
      const items = media.slice(start, start + 3);
      rows.push({ kind: 'media', key: `media:${items[0].id}`, items });
    }
  }
  return rows;
}
