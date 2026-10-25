import { colors } from '../design-system/tokens';
import { WorkspaceHeader } from '../design-system/WorkspaceHeader';
import React, { useCallback, useState } from 'react';
import {
  Alert,
  Pressable,
  ScrollView,
  StyleSheet,
  Switch,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useFocusEffect, useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { ChevronRight, ShieldCheck } from 'lucide-react-native';
import { Text } from '@/components/ui/text';
import {
  honorableNative,
  ImprovementProgramState,
} from '../native/HonorableNative';
import type { RootStackParamList } from '../navigation/types';

const draft = 'DRAFT — REQUIRES LEGAL REVIEW';
function Shell({
  title,
  children,
}: {
  title: string;
  children: React.ReactNode;
}) {
  const navigation = useNavigation();
  return (
    <SafeAreaView style={s.safe}>
      <WorkspaceHeader
        title={title}
        section="Noble / privacy"
        onBack={() => navigation.goBack()}
      />
      <ScrollView contentContainerStyle={s.content}>{children}</ScrollView>
    </SafeAreaView>
  );
}
function LinkRow({
  title,
  detail,
  onPress,
}: {
  title: string;
  detail: string;
  onPress: () => void;
}) {
  return (
    <Pressable accessibilityRole="button" onPress={onPress} style={s.row}>
      <View style={s.rowCopy}>
        <Text style={s.rowTitle}>{title}</Text>
        <Text style={s.rowDetail}>{detail}</Text>
      </View>
      <ChevronRight color={colors.textSecondary} size={18} />
    </Pressable>
  );
}

export function PrivacyDataScreen() {
  const navigation =
    useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  const [state, setState] = useState<ImprovementProgramState>();
  useFocusEffect(
    useCallback(() => {
      honorableNative
        .getImprovementProgramState()
        .then(setState)
        .catch(() => undefined);
    }, []),
  );
  const toggle = (enabled: boolean) => {
    if (enabled) navigation.navigate('SeranConsent');
    else
      Alert.alert(
        'Pause contributions?',
        'Future contributions stop immediately. Your local search index is unchanged.',
        [
          { text: 'Cancel', style: 'cancel' },
          {
            text: 'Pause',
            style: 'destructive',
            onPress: () =>
              honorableNative
                .setImprovementProgramConsent(false)
                .then(setState),
          },
        ],
      );
  };
  return (
    <Shell title="Privacy & Data">
      <View style={s.local}>
        <ShieldCheck color={colors.textPrimary} size={22} />
        <View style={s.localCopy}>
          <Text style={s.localTitle}>Search remains local</Text>
          <Text style={s.localDetail}>
            Normal indexing and memory search stay on this device.
          </Text>
        </View>
      </View>
      <View style={s.program}>
        <View style={s.switchRow}>
          <View>
            <Text style={s.programTitle}>Help improve Seran</Text>
            <Text style={s.programStatus}>
              {state?.enabled ? 'ON' : 'OFF'} · Optional
            </Text>
          </View>
          <Switch
            value={state?.enabled ?? false}
            onValueChange={toggle}
            trackColor={{
              false: colors.surfaceSelected,
              true: colors.textPrimary,
            }}
            thumbColor={colors.canvas}
          />
        </View>
        <Text style={s.programCopy}>
          Separate opt-in contribution program. It is never required to use
          Noble.
        </Text>
      </View>
      <View style={s.rows}>
        <LinkRow
          title="Contribution status"
          detail={state?.backendStatus ?? 'Loading…'}
          onPress={() => navigation.navigate('SeranImprovement')}
        />
        <LinkRow
          title="What may be shared"
          detail="Eligibility and sensitive-media safeguards"
          onPress={() => navigation.navigate('SeranImprovement')}
        />
        <LinkRow
          title="Privacy information"
          detail="Consent, retention, withdrawal, and deletion"
          onPress={() => navigation.navigate('SeranImprovement')}
        />
      </View>
      {state?.enabled && (
        <Pressable
          accessibilityRole="button"
          onPress={() => toggle(false)}
          style={s.danger}
        >
          <Text style={s.dangerText}>Pause contributions</Text>
        </Pressable>
      )}
      <Text style={s.version}>
        Consent: {state?.consentVersion ?? 'not accepted'} · Policy:{' '}
        {state?.policyVersion ?? 'privacy-draft-v1'}
      </Text>
    </Shell>
  );
}

export function SeranConsentScreen() {
  const navigation = useNavigation();
  const [working, setWorking] = useState(false);
  const allow = async () => {
    setWorking(true);
    try {
      await honorableNative.setImprovementProgramConsent(true);
      navigation.goBack();
    } finally {
      setWorking(false);
    }
  };
  return (
    <Shell title="Help improve Seran">
      <Text style={s.lead}>
        If you choose to participate, Noble may securely send eligible media
        and associated analysis data to Noble to help improve future Seran
        models.
      </Text>
      <Text style={s.body}>
        Normal memory search remains on your device. Participation is optional.
        Sensitive media is excluded by a conservative eligibility policy, and
        the current app has no configured upload backend.
      </Text>
      <Text style={s.draft}>{draft}</Text>
      <Pressable
        accessibilityRole="button"
        disabled={working}
        onPress={allow}
        style={s.allow}
      >
        <Text style={s.allowText}>
          {working ? 'Saving…' : 'Allow contribution'}
        </Text>
      </Pressable>
      <Pressable
        accessibilityRole="button"
        onPress={() => navigation.goBack()}
        style={s.notNow}
      >
        <Text style={s.notNowText}>Not now</Text>
      </Pressable>
    </Shell>
  );
}

export function SeranImprovementScreen() {
  const navigation =
    useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  return (
    <Shell title="Seran Improvement Program">
      <Text style={s.draft}>{draft}</Text>
      <Text style={s.sectionTitle}>What is collected</Text>
      <Text style={s.body}>
        Only eligible media separately contributed after explicit consent, plus
        minimized analysis and quality metadata. Names, email, contacts,
        original filenames, exact GPS, and unrelated EXIF are not needed.
      </Text>
      <Text style={s.sectionTitle}>Protection and control</Text>
      <Text style={s.body}>
        Contribution identity is pseudonymous. Future uploads require
        authenticated, short-lived authorization and private object storage. You
        can pause future contributions and request deletion where supported.
      </Text>
      <Text style={s.sectionTitle}>Deletion limits</Text>
      <Text style={s.body}>
        Raw media and dataset records may be deleted where technically and
        legally applicable. Completed training runs may not support model
        unlearning; future training exclusion is separate.
      </Text>
      <Text style={s.sectionTitle}>Current status</Text>
      <Text style={s.body}>
        BACKEND_NOT_CONFIGURED. No actual media upload is active.
      </Text>
      <Pressable
        accessibilityRole="button"
        onPress={() => navigation.navigate('PrivacyData')}
        style={s.notNow}
      >
        <Text style={s.notNowText}>Manage participation</Text>
      </Pressable>
    </Shell>
  );
}

const s = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.canvas },
  content: { padding: 20, paddingBottom: 80 },
  back: {
    width: 42,
    height: 42,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colors.canvas,
  },
  title: {
    marginTop: 22,
    color: colors.textPrimary,
    fontSize: 34,
    fontWeight: '700',
    letterSpacing: -1,
  },
  local: {
    marginTop: 24,
    padding: 16,
    borderRadius: 8,
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: colors.surfaceRaised,
  },
  localCopy: { flex: 1, paddingLeft: 12 },
  localTitle: { color: colors.textPrimary, fontSize: 15, fontWeight: '700' },
  localDetail: {
    marginTop: 4,
    color: colors.textSecondary,
    fontSize: 11,
    lineHeight: 15,
  },
  program: {
    marginTop: 12,
    padding: 20,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.canvas,
  },
  switchRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  programTitle: { color: colors.textPrimary, fontSize: 17, fontWeight: '700' },
  programStatus: { marginTop: 4, color: colors.textSecondary, fontSize: 10 },
  programCopy: {
    marginTop: 16,
    color: colors.textSecondary,
    fontSize: 12,
    lineHeight: 18,
  },
  rows: {
    marginTop: 12,
    borderRadius: 8,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: colors.border,
  },
  row: {
    minHeight: 72,
    padding: 16,
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: colors.canvas,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderColor: colors.border,
  },
  rowCopy: { flex: 1 },
  rowTitle: { color: colors.textPrimary, fontSize: 14, fontWeight: '600' },
  rowDetail: { marginTop: 5, color: colors.textSecondary, fontSize: 10 },
  danger: {
    height: 50,
    marginTop: 16,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: colors.border,
    alignItems: 'center',
    justifyContent: 'center',
  },
  dangerText: { color: colors.textPrimary, fontWeight: '700' },
  version: { marginTop: 14, color: colors.textSecondary, fontSize: 12 },
  lead: {
    marginTop: 24,
    color: colors.textPrimary,
    fontSize: 17,
    lineHeight: 26,
  },
  body: {
    marginTop: 12,
    color: colors.textSecondary,
    fontSize: 13,
    lineHeight: 21,
  },
  draft: {
    marginTop: 18,
    color: colors.textPrimary,
    fontSize: 12,
    fontWeight: '700',
    letterSpacing: 0.7,
  },
  allow: {
    height: 54,
    marginTop: 28,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colors.textPrimary,
  },
  allowText: { color: colors.canvas, fontWeight: '700' },
  notNow: {
    height: 50,
    marginTop: 10,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colors.canvas,
  },
  notNowText: { color: colors.textPrimary, fontWeight: '600' },
  sectionTitle: {
    marginTop: 24,
    color: colors.textPrimary,
    fontSize: 16,
    fontWeight: '700',
  },
});
