import React, { useEffect, useRef, useState } from 'react';
import { colors } from '../design-system/tokens';
import {
  ActivityIndicator,
  Linking,
  Pressable,
  StyleSheet,
  Text,
  View,
  ScrollView,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Images, ShieldCheck } from 'lucide-react-native';
import { useLibrary } from './LibraryContext';
import { useOpenWorkspace } from '../native/openWorkspace';
import { StudioScreen } from '../screens/StudioScreen';

function AccessRecovery({ onCheck }: { onCheck: () => Promise<void> }) {
  const [busy, setBusy] = useState<'settings' | 'check' | null>(null);
  const [error, setError] = useState('');
  const pending = useRef(false),
    active = useRef(true);
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
    };
  }, []);
  const run = async (action: 'settings' | 'check') => {
    if (!active.current || pending.current) return;
    pending.current = true;
    setBusy(action);
    setError('');
    try {
      if (action === 'settings') await Linking.openSettings();
      else await onCheck();
    } catch {
      if (active.current)
        setError(
          action === 'settings'
            ? 'Could not open device settings. Open Settings on your device and choose Noble, then return.'
            : 'Could not check photo access. Try again.',
        );
    } finally {
      pending.current = false;
      if (active.current) setBusy(null);
    }
  };
  return (
    <View testID="library-access-recovery" style={s.accessRecovery}>
      <Text style={s.noteText}>
        If the permission prompt no longer appears, change photo access in
        Noble’s device settings. Returning checks access again.
      </Text>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Open device settings"
        accessibilityState={{
          disabled: busy !== null,
          busy: busy === 'settings',
        }}
        disabled={busy !== null}
        onPress={() => run('settings')}
        style={s.editorButton}
      >
        {busy === 'settings' ? (
          <ActivityIndicator color={colors.textPrimary} />
        ) : (
          <Text style={s.editorText}>Open device settings</Text>
        )}
      </Pressable>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Check photo access again"
        accessibilityState={{ disabled: busy !== null, busy: busy === 'check' }}
        disabled={busy !== null}
        onPress={() => run('check')}
        style={s.editorButton}
      >
        {busy === 'check' ? (
          <ActivityIndicator color={colors.textPrimary} />
        ) : (
          <Text style={s.editorText}>Check access again</Text>
        )}
      </Pressable>
      {!!error && (
        <Text accessibilityRole="alert" style={s.error}>
          {error}
        </Text>
      )}
    </View>
  );
}

function StudioEntry({
  onOpen,
  disabled = false,
}: {
  onOpen: () => void;
  disabled?: boolean;
}) {
  return (
    <View style={s.editors}>
      <Text style={s.noteText}>
        Draw, create a blank image, or reopen a saved project without scanning
        your library.
      </Text>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Open Studio without library setup"
        accessibilityState={{ disabled }}
        disabled={disabled}
        onPress={onOpen}
        style={[s.editorButton, disabled && s.disabled]}
      >
        <Text style={s.editorText}>Open Studio</Text>
      </Pressable>
    </View>
  );
}
function DirectEditors({ onStudio }: { onStudio: () => void }) {
  const { busy, open } = useOpenWorkspace();
  return (
    <View style={s.editors}>
      <Text style={s.noteText}>
        You can also edit one file without indexing your library.
      </Text>
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
            style={[s.editorButton, busy && s.disabled]}
          >
            <Text style={s.editorText}>{label}</Text>
          </Pressable>
        );
      })}
      <StudioEntry onOpen={onStudio} disabled={busy} />
    </View>
  );
}

export function LibraryGate({ children }: { children: React.ReactNode }) {
  const [studioOpen, setStudioOpen] = useState(false);
  const [accessAttempted, setAccessAttempted] = useState(false);
  const { status, loading, indexing, error, requestAccess, reindex, reload } =
    useLibrary();
  if (studioOpen)
    return <StudioScreen onReturnToLibrary={() => setStudioOpen(false)} />;
  if (loading)
    return (
      <SafeAreaView style={s.safe}>
        <ScrollView contentContainerStyle={s.screen}>
          <ActivityIndicator color={colors.textPrimary} />
          <StudioEntry onOpen={() => setStudioOpen(true)} />
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
            Choose the photos and videos Noble can search. Your media stays on
            this device.
          </Text>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Choose photos & videos"
            onPress={() => {
              setAccessAttempted(true);
              return requestAccess();
            }}
            style={s.button}
          >
            <Text style={s.buttonText}>Choose photos & videos</Text>
          </Pressable>
          <View style={s.note}>
            <ShieldCheck color={colors.textPrimary} size={17} />
            <Text style={s.noteText}>You can change access at any time.</Text>
          </View>
          {accessAttempted && <AccessRecovery onCheck={reload} />}
          <DirectEditors onStudio={() => setStudioOpen(true)} />
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
              ? 'Noble is analyzing accessible media on this device.'
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
          <DirectEditors onStudio={() => setStudioOpen(true)} />
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
  editors: { marginTop: 24, gap: 10 },
  accessRecovery: {
    marginTop: 20,
    padding: 12,
    gap: 10,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 12,
    backgroundColor: colors.surfaceRaised,
  },
  editorButton: {
    minHeight: 48,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colors.canvas,
  },
  editorText: { color: colors.textPrimary, fontSize: 14, fontWeight: '500' },
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
