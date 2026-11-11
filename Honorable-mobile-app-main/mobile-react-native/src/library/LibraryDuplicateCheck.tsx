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
  type LibraryDuplicateReport,
} from '../native/HonorableNative';

let serial = 0;
type Props = {
  items: LibraryCollectionItem[];
  displayNames?: Record<string, string>;
  disabled: boolean;
  canStart: () => boolean;
  onBusy: (value: boolean) => void;
};
export function LibraryDuplicateCheck(props: Props) {
  const auth = useMemoryPass();
  const owner = auth.signedIn ? auth.account?.accountId ?? '' : '';
  const ownerRef = useRef(owner);
  ownerRef.current = owner;
  const callbacks = useRef(props);
  callbacks.current = props;
  const job = useRef<
    { id: string; owner: string; items: LibraryCollectionItem[] } | undefined
  >(undefined);
  const view = useRef<object | undefined>(undefined);
  const [viewToken, setViewToken] = useState<object>();
  const active = useRef(true);
  const [open, setOpen] = useState(false),
    [busy, setBusy] = useState(false);
  const [report, setReport] = useState<LibraryDuplicateReport>();
  const [error, setError] = useState('');
  const [names, setNames] = useState<Record<string, string>>({});
  const stop = useCallback(() => {
    const current = job.current;
    job.current = undefined;
    if (current) {
      try {
        honorableNative.cancelLibraryDuplicateCheck(current.id);
      } catch {
        /* Native transport may already have closed; the result owner is invalidated. */
      } finally {
        callbacks.current.onBusy(false);
      }
    }
  }, []);
  const close = useCallback(() => {
    view.current = undefined;
    stop();
    setViewToken(undefined);
    setBusy(false);
    setOpen(false);
    setReport(undefined);
    setError('');
    setNames({});
  }, [stop]);
  useEffect(() => {
    close();
  }, [owner, close]);
  useEffect(() => {
    active.current = true;
    const listener = AppState.addEventListener('change', next => {
      active.current = next === 'active';
      if (!active.current) close();
    });
    return () => {
      active.current = false;
      stop();
      listener.remove();
    };
  }, [close, stop]);
  const compare = async () => {
    const latest = callbacks.current;
    if (
      !active.current ||
      job.current ||
      latest.disabled ||
      !latest.canStart() ||
      !ownerRef.current ||
      latest.items.length < 2
    )
      return;
    const request = {
      id: `noble-duplicates-${Date.now()}-${++serial}`,
      owner: ownerRef.current,
      items: latest.items.map(item => ({ ...item })),
    };
    job.current = request;
    view.current = request;
    setViewToken(request);
    latest.onBusy(true);
    setOpen(true);
    setBusy(true);
    setReport(undefined);
    setError('');
    setNames(
      Object.fromEntries(
        request.items.map((item, index) => [
          item.id,
          latest.displayNames?.[item.id]?.replace(/\s+/g, ' ').slice(0, 120) ||
            `Original ${index + 1}`,
        ]),
      ),
    );
    const current = () =>
      active.current &&
      job.current === request &&
      ownerRef.current === request.owner;
    try {
      const result = await honorableNative.compareLibraryOriginals(
        request.items,
        request.id,
      );
      if (current()) setReport(result);
    } catch (reason) {
      if (current())
        setError(
          reason instanceof Error
            ? reason.message
            : 'Comparison could not finish. Try fewer or smaller originals.',
        );
    } finally {
      if (current()) {
        job.current = undefined;
        setBusy(false);
        callbacks.current.onBusy(false);
      }
    }
  };
  const closeCurrent = () => {
    if (view.current === viewToken) close();
  };
  return (
    <View>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Check selected originals for exact duplicates"
        accessibilityState={{
          disabled: props.disabled || props.items.length < 2 || !owner,
        }}
        disabled={props.disabled || props.items.length < 2 || !owner}
        onPress={() => {
          compare();
        }}
        style={[
          styles.button,
          (props.disabled || props.items.length < 2 || !owner) &&
            styles.disabled,
        ]}
      >
        <Text style={styles.label}>Check exact duplicates</Text>
      </Pressable>
      <Modal visible={open} animationType="slide" onRequestClose={closeCurrent}>
        <ScrollView contentContainerStyle={styles.sheet}>
          <Text accessibilityRole="header" style={styles.title}>
            Compare original bytes
          </Text>
          <Text style={styles.copy}>
            Reads only your selected originals on this device. Matches require
            the same SHA-256 and byte length; visually similar images or
            re-encoded copies do not match. No media, hashes or queries are
            uploaded. Nothing is deleted or edited.
          </Text>
          <Text style={styles.copy}>
            At most 20 originals, 256 MiB per file, 512 MiB total and 60
            seconds. Files that reach a read limit before confirmed end-of-file
            are skipped. Provider availability and changing files can prevent a
            check. A comparison describes the bytes read now; it is not a saved
            integrity baseline.
          </Text>
          {busy && (
            <Text accessibilityLiveRegion="polite" style={styles.copy}>
              Reading selected originals… Cancel stops this request. A provider
              may take time to release blocked IO.
            </Text>
          )}
          {!!error && (
            <Text accessibilityRole="alert" style={styles.copy}>
              {error}
            </Text>
          )}
          {report && (
            <>
              <Text accessibilityLiveRegion="polite" style={styles.copy}>
                {report.checked.length} checked · {report.skipped.length}{' '}
                skipped · {report.groups.length} matching groups ·{' '}
                {report.bytesRead.toLocaleString()} bytes read.
              </Text>
              {!report.groups.length && (
                <Text style={styles.copy}>
                  No matching groups among the fully checked originals. Skipped
                  files were not compared.
                </Text>
              )}
              {report.groups.map((group, index) => (
                <View
                  key={`${group.sha256}-${group.bytes}`}
                  style={styles.card}
                >
                  <Text style={styles.copy}>
                    Group {index + 1}:{' '}
                    {group.ids
                      .map(
                        id => `${names[id] ?? 'Original'} (Library ID ${id})`,
                      )
                      .join(', ')}{' '}
                    · {group.bytes.toLocaleString()} bytes each
                  </Text>
                  <Text style={styles.digest}>SHA-256: {group.sha256}</Text>
                </View>
              ))}
              {report.skipped.map(item => (
                <Text key={item.id} style={styles.copy}>
                  {names[item.id] ?? 'Original'} (Library ID {item.id}):{' '}
                  {
                    {
                      FILE_LIMIT: 'per-file read limit',
                      TOTAL_LIMIT: 'total read limit',
                      UNAVAILABLE: 'unavailable or unreadable',
                      LENGTH_CHANGED: 'provider length changed',
                    }[item.reason]
                  }
                  .
                </Text>
              ))}
            </>
          )}
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={
              busy ? 'Cancel duplicate check' : 'Close duplicate report'
            }
            onPress={closeCurrent}
            style={styles.button}
          >
            <Text style={styles.label}>
              {busy ? 'Cancel check' : 'Close report'}
            </Text>
          </Pressable>
        </ScrollView>
      </Modal>
    </View>
  );
}
const styles = StyleSheet.create({
  sheet: {
    backgroundColor: '#fff',
    paddingHorizontal: 20,
    paddingTop: 32,
    paddingBottom: 48,
    flexGrow: 1,
  },
  title: { color: '#000', fontWeight: '700', fontSize: 24, marginBottom: 10 },
  copy: { color: '#222', fontSize: 14, lineHeight: 21, marginVertical: 8 },
  button: {
    borderWidth: 1,
    borderColor: '#000',
    borderRadius: 12,
    minHeight: 44,
    justifyContent: 'center',
    padding: 12,
    marginVertical: 5,
  },
  label: { color: '#000', fontSize: 14, fontWeight: '600' },
  disabled: { opacity: 0.45 },
  digest: { color: '#333', fontSize: 12, lineHeight: 18 },
  card: {
    borderTopWidth: 1,
    borderTopColor: '#ccc',
    marginVertical: 6,
    paddingVertical: 8,
  },
});
