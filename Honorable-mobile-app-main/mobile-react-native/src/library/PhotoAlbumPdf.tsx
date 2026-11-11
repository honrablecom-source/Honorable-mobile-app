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
  type PhotoAlbumPdfPreview,
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
export function validPhotoPdfPreview(
  value: PhotoAlbumPdfPreview,
  count: number,
): boolean {
  return (
    !!value &&
    typeof value.token === 'string' &&
    value.token.length > 0 &&
    value.token.length <= 128 &&
    Number.isSafeInteger(value.bytes) &&
    value.bytes > 0 &&
    value.bytes <= 16 * 1024 * 1024 &&
    Array.isArray(value.pages) &&
    count >= 2 &&
    count <= 8 &&
    value.pages.length === count &&
    value.pages.every(
      page =>
        !!page &&
        Number.isSafeInteger(page.width) &&
        Number.isSafeInteger(page.height) &&
        page.width >= 1 &&
        page.height >= 1 &&
        page.width <= 1024 &&
        page.height <= 1024 &&
        Number.isSafeInteger(page.sourceWidth) &&
        Number.isSafeInteger(page.sourceHeight) &&
        page.sourceWidth >= 1 &&
        page.sourceHeight >= 1 &&
        page.sourceWidth <= 32768 &&
        page.sourceHeight <= 32768 &&
        page.sourceWidth * page.sourceHeight <= 100_000_000 &&
        typeof page.sampled === 'boolean' &&
        page.sampled ===
          (page.width !== page.sourceWidth ||
            page.height !== page.sourceHeight),
    )
  );
}

export function PhotoAlbumPdfPanel(props: Props) {
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
  const prepared = useRef<PhotoAlbumPdfPreview | undefined>(undefined);
  const preparedBinding = useRef(''),
    viewBinding = useRef('');
  const binding = `${owner}\u0000${selection}`;
  const [session, setSession] = useState<object>();
  const [visible, setVisible] = useState(false),
    [busy, setBusy] = useState(false);
  const [preview, setPreview] = useState<PhotoAlbumPdfPreview>(),
    [error, setError] = useState('');
  const release = (token: string) => {
    try {
      honorableNative.releasePhotoAlbumPdf(token);
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
        honorableNative.releasePhotoAlbumPdf(old.token);
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
          honorableNative.releasePhotoAlbumPdf(old.token);
        } catch {
          /* Host closed. */
        }
      }
      subscription.remove();
    };
  }, [close, stop]);
  const valid =
    props.items.length >= 2 &&
    props.items.length <= 8 &&
    props.items.every(item => item.kind === 'IMAGE') &&
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
      id: `noble-pdf-${Date.now()}-${++serial}`,
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
        const result = await honorableNative.preparePhotoAlbumPdf(
          p.items.map(item => ({ ...item })),
          request.id,
        );
        if (!current()) {
          if (result?.token) release(result.token);
          return;
        }
        if (!validPhotoPdfPreview(result, p.items.length)) {
          if (result?.token) release(result.token);
          throw Error(
            'The PDF did not match the selected photos. Prepare again.',
          );
        }
        prepared.current = result;
        preparedBinding.current = binding;
        setPreview(result);
      } else {
        const result = await honorableNative.openPhotoAlbumPdf(
          old!.token,
          request.id,
        );
        if (!current()) return;
        if (!result.opened)
          throw Error('The PDF viewer did not open. Prepare another copy.');
        close();
      }
    } catch (reason) {
      if (current())
        setError(
          (reason as Error).message || 'PDF preparation failed. Try again.',
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
        accessibilityLabel="Create photo PDF"
        disabled={props.disabled || !owner || !valid}
        onPress={show}
        style={styles.button}
      >
        <Text style={styles.label}>Create photo PDF</Text>
      </Pressable>
      <Modal visible={visible} animationType="slide" onRequestClose={close}>
        <ScrollView contentContainerStyle={styles.sheet}>
          <Text accessibilityRole="header" style={styles.title}>
            Photo PDF
          </Text>
          <Text style={styles.body}>
            One photo per A4 page, fitted without cropping. Pages follow your
            selection order. White backgrounds; transparent areas become white.
            Copies use up to 1024 pixels per edge. Originals stay unchanged.
          </Text>
          {props.items.map((item, index) => (
            <View key={item.id} style={styles.page}>
              <Text style={styles.label} numberOfLines={2}>
                Page {index + 1}:{' '}
                {props.names?.[item.id] || `Photo ${index + 1}`}
              </Text>
              {shown && (
                <Text style={styles.body}>
                  {shown.pages[index].sourceWidth} ×{' '}
                  {shown.pages[index].sourceHeight} → {shown.pages[index].width}{' '}
                  × {shown.pages[index].height} pixels;{' '}
                  {shown.pages[index].sourceWidth >
                  shown.pages[index].sourceHeight
                    ? 'landscape'
                    : 'portrait'}
                </Text>
              )}
            </View>
          ))}
          {shown && (
            <Text style={styles.body}>
              Prepared {shown.pages.length}-page PDF ·{' '}
              {Math.ceil(shown.bytes / 1024)} KiB. Opening gives your chosen PDF
              app read-only access to this separate copy.
            </Text>
          )}
          {!!error && (
            <Text accessibilityRole="alert" style={styles.body}>
              {error}
            </Text>
          )}
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Prepare photo PDF"
            disabled={busy || props.disabled}
            onPress={() => run('prepare')}
            style={styles.button}
          >
            <Text style={styles.label}>
              {busy ? 'Working…' : 'Prepare photo PDF'}
            </Text>
          </Pressable>
          {shown && (
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="Open prepared photo PDF"
              disabled={busy || props.disabled}
              onPress={() => run('open')}
              style={styles.button}
            >
              <Text style={styles.label}>Open prepared PDF</Text>
            </Pressable>
          )}
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={busy ? 'Stop preparing PDF' : 'Close photo PDF'}
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
