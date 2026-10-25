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
import {
  Image as Photo,
  Film,
  PenLine,
  Plus,
  ArrowUpRight,
} from 'lucide-react-native';

/** Guest editing never mounts the account, search or paid-feature navigation. */
export function GuestEditingScreen({ onSignIn }: { onSignIn: () => void }) {
  const { busy, open, create, draw } = useOpenWorkspace('guest');
  return (
    <SafeAreaView style={s.safe}>
      <ScrollView contentContainerStyle={s.content}>
        <Text style={s.wordmark}>Noble</Text>
        <Text accessibilityRole="header" style={s.title}>
          {'Your ideas.\nYour studio.'}
        </Text>
        <Text style={s.copy}>
          Make something from a moment you already have.
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
            <View style={s.cardTop}>
              <PenLine size={25} color={colors.textPrimary} strokeWidth={1.5} />
              <ArrowUpRight size={16} color={colors.textSecondary} />
            </View>
            <Text style={s.buttonText}>Draw an image</Text>
            <Text style={s.cardDetail}>A blank page for your ideas</Text>
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
            <View style={s.cardTop}>
              <Plus size={25} color={colors.textPrimary} strokeWidth={1.5} />
              <ArrowUpRight size={16} color={colors.textSecondary} />
            </View>
            <Text style={s.buttonText}>Create an image</Text>
            <Text style={s.cardDetail}>Choose a canvas. Make it yours.</Text>
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
                <View style={s.cardTop}>
                  {kind === 'IMAGE' ? (
                    <Photo
                      size={25}
                      color={colors.textPrimary}
                      strokeWidth={1.5}
                    />
                  ) : (
                    <Film
                      size={25}
                      color={colors.textPrimary}
                      strokeWidth={1.5}
                    />
                  )}
                  <ArrowUpRight size={16} color={colors.textSecondary} />
                </View>
                <Text style={s.buttonText}>{label}</Text>
                <Text style={s.cardDetail}>
                  {kind === 'IMAGE'
                    ? 'Light, color & a fresh look'
                    : 'Clips, captions & sound'}
                </Text>
              </Pressable>
            );
          })}
        </View>
        <Text style={s.note}>
          Your files stay here. Exports create a new copy. Free on-device AI is
          included; search and account features require sign-in.
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
    marginTop: 28,
  },
  copy: {
    color: colors.textSecondary,
    fontSize: 14,
    lineHeight: 21,
    marginTop: 12,
  },
  actions: {
    gap: 12,
    marginVertical: 26,
    flexDirection: 'row',
    flexWrap: 'wrap',
  },
  button: {
    minHeight: 148,
    width: '47%',
    flexGrow: 1,
    backgroundColor: colors.canvas,
    borderRadius: 18,
    borderWidth: 1,
    borderColor: colors.border,
    padding: 15,
    alignItems: 'stretch',
    justifyContent: 'center',
  },
  cardTop: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 24,
  },
  cardDetail: {
    fontSize: 11,
    lineHeight: 16,
    color: colors.textSecondary,
    marginTop: 5,
  },
  buttonText: { fontSize: 15, fontWeight: '600', color: colors.textPrimary },
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
