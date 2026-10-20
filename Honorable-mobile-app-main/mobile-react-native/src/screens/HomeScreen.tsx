import React, { useEffect, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Keyboard,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import {
  Clock3,
  X,
  UserRound,
  ChevronDown,
  ShieldCheck,
} from 'lucide-react-native';
import { useNavigation } from '@react-navigation/native';
import type { BottomTabNavigationProp } from '@react-navigation/bottom-tabs';
import { Button } from '@/components/ui/button';
import { Text } from '@/components/ui/text';
import { HonorableSearchBar } from '../components/HonorableSearchBar';
import { MediaGrid, MediaItem } from '../components/MediaGrid';
import { MediaViewer } from '../components/MediaViewer';
import { NewWorkspace } from '../components/NewWorkspace';
import { colors } from '../design-system/tokens';
import { honorableNative, SearchResponse } from '../native/HonorableNative';
import { useLibrary } from '../library/LibraryContext';
import type { MainTabParamList } from '../navigation/types';
import { seranCreditCosts } from '../passes/catalog';
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
  const {
    connected: passConnected,
    startSearch,
    finishSearch,
  } = useMemoryPass();
  const [productModel, setProductModel] = useState<'SERAN_V1' | 'SERAN_V2'>(
    'SERAN_V1',
  );
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
    if (!clean || loading) return;
    setQuery(clean);
    Keyboard.dismiss();
    setLoading(true);
    setError('');
    const chargeRequestId = `search-${Date.now()}-${Math.random()}`;
    try {
      if (!passConnected)
        throw Error('Reconnect to verify your Memory Credits.');
      await honorableNative.selectSeranModel(productModel);
      await startSearch(productModel, chargeRequestId);
      const result = await honorableNative.search(clean);
      await finishSearch(
        chargeRequestId,
        result.results.length ? 'SUCCESS' : 'FAILED',
      );
      setResponse(result);
      await refreshHistory();
    } catch (reason) {
      try {
        await finishSearch(chargeRequestId, 'FAILED');
      } catch {}
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
        showsVerticalScrollIndicator={false}
        keyboardShouldPersistTaps="handled"
        contentContainerStyle={styles.content}
      >
        <View style={styles.utility}>
          <View style={styles.wordmarkRow}>
            <View style={styles.brandMark}>
              <Text style={styles.brandLetter}>h</Text>
            </View>
            <Text style={styles.wordmark}>honorable</Text>
          </View>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Account"
            style={styles.accountButton}
            onPress={() => navigation.getParent()?.navigate('Settings')}
          >
            <UserRound size={19} color={colors.textSecondary} />
          </Pressable>
        </View>
        <View style={styles.hero}>
          <View style={styles.eyebrowRow}>
            <View style={styles.statusDot} />
            <Text style={styles.eyebrow}>SERAN / MEMORY SEARCH</Text>
          </View>
          <Text accessibilityRole="header" style={styles.heroTitle}>
            What do you remember?
          </Text>
          <Text style={styles.heroDetail}>
            Describe a photo or a moment in a video.
          </Text>
        </View>
        <HonorableSearchBar
          value={query}
          onChangeText={setQuery}
          onSubmit={() => search()}
          busy={loading}
          footer={
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="Select search model"
              style={styles.modelButton}
              onPress={() => setModeOpen(true)}
            >
              <Text style={styles.modelName}>
                Seran {productModel === 'SERAN_V1' ? 'Fast' : 'Video'}
              </Text>
              <ChevronDown size={12} color={colors.textSecondary} />
              <Text style={styles.modelCost}>
                {productModel === 'SERAN_V1' ? '1 credit' : '3 credits'}
              </Text>
            </Pressable>
          }
        />
        <View style={styles.privacyRow}>
          <ShieldCheck size={12} color={colors.textTertiary} />
          <Text style={styles.privacyText}>Search runs on your device</Text>
        </View>
        <Modal
          visible={modeOpen}
          transparent
          animationType="slide"
          onRequestClose={() => setModeOpen(false)}
        >
          <View
            style={{
              flex: 1,
              justifyContent: 'flex-end',
              backgroundColor: colors.scrim,
            }}
          >
            <View
              style={{
                padding: 24,
                gap: 12,
                backgroundColor: colors.surfaceRaised,
                borderTopLeftRadius: 20,
                borderTopRightRadius: 20,
              }}
            >
              <Text>Select a model</Text>
              {seranCreditCosts.map(m => (
                <Pressable
                  key={m.model}
                  disabled={!m.available || loading}
                  onPress={() => {
                    if (m.model !== 'SERAN_V3') setProductModel(m.model);
                    setModeOpen(false);
                  }}
                  style={{
                    padding: 16,
                    backgroundColor: colors.canvas,
                    borderWidth: 1,
                    borderColor: colors.border,
                    borderRadius: 10,
                  }}
                >
                  <Text>
                    {m.name} · {m.credits} credits
                    {!m.available ? ' · Coming Soon' : ''}
                  </Text>
                </Pressable>
              ))}
              <Pressable
                accessibilityRole="button"
                accessibilityLabel="Close model selection"
                style={{ minHeight: 48, justifyContent: 'center' }}
                onPress={() => setModeOpen(false)}
              >
                <Text>Close</Text>
              </Pressable>
            </View>
          </View>
        </Modal>
        <NewWorkspace />
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
                  accessibilityRole="button"
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
              {response.decision ===
              'VIDEO_INTELLIGENCE_AVAILABLE_WITH_SERAN_V2'
                ? 'Video intelligence is available with Seran V2.'
                : 'No clear match yet'}
            </Text>
            <Text variant="muted" className="mt-1">
              {response.decision ===
              'VIDEO_INTELLIGENCE_AVAILABLE_WITH_SERAN_V2'
                ? 'Switch models to search real video scenes and moments.'
                : 'Try a color, place, date, object, or visible text.'}
            </Text>
            {response.decision ===
              'VIDEO_INTELLIGENCE_AVAILABLE_WITH_SERAN_V2' && (
              <Button
                className="mt-4"
                onPress={() => navigation.getParent()?.navigate('Models')}
              >
                <Text>View Models</Text>
              </Button>
            )}
          </View>
        )}
        {!loading && visible.length > 0 && (
          <>
            <View style={styles.resultsHeader}>
              <Text className="text-[17px] font-semibold">
                {response ? `${nativeItems.length} matches` : 'Your memories'}
              </Text>
              <Text variant="muted" className="text-[11px]">
                {response ? 'Best first' : 'Recently added'}
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
                  className="rounded-md"
                  onPress={() => setFilter(item)}
                >
                  <Text>{item}</Text>
                </Button>
              ))}
            </ScrollView>
            <MediaGrid
              leading={!!response}
              items={visible}
              onPress={setSelected}
            />
          </>
        )}
      </ScrollView>
      <MediaViewer item={selected} onClose={() => setSelected(undefined)} />
    </SafeAreaView>
  );
}
const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  content: { paddingBottom: 48 },
  utility: {
    minHeight: 48,
    paddingHorizontal: 20,
    paddingTop: 8,
    paddingBottom: 12,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  wordmarkRow: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  brandMark: {
    width: 28,
    height: 28,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
  },
  brandLetter: {
    fontSize: 23,
    lineHeight: 27,
    fontWeight: '600',
    color: colors.textPrimary,
  },
  wordmark: { fontSize: 19, fontWeight: '600', letterSpacing: -0.7 },
  accountButton: {
    width: 44,
    height: 44,
    borderRadius: 22,
    borderWidth: 1,
    borderColor: colors.border,
    alignItems: 'center',
    justifyContent: 'center',
  },
  hero: { paddingHorizontal: 20, paddingTop: 28, paddingBottom: 22 },
  eyebrowRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 7,
    marginBottom: 10,
  },
  statusDot: {
    width: 5,
    height: 5,
    borderRadius: 3,
    backgroundColor: colors.accent,
  },
  eyebrow: { fontSize: 10, letterSpacing: 1.5, color: colors.textTertiary },
  heroTitle: {
    fontSize: 30,
    lineHeight: 36,
    fontWeight: '500',
    letterSpacing: -1,
  },
  heroDetail: {
    fontSize: 14,
    lineHeight: 22,
    color: colors.textSecondary,
    marginTop: 16,
  },
  modelButton: {
    minHeight: 44,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    flexWrap: 'wrap',
  },
  modelName: { fontSize: 12, color: colors.textSecondary },
  modelCost: { fontSize: 11, color: colors.textTertiary, marginLeft: 3 },
  privacyRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    marginTop: 12,
    marginBottom: 12,
  },
  privacyText: { fontSize: 11, color: colors.textTertiary },
  history: { marginTop: 12 },
  historyTitle: {
    paddingHorizontal: 16,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  historyLabel: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  historyItems: { paddingHorizontal: 16, paddingTop: 8, gap: 6 },
  historyChip: {
    paddingHorizontal: 12,
    minHeight: 48,
    justifyContent: 'center',
    paddingVertical: 10,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surfaceRaised,
  },
  searching: {
    margin: 16,
    padding: 16,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    borderRadius: 10,
    backgroundColor: colors.surface,
  },
  error: { margin: 16, color: colors.danger },
  message: {
    margin: 16,
    padding: 16,
    borderRadius: 10,
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
