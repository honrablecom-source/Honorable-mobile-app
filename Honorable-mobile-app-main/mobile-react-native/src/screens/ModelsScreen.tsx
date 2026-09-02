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

const models: Array<{
  id: SeranModelId;
  level: string;
  name: string;
  description: string;
  batch: number;
  status: 'AVAILABLE' | 'EXPERIMENTAL' | 'COMING SOON';
}> = [
  {
    id: 'SERAN_V1',
    level: 'FAST',
    name: 'Seran V1',
    description: 'Fast photo intelligence',
    batch: 200,
    status: 'AVAILABLE',
  },
  {
    id: 'SERAN_V2',
    level: 'SMART',
    name: 'Seran V2',
    description: 'Photo + video intelligence',
    batch: 300,
    status: 'AVAILABLE',
  },
  {
    id: 'SERAN_V3',
    level: 'DEEP',
    name: 'Seran V3',
    description: 'Deep memory understanding',
    batch: 100,
    status: 'EXPERIMENTAL',
  },
  {
    id: 'SERAN_ULTRA',
    level: 'ULTRA',
    name: 'Seran Ultra',
    description: 'Maximum adaptive memory intelligence',
    batch: 500,
    status: 'COMING SOON',
  },
];

export function ModelsScreen() {
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
  const available = state?.available.includes(model.id) ?? false;
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
      <ScrollView contentContainerStyle={s.content}>
        <Text style={s.eyebrow}>SERAN INTELLIGENCE</Text>
        <Text accessibilityRole="header" style={s.title}>
          Models
        </Text>
        <Text style={s.subtitle}>
          Choose how Honorable understands your memories.
        </Text>
        {!state && !error ? (
          <ActivityIndicator color="#fff" style={s.loading} />
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
              <Text style={s.capability}>
                {model.batch}-item indexing batches
              </Text>
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
                  <ActivityIndicator color="#000" />
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
                        <Check color="#fff" size={16} />
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
              Access is verified by Honorable's trusted native entitlement
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
  safe: { flex: 1, backgroundColor: '#000' },
  content: { padding: 20, paddingBottom: 110 },
  eyebrow: {
    marginTop: 8,
    color: '#777',
    fontSize: 10,
    fontWeight: '700',
    letterSpacing: 1.8,
  },
  title: {
    marginTop: 8,
    color: '#fff',
    fontSize: 44,
    fontWeight: '700',
    letterSpacing: -1.8,
  },
  subtitle: { marginTop: 5, color: '#999', fontSize: 14 },
  loading: { marginTop: 100 },
  hero: {
    marginTop: 28,
    padding: 24,
    borderRadius: 34,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,.22)',
    backgroundColor: 'rgba(24,24,24,.88)',
  },
  heroTop: { flexDirection: 'row', justifyContent: 'space-between' },
  heroLevel: {
    color: '#888',
    fontSize: 10,
    fontWeight: '700',
    letterSpacing: 1.6,
  },
  status: { color: '#aaa', fontSize: 9, fontWeight: '700', letterSpacing: 1.2 },
  heroName: {
    marginTop: 28,
    color: '#fff',
    fontSize: 36,
    fontWeight: '700',
    letterSpacing: -1.2,
  },
  heroDescription: { marginTop: 7, color: '#aaa', fontSize: 14 },
  rule: {
    height: 1,
    marginVertical: 22,
    backgroundColor: 'rgba(255,255,255,.14)',
  },
  capability: { marginTop: 6, color: '#eee', fontSize: 13 },
  progress: { marginTop: 12, color: '#aaa', fontSize: 11 },
  select: {
    height: 52,
    marginTop: 24,
    borderRadius: 26,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#fff',
  },
  selectMuted: { backgroundColor: '#343434' },
  selectText: { color: '#000', fontSize: 13, fontWeight: '700' },
  selectTextMuted: { color: '#fff' },
  rail: {
    marginTop: 14,
    borderRadius: 28,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,.14)',
  },
  modelRow: {
    height: 70,
    paddingHorizontal: 18,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: '#090909',
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#282828',
  },
  modelRowSelected: { backgroundColor: '#1c1c1c' },
  rowLevel: {
    color: '#777',
    fontSize: 8,
    fontWeight: '700',
    letterSpacing: 1.3,
  },
  rowName: { marginTop: 4, color: '#fff', fontSize: 14, fontWeight: '600' },
  current: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  currentText: {
    color: '#aaa',
    fontSize: 8,
    fontWeight: '700',
    letterSpacing: 1,
  },
  dot: { width: 6, height: 6, borderRadius: 3, backgroundColor: '#fff' },
  authority: { margin: 14, color: '#666', fontSize: 10, lineHeight: 15 },
  error: { marginTop: 18, color: '#d88' },
});
