import React, { useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Linking,
  Pressable,
  ScrollView,
  StyleSheet,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Check, ChevronRight, Images, RefreshCw } from 'lucide-react-native';
import { Text } from '@/components/ui/text';
import { useLibrary } from '../library/LibraryContext';
import { colors } from '../design-system/tokens';
const p = {
  bg: colors.canvas,
  ink: colors.textPrimary,
  muted: colors.textSecondary,
  soft: colors.surface,
  strong: colors.surface,
  line: colors.border,
  white: colors.textPrimary,
  ring: colors.border,
};
const glassDepth = {};

function Metric({
  label,
  value,
  detail,
  strong = false,
}: {
  label: string;
  value: string;
  detail: string;
  strong?: boolean;
}) {
  return (
    <View style={[s.metric, strong && s.metricStrong, glassDepth]}>
      <Text style={s.metricLabel}>{label}</Text>
      <Text style={s.metricValue}>{value}</Text>
      <Text style={s.metricDetail}>{detail}</Text>
    </View>
  );
}
function Action({
  icon,
  title,
  detail,
  onPress,
}: {
  icon: React.ReactNode;
  title: string;
  detail: string;
  onPress?: () => void;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      disabled={!onPress}
      onPress={onPress}
      style={[s.action, glassDepth, !onPress && s.disabled]}
    >
      <View style={s.actionIcon}>{icon}</View>
      <View style={s.actionCopy}>
        <Text style={s.actionTitle}>{title}</Text>
        <Text style={s.actionDetail}>{detail}</Text>
      </View>
      <ChevronRight color={p.muted} size={17} />
    </Pressable>
  );
}

export function StorageScreen() {
  const {
    status,
    loading,
    indexing,
    error,
    reindex: reindexLibrary,
  } = useLibrary();
  const [confirming, setConfirming] = useState(false);
  const reindex = () =>
    Alert.alert(
      'Reindex library?',
      'This rebuilds only Honorable’s private search index. Your original photos and videos will never be deleted.',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Reindex',
          onPress: async () => {
            setConfirming(true);
            try {
              await reindexLibrary();
            } finally {
              setConfirming(false);
            }
          },
        },
      ],
    );
  const refreshing = indexing || confirming;
  const indexed = status?.indexedCount.toLocaleString() ?? '—';
  const ready = status?.status.toLowerCase().includes('ready');
  return (
    <SafeAreaView edges={['top']} style={s.safe}>
      <ScrollView contentContainerStyle={s.content}>
        <View style={s.utilityBar}>
          <View style={s.localPill}>
            <View style={s.localDot} />
            <Text style={s.localText}>On device</Text>
          </View>
        </View>
        <View style={s.intro}>
          <Text style={s.eyebrow}>LOCAL LIBRARY</Text>
          <Text accessibilityRole="header" style={s.title}>
            Your library
          </Text>
          <Text style={[s.title, s.titleMuted]}>stays local.</Text>
          <Text style={s.subtitle}>
            See what is ready to search and manage Honorable’s private index.
          </Text>
        </View>
        {loading ? (
          <ActivityIndicator style={s.loading} color={p.ink} />
        ) : (
          <>
            <View style={s.indexCard}>
              <View style={s.progressRing}>
                <Text style={s.progressValue}>
                  {status?.total
                    ? Math.round(((status.processed ?? 0) / status.total) * 100)
                    : ready
                    ? '100'
                    : '••'}
                  <Text style={s.progressUnit}>%</Text>
                </Text>
              </View>
              <View style={s.indexCopy}>
                <Text style={s.indexLabel}>SEARCH INDEX</Text>
                <Text style={s.indexValue}>{indexed} ready</Text>
                <Text style={s.indexDetail}>
                  {status?.status ?? 'Status unavailable'}
                </Text>
              </View>
              <View style={s.check}>
                <Check color={p.ink} size={16} />
              </View>
            </View>
            <View style={s.metrics}>
              <Metric
                label="INDEXED MEDIA"
                value={indexed}
                detail="Ready to search"
              />
              <Metric
                label="STATUS"
                value={ready ? 'Ready' : 'Working'}
                detail={status?.status ?? 'Unavailable'}
                strong
              />
              <Metric
                label="INDEX STORAGE"
                value="On device"
                detail="Private database"
                strong
              />
              <Metric
                label="PHOTO ACCESS"
                value={status?.permissionGranted ? 'Granted' : 'Required'}
                detail="Managed by device"
              />
            </View>
            <View style={s.actions}>
              <Action
                icon={<RefreshCw color={p.white} size={16} />}
                title={refreshing ? 'Reindexing…' : 'Reindex library'}
                detail="Rebuild Honorable’s search index only"
                onPress={refreshing ? undefined : reindex}
              />
              <Action
                icon={<Images color={p.white} size={16} />}
                title="Manage photo access"
                detail="Choose photos and videos"
                onPress={() => Linking.openSettings()}
              />
            </View>
            <View style={[s.safeNote, glassDepth]}>
              <View style={s.safeIcon}>
                <Check color={p.white} size={14} />
              </View>
              <View style={s.safeCopy}>
                <Text style={s.safeTitle}>Your originals stay safe</Text>
                <Text style={s.safeDetail}>
                  Honorable never deletes photos or videos.
                </Text>
              </View>
              <Text style={s.cache}>Cache not measured</Text>
            </View>
          </>
        )}
        {!!error && <Text style={s.error}>{error}</Text>}
      </ScrollView>
    </SafeAreaView>
  );
}

const s = StyleSheet.create({
  safe: { flex: 1, backgroundColor: p.bg },
  content: { paddingHorizontal: 24, paddingTop: 10, paddingBottom: 104 },
  utilityBar: {
    height: 42,
    flexDirection: 'row',
    justifyContent: 'flex-end',
    alignItems: 'center',
  },
  localPill: {
    paddingHorizontal: 12,
    height: 34,
    borderRadius: 17,
    borderWidth: 1,
    borderColor: p.line,
    backgroundColor: colors.surfaceRaised,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
  },
  localDot: {
    width: 4,
    height: 4,
    borderRadius: 2,
    backgroundColor: colors.accent,
  },
  localText: { color: p.muted, fontSize: 12 },
  intro: { marginTop: 8 },
  eyebrow: {
    color: p.muted,
    fontSize: 12,
    fontWeight: '600',
    letterSpacing: 1.2,
  },
  title: {
    marginTop: 8,
    color: p.ink,
    fontSize: 32,
    lineHeight: 38,
    fontWeight: '700',
    letterSpacing: -1.8,
  },
  titleMuted: { marginTop: -2, color: '#898989' },
  subtitle: {
    maxWidth: 285,
    marginTop: 6,
    color: p.muted,
    fontSize: 12,
    lineHeight: 17,
  },
  loading: { marginTop: 90 },
  indexCard: {
    height: 136,
    marginTop: 15,
    padding: 14,
    borderRadius: 8,
    backgroundColor: colors.surfaceRaised,
    flexDirection: 'row',
    alignItems: 'center',
  },
  progressRing: {
    width: 92,
    height: 92,
    borderRadius: 46,
    borderWidth: 7,
    borderColor: p.ring,
    alignItems: 'center',
    justifyContent: 'center',
  },
  progressValue: { color: p.white, fontSize: 24, fontWeight: '700' },
  progressUnit: { fontSize: 12 },
  indexCopy: { flex: 1, paddingLeft: 15 },
  indexLabel: {
    color: '#AEB7C2',
    fontSize: 12,
    fontWeight: '600',
    letterSpacing: 1,
  },
  indexValue: { marginTop: 6, color: p.white, fontSize: 20, fontWeight: '700' },
  indexDetail: { marginTop: 5, color: '#AAAAAA', fontSize: 12 },
  check: {
    width: 29,
    height: 29,
    borderRadius: 15,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colors.surfaceRaised,
  },
  metrics: { marginTop: 9, flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  metric: {
    width: '100%',
    minHeight: 88,
    paddingHorizontal: 16,
    justifyContent: 'center',
    borderRadius: 8,
    borderWidth: 1,
    borderColor: p.line,
    backgroundColor: p.soft,
  },
  metricStrong: { backgroundColor: p.strong },
  metricLabel: {
    color: p.muted,
    fontSize: 12,
    fontWeight: '600',
    letterSpacing: 0.8,
  },
  metricValue: { marginTop: 5, color: p.ink, fontSize: 20, fontWeight: '700' },
  metricDetail: { marginTop: 3, color: p.muted, fontSize: 12 },
  actions: { marginTop: 24, gap: 8 },
  action: {
    width: '100%',
    minHeight: 72,
    padding: 8,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: p.line,
    backgroundColor: colors.surface,
    flexDirection: 'row',
    alignItems: 'center',
  },
  disabled: { opacity: 0.55 },
  actionIcon: {
    width: 38,
    height: 38,
    borderRadius: 19,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colors.surfaceRaised,
  },
  actionCopy: { flex: 1, paddingLeft: 8 },
  actionTitle: { color: p.ink, fontSize: 12, fontWeight: '700' },
  actionDetail: { marginTop: 4, color: p.muted, fontSize: 12, lineHeight: 18 },
  safeNote: {
    height: 55,
    marginTop: 9,
    paddingHorizontal: 10,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: p.line,
    backgroundColor: colors.surface,
    flexDirection: 'row',
    alignItems: 'center',
  },
  safeIcon: {
    width: 32,
    height: 32,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colors.surfaceRaised,
  },
  safeCopy: { flex: 1, paddingLeft: 8 },
  safeTitle: { color: p.ink, fontSize: 12, fontWeight: '700' },
  safeDetail: { marginTop: 3, color: p.muted, fontSize: 12 },
  cache: { color: p.muted, fontSize: 12 },
  error: { marginTop: 12, color: '#A33', fontSize: 12 },
});
