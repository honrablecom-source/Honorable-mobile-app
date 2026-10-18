import type { MediaItem } from '../src/components/MediaGrid';
import type { SearchResult } from '../src/native/HonorableNative';
import { honorableNative } from '../src/native/HonorableNative';
import { editMemory } from '../src/native/editMemory';

jest.mock('../src/native/HonorableNative', () => ({
  honorableNative: { editPhoto: jest.fn(), editVideo: jest.fn() },
}));
const native = honorableNative as jest.Mocked<typeof honorableNative>;
const video = (timestamp?: number): MediaItem => ({
  id: 'found-video', type: 'VIDEO', mediaUri: 'content://library/fallback',
  result: {mediaUri: 'content://library/found', bestTimestampMs: timestamp} as SearchResult,
});
beforeEach(() => {
  jest.resetAllMocks();
  native.editPhoto.mockResolvedValue({opened:true});
  native.editVideo.mockResolvedValue({opened:true});
});

test.each([0, 42123])('video editing carries the found moment %i with the result URI', async timestamp => {
  await editMemory(video(timestamp));
  expect(native.editVideo).toHaveBeenCalledWith('content://library/found', timestamp);
  expect(native.editPhoto).not.toHaveBeenCalled();
});
test('a library video without a found moment uses the existing entry point', async () => {
  await editMemory({id:'library-video',type:'VIDEO',mediaUri:'content://library/video'});
  expect(native.editVideo).toHaveBeenCalledWith('content://library/video');
});
test.each([NaN, Infinity, -1])('invalid moment %s cannot launch a different edit', async timestamp => {
  await expect(editMemory(video(timestamp))).rejects.toThrow('video moment is unavailable');
  expect(native.editVideo).not.toHaveBeenCalled();
});
test('a thumbnail cannot substitute for an unavailable video source', async () => {
  await expect(editMemory({id:'thumbnail',type:'VIDEO',uri:'content://thumbnails/only'})).rejects.toThrow('Choose a memory');
  expect(native.editVideo).not.toHaveBeenCalled();
});
test('photo results preserve their selected source and authorization errors', async () => {
  native.editPhoto.mockRejectedValue(new Error('Studio authorization required'));
  await expect(editMemory({id:'photo',type:'IMAGE',result:{mediaUri:'content://library/photo'} as SearchResult})).rejects.toThrow('Studio authorization required');
  expect(native.editPhoto).toHaveBeenCalledWith('content://library/photo');
  expect(native.editVideo).not.toHaveBeenCalled();
});
