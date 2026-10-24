import React, { useRef, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Image,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
  useWindowDimensions,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import {
  ArrowUpRight,
  ChevronRight,
  Film,
  Image as Photo,
  Info,
  Plus,
  SlidersHorizontal,
  X,
  Crop,
  Type,
  WandSparkles,
  Music2,
  Download,
} from 'lucide-react-native';
import { colors } from '../design-system/tokens';
import { creativeStyles } from '../design-system/CreativeChrome';
import { useLibrary } from '../library/LibraryContext';
import { useOpenWorkspace, type WorkspaceKind } from '../native/openWorkspace';
import { editMemory } from '../native/editMemory';

export function StudioScreen() {
  const { width } = useWindowDimensions();
  const { items, loading, error } = useLibrary();
  const { busy: importing, open } = useOpenWorkspace();
  const [mode, setMode] = useState<WorkspaceKind>('IMAGE');
  const [selectedId, setSelectedId] = useState<number>();
  const [details, setDetails] = useState(false);
  const [inspectorTab, setInspectorTab] = useState<'tools' | 'source'>('tools');
  const [opening, setOpening] = useState(false);
  const pending = useRef(false);
  const assets = items.filter(item => item.mediaType === mode).slice(0, 12);
  const selected =
    assets.find(item => item.mediaId === selectedId) ?? assets[0];
  const busy = importing || opening;
  const kindName = mode === 'IMAGE' ? 'photo' : 'video';
  const editSelected = async (source = selected) => {
    if (!source || pending.current || importing) return;
    setSelectedId(source.mediaId);
    pending.current = true;
    setOpening(true);
    try {
      await editMemory({
        id: String(source.mediaId),
        mediaUri: source.mediaUri,
        type: source.mediaType,
      });
    } catch (reason) {
      Alert.alert(
        'Open editor',
        reason instanceof Error ? reason.message : 'The editor could not open.',
      );
    } finally {
      pending.current = false;
      setOpening(false);
    }
  };
  const showInspector = (tab: 'tools' | 'source') => {
    setInspectorTab(tab);
    setDetails(true);
  };
  const groups =
    mode === 'IMAGE'
      ? [
          {
            title: 'Crop & transform',
            detail: 'Aspect ratio, rotation and mirror',
            Icon: Crop,
          },
          {
            title: 'Light & color',
            detail: 'Exposure, curves and white balance',
            Icon: SlidersHorizontal,
          },
          {
            title: 'Detail & text',
            detail: 'Sharpen, vignette, text and saved looks',
            Icon: Type,
          },
          {
            title: 'Local AI tools',
            detail: 'Photo looks, people and backgrounds',
            Icon: WandSparkles,
          },
          {
            title: 'Export',
            detail: 'PNG or JPEG. Always a new copy.',
            Icon: Download,
          },
        ]
      : [
          {
            title: 'Edit clips',
            detail: 'Trim, split, speed, reorder and balanced montages',
            Icon: Film,
          },
          {
            title: 'Picture',
            detail: 'Color, framing and white balance',
            Icon: SlidersHorizontal,
          },
          {
            title: 'Titles & audio',
            detail: 'Titles, script captions, SRT, music and fades',
            Icon: Music2,
          },
          {
            title: 'Local AI tools',
            detail: 'Video looks and rough cuts',
            Icon: WandSparkles,
          },
          {
            title: 'Export',
            detail: 'Video copy or audio-only M4A. Original preserved.',
            Icon: Download,
          },
        ];
  const renderSource = (item: (typeof assets)[number], featured = false) => (
    <Pressable
      key={item.mediaId}
      accessibilityRole="button"
      accessibilityLabel={`Edit ${item.displayName}`}
      accessibilityHint="Opens the native editor. Touch and hold for source details."
      disabled={busy}
      onPress={() => editSelected(item)}
      onLongPress={() => {
        setSelectedId(item.mediaId);
        showInspector('source');
      }}
      style={[
        s.sourceCell,
        { width: featured ? Math.max(1, width - 32) : 146 },
      ]}
    >
      <View style={[s.sourceImage, { height: featured ? 196 : 112 }]}>
        {item.thumbnailUri || item.mediaType === 'IMAGE' ? (
          <Image
            source={{ uri: item.thumbnailUri ?? item.mediaUri }}
            resizeMode="cover"
            style={s.fill}
          />
        ) : (
          <Film size={24} color={colors.textSecondary} />
        )}
        {item.mediaType === 'VIDEO' && (
          <View style={s.mediaBadge}>
            <Film size={11} color="#fff" />
            <Text style={s.mediaBadgeText}>
              {item.durationMs
                ? `${Math.floor(item.durationMs / 60000)}:${String(
                    Math.floor(item.durationMs / 1000) % 60,
                  ).padStart(2, '0')}`
                : 'Video'}
            </Text>
          </View>
        )}
      </View>
      <Text numberOfLines={1} style={s.mediaFilename}>
        {item.displayName}
      </Text>
    </Pressable>
  );
  return (
    <SafeAreaView edges={['top']} style={s.safe}>
      <View style={s.launchHeader}>
        <View style={s.titleCopy}>
          <Text accessibilityRole="header" style={s.launchTitle}>
            Studio
          </Text>
          <Text style={s.subtitle}>Photo & video editing</Text>
        </View>
        <View style={s.headerActions}>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Open tools inspector"
            onPress={() => showInspector('tools')}
            style={creativeStyles.iconButton}
          >
            <SlidersHorizontal size={20} color={colors.textPrimary} />
          </Pressable>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Import media"
            disabled={busy}
            onPress={() => open(mode)}
            style={creativeStyles.iconButton}
          >
            <Plus size={22} color={colors.textPrimary} />
          </Pressable>
        </View>
      </View>
      <ScrollView
        showsVerticalScrollIndicator={false}
        contentContainerStyle={s.content}
      >
        <View style={s.sourceActions}>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Create a new video"
            disabled={busy}
            onPress={() => open('VIDEO')}
            style={[s.sourceAction, s.sourcePrimary, busy && s.disabled]}
          >
            <Film size={22} strokeWidth={1.8} color={colors.textPrimary} />
            <Text style={s.sourcePrimaryText}>New video</Text>
            <ArrowUpRight size={16} color={colors.textSecondary} />
          </Pressable>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Choose a photo to edit"
            disabled={busy}
            onPress={() => open('IMAGE')}
            style={[s.sourceAction, busy && s.disabled]}
          >
            <Photo size={21} color={colors.textPrimary} />
            <Text style={s.sourceActionText}>Edit photo</Text>
            <ArrowUpRight size={16} color={colors.textPrimary} />
          </Pressable>
        </View>
        <View style={s.recentHeading}>
          <Text style={s.recentTitle}>Your media</Text>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Toggle source details"
            onPress={() => showInspector('source')}
            style={creativeStyles.iconButton}
          >
            <Info size={18} color={colors.textSecondary} />
          </Pressable>
        </View>
        <View style={s.modeRow}>
          {(['IMAGE', 'VIDEO'] as const).map(kind => (
            <Pressable
              key={kind}
              accessibilityRole="tab"
              accessibilityLabel={
                kind === 'IMAGE' ? 'Photo workspace' : 'Video workspace'
              }
              accessibilityState={{ selected: mode === kind }}
              onPress={() => {
                setMode(kind);
                setSelectedId(undefined);
              }}
              style={[s.modeTab, mode === kind && s.modeActive]}
            >
              <Text style={[s.modeText, mode === kind && s.modeSelected]}>
                {kind === 'IMAGE' ? 'Photos' : 'Videos'}
              </Text>
            </Pressable>
          ))}
        </View>
        {loading ? (
          <ActivityIndicator color={colors.textPrimary} />
        ) : assets.length ? (
          <View style={s.sourceCollection}>
            <View style={s.featuredSource}>
              {renderSource(assets[0], true)}
            </View>
            {assets.length > 1 && (
              <ScrollView
                horizontal
                showsHorizontalScrollIndicator={false}
                contentContainerStyle={s.sourceStrip}
              >
                {assets.slice(1).map(item => renderSource(item))}
              </ScrollView>
            )}
          </View>
        ) : (
          <View style={s.empty}>
            <Photo size={26} color={colors.textSecondary} />
            <Text style={s.emptyTitle}>Choose a {kindName} to begin</Text>
            <Text style={s.emptyText}>
              Import from your device or allow access to your library.
            </Text>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="Import another source"
              disabled={busy}
              onPress={() => open(mode)}
              style={s.emptyButton}
            >
              <Plus size={17} color={colors.textPrimary} />
              <Text style={s.emptyButtonText}>Choose {kindName}</Text>
            </Pressable>
          </View>
        )}
        {opening && (
          <ActivityIndicator
            accessibilityLabel="Opening editor"
            color={colors.textPrimary}
          />
        )}
        {!!error && (
          <Text accessibilityRole="alert" style={s.error}>
            {error}
          </Text>
        )}
        <Text style={s.sourceFootnote}>
          Tap media to edit. Exports create a new copy.
        </Text>
      </ScrollView>
      <Modal
        visible={details}
        transparent
        animationType="slide"
        onRequestClose={() => setDetails(false)}
      >
        <View style={s.overlay}>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Dismiss source details"
            onPress={() => setDetails(false)}
            style={s.dismiss}
          />
          <View style={s.sheet}>
            <View style={s.handle} />
            <View style={s.sheetHeader}>
              <View style={s.captionCopy}>
                <Text style={s.sheetTitle}>
                  {inspectorTab === 'tools'
                    ? 'In your editor'
                    : 'Source details'}
                </Text>
                <Text style={s.caption}>
                  {mode === 'IMAGE' ? 'Photo' : 'Video'} workspace
                </Text>
              </View>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel="Close source details"
                onPress={() => setDetails(false)}
                style={creativeStyles.iconButton}
              >
                <X size={20} color={colors.textPrimary} />
              </Pressable>
            </View>
            <View style={s.sheetTabs}>
              {(['tools', 'source'] as const).map(tab => (
                <Pressable
                  key={tab}
                  accessibilityRole="tab"
                  accessibilityLabel={`${tab} inspector tab`}
                  accessibilityState={{ selected: tab === inspectorTab }}
                  onPress={() => setInspectorTab(tab)}
                  style={[s.modeTab, tab === inspectorTab && s.modeActive]}
                >
                  <Text
                    style={[s.modeText, tab === inspectorTab && s.modeSelected]}
                  >
                    {tab === 'tools' ? 'Tools' : 'Source'}
                  </Text>
                </Pressable>
              ))}
            </View>
            <ScrollView
              style={s.sheetScroll}
              contentContainerStyle={s.sheetBody}
            >
              {inspectorTab === 'tools' ? (
                groups.map(({ title, detail, Icon }) => (
                  <View style={s.toolRow} key={title}>
                    <View style={s.toolIcon}>
                      <Icon
                        size={21}
                        strokeWidth={1.5}
                        color={colors.textPrimary}
                      />
                    </View>
                    <View style={s.captionCopy}>
                      <Text style={s.toolTitle}>{title}</Text>
                      <Text style={s.toolDetail}>{detail}</Text>
                    </View>
                  </View>
                ))
              ) : (
                <>
                  <Text style={s.sourceName}>
                    {selected?.displayName ?? 'No media selected'}
                  </Text>
                  <Text style={s.toolDetail}>
                    Saving creates a new copy. The original remains unchanged.
                  </Text>
                  <Text style={s.toolDetail}>Type: {kindName}</Text>
                </>
              )}
            </ScrollView>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="Open editor from inspector"
              disabled={!selected || busy}
              onPress={() => {
                setDetails(false);
                editSelected();
              }}
              style={[s.editButton, (!selected || busy) && s.disabled]}
            >
              <Text style={s.editLabel}>
                {selected ? `Open ${kindName} editor` : 'Choose media first'}
              </Text>
              <ChevronRight size={18} color="#fff" />
            </Pressable>
          </View>
        </View>
      </Modal>
    </SafeAreaView>
  );
}
const s = StyleSheet.create({
  launchHeader: {
    minHeight: 100,
    paddingHorizontal: 16,
    paddingVertical: 16,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  launchTitle: {
    fontSize: 32,
    lineHeight: 38,
    fontWeight: '600',
    letterSpacing: -0.7,
    color: colors.textPrimary,
  },
  titleCopy: { flex: 1, gap: 3 },
  subtitle: { fontSize: 13, lineHeight: 19, color: colors.textSecondary },
  headerActions: { flexDirection: 'row', gap: 6 },
  sourceActions: {
    flexDirection: 'row',
    gap: 8,
    paddingHorizontal: 16,
    paddingTop: 4,
    paddingBottom: 16,
  },
  sourceAction: {
    flex: 1,
    minHeight: 64,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.surfaceSelected,
    backgroundColor: colors.surfaceRaised,
    borderRadius: 14,
    paddingHorizontal: 14,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 9,
  },
  sourcePrimary: {
    backgroundColor: colors.surfaceRaised,
    borderColor: colors.surfaceSelected,
  },
  sourcePrimaryText: {
    flex: 1,
    fontSize: 14,
    fontWeight: '500',
    color: colors.textPrimary,
  },
  sourceActionText: {
    flex: 1,
    fontSize: 14,
    fontWeight: '500',
    color: colors.textPrimary,
  },
  recentHeading: {
    paddingHorizontal: 16,
    marginTop: 6,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  recentTitle: { fontSize: 20, fontWeight: '600', color: colors.textPrimary },
  sourceCollection: { gap: 18 },
  featuredSource: { paddingHorizontal: 16 },
  sourceStrip: { paddingHorizontal: 16, gap: 10, paddingBottom: 4 },
  sourceGrid: {
    paddingHorizontal: 16,
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'flex-start',
    columnGap: 10,
    rowGap: 18,
  },
  sourceCell: { gap: 8 },
  sourceImage: {
    width: '100%',
    borderRadius: 12,
    overflow: 'hidden',
    backgroundColor: colors.surfaceSelected,
    alignItems: 'center',
    justifyContent: 'center',
  },
  fill: { width: '100%', height: '100%' },
  mediaFilename: { fontSize: 12, lineHeight: 18, color: colors.textSecondary },
  mediaBadge: {
    position: 'absolute',
    bottom: 5,
    right: 5,
    backgroundColor: 'rgba(0,0,0,.7)',
    borderRadius: 4,
    padding: 4,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 3,
  },
  mediaBadgeText: { fontSize: 9, color: '#fff' },
  sourceFootnote: {
    marginHorizontal: 16,
    marginTop: 24,
    fontSize: 11,
    lineHeight: 17,
    color: colors.textTertiary,
  },
  safe: { flex: 1, backgroundColor: colors.canvas },
  content: { paddingBottom: 24 },
  modeRow: {
    flexDirection: 'row',
    gap: 4,
    marginHorizontal: 16,
    marginBottom: 18,
    padding: 3,
    borderRadius: 13,
    backgroundColor: colors.surfaceRaised,
  },
  modeTab: {
    flex: 1,
    minHeight: 44,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 10,
    paddingHorizontal: 8,
  },
  modeActive: {
    backgroundColor: colors.canvas,
    shadowColor: '#000',
    shadowOpacity: 0.06,
    shadowRadius: 3,
    shadowOffset: { width: 0, height: 1 },
    elevation: 1,
  },
  modeText: { fontSize: 14, color: colors.textTertiary },
  modeSelected: { fontWeight: '600', color: colors.textPrimary },
  captionCopy: { flex: 1, gap: 4 },
  caption: { fontSize: 11, lineHeight: 17, color: colors.textTertiary },
  editButton: {
    minHeight: 52,
    borderRadius: 26,
    backgroundColor: colors.textPrimary,
    paddingHorizontal: 22,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 12,
  },
  editLabel: { fontSize: 14, fontWeight: '600', color: '#fff' },
  disabled: { opacity: 0.4 },
  empty: {
    marginHorizontal: 16,
    padding: 24,
    alignItems: 'center',
    backgroundColor: colors.surfaceRaised,
    borderRadius: 20,
  },
  emptyTitle: {
    fontSize: 19,
    fontWeight: '600',
    letterSpacing: -0.4,
    color: colors.textPrimary,
  },
  emptyText: {
    fontSize: 13,
    lineHeight: 20,
    color: colors.textTertiary,
    textAlign: 'center',
    marginTop: 8,
    maxWidth: 240,
  },
  emptyButton: {
    minHeight: 48,
    paddingHorizontal: 18,
    borderWidth: 1,
    borderColor: colors.textPrimary,
    borderRadius: 24,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginTop: 20,
  },
  emptyButtonText: {
    fontSize: 13,
    fontWeight: '500',
    color: colors.textPrimary,
  },
  error: { margin: 20, color: colors.danger, fontSize: 13 },
  overlay: {
    flex: 1,
    justifyContent: 'flex-end',
    backgroundColor: colors.scrim,
  },
  dismiss: { flex: 1 },
  sheet: {
    maxHeight: '85%',
    backgroundColor: colors.canvas,
    borderTopLeftRadius: 26,
    borderTopRightRadius: 26,
    padding: 20,
    paddingTop: 10,
    gap: 14,
  },
  handle: {
    width: 34,
    height: 4,
    borderRadius: 2,
    backgroundColor: colors.border,
    alignSelf: 'center',
    marginBottom: 6,
  },
  sheetHeader: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  sheetTitle: {
    fontSize: 22,
    fontWeight: '600',
    letterSpacing: -0.6,
    color: colors.textPrimary,
  },
  sheetTabs: {
    padding: 3,
    borderRadius: 13,
    backgroundColor: colors.surfaceRaised,
    flexDirection: 'row',
    gap: 4,
  },
  sheetScroll: { flexShrink: 1 },
  sheetBody: { gap: 4, paddingBottom: 8 },
  toolRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
    minHeight: 68,
    paddingVertical: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.surfaceSelected,
  },
  toolIcon: {
    width: 44,
    height: 44,
    borderRadius: 14,
    backgroundColor: colors.surfaceRaised,
    alignItems: 'center',
    justifyContent: 'center',
  },
  toolTitle: { fontSize: 14, fontWeight: '500', color: colors.textPrimary },
  toolDetail: { fontSize: 12, lineHeight: 18, color: colors.textTertiary },
  sourceName: {
    fontSize: 17,
    fontWeight: '500',
    lineHeight: 24,
    color: colors.textPrimary,
    marginBottom: 8,
  },
});
