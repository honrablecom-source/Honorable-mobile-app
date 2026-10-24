import React from 'react';
import {
  Alert,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { colors } from '../design-system/tokens';
import { useOpenWorkspace } from '../native/openWorkspace';

/** Guest editing never mounts the account, search or paid-feature navigation. */
export function GuestEditingScreen({ onSignIn }: { onSignIn: () => void }) {
  const { busy, open, create, draw } = useOpenWorkspace('guest');
  return (
    <SafeAreaView style={s.safe}>
      <ScrollView contentContainerStyle={s.content}>
        <Text style={s.wordmark}>honorable</Text>
        <Text accessibilityRole="header" style={s.title}>
          Edit on this device.
        </Text>
        <Text style={s.copy}>
          Choose a photo or video for local editing. Your files stay on this
          device. Exports create a new copy.
        </Text>
        <View style={s.actions}>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Draw an image"
            accessibilityState={{ disabled: busy }}
            disabled={busy}
            onPress={draw}
            style={[s.button, busy && s.disabled]}
          >
            <Text style={s.buttonText}>Draw an image</Text>
          </Pressable>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Create an image"
            accessibilityState={{ disabled: busy }}
            disabled={busy}
            style={[s.button, busy && s.disabled]}
            onPress={() =>
              Alert.alert(
                'Create an image',
                'Start with a white canvas, then add text and edit. Export a copy to keep it.',
                [
                  { text: 'Square', onPress: () => create('square') },
                  { text: 'Portrait', onPress: () => create('portrait') },
                  { text: 'Landscape', onPress: () => create('landscape') },
                ],
                { cancelable: true },
              )
            }
          >
            <Text style={s.buttonText}>Create an image</Text>
          </Pressable>
          {(['IMAGE', 'VIDEO'] as const).map(kind => {
            const label = kind === 'IMAGE' ? 'Edit a photo' : 'Edit a video';
            return (
              <Pressable
                key={kind}
                accessibilityRole="button"
                accessibilityLabel={label}
                accessibilityState={{ disabled: busy }}
                disabled={busy}
                onPress={() => open(kind)}
                style={[s.button, busy && s.disabled]}
              >
                <Text style={s.buttonText}>{label}</Text>
              </Pressable>
            );
          })}
        </View>
        <Text style={s.note}>
          On-device AI tools run locally. Search, credits and account features
          require sign-in.
        </Text>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Back to sign in"
          disabled={busy}
          accessibilityState={{ disabled: busy }}
          onPress={onSignIn}
          style={[s.returnButton, busy && s.disabled]}
        >
          <Text style={s.returnText}>Back to sign in</Text>
        </Pressable>
      </ScrollView>
    </SafeAreaView>
  );
}

const s = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.canvas },
  content: { flexGrow: 1, padding: 24, paddingBottom: 40 },
  wordmark: {
    color: colors.textPrimary,
    fontSize: 23,
    fontWeight: '700',
    letterSpacing: -0.6,
  },
  title: {
    color: colors.textPrimary,
    fontSize: 36,
    lineHeight: 42,
    fontWeight: '500',
    letterSpacing: -1.2,
    marginTop: 48,
  },
  copy: {
    color: colors.textSecondary,
    fontSize: 17,
    lineHeight: 25,
    marginTop: 16,
  },
  actions: { gap: 12, marginVertical: 24 },
  button: {
    minHeight: 56,
    backgroundColor: colors.textPrimary,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: colors.border,
    alignItems: 'center',
    justifyContent: 'center',
  },
  buttonText: { fontSize: 15, fontWeight: '600', color: colors.canvas },
  note: { color: colors.textTertiary, fontSize: 13, lineHeight: 20 },
  disabled: { opacity: 0.45 },
  returnButton: {
    minHeight: 48,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 24,
  },
  returnText: { fontSize: 15, fontWeight: '600', color: colors.textPrimary },
});
