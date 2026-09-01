import React, {useMemo, useState} from 'react';
import {Pressable, ScrollView, StyleSheet, View} from 'react-native';
import {SafeAreaView} from 'react-native-safe-area-context';
import {Button} from '@/components/ui/button';
import {Text} from '@/components/ui/text';
import {HonorableSearchBar} from '../components/HonorableSearchBar';
import {MediaGrid, MediaItem} from '../components/MediaGrid';
import {MediaViewer} from '../components/MediaViewer';
import {colors} from '../design-system';
import {useLibrary} from '../library/LibraryContext';

const filters = ['All', 'Photos', 'Videos', 'Screenshots'] as const;
type Filter = (typeof filters)[number];

function duration(ms?: number) {
  if (ms === undefined) return undefined;
  const seconds = Math.floor(ms / 1000);
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
}

function monthLabel(timestamp: number) {
  return new Date(timestamp).toLocaleDateString(undefined, {month: 'long', year: 'numeric'});
}

export function MemoriesScreen() {
  const {items, total, loadMore} = useLibrary();
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState<Filter>('All');
  const [selected, setSelected] = useState<MediaItem>();

  const sections = useMemo(() => {
    const cleanQuery = query.trim().toLowerCase();
    const visible = items.filter(item => {
      const screenshot = item.displayName.toLowerCase().includes('screenshot');
      const kindMatches = filter === 'All' ||
        (filter === 'Photos' && item.mediaType === 'IMAGE') ||
        (filter === 'Videos' && item.mediaType === 'VIDEO') ||
        (filter === 'Screenshots' && screenshot);
      return kindMatches && (!cleanQuery || item.displayName.toLowerCase().includes(cleanQuery));
    });
    const grouped = new Map<string, MediaItem[]>();
    visible.forEach(item => {
      const label = monthLabel(item.capturedAt);
      const media: MediaItem = {
        id: String(item.mediaId),
        uri: item.thumbnailUri ?? item.mediaUri,
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
      <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={styles.content}>
        <View style={styles.header}>
          <View>
            <Text className="text-[12px] font-semibold tracking-[2px] text-muted-foreground">MEMORIES</Text>
            <Text accessibilityRole="header" className="mt-1 text-[28px] font-semibold">Your library</Text>
          </View>
          <Text variant="muted" className="text-[12px]">{total.toLocaleString()} items</Text>
        </View>
        <HonorableSearchBar compact value={query} onChangeText={setQuery} onSubmit={() => undefined}/>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.filters}>
          {filters.map(item => (
            <Button key={item} size="sm" variant={item === filter ? 'default' : 'ghost'} className="rounded-full" onPress={() => setFilter(item)}>
              <Text>{item}</Text>
            </Button>
          ))}
        </ScrollView>
        {sections.length === 0 ? (
          <View style={styles.empty}>
            <Text className="text-[17px] font-semibold">No memories found</Text>
            <Text variant="muted" className="mt-1 text-center">Try another filter or filename.</Text>
          </View>
        ) : sections.map(([label, media]) => (
          <View key={label} style={styles.section}>
            <Text className="mb-3 px-4 text-[16px] font-semibold">{label}</Text>
            <MediaGrid items={media} onPress={setSelected}/>
          </View>
        ))}
        {items.length < total && (
          <Pressable accessibilityRole="button" onPress={() => void loadMore()} style={styles.more}>
            <Text className="font-semibold">Load more</Text>
          </Pressable>
        )}
      </ScrollView>
      <MediaViewer item={selected} onClose={() => setSelected(undefined)}/>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: {flex: 1, backgroundColor: colors.background},
  content: {paddingBottom: 90},
  header: {paddingHorizontal: 16, paddingTop: 12, paddingBottom: 14, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center'},
  filters: {paddingHorizontal: 12, paddingVertical: 12, gap: 6},
  section: {marginBottom: 20},
  empty: {margin: 16, padding: 28, alignItems: 'center', borderRadius: 22, backgroundColor: colors.surface},
  more: {height: 48, marginHorizontal: 16, marginTop: 8, borderRadius: 24, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.surface},
});
