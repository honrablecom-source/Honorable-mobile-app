import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  AppState,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { useMemoryPass } from '../passes/MemoryPassContext';
import {
  honorableNative,
  type LibraryCollectionItem,
  type LibraryOriginalArchivePreview,
} from '../native/HonorableNative';

type Props = {
  items: LibraryCollectionItem[];
  names?: Record<string, string>;
  disabled: boolean;
  canStart: () => boolean;
  selectionCurrent: () => boolean;
  onBusy: (value: boolean) => void;
};
let serial = 0;
const archiveExtensions: Record<string, string> = {
  'image/jpeg': 'jpg',
  'image/png': 'png',
  'image/webp': 'webp',
  'image/heic': 'heic',
  'image/heif': 'heif',
  'image/avif': 'avif',
  'image/gif': 'gif',
  'video/mp4': 'mp4',
  'video/webm': 'webm',
  'video/quicktime': 'mov',
  'application/octet-stream': 'bin',
};
export function validOriginalArchivePreview(
  value: LibraryOriginalArchivePreview,
  count: number,
): boolean {
  if (
    !value ||
    typeof value.token !== 'string' ||
    !value.token ||
    value.token.length > 128 ||
    !Number.isSafeInteger(value.bytes) ||
    value.bytes <= 0 ||
    value.bytes > 257 * 1024 * 1024 ||
    typeof value.sha256 !== 'string' ||
    !/^[a-f0-9]{64}$/.test(value.sha256) ||
    count < 1 ||
    count > 20 ||
    !Array.isArray(value.entries) ||
    value.entries.length !== count
  )
    return false;
  let total = 0;
  return value.entries.every((entry, index) => {
    if (
      !entry ||
      !Object.prototype.hasOwnProperty.call(archiveExtensions, entry.mime) ||
      entry.name !==
        `originals/${String(index + 1).padStart(3, '0')}.${
          archiveExtensions[entry.mime]
        }` ||
      !Number.isSafeInteger(entry.bytes) ||
      entry.bytes <= 0 ||
      entry.bytes > 64 * 1024 * 1024 ||
      typeof entry.sha256 !== 'string' ||
      !/^[a-f0-9]{64}$/.test(entry.sha256)
    )
      return false;
    total += entry.bytes;
    return total <= 256 * 1024 * 1024 && total < value.bytes;
  });
}

export function LibraryOriginalArchivePanel(props: Props) {
  const auth = useMemoryPass();
  const owner = auth.signedIn ? auth.account?.accountId ?? '' : '';
  const selection = JSON.stringify(props.items);
  const latest = useRef(props),
    ownerRef = useRef(owner),
    selectionRef = useRef(selection);
  latest.current = props;
  ownerRef.current = owner;
  selectionRef.current = selection;
  const active = useRef(true);
  const job = useRef<
    { id: string; owner: string; selection: string } | undefined
  >(undefined);
  const view = useRef<object | undefined>(undefined);
  const prepared = useRef<LibraryOriginalArchivePreview | undefined>(undefined);
  const preparedBinding = useRef(''),
    viewBinding = useRef('');
  const binding = `${owner}\u0000${selection}`;
  const [session, setSession] = useState<object>();
  const [visible, setVisible] = useState(false),
    [busy, setBusy] = useState(false);
  const [preview, setPreview] = useState<LibraryOriginalArchivePreview>(),
    [error, setError] = useState('');
  const release = (token: string) => {
    try {
      honorableNative.releaseLibraryOriginalArchive(token);
    } catch {
      /* Host may have disposed it. */
    }
  };
  const stop = useCallback(() => {
    const old = job.current;
    job.current = undefined;
    if (old) {
      try {
        honorableNative.cancelLibraryDuplicateCheck(old.id);
      } catch {
        /* Host may be gone. */
      } finally {
        latest.current.onBusy(false);
      }
    }
  }, []);
  const close = useCallback(() => {
    view.current = undefined;
    viewBinding.current = '';
    preparedBinding.current = '';
    stop();
    const old = prepared.current;
    prepared.current = undefined;
    if (old) {
      try {
        honorableNative.releaseLibraryOriginalArchive(old.token);
      } catch {
        /* Already expired. */
      }
    }
    setSession(undefined);
    setVisible(false);
    setBusy(false);
    setPreview(undefined);
    setError('');
  }, [stop]);
  useEffect(() => {
    close();
  }, [owner, selection, close]);
  useEffect(() => {
    active.current = true;
    const subscription = AppState.addEventListener('change', state => {
      active.current = state === 'active';
      if (!active.current) close();
    });
    return () => {
      active.current = false;
      view.current = undefined;
      stop();
      const old = prepared.current;
      prepared.current = undefined;
      if (old) {
        try {
          honorableNative.releaseLibraryOriginalArchive(old.token);
        } catch {
          /* Host closed. */
        }
      }
      subscription.remove();
    };
  }, [close, stop]);
  const valid =
    props.items.length >= 1 &&
    props.items.length <= 20 &&
    props.items.every(item => item.kind === 'IMAGE' || item.kind === 'VIDEO') &&
    new Set(props.items.map(item => item.id)).size === props.items.length &&
    new Set(props.items.map(item => item.uri)).size === props.items.length;
  const show = () => {
    if (
      !active.current ||
      job.current ||
      props.disabled ||
      !owner ||
      !valid ||
      !props.canStart() ||
      !props.selectionCurrent() ||
      ownerRef.current !== owner ||
      selectionRef.current !== selection
    )
      return;
    close();
    const token = {};
    view.current = token;
    viewBinding.current = binding;
    setSession(token);
    setVisible(true);
  };
  const run = async (action: 'prepare' | 'open') => {
    const p = latest.current;
    if (
      !active.current ||
      job.current ||
      p.disabled ||
      !p.canStart() ||
      !p.selectionCurrent() ||
      !owner ||
      ownerRef.current !== owner ||
      selectionRef.current !== selection ||
      !visible ||
      !session ||
      view.current !== session ||
      viewBinding.current !== binding ||
      !valid
    )
      return;
    const old = prepared.current;
    if (action === 'open' && (!old || preparedBinding.current !== binding))
      return;
    const request = {
      id: `noble-archive-${Date.now()}-${++serial}`,
      owner,
      selection,
    };
    job.current = request;
    view.current = request;
    setSession(request);
    p.onBusy(true);
    setBusy(true);
    setError('');
    prepared.current = undefined;
    preparedBinding.current = '';
    setPreview(undefined);
    if (action === 'prepare' && old) release(old.token);
    const current = () =>
      active.current &&
      job.current === request &&
      ownerRef.current === request.owner &&
      selectionRef.current === request.selection &&
      latest.current.selectionCurrent();
    try {
      if (action === 'prepare') {
        const result = await honorableNative.prepareLibraryOriginalArchive(
          p.items.map(item => ({ ...item })),
          request.id,
        );
        if (!current()) {
          if (result?.token) release(result.token);
          return;
        }
        if (!validOriginalArchivePreview(result, p.items.length)) {
          if (result?.token) release(result.token);
          throw Error(
            'The archive did not match the selected originals. Prepare again.',
          );
        }
        prepared.current = result;
        preparedBinding.current = binding;
        setPreview(result);
      } else {
        const result = await honorableNative.shareLibraryOriginalArchive(
          old!.token,
          request.id,
        );
        if (!current()) return;
        if (!result.opened)
          throw Error(
            'The archive share sheet did not open. Prepare another copy.',
          );
        close();
      }
    } catch (reason) {
      if (current())
        setError(
          (reason as Error).message || 'Archive preparation failed. Try again.',
        );
    } finally {
      if (job.current === request) {
        job.current = undefined;
        latest.current.onBusy(false);
        if (active.current) setBusy(false);
      }
    }
  };
  const shown =
    preview &&
    prepared.current === preview &&
    preparedBinding.current === binding
      ? preview
      : undefined;
  return (
    <View>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Create original archive"
        disabled={props.disabled || !owner || !valid}
        onPress={show}
        style={styles.button}
      >
        <Text style={styles.label}>Create original archive</Text>
      </Pressable>
      <Modal visible={visible} animationType="slide" onRequestClose={close}>
        <ScrollView contentContainerStyle={styles.sheet}>
          <Text accessibilityRole="header" style={styles.title}>
            Original archive
          </Text>
          <Text style={styles.body}>
            Packages the exact selected photo and video original bytes in
            selection order, with numbered filenames and a checksum manifest.
            Original metadata, including location or identifying fields, stays
            included. This is not a sanitized copy. Up to 20 originals, 64 MiB
            each and 256 MiB total. Originals stay unchanged.
          </Text>
          {props.items.map((item, index) => (
            <View key={item.id} style={styles.page}>
              <Text style={styles.label} numberOfLines={2}>
                File {index + 1}:{' '}
                {props.names?.[item.id] || `Original ${index + 1}`}
              </Text>
              {shown && (
                <Text style={styles.body}>
                  {shown.entries[index].name} · {shown.entries[index].bytes}{' '}
                  bytes
                  {'\n'}SHA-256 {shown.entries[index].sha256}
                </Text>
              )}
            </View>
          ))}
          {shown && (
            <Text style={styles.body}>
              Prepared {shown.entries.length}-file ZIP ·{' '}
              {Math.ceil(shown.bytes / 1024)} KiB.
              {'\n'}Archive SHA-256 {shown.sha256}
              {'\n'}Sharing gives your chosen app read-only access to this
              separate archive, including original metadata. Numbered names
              replace source names; the manifest contains MIME labels, sizes and
              checksums. This temporary cached copy may expire; save it through
              your chosen app to keep it.
            </Text>
          )}

          {!!error && (
            <Text accessibilityRole="alert" style={styles.body}>
              {error}
            </Text>
          )}
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Prepare original archive"
            disabled={busy || props.disabled}
            onPress={() => run('prepare')}
            style={styles.button}
          >
            <Text style={styles.label}>
              {busy ? 'Working…' : 'Prepare original archive'}
            </Text>
          </Pressable>
          {shown && (
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="Share prepared original archive"
              disabled={busy || props.disabled}
              onPress={() => run('open')}
              style={styles.button}
            >
              <Text style={styles.label}>Share prepared archive</Text>
            </Pressable>
          )}
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={
              busy ? 'Stop preparing archive' : 'Close original archive'
            }
            onPress={close}
            style={styles.button}
          >
            <Text style={styles.label}>{busy ? 'Stop' : 'Close'}</Text>
          </Pressable>
        </ScrollView>
      </Modal>
    </View>
  );
}
const styles = StyleSheet.create({
  button: {
    minHeight: 44,
    borderWidth: 1,
    borderColor: '#000',
    paddingHorizontal: 14,
    paddingVertical: 12,
    marginVertical: 5,
    justifyContent: 'center',
  },
  label: { color: '#000', fontSize: 14, fontWeight: '600' },
  sheet: {
    padding: 22,
    paddingTop: 48,
    paddingBottom: 40,
    backgroundColor: '#fff',
    flexGrow: 1,
  },
  title: { fontSize: 24, fontWeight: '700', color: '#000', marginBottom: 12 },
  body: { color: '#333', fontSize: 14, lineHeight: 21, marginVertical: 8 },
  page: { borderBottomWidth: 1, borderColor: '#ddd', paddingVertical: 9 },
});
