import { NativeModules } from 'react-native';
import { honorableNative } from '../src/native/HonorableNative';

jest.mock('react-native', () => ({
  Platform: { OS: 'android' },
  NativeModules: { HonorableSearchModule: { shareMedia: jest.fn() } },
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
