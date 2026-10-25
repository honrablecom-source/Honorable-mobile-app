import { colors } from '../design-system/tokens';
import { WorkspaceHeader } from '../design-system/WorkspaceHeader';
import { useNavigation } from '@react-navigation/native';
import React, { useEffect, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Pressable,
  ScrollView,
  StyleSheet,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Check, LockKeyhole } from 'lucide-react-native';
import { Text } from '@/components/ui/text';
import {
  honorableNative,
  SeranModelId,
  SeranModelState,
} from '../native/HonorableNative';

import { seranCreditCosts } from '../passes/catalog';
const models = seranCreditCosts.map(m => ({
  id: m.model,
  level: m.name,
  name: m.name,
  description: m.description,
  batch: 0,
  status: m.available ? 'AVAILABLE' : 'COMING SOON',
}));

export function ModelsScreen() {
  const navigation = useNavigation();
  const [state, setState] = useState<SeranModelState>();
  const [preview, setPreview] = useState<SeranModelId>();
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  useEffect(() => {
    honorableNative
      .getSeranModelState()
      .then(value => {
        setState(value);
        setPreview(value.selected);
      })
      .catch(reason =>
        setError(
          reason instanceof Error ? reason.message : 'Models unavailable',
        ),
      );
  }, []);
  useEffect(() => {
    if (state?.enrichmentStatus !== 'PREPARING_VIDEO_INTELLIGENCE') return;
    const timer = setInterval(
      () =>
        honorableNative
          .getSeranModelState()
          .then(setState)
          .catch(() => undefined),
      750,
    );
    return () => clearInterval(timer);
  }, [state?.enrichmentStatus]);
  const model = useMemo(
    () =>
      models.find(item => item.id === (preview ?? state?.selected)) ??
      models[0],
    [preview, state],
  );
  const available =
    model.status === 'AVAILABLE' &&
    (state?.available.includes(model.id) ?? false);
  const select = async () => {
    if (!available || model.id === state?.selected) return;
    setSaving(true);
    setError('');
    try {
      setState(await honorableNative.selectSeranModel(model.id));
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Model unavailable');
    } finally {
      setSaving(false);
    }
  };
  return (
    <SafeAreaView edges={['top']} style={s.safe}>
      <WorkspaceHeader
        title="Seran models"
        onBack={() => navigation.goBack()}
      />
      <ScrollView contentContainerStyle={s.content}>
        <Text style={s.subtitle}>
          Choose how Noble understands your memories.
        </Text>
        {!state && !error ? (
          <ActivityIndicator color={colors.textPrimary} style={s.loading} />
        ) : (
          <>
            <View style={s.hero}>
              <View style={s.heroTop}>
                <Text style={s.heroLevel}>{model.level}</Text>
                <Text style={s.status}>{model.status}</Text>
              </View>
              <Text style={s.heroName}>{model.name}</Text>
              <Text style={s.heroDescription}>{model.description}</Text>
              <View style={s.rule} />
              <Text style={s.capability}>{model.description}</Text>

              {model.id === state?.selected &&
                state.enrichmentStatus === 'PREPARING_VIDEO_INTELLIGENCE' && (
                  <Text style={s.progress}>
                    Preparing video intelligence · {state.enrichmentProcessed} /{' '}
                    {state.enrichmentTotal}
                  </Text>
                )}
              <Pressable
                accessibilityRole="button"
                disabled={!available || saving || model.id === state?.selected}
                onPress={select}
                style={[
                  s.select,
                  (!available || model.id === state?.selected) && s.selectMuted,
                ]}
              >
                {saving ? (
                  <ActivityIndicator color={colors.canvas} />
                ) : (
                  <Text
                    style={[
                      s.selectText,
                      (!available || model.id === state?.selected) &&
                        s.selectTextMuted,
                    ]}
                  >
                    {model.id === state?.selected
                      ? 'Current'
                      : model.status === 'COMING SOON'
                      ? 'Coming soon'
                      : model.status === 'EXPERIMENTAL'
                      ? 'Experimental — unavailable'
                      : available
                      ? 'Use this model'
                      : 'Not included in your plan'}
                  </Text>
                )}
              </Pressable>
            </View>
            <View style={s.rail}>
              {models.map(item => {
                const isSelected = item.id === model.id;
                const current = item.id === state?.selected;
                const unlocked = state?.available.includes(item.id);
                return (
                  <Pressable
                    key={item.id}
                    accessibilityRole="button"
                    onPress={() => setPreview(item.id)}
                    style={[s.modelRow, isSelected && s.modelRowSelected]}
                  >
                    <View>
                      <Text style={s.rowLevel}>{item.level}</Text>
                      <Text style={s.rowName}>{item.name}</Text>
                    </View>
                    {current ? (
                      <View style={s.current}>
                        <Text style={s.currentText}>CURRENT</Text>
                        <Check color={colors.textPrimary} size={16} />
                      </View>
                    ) : !unlocked ? (
                      <LockKeyhole color="#777" size={16} />
                    ) : (
                      <View style={s.dot} />
                    )}
                  </Pressable>
                );
              })}
            </View>
            <Text style={s.authority}>
              Access is verified by Noble's trusted native entitlement
              system. Search mode remains a separate per-query control.
            </Text>
          </>
        )}
        {!!error && <Text style={s.error}>{error}</Text>}
      </ScrollView>
    </SafeAreaView>
  );
}

const s = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.canvas },
  content: { padding: 20, paddingBottom: 110 },
  eyebrow: {
    marginTop: 8,
    color: colors.textSecondary,
    fontSize: 12,
    fontWeight: '700',
    letterSpacing: 1.8,
  },
  title: {
    marginTop: 8,
    color: colors.textPrimary,
    fontSize: 44,
    fontWeight: '700',
    letterSpacing: -1.8,
  },
  subtitle: { marginTop: 5, color: colors.textSecondary, fontSize: 14 },
  loading: { marginTop: 100 },
  hero: {
    marginTop: 28,
    padding: 24,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.canvas,
  },
  heroTop: { flexDirection: 'row', justifyContent: 'space-between' },
  heroLevel: {
    color: colors.textSecondary,
    fontSize: 12,
    fontWeight: '700',
    letterSpacing: 1.6,
  },
  status: {
    color: colors.textSecondary,
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 1.2,
  },
  heroName: {
    marginTop: 28,
    color: colors.textPrimary,
    fontSize: 28,
    fontWeight: '700',
    letterSpacing: -1.2,
  },
  heroDescription: { marginTop: 7, color: colors.textSecondary, fontSize: 14 },
  rule: {
    height: 1,
    marginVertical: 22,
    backgroundColor: colors.border,
  },
  capability: { marginTop: 6, color: colors.textSecondary, fontSize: 13 },
  progress: { marginTop: 12, color: colors.textSecondary, fontSize: 11 },
  select: {
    height: 52,
    marginTop: 24,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colors.textPrimary,
  },
  selectMuted: { backgroundColor: colors.textPrimary, opacity: 0.4 },
  selectText: { color: colors.canvas, fontSize: 13, fontWeight: '700' },
  selectTextMuted: { color: colors.canvas },
  rail: {
    marginTop: 14,
    borderRadius: 12,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: colors.border,
  },
  modelRow: {
    height: 70,
    paddingHorizontal: 18,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: colors.canvas,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.border,
  },
  modelRowSelected: { backgroundColor: colors.surfaceSelected },
  rowLevel: {
    color: colors.textSecondary,
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 1.3,
  },
  rowName: {
    marginTop: 4,
    color: colors.textPrimary,
    fontSize: 14,
    fontWeight: '600',
  },
  current: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  currentText: {
    color: colors.textSecondary,
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 1,
  },
  dot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: colors.textPrimary,
  },
  authority: { margin: 14, color: '#666', fontSize: 12, lineHeight: 15 },
  error: { marginTop: 18, color: colors.textPrimary },
});
