import { NativeModules } from 'react-native';
import { honorableNative } from '../src/native/HonorableNative';

jest.mock('react-native', () => ({
  Platform: { OS: 'android' },
  NativeModules: {
    HonorableSearchModule: {
      shareMedia: jest.fn(),
      shareMediaBatch: jest.fn(),
    },
  },
}));
const share = jest.mocked(NativeModules.HonorableSearchModule.shareMedia);
beforeEach(() => jest.resetAllMocks());
test.each(['IMAGE', 'VIDEO'] as const)(
  'share wrapper passes the exact selected %s source to the native stream boundary',
  async kind => {
    share.mockResolvedValue({ opened: true });
    await expect(
      honorableNative.shareMedia({
        mediaUri: 'content://synthetic/selected',
        mediaType: kind,
      }),
    ).resolves.toEqual({ opened: true });
    expect(share).toHaveBeenCalledWith('content://synthetic/selected', kind);
  },
);
test('native provider failure reaches the guarded viewer action instead of reporting success', async () => {
  share.mockRejectedValue(Error('Synthetic provider read denied'));
  await expect(
    honorableNative.shareMedia({
      mediaUri: 'content://synthetic/selected',
      mediaType: 'IMAGE',
    }),
  ).rejects.toThrow('Synthetic provider read denied');
});

test('batch wrapper sends only exact ordered original URI and type records as JSON', async () => {
  const batch = jest.mocked(
    NativeModules.HonorableSearchModule.shareMediaBatch,
  );
  batch.mockResolvedValue({ opened: true });
  const items = [
    { mediaUri: 'content://synthetic/photo', mediaType: 'IMAGE' as const },
    { mediaUri: 'content://synthetic/video', mediaType: 'VIDEO' as const },
  ];
  await expect(honorableNative.shareMediaBatch(items)).resolves.toEqual({
    opened: true,
  });
  expect(batch).toHaveBeenCalledWith(JSON.stringify(items));
  expect(share).not.toHaveBeenCalled();
});
test('batch native access failure remains a rejection rather than delivery success', async () => {
  jest
    .mocked(NativeModules.HonorableSearchModule.shareMediaBatch)
    .mockRejectedValue(new Error('Synthetic missing original'));
  await expect(
    honorableNative.shareMediaBatch([
      { mediaUri: 'content://synthetic/photo', mediaType: 'IMAGE' },
    ]),
  ).rejects.toThrow('Synthetic missing original');
});
