import type { MediaItem } from '../components/MediaGrid';
import { honorableNative } from './HonorableNative';

/** Keep the selected search result and its found moment together at the editor boundary. */
export async function editMemory(item: MediaItem): Promise<{ opened: boolean }> {
  const uri =
    item.result?.mediaUri ??
    item.mediaUri ??
    (item.type === 'IMAGE' ? item.uri : undefined);
  if (!uri) throw new Error('Choose a memory from your library to edit.');
  if (item.type === 'IMAGE') return honorableNative.editPhoto(uri);
  const timestamp = item.result?.bestTimestampMs;
  if (timestamp === undefined) return honorableNative.editVideo(uri);
  if (!Number.isFinite(timestamp) || timestamp < 0) {
    throw new Error('This video moment is unavailable. Open the video from your library.');
  }
  return honorableNative.editVideo(uri, timestamp);
}
