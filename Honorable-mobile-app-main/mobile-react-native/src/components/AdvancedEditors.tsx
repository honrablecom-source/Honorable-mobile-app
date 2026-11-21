import React, {useEffect, useRef, useState} from 'react';
import {AppState, NativeModules, Pressable, StyleSheet, Text, View} from 'react-native';
import {colors} from '../design-system/tokens';
type Kind = 'image' | 'video';
const photoTools = [
  { id: 'subject', title: 'Color just the subject', detail: 'Detect the foreground and adjust its color locally.' },
  { id: 'object', title: 'Remove background', detail: 'Keep the subject. Refine the edges.' },
  { id: 'color', title: 'Adjust color and tone', detail: 'Review a learned correction and set its strength.' },
  { id: 'denoise', title: 'Reduce photo noise', detail: 'Clean up a small working copy.' },
  { id: 'style', title: 'Turn a photo into a painting', detail: 'Create a small Mosaic artwork.' },
] as const;
type PhotoTool = typeof photoTools[number]['id'];
type CreativeModule = {
  getCapabilities: () => Promise<{image?: boolean; video?: boolean}>;
  openEditor: (kind: Kind) => Promise<boolean>;
  openImageAi?: (tool: PhotoTool) => Promise<boolean>;
};

/** Entries require verified packaged native bundles, not merely named commands. */
export function AdvancedEditors({busy = false, featured = false}: {busy?: boolean; featured?: boolean}) {
  const native = NativeModules.NobleCreative as CreativeModule | undefined;
  const available = typeof native?.getCapabilities === 'function' && typeof native?.openEditor === 'function';
  const [caps, setCaps] = useState<{image: boolean; video: boolean} | null>(null);
  const [opening, setOpening] = useState<Kind | PhotoTool | null>(null), [error, setError] = useState('');
  const mounted = useRef(false), pending = useRef(false);
  useEffect(() => {
    mounted.current = true; let current = true;
    if (available) native!.getCapabilities().then(value => {
      if (current && mounted.current) setCaps({image: value?.image === true, video: value?.video === true});
    }).catch(() => {if (current && mounted.current) setCaps(null);});
    return () => {current = false; mounted.current = false; pending.current = false;};
  }, [available, native]);
  useEffect(() => {
    if (!available) return;
    const subscription = AppState.addEventListener('change', state => {
      if (state === 'active' && mounted.current) {pending.current = false; setOpening(null);}
    });
    return () => subscription.remove();
  }, [available]);
  const open = async (kind: Kind, tool?: PhotoTool) => {
    if (!native || !mounted.current || pending.current || busy || !caps?.[kind]) return;
    if (tool && typeof native.openImageAi !== 'function') return;
    pending.current = true; setOpening(tool ?? kind); setError('');
    try {
      const opened = tool ? await native.openImageAi?.(tool) : await native.openEditor(kind);
      if (opened !== true) throw new Error('The editor did not open. Existing projects are kept.');
      // Retain ownership until returning from the native editor: no duplicate launch.
    } catch (failure) {
      if (mounted.current) {pending.current = false; setOpening(null); setError(failure instanceof Error ? failure.message : 'The editor could not open. Existing projects are kept.');}
    }
  };
  if (featured) {
    if (!caps?.image || typeof native?.openImageAi !== 'function') return null;
    const disabled = busy || opening !== null;
    return (
      <View style={s.feature}>
        <Text accessibilityRole="header" style={s.featureTitle}>On-device photo AI</Text>
        <Text style={s.featureCopy}>Compare the result before saving a copy.</Text>
        <View style={s.toolList}>
          {photoTools.map(({id, title, detail}, index) => (
            <Pressable
              key={id}
              accessibilityRole="button"
              accessibilityLabel={title}
              accessibilityHint={detail}
              accessibilityState={{disabled, busy: opening === id}}
              disabled={disabled}
              onPress={() => {open('image', id);}}
              style={[s.tool, index === 0 && s.primaryTool, disabled && s.disabled]}
            >
              <View style={s.toolCopy}>
                <Text style={[s.toolTitle, index === 0 && s.primaryText]}>{opening === id ? 'Opening…' : title}</Text>
                <Text style={[s.copy, index === 0 && s.primaryDetail]}>{detail}</Text>
              </View>
              <Text importantForAccessibility="no" style={[s.chevron, index === 0 && s.primaryText]}>›</Text>
            </Pressable>
          ))}
        </View>
        {!!error && <Text accessibilityRole="alert" style={s.copy}>{error}</Text>}
      </View>
    );
  }
  if (!available || !caps || (!caps.image && !caps.video)) return null;
  return <View style={s.section}>
    <Text accessibilityRole="header" style={s.heading}>Advanced studios · preview</Text>
    <Text style={s.copy}>Layered images and multi-track video, using local files. Save your project before closing.</Text>
    {(['image', 'video'] as const).filter(kind => caps[kind]).map(kind => <Pressable key={kind}
      accessibilityRole="button" accessibilityLabel={kind === 'image' ? 'Open image studio' : 'Open video studio'}
      accessibilityState={{disabled: busy || opening !== null}} disabled={busy || opening !== null}
      style={[s.button, (busy || opening !== null) && s.disabled]} onPress={() => {open(kind);}}>
      <Text style={s.label}>{opening === kind ? 'Opening…' : kind === 'image' ? 'Image studio' : 'Video studio'}</Text>
      <Text style={s.copy}>{kind === 'image' ? 'Layers, masks, painting and image projects' : 'Timeline, grading, sound and video projects'}</Text>
    </Pressable>)}
    {!!error && <Text accessibilityRole="alert" style={s.copy}>{error}</Text>}
    <Text style={s.small}>Local recovery and imported copies may remain on this device after sign-out. Shared storage is not protected by an account.</Text>
  </View>;
}
const s = StyleSheet.create({
  feature: {backgroundColor: colors.surface, borderRadius: 20, borderWidth: 1, borderColor: colors.border, padding: 16, gap: 8},
  featureTitle: {fontSize: 24, fontWeight: '700', letterSpacing: -0.7, color: colors.textPrimary},
  featureCopy: {fontSize: 14, lineHeight: 21, color: colors.textSecondary},
  toolList: {gap: 6, marginTop: 4},
  tool: {borderRadius: 12, minHeight: 56, paddingHorizontal: 12, paddingVertical: 9, backgroundColor: colors.canvas, flexDirection: 'row', alignItems: 'center', gap: 12},
  primaryTool: {backgroundColor: '#171717'},
  primaryText: {color: '#fff'},
  primaryDetail: {color: '#e0e0e0'},
  toolCopy: {flex: 1, gap: 2},
  toolTitle: {color: colors.textPrimary, fontWeight: '700', fontSize: 15},
  chevron: {fontSize: 23, color: colors.textSecondary},
  section: {gap: 12, marginVertical: 16}, heading: {fontSize: 22, fontWeight: '600', color: colors.textPrimary},
  copy: {fontSize: 14, lineHeight: 21, color: colors.textSecondary}, small: {fontSize: 12, lineHeight: 18, color: colors.textSecondary},
  button: {padding: 18, minHeight: 76, borderRadius: 16, borderWidth: 1, borderColor: colors.border, backgroundColor: colors.surface, gap: 6},
  label: {fontSize: 18, fontWeight: '600', color: colors.textPrimary}, disabled: {opacity: 0.45},
});
