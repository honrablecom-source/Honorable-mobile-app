import { useEffect, useRef, useState } from 'react';
import { Alert } from 'react-native';
import { honorableNative } from './HonorableNative';

export type WorkspaceKind = 'IMAGE' | 'VIDEO';
export type WorkspaceAccess = 'account' | 'guest';

/** Only the user-selected URI reaches the existing authorized editor entry point. */
export async function openWorkspace(
  kind: WorkspaceKind,
  access: WorkspaceAccess = 'account',
  isCurrent: () => boolean = () => true,
): Promise<boolean> {
  if (!isCurrent()) return false;
  const selected = await honorableNative.pickWorkspaceMedia(kind);
  if (!selected || !isCurrent()) return false;
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
  const lifecycle = useRef({ active: false, generation: 0, access });
  useEffect(() => {
    const session = lifecycle.current;
    session.active = true;
    session.access = access;
    session.generation++;
    return () => {
      session.active = false;
      session.generation++;
    };
  }, [access]);
  const [busy, setBusy] = useState(false);
  const run = async (
    action: (isCurrent: () => boolean) => Promise<unknown>,
  ) => {
    if (
      pending.current ||
      !lifecycle.current.active ||
      lifecycle.current.access !== access
    )
      return;
    const generation = lifecycle.current.generation;
    const isCurrent = () =>
      lifecycle.current.active &&
      lifecycle.current.generation === generation &&
      lifecycle.current.access === access;
    pending.current = true;
    setBusy(true);
    try {
      await action(isCurrent);
    } catch (error) {
      if (!isCurrent()) return;
      Alert.alert(
        'Open workspace',
        error instanceof Error
          ? error.message
          : 'The editor could not open. Try again.',
      );
    } finally {
      pending.current = false;
      if (lifecycle.current.active) setBusy(false);
    }
  };
  const open = (kind: WorkspaceKind) =>
    run(isCurrent => openWorkspace(kind, access, isCurrent));
  const create = (
    format:
      | 'square'
      | 'portrait'
      | 'landscape'
      | 'feed_portrait'
      | 'story'
      | 'reels'
      | 'ultrawide'
      | 'postcard'
      | 'banner'
      | 'poster'
      | 'cover'
      | 'wallpaper',
  ) => run(() => honorableNative.createImageGuest(format));
  const draw = () => run(() => honorableNative.createDrawingGuest());
  const createCustom = (
    width: number,
    height: number,
    background: 'white' | 'black' | 'transparent',
  ) =>
    run(() =>
      honorableNative.createCustomImageGuest(width, height, background),
    );
  const projects = () => run(() => honorableNative.openCanvasProjectsGuest());
  const photoVideo = () => run(() => honorableNative.createPhotoVideoGuest());
  const videoProjects = () =>
    run(() => honorableNative.openPhotoVideoProjectsGuest());
  return {
    busy,
    open,
    create,
    createCustom,
    draw,
    projects,
    photoVideo,
    videoProjects,
  };
}
