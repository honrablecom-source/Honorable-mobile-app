import React, {useEffect, useRef, useState} from 'react';
import {AppState, Platform, Pressable, StyleSheet, Text, View} from 'react-native';
import {honorableNative} from '../native/HonorableNative';

type Props = {disabled: boolean; canStart: () => boolean};

/** The native screen owns picking, verification, import review and guest-copy lifetime. */
export function VideoProjectBackupEntry({disabled, canStart}: Props) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const mounted = useRef(true);
  const request = useRef(0);
  const running = useRef(false);
  const foreground = useRef(AppState.currentState === 'active');
  useEffect(() => {
    mounted.current = true;
    const subscription = AppState.addEventListener('change', state => {
      foreground.current = state === 'active';
      if (!foreground.current) {
        request.current += 1;
        running.current = false;
        if (mounted.current) setBusy(false);
      }
    });
    return () => {
      mounted.current = false;
      request.current += 1;
      subscription.remove();
    };
  }, []);
  const open = async () => {
    if (disabled || running.current || !foreground.current || !canStart()) return;
    running.current = true;
    const id = ++request.current;
    setBusy(true);
    setError('');
    try {
      const result = await honorableNative.openVideoProjectBackups();
      if (!result || result.opened !== true) throw new Error('Video backups did not open.');
    } catch {
      if (mounted.current && foreground.current && request.current === id) {
        setError('Video backups could not open. Update the native app or try again; your files and projects were kept.');
      }
    } finally {
      if (mounted.current && request.current === id) {
        running.current = false;
        setBusy(false);
      }
    }
  };
  if (Platform.OS !== 'android') return null;
  return (
    <View style={styles.panel}>
      <Pressable accessibilityRole="button" accessibilityState={{disabled: disabled || busy}} disabled={disabled || busy} onPress={open} style={styles.button}>
        <Text style={styles.label}>{busy ? 'Opening video backups…' : 'Video project backups'}</Text>
      </Pressable>
      <Text style={styles.help}>Open a complete Noble video backup as a separate local guest copy. Create a backup in the video editor to include its editable project and original media.</Text>
      {!!error && <Text accessibilityRole="alert" style={styles.help}>{error}</Text>}
    </View>
  );
}
const styles = StyleSheet.create({
  panel: {backgroundColor: '#fff', marginHorizontal: 16, marginVertical: 8},
  button: {minHeight: 48, borderColor: '#111', borderWidth: 1, borderRadius: 8, paddingHorizontal: 12, justifyContent: 'center'},
  label: {color: '#111', fontSize: 14, fontWeight: '600'},
  help: {color: '#555', fontSize: 12, marginTop: 6, lineHeight: 18},
});
