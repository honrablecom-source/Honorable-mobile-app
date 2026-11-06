import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Text } from '@/components/ui/text';
import { HonorableSearchBar } from '../components/HonorableSearchBar';
import { MediaGrid, MediaItem } from '../components/MediaGrid';
import { MediaViewer } from '../components/MediaViewer';
import { colors, WorkspaceHeader } from '../design-system';
import { useLibrary } from '../library/LibraryContext';
import { FilterTabs } from '../design-system/CreativeChrome';

const filters = ['All', 'Photos', 'Videos', 'Screenshots'] as const;
type Filter = (typeof filters)[number];

function duration(ms?: number) {
  if (ms === undefined) return undefined;
  const seconds = Math.floor(ms / 1000);
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
}

function monthLabel(timestamp: number) {
  return new Date(timestamp).toLocaleDateString(undefined, {
    month: 'long',
    year: 'numeric',
  });
}

export function MemoriesScreen() {
  const { items, total, loadMore, reload, loading, indexing, error } =
    useLibrary();
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState<Filter>('All');
  const [selected, setSelected] = useState<MediaItem>();
  const [operation, setOperation] = useState<'refresh' | 'more' | null>(null);
  const [localError, setLocalError] = useState('');
  const pending = useRef(false),
    active = useRef(true);
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
    };
  }, []);
  const readLibrary = async (kind: 'refresh' | 'more') => {
    if (
      !active.current ||
      pending.current ||
      (kind === 'more' && (loading || indexing || items.length >= total))
    )
      return;
    pending.current = true;
    setOperation(kind);
    setLocalError('');
    if (kind === 'refresh') setSelected(undefined);
    try {
      if (kind === 'refresh') await reload();
      else await loadMore();
    } catch (reason) {
      if (active.current)
        setLocalError(
          reason instanceof Error
            ? reason.message
            : 'The library could not load. Try again.',
        );
    } finally {
      pending.current = false;
      if (active.current) setOperation(null);
    }
  };
  const libraryError = localError || error;
  const smartCollections = [
    { label: 'All photos', query: '', filter: 'Photos' as Filter },
    { label: 'All videos', query: '', filter: 'Videos' as Filter },
    {
      label: 'Screenshots',
      query: 'screenshot',
      filter: 'Screenshots' as Filter,
    },
    { label: 'Camera photos', query: 'IMG_', filter: 'Photos' as Filter },
    { label: 'Camera videos', query: 'VID_', filter: 'Videos' as Filter },
    { label: 'Scanned files', query: 'scan', filter: 'All' as Filter },
    { label: 'Burst photos', query: 'burst', filter: 'Photos' as Filter },
    { label: 'Panoramas', query: 'pano', filter: 'Photos' as Filter },
    { label: 'Self portraits', query: 'selfie', filter: 'Photos' as Filter },
    {
      label: 'Screen recordings',
      query: 'screenrecord',
      filter: 'Videos' as Filter,
    },
    { label: 'Downloads', query: 'download', filter: 'All' as Filter },
    { label: 'Exported media', query: 'honorable', filter: 'All' as Filter },
  ];

  const sections = useMemo(() => {
    const cleanQuery = query.trim().toLowerCase();
    const visible = items.filter(item => {
      const screenshot = item.displayName.toLowerCase().includes('screenshot');
      const kindMatches =
        filter === 'All' ||
        (filter === 'Photos' && item.mediaType === 'IMAGE') ||
        (filter === 'Videos' && item.mediaType === 'VIDEO') ||
        (filter === 'Screenshots' && screenshot);
      return (
        kindMatches &&
        (!cleanQuery || item.displayName.toLowerCase().includes(cleanQuery))
      );
    });
    const grouped = new Map<string, MediaItem[]>();
    visible.forEach(item => {
      const label = monthLabel(item.capturedAt);
      const media: MediaItem = {
        id: String(item.mediaId),
        uri:
          item.thumbnailUri ??
          (item.mediaType === 'IMAGE' ? item.mediaUri : undefined),
        mediaUri: item.mediaUri,
        type: item.mediaType,
        duration: duration(item.durationMs),
        capturedAt: item.capturedAt,
        displayName: item.displayName,
      };
      grouped.set(label, [...(grouped.get(label) ?? []), media]);
    });
    return [...grouped.entries()];
  }, [filter, items, query]);

  return (
    <SafeAreaView edges={['top']} style={styles.safe}>
      <ScrollView
        keyboardShouldPersistTaps="handled"
        contentContainerStyle={styles.content}
        refreshControl={
          <RefreshControl
            accessibilityLabel="Refresh library"
            refreshing={operation === 'refresh'}
            onRefresh={() => readLibrary('refresh')}
            tintColor={colors.textPrimary}
            colors={[colors.textPrimary]}
          />
        }
      >
        <WorkspaceHeader
          title="Library"
          section={`${total.toLocaleString()} items on your device`}
        />

        <HonorableSearchBar
          compact
          value={query}
          onChangeText={setQuery}
          onSubmit={() => undefined}
        />
        <View style={{ height: 8 }} />
        <FilterTabs items={filters} value={filter} onChange={setFilter} />
        <ScrollView
          horizontal
          keyboardShouldPersistTaps="handled"
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={styles.collections}
        >
          {smartCollections.map(collection => (
            <Pressable
              key={collection.label}
              accessibilityRole="button"
              accessibilityLabel={`Show ${collection.label}`}
              onPress={() => {
                setFilter(collection.filter);
                setQuery(collection.query);
              }}
              style={styles.collectionChip}
            >
              <Text className="text-[11px] font-semibold">
                {collection.label}
              </Text>
            </Pressable>
          ))}
        </ScrollView>
        <View style={{ height: 16 }} />
        {!loading && (
          <Text style={styles.loadedNotice}>
            {items.length} of {total} library items loaded. Filename filters
            apply to loaded items.
          </Text>
        )}
        {indexing && (
          <Text accessibilityLiveRegion="polite" style={styles.loadedNotice}>
            Indexing is running on this device. More items can load when it
            finishes.
          </Text>
        )}
        {!!libraryError && (
          <View style={styles.recovery}>
            <Text accessibilityRole="alert" style={styles.error}>
              {libraryError}
            </Text>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="Retry loading library"
              disabled={operation !== null || loading}
              accessibilityState={{
                disabled: operation !== null || loading,
                busy: operation === 'refresh',
              }}
              onPress={() => readLibrary('refresh')}
              style={styles.more}
            >
              <Text className="font-semibold">Retry library</Text>
            </Pressable>
          </View>
        )}
        {loading ? (
          <ActivityIndicator
            accessibilityLabel="Loading library"
            color={colors.textPrimary}
          />
        ) : sections.length === 0 && !libraryError ? (
          <View style={styles.empty}>
            <Text className="text-[17px] font-semibold">
              No loaded memories match
            </Text>
            <Text variant="muted" className="mt-1 text-center">
              Try another filter or filename, or load more library items.
            </Text>
          </View>
        ) : (
          sections.map(([label, media]) => (
            <View key={label} style={styles.section}>
              <Text className="mb-4 px-4 text-[13px] font-semibold">
                {label}
              </Text>
              <MediaGrid edgeToEdge items={media} onPress={setSelected} />
            </View>
          ))
        )}
        {items.length < total && (
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Load more memories"
            accessibilityState={{
              disabled: operation !== null || loading || indexing,
              busy: operation === 'more',
            }}
            disabled={operation !== null || loading || indexing}
            onPress={() => readLibrary('more')}
            style={styles.more}
          >
            {operation === 'more' ? (
              <ActivityIndicator
                accessibilityLabel="Loading more memories"
                color={colors.textPrimary}
              />
            ) : (
              <Text className="font-semibold">Load more</Text>
            )}
          </Pressable>
        )}
      </ScrollView>
      <MediaViewer item={selected} onClose={() => setSelected(undefined)} />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  content: { paddingBottom: 24 },
  loadedNotice: {
    marginHorizontal: 16,
    marginBottom: 12,
    fontSize: 12,
    lineHeight: 18,
    color: colors.textSecondary,
  },
  recovery: {
    marginHorizontal: 16,
    marginBottom: 16,
    padding: 12,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 12,
    backgroundColor: colors.surfaceRaised,
  },
  error: { color: colors.textPrimary, fontSize: 14, lineHeight: 20 },
  header: {
    paddingHorizontal: 16,
    paddingTop: 24,
    paddingBottom: 24,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  filters: { paddingHorizontal: 12, paddingVertical: 12, gap: 6 },
  collections: { gap: 8, paddingHorizontal: 16, paddingVertical: 10 },
  collectionChip: {
    minHeight: 40,
    paddingHorizontal: 12,
    justifyContent: 'center',
    borderRadius: 8,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surfaceRaised,
  },
  section: { marginBottom: 20 },
  empty: {
    margin: 16,
    padding: 24,
    alignItems: 'center',
    borderRadius: 18,
    backgroundColor: colors.surfaceRaised,
    borderWidth: 0,
    borderColor: colors.border,
  },
  more: {
    height: 48,
    marginHorizontal: 16,
    marginTop: 8,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colors.surfaceRaised,
    borderWidth: 0,
    borderColor: colors.border,
  },
});
