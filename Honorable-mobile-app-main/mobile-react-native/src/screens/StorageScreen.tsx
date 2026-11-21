import React, { useState } from 'react';
import { useNavigation } from '@react-navigation/native';
import {
  Alert,
  Linking,
  Pressable,
  ScrollView,
  StyleSheet,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { ChevronRight, Images, RefreshCw } from 'lucide-react-native';
import { Text } from '@/components/ui/text';
import {
  WorkspaceHeader,
  workspaceStyles,
} from '../design-system/WorkspaceHeader';
import { colors } from '../design-system/tokens';
import { useLibrary } from '../library/LibraryContext';

const titles: Record<string, string> = {
  READY: 'Ready to search',
  NOT_INDEXED: 'Waiting to index',
  PARTIAL_FAILURE: 'Indexed with some errors',
  FAILED: 'Indexing stopped with an error',
  CANCELLED: 'Indexing stopped',
};

export function StorageScreen() {
  const navigation = useNavigation();
  const { status, loading, indexing, error, reindex } = useLibrary();
  const [rebuilding, setRebuilding] = useState(false);
  const [actionError, setActionError] = useState('');
  const nativeWorking =
    status?.status === 'RUNNING' || status?.status === 'QUEUED';
  const working = rebuilding || indexing || nativeWorking;
  const canReindex = !!status?.permissionGranted && !loading && !working;
  const progress =
    !loading &&
    nativeWorking &&
    status?.permissionGranted &&
    status.total &&
    Number.isFinite(status.processed)
      ? Math.round(
          Math.max(0, Math.min(1, (status.processed ?? 0) / status.total)) *
            100,
        )
      : undefined;
  const state = loading
    ? 'Checking library'
    : !status
    ? 'Status unavailable'
    : !status.permissionGranted
    ? 'Photo access needed'
    : working
    ? 'Indexing your library'
    : titles[status.status] ?? 'Status unavailable';

  const confirmReindex = () => {
    if (!canReindex) return;
    Alert.alert(
      'Reindex library?',
      'Noble will rebuild its local search index. Your original photos and videos stay intact.',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Reindex',
          onPress: async () => {
            setActionError('');
            setRebuilding(true);
            try {
              await reindex();
            } catch {
              setActionError('The library could not be reindexed. Try again.');
            } finally {
              setRebuilding(false);
            }
          },
        },
      ],
    );
  };
  const openSettings = async () => {
    setActionError('');
    try {
      await Linking.openSettings();
    } catch {
      setActionError('Open your device settings to change photo access.');
    }
  };

  return (
    <SafeAreaView edges={['top']} style={workspaceStyles.screen}>
      <WorkspaceHeader
        title="Storage & indexing"
        onBack={() => navigation.goBack()}
      />
      <ScrollView contentContainerStyle={workspaceStyles.content}>
        <View style={s.summary}>
          <Text accessibilityRole="header" style={s.title}>
            Local search index
          </Text>
          <Text accessibilityLiveRegion="polite" style={s.detail}>
            {progress === undefined ? state : `${state} · ${progress}%`}
          </Text>
          {!!status && (
            <Text style={s.count}>
              {status.indexedCount.toLocaleString()} indexed items
            </Text>
          )}
        </View>
        <View style={s.actions}>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Manage photo access"
            onPress={openSettings}
            style={s.action}
          >
            <Images size={20} color={colors.textPrimary} />
            <View style={s.copy}>
              <Text style={s.label}>Manage photo access</Text>
              <Text style={s.detail}>Choose what Noble can read</Text>
            </View>
            <ChevronRight size={18} color={colors.textSecondary} />
          </Pressable>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Reindex library"
            accessibilityState={{ disabled: !canReindex, busy: working }}
            disabled={!canReindex}
            onPress={confirmReindex}
            style={[s.action, !canReindex && s.disabled]}
          >
            <RefreshCw size={20} color={colors.textPrimary} />
            <View style={s.copy}>
              <Text style={s.label}>Reindex library</Text>
              <Text style={s.detail}>Rebuild Noble’s search index</Text>
            </View>
            <ChevronRight size={18} color={colors.textSecondary} />
          </Pressable>
        </View>
        {!!(actionError || error) && (
          <Text accessibilityRole="alert" style={s.detail}>
            {actionError || error}
          </Text>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

const s = StyleSheet.create({
  summary: {
    padding: 16,
    gap: 5,
    borderWidth: 1,
    borderRadius: 16,
    borderColor: colors.border,
    backgroundColor: colors.surfaceRaised,
  },
  title: { fontSize: 21, color: colors.textPrimary, fontWeight: '700' },
  detail: { fontSize: 14, lineHeight: 20, color: colors.textSecondary },
  count: {
    fontSize: 14,
    color: colors.textPrimary,
    fontWeight: '600',
    marginTop: 8,
  },
  actions: { gap: 8 },
  action: {
    minHeight: 64,
    flexDirection: 'row',
    gap: 12,
    alignItems: 'center',
    padding: 12,
    borderWidth: 1,
    borderRadius: 12,
    borderColor: colors.border,
  },
  copy: { flex: 1, minWidth: 0, gap: 2 },
  label: { fontSize: 15, color: colors.textPrimary, fontWeight: '600' },
  disabled: { opacity: 0.55 },
});
