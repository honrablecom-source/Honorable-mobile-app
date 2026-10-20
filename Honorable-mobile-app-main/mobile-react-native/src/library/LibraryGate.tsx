import React from 'react';
import { colors } from '../design-system/tokens';
import {
  ActivityIndicator,
  Pressable,
  StyleSheet,
  Text,
  View,
  ScrollView,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Images, ShieldCheck } from 'lucide-react-native';
import { useLibrary } from './LibraryContext';
export function LibraryGate({ children }: { children: React.ReactNode }) {
  const { status, loading, indexing, error, requestAccess, reindex } =
    useLibrary();
  if (loading)
    return (
      <SafeAreaView style={s.safe}>
        <ScrollView contentContainerStyle={s.screen}>
          <ActivityIndicator color={colors.textPrimary} />
        </ScrollView>
      </SafeAreaView>
    );
  if (!status?.permissionGranted)
    return (
      <SafeAreaView style={s.safe}>
        <ScrollView contentContainerStyle={s.screen}>
          <View style={s.orb}>
            <Images color={colors.textPrimary} size={32} />
          </View>
          <Text style={s.kicker}>PRIVATE PHOTO SEARCH</Text>
          <Text style={s.title}>Find anything{`\n`}you remember.</Text>
          <Text style={s.copy}>
            Choose the photos and videos Honorable can search. Your media stays
            on this device.
          </Text>
          <Pressable
            accessibilityRole="button"
            onPress={requestAccess}
            style={s.button}
          >
            <Text style={s.buttonText}>Choose photos & videos</Text>
          </Pressable>
          <View style={s.note}>
            <ShieldCheck color={colors.textPrimary} size={17} />
            <Text style={s.noteText}>You can change access at any time.</Text>
          </View>
          {!!error && <Text style={s.error}>{error}</Text>}
        </ScrollView>
      </SafeAreaView>
    );
  if (status.indexedCount === 0)
    return (
      <SafeAreaView style={s.safe}>
        <ScrollView contentContainerStyle={s.screen}>
          <View style={s.progressOrb}>
            <Text style={s.progressText}>{indexing ? '••' : '0'}</Text>
          </View>
          <Text style={s.kicker}>LOCAL INDEX</Text>
          <Text style={s.title}>
            {indexing
              ? 'Preparing your library.'
              : 'Make your library searchable.'}
          </Text>
          <Text style={s.copy}>
            {indexing
              ? 'Honorable is analyzing accessible media on this device.'
              : 'Build a private index of the photos and videos you selected.'}
          </Text>
          <Pressable
            accessibilityRole="button"
            disabled={indexing}
            onPress={reindex}
            style={[s.button, indexing && s.disabled]}
          >
            <Text style={s.buttonText}>
              {indexing ? 'Indexing…' : 'Start indexing'}
            </Text>
          </Pressable>
          {!!error && <Text style={s.error}>{error}</Text>}
        </ScrollView>
      </SafeAreaView>
    );
  return <>{children}</>;
}
const s = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.canvas },
  screen: {
    flexGrow: 1,
    paddingVertical: 32,
    paddingHorizontal: 24,
    justifyContent: 'center',
    backgroundColor: colors.canvas,
  },
  orb: {
    width: 74,
    height: 74,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: colors.border,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colors.surfaceRaised,
  },
  progressOrb: {
    width: 104,
    height: 104,
    borderRadius: 52,
    borderWidth: 2,
    borderColor: colors.border,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colors.surfaceRaised,
  },
  progressText: { color: colors.textPrimary, fontSize: 28, fontWeight: '500' },
  kicker: {
    marginTop: 28,
    color: colors.textSecondary,
    fontSize: 10,
    fontWeight: '500',
    letterSpacing: 1.4,
  },
  title: {
    marginTop: 10,
    color: colors.textPrimary,
    fontSize: 32,
    lineHeight: 38,
    fontWeight: '500',
    letterSpacing: -1.4,
  },
  copy: {
    maxWidth: 340,
    marginTop: 14,
    color: colors.textSecondary,
    fontSize: 15,
    lineHeight: 22,
  },
  button: {
    height: 56,
    borderWidth: 1,
    borderColor: colors.border,
    marginTop: 28,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colors.textPrimary,
  },
  disabled: { opacity: 0.55 },
  buttonText: { color: colors.canvas, fontSize: 14, fontWeight: '500' },
  note: {
    marginTop: 18,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
  },
  noteText: { color: colors.textSecondary, fontSize: 12 },
  error: { marginTop: 14, color: colors.danger, textAlign: 'center' },
});
