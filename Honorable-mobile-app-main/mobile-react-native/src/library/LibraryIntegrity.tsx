import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  AppState,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { useMemoryPass } from '../passes/MemoryPassContext';
import {
  honorableNative,
  type LibraryCollectionItem,
  type LibraryIntegritySnapshot,
} from '../native/HonorableNative';

let serial = 0;
type Action = 'list' | 'create' | 'verify' | 'delete';
type Baseline = LibraryIntegritySnapshot['baselines'][number];
type Props = {
  items: LibraryCollectionItem[];
  disabled: boolean;
  canStart: () => boolean;
  onBusy: (value: boolean) => void;
};
export function LibraryIntegrityPanel(props: Props) {
  const auth = useMemoryPass();
  const owner = auth.signedIn ? auth.account?.accountId ?? '' : '';
  const ownerRef = useRef(owner);
  ownerRef.current = owner;
  const callbacks = useRef(props);
  callbacks.current = props;
  const job = useRef<{ id: string; owner: string } | undefined>(undefined);
  const shelf = useRef<LibraryIntegritySnapshot | undefined>(undefined);
  const view = useRef<object | undefined>(undefined);
  const active = useRef(true);
  const [viewToken, setViewToken] = useState<object>();
  const [open, setOpen] = useState(false),
    [busy, setBusy] = useState(false);
  const [data, setData] = useState<LibraryIntegritySnapshot>();
  const [verification, setVerification] =
    useState<LibraryIntegritySnapshot['verification']>();
  const [name, setName] = useState(''),
    [error, setError] = useState(''),
    [notice, setNotice] = useState('');
  const [deleting, setDeleting] = useState<Baseline>();
  const stop = useCallback(() => {
    const current = job.current;
    job.current = undefined;
    if (current) {
      try {
        honorableNative.cancelLibraryDuplicateCheck(current.id);
      } catch {
        /* Native transport may have closed. */
      } finally {
        callbacks.current.onBusy(false);
      }
    }
  }, []);
  const close = useCallback(() => {
    view.current = undefined;
    shelf.current = undefined;
    stop();
    setViewToken(undefined);
    setOpen(false);
    setBusy(false);
    setData(undefined);
    setVerification(undefined);
    setDeleting(undefined);
    setName('');
    setError('');
    setNotice('');
  }, [stop]);
  useEffect(() => {
    close();
  }, [owner, close]);
  useEffect(() => {
    active.current = true;
    const listener = AppState.addEventListener('change', state => {
      active.current = state === 'active';
      if (!active.current) close();
    });
    return () => {
      active.current = false;
      stop();
      listener.remove();
    };
  }, [close, stop]);
  const currentShelf = () =>
    active.current &&
    !job.current &&
    ownerRef.current === owner &&
    shelf.current === data &&
    !!data;
  const run = async (action: Action, id = '') => {
    const latest = callbacks.current;
    if (
      !active.current ||
      job.current ||
      latest.disabled ||
      !latest.canStart() ||
      !ownerRef.current
    )
      return;
    if (action !== 'list' && !currentShelf()) return;
    const chosen =
      action === 'create' ? latest.items.map(item => ({ ...item })) : [];
    if (action === 'create' && (!chosen.length || !name.trim())) return;
    const request = {
      id: `noble-integrity-${Date.now()}-${++serial}`,
      owner: ownerRef.current,
    };
    job.current = request;
    view.current = request;
    setViewToken(request);
    latest.onBusy(true);
    setOpen(true);
    setBusy(true);
    setError('');
    setNotice('');
    setVerification(undefined);
    if (action === 'list') {
      shelf.current = undefined;
      setData(undefined);
    }
    const current = () =>
      active.current &&
      job.current === request &&
      ownerRef.current === request.owner;
    try {
      const next = await honorableNative.libraryIntegrity(
        action,
        data?.owner ?? '',
        id,
        name.trim(),
        chosen,
        request.id,
      );
      if (!current()) return;
      if (action !== 'list' && next.owner !== data?.owner)
        throw Error('Account changed. Reopen integrity baselines.');
      shelf.current = next;
      setData(next);
      setVerification(next.verification);
      if (action === 'create') {
        setName('');
        setNotice(
          'Recorded baseline kept privately. Verification never replaces its recorded hashes.',
        );
      }
      if (action === 'delete') {
        setDeleting(undefined);
        setNotice('Baseline references removed. Original files were kept.');
      }
    } catch (reason) {
      if (current())
        setError(
          reason instanceof Error
            ? reason.message
            : 'Integrity work could not finish. Try again.',
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
  const button = (label: string, action: () => void, disabled = false) => (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      disabled={disabled}
      accessibilityState={{ disabled }}
      onPress={action}
      style={[styles.button, disabled && styles.disabled]}
    >
      <Text style={styles.label}>{label}</Text>
    </Pressable>
  );
  const statuses = {
    MATCH: 'Matches recorded bytes',
    CHANGED: 'Bytes changed',
    MISSING: 'Saved identity missing',
    UNAVAILABLE: 'Not checked: unreadable',
    FILE_LIMIT: 'Not checked: file read limit',
    TOTAL_LIMIT: 'Not checked: total read limit',
    LENGTH_CHANGED: 'Not checked: provider length changed',
  };
  return (
    <View>
      {button(
        'Private integrity baselines',
        () => {
          run('list');
        },
        props.disabled || !owner,
      )}
      <Modal visible={open} animationType="slide" onRequestClose={closeCurrent}>
        <ScrollView
          contentContainerStyle={styles.sheet}
          keyboardShouldPersistTaps="handled"
        >
          <Text accessibilityRole="header" style={styles.title}>
            Original integrity
          </Text>
          <Text style={styles.copy}>
            Record selected originals now, then verify later for changed bytes,
            missing identities or unreadable files. This is a checksum record,
            not a backup or a guarantee that files cannot change. No media or
            hashes are uploaded. Original files are never edited or deleted.
          </Text>
          <Text style={styles.copy}>
            Keeps up to 10 named baselines of 1–20 originals, with filenames,
            source references, byte lengths and SHA-256 hashes encrypted
            privately for this account on this device. Recording requires every
            selected original to finish reading. Verification keeps the recorded
            baseline unchanged; missing/unreadable files are never reported as
            matching.
          </Text>
          <Text style={styles.copy}>
            Reads use the existing 256 MiB/file, 512 MiB total and 60-second
            checked limits. Files reaching a limit before confirmed EOF are not
            checked. Providers may delay cancellation; this is not a locked file
            snapshot. Cancel cannot roll back metadata already committed.
          </Text>
          {busy && (
            <Text accessibilityLiveRegion="polite" style={styles.copy}>
              Working locally…
            </Text>
          )}
          {!!error && (
            <Text accessibilityRole="alert" style={styles.copy}>
              {error}
            </Text>
          )}
          {!!notice && (
            <Text accessibilityLiveRegion="polite" style={styles.copy}>
              {notice}
            </Text>
          )}
          {data && (
            <>
              <Text style={styles.copy}>
                {props.items.length} originals selected. Recording reads their
                current bytes and saves a new baseline after all checks finish.
              </Text>
              <TextInput
                accessibilityLabel="Integrity baseline name"
                placeholder="Baseline name"
                value={name}
                onChangeText={setName}
                editable={!busy}
                maxLength={80}
                style={styles.input}
              />
              {button(
                'Record selected original fingerprints',
                () => {
                  run('create');
                },
                busy || !props.items.length || !name.trim(),
              )}
              {!data.baselines.length && (
                <Text style={styles.copy}>No baselines recorded yet.</Text>
              )}
              {deleting ? (
                <View style={styles.card}>
                  <Text style={styles.copy}>
                    Remove “{deleting.name}” and its {deleting.count} recorded
                    fingerprints? Originals remain. This cannot be undone.
                  </Text>
                  {button(
                    'Keep integrity baseline',
                    () => {
                      if (currentShelf()) setDeleting(undefined);
                    },
                    busy,
                  )}
                  {button(
                    'Remove recorded baseline',
                    () => {
                      run('delete', deleting.id);
                    },
                    busy,
                  )}
                </View>
              ) : (
                data.baselines.map(baseline => (
                  <View key={baseline.id} style={styles.card}>
                    <Text style={styles.copy}>
                      {baseline.name} · {baseline.count} originals · recorded{' '}
                      {new Date(baseline.recordedAt).toLocaleString()}
                    </Text>
                    {button(
                      `Verify baseline ${baseline.name}`,
                      () => {
                        run('verify', baseline.id);
                      },
                      busy,
                    )}
                    {button(
                      `Remove baseline ${baseline.name}`,
                      () => {
                        if (currentShelf()) setDeleting(baseline);
                      },
                      busy,
                    )}
                  </View>
                ))
              )}
            </>
          )}
          {verification && (
            <View style={styles.card}>
              <Text accessibilityRole="header" style={styles.heading}>
                Verification ·{' '}
                {new Date(verification.checkedAt).toLocaleString()}
              </Text>
              <Text style={styles.copy}>
                Recorded fingerprints were kept. Results describe bytes
                delivered during this check.
              </Text>
              {verification.items.map(item => (
                <Text key={item.id} style={styles.copy}>
                  {item.label || 'Original'} (Library ID {item.id}):{' '}
                  {statuses[item.status]}. Recorded{' '}
                  {item.previousBytes.toLocaleString()} bytes
                  {item.currentBytes !== undefined
                    ? `; read ${item.currentBytes.toLocaleString()} bytes`
                    : ''}
                  .
                </Text>
              ))}
            </View>
          )}
          {!data &&
            !busy &&
            button('Retry integrity shelf', () => {
              run('list');
            })}
          {button(
            busy ? 'Cancel integrity work' : 'Close integrity shelf',
            closeCurrent,
          )}
        </ScrollView>
      </Modal>
    </View>
  );
}
const styles = StyleSheet.create({
  sheet: {
    backgroundColor: '#fff',
    flexGrow: 1,
    paddingHorizontal: 20,
    paddingTop: 32,
    paddingBottom: 48,
  },
  title: { color: '#000', fontSize: 24, fontWeight: '700', marginBottom: 10 },
  heading: { color: '#000', fontSize: 18, fontWeight: '600' },
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
  input: {
    color: '#000',
    minHeight: 44,
    borderWidth: 1,
    borderColor: '#777',
    borderRadius: 8,
    padding: 10,
    marginVertical: 8,
  },
  card: {
    borderTopWidth: 1,
    borderTopColor: '#ddd',
    marginVertical: 8,
    paddingVertical: 8,
  },
});
