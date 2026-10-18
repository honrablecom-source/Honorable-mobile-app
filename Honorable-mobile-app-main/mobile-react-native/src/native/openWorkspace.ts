import { useRef, useState } from 'react';
import { Alert } from 'react-native';
import { honorableNative } from './HonorableNative';

export type WorkspaceKind = 'IMAGE' | 'VIDEO';

/** Only the user-selected URI reaches the existing authorized editor entry point. */
export async function openWorkspace(kind: WorkspaceKind): Promise<boolean> {
  const selected = await honorableNative.pickWorkspaceMedia(kind);
  if (!selected) return false;
  const result =
    kind === 'IMAGE'
      ? await honorableNative.editPhoto(selected.uri)
      : await honorableNative.editVideo(selected.uri);
  return result.opened;
}

export function useOpenWorkspace() {
  const pending = useRef(false);
  const [busy, setBusy] = useState(false);
  const open = async (kind: WorkspaceKind) => {
    if (pending.current) return;
    pending.current = true;
    setBusy(true);
    try {
      await openWorkspace(kind);
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
  return { busy, open };
}
