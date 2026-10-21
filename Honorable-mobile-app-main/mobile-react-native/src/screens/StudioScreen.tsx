import React, { useRef, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Image,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
  Modal,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import {
  ArrowUpRight,
  Film,
  Image as Photo,
  Plus,
  Info,
  X,
} from 'lucide-react-native';
import { colors } from '../design-system/tokens';
import { WorkspaceHeader } from '../design-system/WorkspaceHeader';
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
  const showDetails = details;
  const assets = items.filter(item => item.mediaType === mode).slice(0, 12);
  const selected =
    assets.find(item => item.mediaId === selectedId) ?? assets[0];
  const busy = importing || opening;
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
  const toolGroups =
    mode === 'IMAGE'
      ? [
          ['Framing', 'Aspect ratios · rotate · mirror'],
          [
            'Light & color',
            'Exposure · curves · white balance · shadows / highlights',
          ],
          ['Finish', 'Vignette · sharpen · text overlay'],
          [
            'On-device AI',
            'Scene looks · person background · cutout · read text',
          ],
          ['Output', 'PNG transparency · JPEG quality'],
        ]
      : [
          ['Timeline', 'Trim · split · reorder · speed'],
          [
            'Picture',
            'Color · white balance · rotate · mirror · copy color to all',
          ],
          ['Titles & sound', 'Title style · soundtrack · project audio fades'],
          ['On-device AI', 'Video looks · scene suggestion · rough cut'],
          ['Output', 'Square · vertical · wide · portrait · fit / crop'],
        ];
  const inspector = (
    <View style={st.inspector}>
      <View style={st.sectionHeading}>
        <View style={st.inspectorCopy}>
          <Text style={st.sectionLabel}>WORKSPACE INSPECTOR</Text>
          <Text style={st.detailsTitle}>
            {mode === 'IMAGE' ? 'Photo' : 'Video'}
          </Text>
        </View>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Close source details"
          onPress={() => setDetails(false)}
          style={st.detailsButton}
        >
          <X size={20} color={colors.textPrimary} />
        </Pressable>
      </View>
      <View style={st.inspectorTabs}>
        {(['tools', 'source'] as const).map(tab => (
          <Pressable
            key={tab}
            accessibilityRole="tab"
            accessibilityState={{ selected: inspectorTab === tab }}
            accessibilityLabel={`${tab} inspector tab`}
            onPress={() => setInspectorTab(tab)}
            style={[
              st.inspectorTab,
              inspectorTab === tab && st.inspectorTabActive,
            ]}
          >
            <Text style={st.value}>{tab === 'tools' ? 'Tools' : 'Source'}</Text>
          </Pressable>
        ))}
      </View>
      <ScrollView
        style={st.inspectorScroll}
        contentContainerStyle={st.inspectorBody}
      >
        {inspectorTab === 'tools' ? (
          toolGroups.map(([title, description]) => (
            <View key={title} style={st.toolGroup}>
              <Text style={st.toolGroupTitle}>{title}</Text>
              <Text style={st.help}>{description}</Text>
            </View>
          ))
        ) : (
          <>
            <Text style={st.sourceName}>
              {selected?.displayName ?? 'No media selected'}
            </Text>
            <View style={st.property}>
              <Text style={st.muted}>Type</Text>
              <Text style={st.value}>
                {mode === 'IMAGE' ? 'Photo' : 'Video'}
              </Text>
            </View>
            <View style={st.property}>
              <Text style={st.muted}>Saving</Text>
              <Text style={st.value}>New copy</Text>
            </View>
            <Text style={st.help}>
              Your original remains unchanged. Tools open in the full editor for
              this source.
            </Text>
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
        style={[st.primary, (!selected || busy) && st.disabled]}
      >
        <Text style={st.primaryLabel}>
          Open {mode === 'IMAGE' ? 'photo' : 'video'} editor
        </Text>
        <ArrowUpRight size={18} color={colors.canvas} />
      </Pressable>
    </View>
  );
  return (
    <SafeAreaView edges={['top']} style={st.safe}>
      <WorkspaceHeader
        title="Studio"
        section="honorable / studio"
        action={
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Import media"
            disabled={busy}
            accessibilityState={{ disabled: busy, busy }}
            onPress={() => open(mode)}
            style={st.importButton}
          >
            <Plus size={17} color={colors.textPrimary} />
            <Text style={st.value}>Import</Text>
          </Pressable>
        }
      />
      <ScrollView
        showsVerticalScrollIndicator={false}
        contentContainerStyle={st.content}
      >
        <View style={st.modeBar}>
          <View style={st.modes}>
            {(['IMAGE', 'VIDEO'] as const).map(kind => {
              const Icon = kind === 'IMAGE' ? Photo : Film;
              const active = kind === mode;
              return (
                <Pressable
                  key={kind}
                  accessibilityRole="tab"
                  accessibilityLabel={
                    kind === 'IMAGE' ? 'Photo workspace' : 'Video workspace'
                  }
                  accessibilityState={{ selected: active }}
                  onPress={() => {
                    setMode(kind);
                    setSelectedId(undefined);
                  }}
                  style={[st.mode, active && st.activeMode]}
                >
                  <Icon
                    size={15}
                    color={active ? colors.textPrimary : colors.textTertiary}
                  />
                  <Text style={[st.modeLabel, active && st.activeLabel]}>
                    {kind === 'IMAGE' ? 'Photo' : 'Video'}
                  </Text>
                </Pressable>
              );
            })}
          </View>
        </View>
        <View style={st.workspace}>
          <View style={st.stageColumn}>
            <View style={st.canvasHeader}>
              <Text style={st.sectionLabel}>
                {mode === 'IMAGE' ? 'PHOTO' : 'VIDEO'} / ORIGINAL
              </Text>
              <Text style={st.fitLabel}>Fit</Text>
            </View>
            <View style={st.stage}>
              {selected && (selected.thumbnailUri || mode === 'IMAGE') ? (
                <Image
                  accessibilityLabel={selected.displayName}
                  resizeMode="contain"
                  source={{ uri: selected.thumbnailUri ?? selected.mediaUri }}
                  style={st.canvasImage}
                />
              ) : (
                <View style={st.emptyCanvas}>
                  {loading ? (
                    <ActivityIndicator color={colors.textTertiary} />
                  ) : (
                    <>
                      <View style={st.emptyIcon}>
                        {mode === 'IMAGE' ? (
                          <Photo
                            size={30}
                            strokeWidth={1}
                            color={colors.textTertiary}
                          />
                        ) : (
                          <Film
                            size={30}
                            strokeWidth={1}
                            color={colors.textTertiary}
                          />
                        )}
                      </View>
                      <Text style={st.emptyTitle}>
                        {selected ? 'Video selected' : 'Choose your source'}
                      </Text>
                      <Text style={st.emptyDetail}>
                        {selected
                          ? 'Open the editor to play and edit this video.'
                          : 'Import a photo or video to begin.'}
                      </Text>
                      <Pressable
                        accessibilityRole="button"
                        disabled={busy}
                        onPress={() => (selected ? editSelected() : open(mode))}
                        style={st.emptyAction}
                      >
                        <Text style={st.value}>
                          {selected ? 'Open video editor' : 'Choose media'}
                        </Text>
                        <ArrowUpRight size={15} color={colors.textSecondary} />
                      </Pressable>
                    </>
                  )}
                </View>
              )}
            </View>
            <View style={st.stageFooter}>
              <Text style={st.filename} numberOfLines={1}>
                {selected?.displayName ?? 'No source selected'}
              </Text>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel="Toggle source details"
                accessibilityState={{ expanded: showDetails }}
                onPress={() => {
                  setInspectorTab('source');
                  setDetails(!showDetails);
                }}
                style={st.detailsButton}
              >
                <Info size={19} color={colors.textSecondary} />
              </Pressable>
            </View>
          </View>
        </View>
        <View style={st.workspaceRail}>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Open tools inspector"
            onPress={() => {
              setInspectorTab('tools');
              setDetails(true);
            }}
            style={st.railAction}
          >
            <Text style={st.value}>Tools</Text>
            <Info size={16} color={colors.textPrimary} />
          </Pressable>
          <View style={st.railDivider} />
          <Text style={st.railHint}>
            {mode === 'IMAGE' ? 'Photo workspace' : 'Video workspace'}
          </Text>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Open source inspector"
            onPress={() => {
              setInspectorTab('source');
              setDetails(true);
            }}
            style={st.railAction}
          >
            <Text style={st.value}>Source</Text>
          </Pressable>
        </View>
        <View style={st.assetHeader}>
          <View style={st.sectionHeading}>
            <Text style={st.libraryTitle}>Source media</Text>
            <Text style={st.count}>{assets.length}</Text>
          </View>
          <Text style={st.fitLabel}>Recent</Text>
        </View>
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={st.assetStrip}
        >
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Import another source"
            disabled={busy}
            onPress={() => open(mode)}
            style={st.addAsset}
          >
            <Plus size={22} strokeWidth={1} color={colors.textSecondary} />
            <Text style={st.addLabel}>Import</Text>
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
              style={[
                st.asset,
                item.mediaId === selected?.mediaId && st.selectedAsset,
              ]}
            >
              {item.thumbnailUri || item.mediaType === 'IMAGE' ? (
                <Image
                  source={{ uri: item.thumbnailUri ?? item.mediaUri }}
                  style={st.assetImage}
                />
              ) : (
                <Film size={22} color={colors.textTertiary} />
              )}
              <Text numberOfLines={1} style={st.assetName}>
                {item.displayName}
              </Text>
            </Pressable>
          ))}
        </ScrollView>
        {!!error && (
          <Text accessibilityRole="alert" style={st.error}>
            {error}
          </Text>
        )}
      </ScrollView>
      <View style={st.actionDock}>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Open selected media in editor"
          disabled={!selected || busy}
          accessibilityState={{ disabled: !selected || busy, busy }}
          onPress={editSelected}
          style={[st.primary, (!selected || busy) && st.disabled]}
        >
          <Text style={st.primaryLabel}>
            {opening
              ? 'Opening…'
              : mode === 'IMAGE'
              ? 'Edit photo'
              : 'Edit video'}
          </Text>
          <ArrowUpRight size={18} color={colors.canvas} />
        </Pressable>
        <Text style={st.copyNote}>
          Edits save as a copy. Your original stays yours.
        </Text>
      </View>
      <Modal
        visible={showDetails}
        transparent
        animationType="slide"
        onRequestClose={() => setDetails(false)}
      >
        <View style={st.sheetOverlay}>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Dismiss source details"
            onPress={() => setDetails(false)}
            style={st.sheetDismiss}
          />
          {inspector}
        </View>
      </Modal>
    </SafeAreaView>
  );
}
const st = StyleSheet.create({
  inspectorCopy: { flex: 1, gap: 4 },
  inspectorTabs: {
    flexDirection: 'row',
    gap: 20,
    borderBottomWidth: 1,
    borderBottomColor: colors.surfaceSelected,
  },
  inspectorTab: {
    minHeight: 48,
    justifyContent: 'center',
    paddingHorizontal: 4,
    borderBottomWidth: 2,
    borderBottomColor: 'transparent',
  },
  inspectorTabActive: { borderBottomColor: colors.textPrimary },
  inspectorScroll: { flexShrink: 1 },
  inspectorBody: { paddingVertical: 8, gap: 4 },
  toolGroup: {
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: colors.surfaceSelected,
    gap: 4,
  },
  toolGroupTitle: {
    fontSize: 14,
    fontWeight: '600',
    color: colors.textPrimary,
  },
  workspaceRail: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    borderBottomWidth: 1,
    borderBottomColor: colors.surfaceSelected,
    marginBottom: 4,
  },
  railAction: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 7,
    minHeight: 52,
    paddingHorizontal: 4,
  },
  railDivider: {
    width: 1,
    height: 16,
    backgroundColor: colors.surfaceSelected,
  },
  railHint: { flex: 1, fontSize: 11, color: colors.textTertiary },
  safe: { flex: 1, backgroundColor: colors.background },
  content: {
    padding: 16,
    paddingBottom: 24,
    maxWidth: 560,
    width: '100%',
    alignSelf: 'center',
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    gap: 12,
    marginBottom: 24,
  },
  headerText: { flex: 1 },
  subtitle: { fontSize: 13, color: colors.textTertiary, marginTop: 6 },
  title: {
    fontSize: 30,
    fontWeight: '500',
    letterSpacing: -0.8,
    color: colors.textPrimary,
  },
  importButton: {
    flexDirection: 'row',
    gap: 6,
    alignItems: 'center',
    minHeight: 48,
    paddingHorizontal: 12,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 8,
  },
  modeBar: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 4,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 6,
    backgroundColor: colors.surfaceRaised,
    marginBottom: 12,
  },
  modes: { flexDirection: 'row', gap: 4, flex: 1 },
  mode: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    flex: 1,
    justifyContent: 'center',
    paddingHorizontal: 14,
    minHeight: 48,
    borderRadius: 6,
  },
  activeMode: {
    backgroundColor: colors.canvas,
    borderWidth: 1,
    borderColor: colors.border,
  },
  modeLabel: { fontSize: 13, color: colors.textTertiary },
  activeLabel: { color: colors.textPrimary },
  detailsButton: {
    minWidth: 48,
    minHeight: 48,
    alignItems: 'center',
    justifyContent: 'center',
  },
  workspace: {
    borderRadius: 6,
    borderWidth: 1,
    borderColor: colors.border,
    overflow: 'hidden',
    backgroundColor: colors.canvas,
  },
  stageColumn: { flex: 1 },
  canvasHeader: {
    minHeight: 32,
    paddingHorizontal: 12,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    borderBottomWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.canvas,
  },
  sectionLabel: {
    fontSize: 10,
    letterSpacing: 1.2,
    color: colors.textTertiary,
  },
  fitLabel: { fontSize: 10, color: colors.textTertiary },
  stage: {
    minHeight: 240,
    aspectRatio: 1,
    backgroundColor: colors.surfaceRaised,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 12,
  },
  canvasImage: { width: '100%', height: '100%' },
  emptyCanvas: { alignItems: 'center', paddingHorizontal: 12, gap: 12 },
  emptyIcon: {
    width: 62,
    height: 62,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
  },
  emptyTitle: { color: colors.textPrimary, fontSize: 16 },
  emptyDetail: {
    color: colors.textTertiary,
    fontSize: 12,
    textAlign: 'center',
    lineHeight: 18,
  },
  emptyAction: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    minHeight: 44,
  },
  stageFooter: {
    minHeight: 48,
    paddingHorizontal: 12,
    gap: 12,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    borderTopWidth: 1,
    borderColor: colors.border,
  },
  filename: { fontSize: 11, color: colors.textSecondary, flex: 1 },
  inspector: {
    maxHeight: '85%',
    padding: 20,
    paddingBottom: 24,
    gap: 12,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderTopLeftRadius: 14,
    borderTopRightRadius: 14,
  },
  sectionHeading: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 8,
  },
  sourceName: { fontSize: 13, lineHeight: 19, color: colors.textPrimary },
  property: { flexDirection: 'row', justifyContent: 'space-between', gap: 12 },
  muted: { fontSize: 12, color: colors.textTertiary },
  value: { fontSize: 12, color: colors.textPrimary },
  divider: { height: 1, backgroundColor: colors.border },
  help: { fontSize: 12, color: colors.textTertiary, lineHeight: 20 },
  primary: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: colors.textPrimary,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 6,
    minHeight: 54,
    paddingHorizontal: 18,
  },
  primaryLabel: { fontSize: 15, fontWeight: '600', color: colors.canvas },
  disabled: { opacity: 0.4 },
  assetHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginTop: 14,
    marginBottom: 14,
  },
  count: { fontSize: 10, color: colors.textTertiary },
  assetStrip: { gap: 8, paddingBottom: 2 },
  addAsset: {
    width: 82,
    height: 94,
    borderWidth: 1,
    borderStyle: 'dashed',
    borderColor: colors.border,
    borderRadius: 6,
    justifyContent: 'center',
    alignItems: 'center',
    gap: 6,
  },
  addLabel: { fontSize: 10, color: colors.textTertiary },
  asset: {
    width: 94,
    height: 94,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 6,
    padding: 3,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 4,
    backgroundColor: colors.surfaceRaised,
  },
  selectedAsset: { borderColor: colors.accent, borderWidth: 2 },
  assetImage: { width: '100%', height: 64, borderRadius: 5 },
  assetName: {
    fontSize: 9,
    color: colors.textSecondary,
    width: '100%',
    paddingHorizontal: 2,
  },
  libraryTitle: { fontSize: 14, color: colors.textPrimary, fontWeight: '500' },
  detailsTitle: {
    fontSize: 18,
    color: colors.textPrimary,
    fontWeight: '600',
  },
  sheetOverlay: {
    flex: 1,
    justifyContent: 'flex-end',
    backgroundColor: colors.scrim,
  },
  sheetDismiss: { flex: 1 },
  actionDock: {
    paddingHorizontal: 20,
    paddingTop: 10,
    paddingBottom: 12,
    borderTopWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.canvas,
    width: '100%',
    maxWidth: 560,
    alignSelf: 'center',
  },
  copyNote: {
    color: colors.textTertiary,
    fontSize: 11,
    textAlign: 'center',
    marginTop: 8,
    lineHeight: 18,
  },
  error: { fontSize: 12, color: colors.danger, marginTop: 12 },
});
