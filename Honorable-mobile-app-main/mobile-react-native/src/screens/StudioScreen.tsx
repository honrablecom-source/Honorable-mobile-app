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
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import {
  ArrowUpRight,
  Check,
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
import { WorkspaceHeader } from '../design-system/WorkspaceHeader';
import {
  creativeStyles,
  SectionHeading,
} from '../design-system/CreativeChrome';
import { useLibrary } from '../library/LibraryContext';
import { useOpenWorkspace, type WorkspaceKind } from '../native/openWorkspace';
import { editMemory } from '../native/editMemory';

export function StudioScreen() {
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
  const editSelected = async () => {
    if (!selected || pending.current || importing) return;
    pending.current = true;
    setOpening(true);
    try {
      await editMemory({
        id: String(selected.mediaId),
        mediaUri: selected.mediaUri,
        type: selected.mediaType,
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
            detail: 'Sharpen, vignette and captions',
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
            detail: 'Trim, split, speed and reorder',
            Icon: Film,
          },
          {
            title: 'Picture',
            detail: 'Color, framing and white balance',
            Icon: SlidersHorizontal,
          },
          {
            title: 'Titles & audio',
            detail: 'Text, music and fades',
            Icon: Music2,
          },
          {
            title: 'Local AI tools',
            detail: 'Video looks and rough cuts',
            Icon: WandSparkles,
          },
          {
            title: 'Export',
            detail: 'Format, fit and crop. A new copy.',
            Icon: Download,
          },
        ];
  return (
    <SafeAreaView edges={['top']} style={s.safe}>
      <WorkspaceHeader
        title="Studio"
        section="Your creative space"
        action={
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Import media"
            accessibilityState={{ disabled: busy, busy }}
            disabled={busy}
            onPress={() => open(mode)}
            style={creativeStyles.iconButton}
          >
            <Plus size={22} color={colors.textPrimary} />
          </Pressable>
        }
      />
      <ScrollView
        showsVerticalScrollIndicator={false}
        contentContainerStyle={s.content}
      >
        <View style={s.createRow}>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Create a new video"
            disabled={busy}
            onPress={() => open('VIDEO')}
            style={[s.createVideo, busy && s.disabled]}
          >
            <View style={s.createTop}>
              <Film size={23} color="#fff" />
              <ArrowUpRight size={19} color="#fff" />
            </View>
            <Text style={s.createTitle}>New video</Text>
            <Text style={s.createSubtitle}>Start with your clips</Text>
          </Pressable>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Choose a photo to edit"
            disabled={busy}
            onPress={() => open('IMAGE')}
            style={[s.createPhoto, busy && s.disabled]}
          >
            <View style={s.createTop}>
              <Photo size={23} color={colors.textPrimary} />
              <ArrowUpRight size={19} color={colors.textTertiary} />
            </View>
            <Text style={s.photoTitle}>Edit photo</Text>
            <Text style={s.photoSubtitle}>Make it your own</Text>
          </Pressable>
        </View>
        <SectionHeading
          title="Your media"
          detail="Choose a source. Keep the original."
          action={
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="Open tools inspector"
              onPress={() => showInspector('tools')}
              style={creativeStyles.iconButton}
            >
              <SlidersHorizontal size={20} color={colors.textPrimary} />
            </Pressable>
          }
        />
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
        {selected ? (
          <View style={s.selection}>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="Open selected media in editor"
              disabled={busy}
              onPress={editSelected}
              style={s.cover}
            >
              {selected.thumbnailUri || mode === 'IMAGE' ? (
                <Image
                  accessibilityLabel={selected.displayName}
                  source={{ uri: selected.thumbnailUri ?? selected.mediaUri }}
                  resizeMode="cover"
                  style={s.coverImage}
                />
              ) : (
                <Film
                  size={40}
                  strokeWidth={1.2}
                  color={colors.textSecondary}
                />
              )}
              <View style={s.coverBadge}>
                <Text style={s.coverBadgeText}>
                  {mode === 'IMAGE' ? 'PHOTO' : 'VIDEO'}
                </Text>
                <ArrowUpRight size={14} color="#fff" />
              </View>
            </Pressable>
            <View style={s.selectedCaption}>
              <View style={s.captionCopy}>
                <Text numberOfLines={1} style={s.filename}>
                  {selected.displayName}
                </Text>
                <Text style={s.caption}>
                  Ready to edit · original preserved
                </Text>
              </View>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel="Toggle source details"
                onPress={() => showInspector('source')}
                style={creativeStyles.iconButton}
              >
                <Info size={19} color={colors.textSecondary} />
              </Pressable>
            </View>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="Open selected editor"
              disabled={busy}
              onPress={editSelected}
              style={[s.editButton, busy && s.disabled]}
            >
              {opening ? (
                <ActivityIndicator color="#fff" />
              ) : (
                <>
                  <Text style={s.editLabel}>Edit {kindName}</Text>
                  <ArrowUpRight size={18} color="#fff" />
                </>
              )}
            </Pressable>
          </View>
        ) : (
          <View style={s.empty}>
            {loading ? (
              <ActivityIndicator color={colors.textPrimary} />
            ) : (
              <>
                <View style={s.emptySymbol}>
                  {mode === 'IMAGE' ? (
                    <Photo
                      size={28}
                      strokeWidth={1.3}
                      color={colors.textPrimary}
                    />
                  ) : (
                    <Film
                      size={28}
                      strokeWidth={1.3}
                      color={colors.textPrimary}
                    />
                  )}
                </View>
                <Text style={s.emptyTitle}>Start with a {kindName}</Text>
                <Text style={s.emptyText}>
                  Choose from your device. Your original stays untouched.
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
              </>
            )}
          </View>
        )}
        {assets.length > 0 && (
          <View style={s.assetSection}>
            <Text style={s.recentLabel}>RECENT SOURCES</Text>
            <ScrollView
              horizontal
              showsHorizontalScrollIndicator={false}
              contentContainerStyle={s.assetStrip}
            >
              <Pressable
                accessibilityRole="button"
                accessibilityLabel="Import another source"
                disabled={busy}
                onPress={() => open(mode)}
                style={s.addAsset}
              >
                <Plus size={23} color={colors.textPrimary} />
                <Text style={s.addLabel}>Import</Text>
              </Pressable>
              {assets.map(item => (
                <Pressable
                  key={item.mediaId}
                  accessibilityRole="button"
                  accessibilityLabel={`Select ${item.displayName}`}
                  accessibilityState={{
                    selected: item.mediaId === selected?.mediaId,
                  }}
                  onPress={() => setSelectedId(item.mediaId)}
                  style={s.asset}
                >
                  <View style={s.assetPicture}>
                    {item.thumbnailUri || item.mediaType === 'IMAGE' ? (
                      <Image
                        source={{ uri: item.thumbnailUri ?? item.mediaUri }}
                        style={s.assetImage}
                      />
                    ) : (
                      <Film size={24} color={colors.textSecondary} />
                    )}
                    {item.mediaId === selected?.mediaId && (
                      <View style={s.check}>
                        <Check size={12} color="#fff" />
                      </View>
                    )}
                  </View>
                  <Text numberOfLines={1} style={s.assetName}>
                    {item.displayName}
                  </Text>
                </Pressable>
              ))}
            </ScrollView>
          </View>
        )}
        {!!error && (
          <Text accessibilityRole="alert" style={s.error}>
            {error}
          </Text>
        )}
        <View style={s.footer}>
          <Text style={s.footerText}>Made from your memories.</Text>
          <Text style={s.footerText}>Saved as something new.</Text>
        </View>
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
  safe: { flex: 1, backgroundColor: colors.canvas },
  content: { paddingBottom: 24 },
  createRow: {
    flexDirection: 'row',
    gap: 12,
    paddingHorizontal: 20,
    paddingTop: 10,
  },
  createVideo: {
    flex: 1,
    backgroundColor: '#151515',
    borderRadius: 20,
    padding: 18,
    minHeight: 152,
    justifyContent: 'space-between',
  },
  createPhoto: {
    flex: 1,
    backgroundColor: colors.surfaceSelected,
    borderRadius: 20,
    padding: 18,
    minHeight: 152,
    justifyContent: 'space-between',
  },
  createTop: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 24,
  },
  createTitle: {
    fontSize: 20,
    fontWeight: '600',
    letterSpacing: -0.5,
    color: '#fff',
  },
  createSubtitle: { fontSize: 11, color: '#ccc', marginTop: 5 },
  photoTitle: {
    fontSize: 20,
    fontWeight: '600',
    letterSpacing: -0.5,
    color: colors.textPrimary,
  },
  photoSubtitle: { fontSize: 11, color: colors.textSecondary, marginTop: 5 },
  modeRow: {
    flexDirection: 'row',
    gap: 26,
    marginHorizontal: 20,
    marginBottom: 16,
    borderBottomWidth: 1,
    borderColor: colors.surfaceSelected,
  },
  modeTab: {
    minHeight: 48,
    justifyContent: 'center',
    borderBottomWidth: 2,
    borderBottomColor: 'transparent',
    paddingHorizontal: 2,
  },
  modeActive: { borderBottomColor: colors.textPrimary },
  modeText: { fontSize: 14, color: colors.textTertiary },
  modeSelected: { fontWeight: '600', color: colors.textPrimary },
  selection: { marginHorizontal: 20 },
  cover: {
    height: 220,
    borderRadius: 18,
    overflow: 'hidden',
    backgroundColor: colors.surfaceSelected,
    alignItems: 'center',
    justifyContent: 'center',
  },
  coverImage: { width: '100%', height: '100%' },
  coverBadge: {
    position: 'absolute',
    bottom: 12,
    right: 12,
    flexDirection: 'row',
    gap: 8,
    alignItems: 'center',
    backgroundColor: 'rgba(0,0,0,.68)',
    paddingHorizontal: 11,
    paddingVertical: 7,
    borderRadius: 20,
  },
  coverBadgeText: {
    fontSize: 9,
    fontWeight: '600',
    letterSpacing: 1,
    color: '#fff',
  },
  selectedCaption: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingVertical: 12,
  },
  captionCopy: { flex: 1, gap: 4 },
  filename: { fontSize: 15, fontWeight: '600', color: colors.textPrimary },
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
    marginHorizontal: 20,
    padding: 24,
    alignItems: 'center',
    backgroundColor: colors.surfaceRaised,
    borderRadius: 20,
  },
  emptySymbol: {
    width: 64,
    height: 64,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 20,
    backgroundColor: colors.surfaceSelected,
    marginBottom: 18,
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
  assetSection: { marginTop: 24 },
  recentLabel: {
    fontSize: 10,
    fontWeight: '600',
    letterSpacing: 1.2,
    color: colors.textTertiary,
    marginHorizontal: 20,
    marginBottom: 12,
  },
  assetStrip: { gap: 10, paddingHorizontal: 20 },
  addAsset: {
    width: 82,
    height: 82,
    backgroundColor: colors.surfaceSelected,
    borderRadius: 14,
    justifyContent: 'center',
    alignItems: 'center',
    gap: 8,
  },
  addLabel: { fontSize: 10, color: colors.textSecondary },
  asset: { width: 100, gap: 7 },
  assetPicture: {
    width: 100,
    height: 82,
    borderRadius: 14,
    backgroundColor: colors.surfaceSelected,
    overflow: 'hidden',
    alignItems: 'center',
    justifyContent: 'center',
  },
  assetImage: { width: '100%', height: '100%' },
  assetName: { fontSize: 10, color: colors.textSecondary },
  check: {
    position: 'absolute',
    right: 6,
    bottom: 6,
    backgroundColor: colors.textPrimary,
    width: 22,
    height: 22,
    borderRadius: 11,
    borderWidth: 2,
    borderColor: colors.canvas,
    justifyContent: 'center',
    alignItems: 'center',
  },
  footer: { alignItems: 'center', gap: 4, marginTop: 30 },
  footerText: { fontSize: 11, lineHeight: 16, color: colors.textTertiary },
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
    flexDirection: 'row',
    gap: 24,
    borderBottomWidth: 1,
    borderColor: colors.surfaceSelected,
  },
  sheetScroll: { flexShrink: 1 },
  sheetBody: { gap: 4, paddingBottom: 8 },
  toolRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
    minHeight: 70,
    paddingVertical: 12,
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
