import React, { useEffect, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Keyboard,
  Pressable,
  ScrollView,
  StyleSheet,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Clock3, X } from 'lucide-react-native';
import { useNavigation } from '@react-navigation/native';
import type { BottomTabNavigationProp } from '@react-navigation/bottom-tabs';
import { Button } from '@/components/ui/button';
import { Text } from '@/components/ui/text';
import { HonorableSearchBar } from '../components/HonorableSearchBar';
import { MediaGrid, MediaItem } from '../components/MediaGrid';
import { MediaViewer } from '../components/MediaViewer';
import {
  SearchModeControl,
  SearchModeSheet,
} from '../components/SearchModeSheet';
import { colors } from '../design-system/tokens';
import { honorableNative, SearchResponse } from '../native/HonorableNative';
import { useLibrary } from '../library/LibraryContext';
import { useSearchMode } from '../search/SearchModeContext';
import type { MainTabParamList } from '../navigation/types';
import { useMemoryPass } from '../passes/MemoryPassContext';
const filters = ['All', 'Photos', 'Videos', 'Screenshots'] as const;
type Filter = (typeof filters)[number];
function duration(ms?: number) {
  if (ms === undefined) return undefined;
  const seconds = Math.floor(ms / 1000);
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
}
export function HomeScreen() {
  const navigation = useNavigation<BottomTabNavigationProp<MainTabParamList>>();
  const { items: library } = useLibrary();
  const { mode, setMode, entitlement } = useSearchMode();
  const {connected:passConnected,charge}=useMemoryPass();
  const [query, setQuery] = useState('');
  const [response, setResponse] = useState<SearchResponse>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [filter, setFilter] = useState<Filter>('All');
  const [selected, setSelected] = useState<MediaItem>();
  const [modeOpen, setModeOpen] = useState(false);
  const [history, setHistory] = useState<string[]>([]);
  const refreshHistory = () =>
    honorableNative
      .getSearchHistory()
      .then(value => setHistory(value.queries))
      .catch(() => setHistory([]));
  useEffect(() => {
    void refreshHistory();
  }, []);
  const libraryItems = useMemo(
    () =>
      library.map(
        item =>
          ({
            id: String(item.mediaId),
            uri: item.thumbnailUri ?? item.mediaUri,
            mediaUri: item.mediaUri,
            type: item.mediaType,
            duration: duration(item.durationMs),
            capturedAt: item.capturedAt,
            displayName: item.displayName,
          } as MediaItem),
      ),
    [library],
  );
  const nativeItems = useMemo(
    () =>
      response?.results.map(result => {
        const source = library.find(item => item.mediaId === result.mediaId);
        return {
          id: `${result.mediaId}-${result.rank}`,
          uri: source?.thumbnailUri ?? result.mediaUri,
          mediaUri: result.mediaUri,
          type: result.mediaType,
          duration: duration(source?.durationMs),
          capturedAt: source?.capturedAt,
          displayName: result.displayName,
          result,
        } as MediaItem;
      }) ?? [],
    [response, library],
  );
  const base = response ? nativeItems : libraryItems.slice(0, 18);
  const visible = base.filter(
    item =>
      filter === 'All' ||
      (filter === 'Photos' && item.type === 'IMAGE') ||
      (filter === 'Videos' && item.type === 'VIDEO') ||
      (filter === 'Screenshots' &&
        (item.displayName ?? '').toLowerCase().includes('screenshot')),
  );
  const search = async (value = query) => {
    const clean = value.trim();
    if (!clean) return;
    setQuery(clean);
    Keyboard.dismiss();
    setLoading(true);
    setError('');
    const chargeRequestId=`search-${Date.now()}-${Math.random()}`;
    try {
      const result=await honorableNative.search(clean);
      if(passConnected){const model=await honorableNative.getSeranModelState();await charge(model.selected,chargeRequestId)}
      setResponse(result);
      await refreshHistory();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Search unavailable');
      setResponse(undefined);
    } finally {
      setLoading(false);
    }
  };
  const clearHistory = async () => {
    await honorableNative.clearSearchHistory();
    setHistory([]);
  };
  return (
    <SafeAreaView edges={['top']} style={styles.safe}>
      <ScrollView
        keyboardShouldPersistTaps="handled"
        contentContainerStyle={styles.content}
      >
        <View style={styles.utility}>
          <Text variant="muted" className="text-[12px]">
            On-device
          </Text>
        </View>
        <Text
          accessibilityRole="header"
          className="mx-4 mt-2 text-[30px] font-semibold"
        >
          Find a moment.
        </Text>
        <Text variant="muted" className="mx-4 mb-4 mt-1">
          Describe anything you remember.
        </Text>
        <HonorableSearchBar
          value={query}
          onChangeText={setQuery}
          onSubmit={() => search()}
        />
        <SearchModeControl value={mode} onPress={() => setModeOpen(true)} />
        {history.length > 0 && !response && (
          <View style={styles.history}>
            <View style={styles.historyTitle}>
              <View style={styles.historyLabel}>
                <Clock3 color={colors.iconMuted} size={13} />
                <Text variant="muted" className="text-[11px] font-semibold">
                  Recent searches
                </Text>
              </View>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel="Clear search history"
                onPress={clearHistory}
              >
                <X color={colors.iconMuted} size={15} />
              </Pressable>
            </View>
            <ScrollView
              horizontal
              showsHorizontalScrollIndicator={false}
              contentContainerStyle={styles.historyItems}
            >
              {history.map(item => (
                <Pressable
                  key={item}
                  onPress={() => search(item)}
                  style={styles.historyChip}
                >
                  <Text className="text-[11px]">{item}</Text>
                </Pressable>
              ))}
            </ScrollView>
          </View>
        )}
        {loading && (
          <View style={styles.searching}>
            <ActivityIndicator color={colors.accent} />
            <View>
              <Text className="font-semibold">Searching your library…</Text>
              <Text variant="muted" className="mt-1 text-[11px]">
                Photos and videos stay on this device.
              </Text>
            </View>
          </View>
        )}
        {!!error && <Text style={styles.error}>{error}</Text>}
        {!loading && response && !response.confident && (
          <View style={styles.message}>
            <Text className="font-semibold">
              {response.decision === 'VIDEO_INTELLIGENCE_AVAILABLE_WITH_SERAN_V2'
                ? 'Video intelligence is available with Seran V2.'
                : 'No clear match yet'}
            </Text>
            <Text variant="muted" className="mt-1">
              {response.decision === 'VIDEO_INTELLIGENCE_AVAILABLE_WITH_SERAN_V2'
                ? 'Switch models to search real video scenes and moments.'
                : 'Try a color, place, date, object, or visible text.'}
            </Text>
            {response.decision === 'VIDEO_INTELLIGENCE_AVAILABLE_WITH_SERAN_V2' && (
              <Button className="mt-4" onPress={() => navigation.navigate('Models')}>
                <Text>View Models</Text>
              </Button>
            )}
          </View>
        )}
        {!loading && visible.length > 0 && (
          <>
            <View style={styles.resultsHeader}>
              <Text className="text-[17px] font-semibold">
                {response ? `${nativeItems.length} matches` : 'Recent'}
              </Text>
              <Text variant="muted" className="text-[11px]">
                {response ? 'Best first' : 'Your library'}
              </Text>
            </View>
            <ScrollView
              horizontal
              showsHorizontalScrollIndicator={false}
              contentContainerStyle={styles.filters}
            >
              {filters.map(item => (
                <Button
                  key={item}
                  size="sm"
                  variant={filter === item ? 'default' : 'ghost'}
                  className="rounded-full"
                  onPress={() => setFilter(item)}
                >
                  <Text>{item}</Text>
                </Button>
              ))}
            </ScrollView>
            <MediaGrid items={visible} onPress={setSelected} />
          </>
        )}
      </ScrollView>
      <MediaViewer item={selected} onClose={() => setSelected(undefined)} />
      <SearchModeSheet
        visible={modeOpen}
        value={mode}
        entitlement={entitlement}
        onClose={() => setModeOpen(false)}
        onSelect={setMode}
      />
    </SafeAreaView>
  );
}
const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  content: { paddingBottom: 90 },
  utility: {
    height: 36,
    paddingHorizontal: 16,
    alignItems: 'flex-end',
    justifyContent: 'center',
  },
  history: { marginTop: 12 },
  historyTitle: {
    paddingHorizontal: 17,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  historyLabel: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  historyItems: { paddingHorizontal: 16, paddingTop: 8, gap: 6 },
  historyChip: {
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 999,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.glass,
  },
  searching: {
    margin: 16,
    padding: 16,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    borderRadius: 20,
    backgroundColor: colors.surface,
  },
  error: { margin: 16, color: colors.danger },
  message: {
    margin: 16,
    padding: 18,
    borderRadius: 18,
    backgroundColor: colors.surface,
  },
  resultsHeader: {
    marginTop: 20,
    marginBottom: 10,
    paddingHorizontal: 16,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  filters: { paddingHorizontal: 12, paddingBottom: 12, gap: 6 },
});
