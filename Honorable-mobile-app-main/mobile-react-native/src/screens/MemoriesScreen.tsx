import React, { useMemo, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
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
  const { items, total, loadMore } = useLibrary();
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState<Filter>('All');
  const [selected, setSelected] = useState<MediaItem>();

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
        <View style={{ height: 16 }} />
        {sections.length === 0 ? (
          <View style={styles.empty}>
            <Text className="text-[17px] font-semibold">No memories found</Text>
            <Text variant="muted" className="mt-1 text-center">
              Try another filter or filename.
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
            onPress={() => void loadMore()}
            style={styles.more}
          >
            <Text className="font-semibold">Load more</Text>
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
  header: {
    paddingHorizontal: 16,
    paddingTop: 24,
    paddingBottom: 24,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  filters: { paddingHorizontal: 12, paddingVertical: 12, gap: 6 },
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
