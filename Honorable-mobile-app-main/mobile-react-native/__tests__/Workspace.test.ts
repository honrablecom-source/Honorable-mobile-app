import { openWorkspace } from '../src/native/openWorkspace';
import { honorableNative } from '../src/native/HonorableNative';

jest.mock('../src/native/HonorableNative', () => ({
  honorableNative: {
    pickWorkspaceMedia: jest.fn(),
    editPhoto: jest.fn(),
    editVideo: jest.fn(),
  },
}));
const native = honorableNative as jest.Mocked<typeof honorableNative>;
beforeEach(() => jest.resetAllMocks());

test.each(['IMAGE', 'VIDEO'] as const)(
  'opens the selected %s in its authorized editor',
  async kind => {
    native.pickWorkspaceMedia.mockResolvedValue({
      uri: 'content://test/user-chosen',
    });
    native.editPhoto.mockResolvedValue({ opened: true });
    native.editVideo.mockResolvedValue({ opened: true });
    expect(await openWorkspace(kind)).toBe(true);
    expect(native.pickWorkspaceMedia).toHaveBeenCalledWith(kind);
    expect(
      kind === 'IMAGE' ? native.editPhoto : native.editVideo,
    ).toHaveBeenCalledWith('content://test/user-chosen');
    expect(
      kind === 'IMAGE' ? native.editVideo : native.editPhoto,
    ).not.toHaveBeenCalled();
  },
);

test('picker cancellation never launches an editor', async () => {
  native.pickWorkspaceMedia.mockResolvedValue(null);
  expect(await openWorkspace('VIDEO')).toBe(false);
  expect(native.editPhoto).not.toHaveBeenCalled();
  expect(native.editVideo).not.toHaveBeenCalled();
});

test('picker failures never open a fallback library item', async () => {
  native.pickWorkspaceMedia.mockRejectedValue(new Error('Permission denied'));
  await expect(openWorkspace('VIDEO')).rejects.toThrow('Permission denied');
  expect(native.editVideo).not.toHaveBeenCalled();
});

test('existing editor authorization errors reach the caller', async () => {
  native.pickWorkspaceMedia.mockResolvedValue({ uri: 'content://test/chosen' });
  native.editVideo.mockRejectedValue(
    new Error('Reconnect to authorize Studio'),
  );
  await expect(openWorkspace('VIDEO')).rejects.toThrow(
    'Reconnect to authorize Studio',
  );
});
