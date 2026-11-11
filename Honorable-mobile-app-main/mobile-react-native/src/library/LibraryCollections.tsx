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
  type LibraryCollection,
  type LibraryCollectionItem,
  type LibraryCollections,
} from '../native/HonorableNative';
import type { MemoryEntry } from './memoryRows';

export function collectionEntries(
  entries: readonly MemoryEntry[],
  collection?: LibraryCollection,
): MemoryEntry[] {
  if (!collection) return [...entries];
  const bindings = new Map(collection.items.map(item => [item.id, item]));
  return entries.filter(({ media }) => {
    const chosen = bindings.get(media.id);
    return chosen?.uri === media.mediaUri && chosen?.kind === media.type;
  });
}

type Props = {
  selectedItems: LibraryCollectionItem[];
  disabled: boolean;
  canStart?: () => boolean;
  onOpen: (collection: LibraryCollection) => void;
  onDelete: (id: string) => void;
  onBusy: (busy: boolean) => void;
  onReset: () => void;
};

/** User-opened private metadata shelf. No automatic clipboard, media or network access. */
export function LibraryCollectionsPanel(props: Props) {
  const auth = useMemoryPass();
  const ownerKey = auth.signedIn ? auth.account?.accountId ?? '' : '';
  const ownerRef = useRef(ownerKey);
  ownerRef.current = ownerKey;
  const callbacks = useRef(props);
  callbacks.current = props;
  const [open, setOpen] = useState(false);
  const [data, setData] = useState<LibraryCollections>();
  const shelf = useRef<LibraryCollections | undefined>(undefined);
  const [name, setName] = useState('');
  const [deleting, setDeleting] = useState<LibraryCollection>();
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const request = useRef(0),
    pending = useRef(false),
    active = useRef(true);
  const close = useCallback(() => {
    request.current++;
    if (pending.current) callbacks.current.onBusy(false);
    pending.current = false;
    shelf.current = undefined;
    setBusy(false);
    setOpen(false);
    setData(undefined);
    setName('');
    setDeleting(undefined);
    setError('');
    setNotice('');
  }, []);
  const deactivate = useCallback(() => {
    active.current = false;
    request.current++;
    if (pending.current) callbacks.current.onBusy(false);
    pending.current = false;
    shelf.current = undefined;
  }, []);
  useEffect(() => {
    close();
    callbacks.current.onReset();
  }, [ownerKey, close]);
  useEffect(() => {
    active.current = true;
    const listener = AppState.addEventListener('change', state => {
      active.current = state === 'active';
      if (!active.current) close();
    });
    return () => {
      deactivate();
      listener.remove();
    };
  }, [close, deactivate]);
  const run = async (action: 'list' | 'create' | 'delete') => {
    if (
      !active.current ||
      pending.current ||
      callbacks.current.disabled ||
      callbacks.current.canStart?.() === false ||
      !ownerRef.current
    )
      return;
    if (
      action !== 'list' &&
      (!data || shelf.current !== data || ownerRef.current !== ownerKey)
    )
      return;
    const epoch = ++request.current,
      owner = ownerRef.current,
      remove = deleting;
    const chosen = callbacks.current.selectedItems.map(item => ({ ...item }));
    if (action === 'create' && !chosen.length) return;
    if (action === 'delete' && !remove) return;
    pending.current = true;
    setBusy(true);
    callbacks.current.onBusy(true);
    setError('');
    setNotice('');
    if (action === 'list') {
      setOpen(true);
      setData(undefined);
      shelf.current = undefined;
    }
    const current = () =>
      active.current && request.current === epoch && ownerRef.current === owner;
    try {
      const next = await honorableNative.libraryCollections(
        action,
        data?.owner ?? '',
        remove?.id ?? '',
        name.trim(),
        action === 'create' ? chosen : [],
      );
      if (!current()) return;
      if (data && action !== 'list' && next.owner !== data.owner)
        throw Error('Account changed. Reopen collections.');
      shelf.current = next;
      setData(next);
      if (action === 'create') {
        setName('');
        setNotice('Selection saved privately on this device.');
      }
      if (action === 'delete') {
        callbacks.current.onDelete(remove!.id);
        setDeleting(undefined);
        setNotice('Collection removed. Original files were kept.');
      }
    } catch (reason) {
      if (current())
        setError(
          reason instanceof Error
            ? reason.message
            : 'Collections are unavailable. Try again.',
        );
    } finally {
      if (current()) {
        pending.current = false;
        setBusy(false);
        callbacks.current.onBusy(false);
      }
    }
  };
  const button = (label: string, action: () => void, disabled = false) => (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ disabled }}
      disabled={disabled}
      onPress={action}
      style={[styles.button, disabled && styles.disabled]}
    >
      <Text style={styles.buttonText}>{label}</Text>
    </Pressable>
  );
  return (
    <View>
      {button(
        'Private collections',
        () => {
          run('list');
        },
        props.disabled || !ownerKey,
      )}
      <Modal
        visible={open}
        onRequestClose={() => {
          if (!pending.current) close();
        }}
        animationType="slide"
      >
        <ScrollView
          contentContainerStyle={styles.sheet}
          keyboardShouldPersistTaps="handled"
        >
          <Text accessibilityRole="header" style={styles.title}>
            Private collections
          </Text>
          <Text style={styles.copy}>
            Keep up to 20 named selections of 1–20 originals on this device,
            separate for each account. Files are referenced, never copied or
            uploaded. Removed files and reused IDs are never replaced by
            different media. Collection views show only loaded matching
            originals, in Library date order.
          </Text>
          {busy && (
            <Text accessibilityLiveRegion="polite" style={styles.copy}>
              Working…
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
                {props.selectedItems.length} originals currently selected.
                Select originals in Library before saving a new collection.
              </Text>
              <TextInput
                accessibilityLabel="Collection name"
                placeholder="Collection name"
                value={name}
                onChangeText={setName}
                editable={!busy}
                maxLength={80}
                style={styles.input}
              />
              {button(
                'Save selected originals as collection',
                () => {
                  run('create');
                },
                busy || !props.selectedItems.length || !name.trim(),
              )}
              {!data.collections.length && (
                <Text style={styles.copy}>No collections saved yet.</Text>
              )}
              {deleting ? (
                <View style={styles.card}>
                  <Text style={styles.copy}>
                    Remove “{deleting.name}” and its {deleting.items.length}{' '}
                    saved references? Original files remain. This removal cannot
                    be undone.
                  </Text>
                  {button(
                    'Keep collection',
                    () => setDeleting(undefined),
                    busy,
                  )}
                  {button(
                    'Remove collection references',
                    () => {
                      run('delete');
                    },
                    busy,
                  )}
                </View>
              ) : (
                data.collections.map(collection => (
                  <View key={collection.id} style={styles.card}>
                    <Text style={styles.copy}>
                      {collection.name} · {collection.items.length} originals
                    </Text>
                    {button(
                      `Open collection ${collection.name}`,
                      () => {
                        if (
                          pending.current ||
                          !active.current ||
                          ownerRef.current !== ownerKey ||
                          shelf.current !== data
                        )
                          return;
                        callbacks.current.onOpen(collection);
                        close();
                      },
                      busy,
                    )}
                    {button(
                      `Remove collection ${collection.name}`,
                      () => {
                        if (
                          active.current &&
                          ownerRef.current === ownerKey &&
                          shelf.current === data
                        )
                          setDeleting(collection);
                      },
                      busy,
                    )}
                  </View>
                ))
              )}
            </>
          )}
          {!data &&
            !busy &&
            button('Retry collections', () => {
              run('list');
            })}
          {button('Close collections', close, busy)}
        </ScrollView>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  sheet: {
    paddingHorizontal: 20,
    paddingTop: 32,
    paddingBottom: 48,
    backgroundColor: '#fff',
    flexGrow: 1,
  },
  title: { color: '#000', fontSize: 24, fontWeight: '700', marginBottom: 12 },
  copy: { color: '#222', fontSize: 14, lineHeight: 21, marginVertical: 8 },
  button: {
    minHeight: 44,
    borderWidth: 1,
    borderColor: '#000',
    borderRadius: 12,
    padding: 12,
    marginVertical: 5,
    justifyContent: 'center',
  },
  buttonText: { color: '#000', fontSize: 14, fontWeight: '600' },
  disabled: { opacity: 0.45 },
  input: {
    minHeight: 44,
    borderWidth: 1,
    borderColor: '#777',
    borderRadius: 8,
    padding: 10,
    color: '#000',
    marginVertical: 8,
  },
  card: { borderTopWidth: 1, borderTopColor: '#ddd', paddingVertical: 10 },
});
