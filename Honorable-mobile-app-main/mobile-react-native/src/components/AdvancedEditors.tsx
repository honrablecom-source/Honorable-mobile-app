import React, {useEffect, useRef, useState} from 'react';
import {AppState, NativeModules, Pressable, StyleSheet, Text, View} from 'react-native';
import {colors} from '../design-system/tokens';
type Kind = 'image' | 'video';
type CreativeModule = {getCapabilities: () => Promise<{image?: boolean; video?: boolean}>; openEditor: (kind: Kind) => Promise<boolean>};

/** Entries require verified packaged native bundles, not merely named commands. */
export function AdvancedEditors({busy = false}: {busy?: boolean}) {
  const native = NativeModules.NobleCreative as CreativeModule | undefined;
  const available = typeof native?.getCapabilities === 'function' && typeof native?.openEditor === 'function';
  const [caps, setCaps] = useState<{image: boolean; video: boolean} | null>(null);
  const [opening, setOpening] = useState<Kind | null>(null), [error, setError] = useState('');
  const mounted = useRef(false), pending = useRef(false);
  useEffect(() => {
    mounted.current = true; let current = true;
    if (available) native!.getCapabilities().then(value => {
      if (current && mounted.current) setCaps({image: value?.image === true, video: value?.video === true});
    }).catch(() => {if (current && mounted.current) setCaps(null);});
    return () => {current = false; mounted.current = false; pending.current = false;};
  }, [available, native]);
  useEffect(() => {
    const subscription = AppState.addEventListener('change', state => {
      if (state === 'active' && mounted.current) {pending.current = false; setOpening(null);}
    });
    return () => subscription.remove();
  }, []);
  const open = async (kind: Kind) => {
    if (!native || !mounted.current || pending.current || busy || !caps?.[kind]) return;
    pending.current = true; setOpening(kind); setError('');
    try {
      if (await native.openEditor(kind) !== true) throw new Error('The editor did not open. Existing projects are kept.');
      // Retain ownership until returning from the native editor: no duplicate launch.
    } catch (failure) {
      if (mounted.current) {pending.current = false; setOpening(null); setError(failure instanceof Error ? failure.message : 'The editor could not open. Existing projects are kept.');}
    }
  };
  if (!available || !caps || (!caps.image && !caps.video)) return null;
  return <View style={s.section}>
    <Text accessibilityRole="header" style={s.heading}>Advanced studios</Text>
    <Text style={s.copy}>Layered images and multi-track video, using local files. Save your project before closing.</Text>
    {(['image', 'video'] as const).filter(kind => caps[kind]).map(kind => <Pressable key={kind}
      accessibilityRole="button" accessibilityLabel={kind === 'image' ? 'Open image studio' : 'Open video studio'}
      accessibilityState={{disabled: busy || opening !== null}} disabled={busy || opening !== null}
      style={[s.button, (busy || opening !== null) && s.disabled]} onPress={() => {void open(kind);}}>
      <Text style={s.label}>{opening === kind ? 'Opening…' : kind === 'image' ? 'Image studio' : 'Video studio'}</Text>
      <Text style={s.copy}>{kind === 'image' ? 'Layers, masks, painting and image projects' : 'Timeline, grading, sound and video projects'}</Text>
    </Pressable>)}
    {!!error && <Text accessibilityRole="alert" style={s.copy}>{error}</Text>}
    <Text style={s.small}>Local recovery and imported copies may remain on this device after sign-out. Shared storage is not protected by an account.</Text>
  </View>;
}
const s = StyleSheet.create({
  section: {gap: 12, marginVertical: 16}, heading: {fontSize: 22, fontWeight: '600', color: colors.textPrimary},
  copy: {fontSize: 14, lineHeight: 21, color: colors.textSecondary}, small: {fontSize: 12, lineHeight: 18, color: colors.textSecondary},
  button: {padding: 18, minHeight: 76, borderRadius: 16, borderWidth: 1, borderColor: colors.border, backgroundColor: colors.surface, gap: 6},
  label: {fontSize: 18, fontWeight: '600', color: colors.textPrimary}, disabled: {opacity: 0.45},
});
