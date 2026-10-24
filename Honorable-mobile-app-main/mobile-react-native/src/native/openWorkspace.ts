import { useRef, useState } from 'react';
import { Alert } from 'react-native';
import { honorableNative } from './HonorableNative';

export type WorkspaceKind = 'IMAGE' | 'VIDEO';
export type WorkspaceAccess = 'account' | 'guest';

/** Only the user-selected URI reaches the existing authorized editor entry point. */
export async function openWorkspace(
  kind: WorkspaceKind,
  access: WorkspaceAccess = 'account',
): Promise<boolean> {
  const selected = await honorableNative.pickWorkspaceMedia(kind);
  if (!selected) return false;
  if (access === 'guest') {
    const result =
      kind === 'IMAGE'
        ? await honorableNative.editPhotoGuest(selected.uri)
        : await honorableNative.editVideoGuest(selected.uri);
    return result.opened;
  }
  const result =
    kind === 'IMAGE'
      ? await honorableNative.editPhoto(selected.uri)
      : await honorableNative.editVideo(selected.uri);
  return result.opened;
}

export function useOpenWorkspace(access: WorkspaceAccess = 'account') {
  const pending = useRef(false);
  const [busy, setBusy] = useState(false);
  const run = async (action: () => Promise<unknown>) => {
    if (pending.current) return;
    pending.current = true;
    setBusy(true);
    try {
      await action();
    } catch (error) {
      Alert.alert(
        'Open workspace',
        error instanceof Error
          ? error.message
          : 'The editor could not open. Try again.',
      );
    } finally {
      pending.current = false;
      setBusy(false);
    }
  };
  const open = (kind: WorkspaceKind) => run(() => openWorkspace(kind, access));
  const create = (format: 'square' | 'portrait' | 'landscape') =>
    run(() => honorableNative.createImageGuest(format));
  return { busy, open, create };
}
