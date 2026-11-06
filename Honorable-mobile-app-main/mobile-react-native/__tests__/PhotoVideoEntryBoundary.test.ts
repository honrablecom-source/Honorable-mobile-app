import { NativeModules } from 'react-native';
import { honorableNative } from '../src/native/HonorableNative';
jest.mock('react-native', () => ({
  Platform: { OS: 'android' },
  NativeModules: {
    HonorableSearchModule: { createPhotoVideoFromLibraryGuest: jest.fn() },
  },
}));
const open = jest.mocked(
  NativeModules.HonorableSearchModule.createPhotoVideoFromLibraryGuest,
);
beforeEach(() => jest.resetAllMocks());
test('library photo-video wrapper passes exact original records in chosen order as bounded-native input', async () => {
  open.mockResolvedValue({ opened: true });
  const chosen = [
    { mediaUri: 'content://synthetic/second', mediaType: 'IMAGE' as const },
    { mediaUri: 'content://synthetic/first', mediaType: 'IMAGE' as const },
  ];
  await expect(
    honorableNative.createPhotoVideoFromLibraryGuest(chosen),
  ).resolves.toEqual({ opened: true });
  expect(open).toHaveBeenCalledWith(JSON.stringify(chosen));
});
test('native creator access refusal remains a rejection for selection-preserving retry', async () => {
  open.mockRejectedValue(new Error('Synthetic read denied'));
  await expect(
    honorableNative.createPhotoVideoFromLibraryGuest([
      { mediaUri: 'content://synthetic/photo', mediaType: 'IMAGE' },
    ]),
  ).rejects.toThrow('Synthetic read denied');
});
