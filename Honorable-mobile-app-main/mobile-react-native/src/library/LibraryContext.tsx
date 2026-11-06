import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from 'react';
import { AppState, PermissionsAndroid, Platform } from 'react-native';
import {
  honorableNative,
  IndexStatus,
  LibraryItem,
} from '../native/HonorableNative';

type LibraryValue = {
  status?: IndexStatus;
  items: LibraryItem[];
  total: number;
  loading: boolean;
  indexing: boolean;
  error: string;
  requestAccess: () => Promise<void>;
  reindex: () => Promise<void>;
  reload: () => Promise<void>;
  loadMore: () => Promise<void>;
};
const Context = createContext<LibraryValue | undefined>(undefined);
const PAGE = 60;
export function LibraryProvider({ children }: { children: React.ReactNode }) {
  const [status, setStatus] = useState<IndexStatus>();
  const [items, setItems] = useState<LibraryItem[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [indexing, setIndexing] = useState(false);
  const [error, setError] = useState('');
  const active = useRef(false),
    generation = useRef(0);
  const reloading = useRef<number | null>(null);
  const pageRequest = useRef<object | null>(null),
    indexRequest = useRef<object | null>(null);
  const pollRequest = useRef<object | null>(null);
  const permissionRequest = useRef<object | null>(null);
  const current = useCallback(
    (epoch: number) => active.current && generation.current === epoch,
    [],
  );
  const reload = useCallback(async () => {
    if (!active.current) return;
    const epoch = ++generation.current;
    pageRequest.current = null;
    pollRequest.current = null;
    reloading.current = epoch;
    setError('');
    try {
      const next = await honorableNative.getStatus();
      if (!current(epoch)) return;
      setStatus(next);
      if (next.permissionGranted && next.indexedCount > 0) {
        const page = await honorableNative.getLibrary(PAGE, 0);
        if (!current(epoch)) return;
        setTotal(page.total);
        setItems(page.items);
      } else {
        setItems([]);
        setTotal(0);
      }
    } catch (reason) {
      if (current(epoch))
        setError(
          reason instanceof Error ? reason.message : 'Library unavailable',
        );
    } finally {
      if (current(epoch)) {
        reloading.current = null;
        setLoading(false);
      }
    }
  }, [current]);
  const deactivate = useCallback(() => {
    active.current = false;
    generation.current++;
    indexRequest.current = null;
    permissionRequest.current = null;
  }, []);
  useEffect(() => {
    active.current = true;
    reload();
    return deactivate;
  }, [deactivate, reload]);
  useEffect(() => {
    let previous = AppState.currentState;
    const subscription = AppState.addEventListener('change', next => {
      const returning =
        next === 'active' &&
        (previous === 'background' || previous === 'inactive');
      previous = next;
      if (returning) reload();
    });
    return () => subscription.remove();
  }, [reload]);
  useEffect(() => {
    if (!indexing) return;
    const timer = setInterval(() => {
      if (
        !active.current ||
        reloading.current !== null ||
        !indexRequest.current ||
        pollRequest.current
      )
        return;
      const epoch = generation.current,
        job = indexRequest.current,
        request = {};
      pollRequest.current = request;
      honorableNative
        .getStatus()
        .then(next => {
          if (
            current(epoch) &&
            indexRequest.current === job &&
            reloading.current === null &&
            pollRequest.current === request
          ) {
            setStatus(next);
            if (!next.permissionGranted) {
              generation.current++;
              pageRequest.current = null;
              setItems([]);
              setTotal(0);
            }
          }
        })
        .catch(() => undefined)
        .finally(() => {
          if (pollRequest.current === request) pollRequest.current = null;
        });
    }, 750);
    return () => clearInterval(timer);
  }, [indexing, current]);
  const requestAccess = useCallback(async () => {
    if (!active.current || permissionRequest.current) return;
    const request = {};
    permissionRequest.current = request;
    // A foreground reload may advance the library generation while the OS dialog is open.
    const owned = () => active.current && permissionRequest.current === request;
    try {
      if (Platform.OS === 'android') {
        const permissions =
          Number(Platform.Version) >= 33
            ? [
                PermissionsAndroid.PERMISSIONS.READ_MEDIA_IMAGES,
                PermissionsAndroid.PERMISSIONS.READ_MEDIA_VIDEO,
              ]
            : [PermissionsAndroid.PERMISSIONS.READ_EXTERNAL_STORAGE];
        await PermissionsAndroid.requestMultiple(permissions);
      }
      if (owned()) await reload();
    } catch (reason) {
      if (owned())
        setError(
          reason instanceof Error
            ? reason.message
            : 'Photo access could not be requested',
        );
    } finally {
      if (permissionRequest.current === request)
        permissionRequest.current = null;
    }
  }, [reload]);
  const reindex = useCallback(async () => {
    if (!active.current || indexRequest.current) return;
    const job = {};
    indexRequest.current = job;
    const epoch = ++generation.current;
    reloading.current = null;
    pageRequest.current = null;
    pollRequest.current = null;
    setIndexing(true);
    setError('');
    try {
      await honorableNative.refreshIndex();
      if (active.current && indexRequest.current === job) await reload();
    } catch (reason) {
      if (current(epoch) && indexRequest.current === job)
        setError(reason instanceof Error ? reason.message : 'Indexing failed');
    } finally {
      if (active.current && indexRequest.current === job) {
        indexRequest.current = null;
        setIndexing(false);
      }
    }
  }, [current, reload]);
  const loadMore = useCallback(async () => {
    if (
      !active.current ||
      reloading.current !== null ||
      indexRequest.current ||
      pageRequest.current ||
      !status?.permissionGranted ||
      items.length >= total
    )
      return;
    const epoch = generation.current,
      request = {};
    pageRequest.current = request;
    setError('');
    try {
      const page = await honorableNative.getLibrary(PAGE, items.length);
      if (!current(epoch)) return;
      setTotal(page.total);
      setItems(previous =>
        current(epoch)
          ? previous.concat(
              page.items.filter(
                item => !previous.some(old => old.mediaId === item.mediaId),
              ),
            )
          : previous,
      );
    } catch (reason) {
      if (current(epoch))
        setError(
          reason instanceof Error
            ? reason.message
            : 'More library items could not be loaded',
        );
    } finally {
      if (pageRequest.current === request) pageRequest.current = null;
    }
  }, [current, items.length, status?.permissionGranted, total]);
  return (
    <Context.Provider
      value={{
        status,
        items,
        total,
        loading,
        indexing,
        error,
        requestAccess,
        reindex,
        reload,
        loadMore,
      }}
    >
      {children}
    </Context.Provider>
  );
}
export function useLibrary() {
  const value = useContext(Context);
  if (!value) throw new Error('useLibrary must be used inside LibraryProvider');
  return value;
}
