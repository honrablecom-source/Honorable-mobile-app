import { WorkspaceHeader } from '../design-system/WorkspaceHeader';
import React from 'react';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { SafeAreaView } from 'react-native-safe-area-context';
import { ChevronRight } from 'lucide-react-native';
import { Text } from '@/components/ui/text';
import { colors } from '../design-system';
import type { RootStackParamList } from '../navigation/types';
function Shell({
  title,
  onBack,
  children,
}: {
  title: string;
  onBack: () => void;
  children: React.ReactNode;
}) {
  return (
    <SafeAreaView style={styles.safe}>
      <WorkspaceHeader
        title={title}
        section="Noble / privacy"
        onBack={onBack}
      />
      <ScrollView contentContainerStyle={styles.content}>{children}</ScrollView>
    </SafeAreaView>
  );
}
function LinkRow({ title, onPress }: { title: string; onPress: () => void }) {
  return (
    <Pressable accessibilityRole="button" onPress={onPress} style={styles.row}>
      <Text className="font-medium">{title}</Text>
      <ChevronRight color={colors.iconMuted} size={18} />
    </Pressable>
  );
}
export function AboutLegalScreen() {
  const navigation =
    useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  return (
    <Shell title="About & legal" onBack={() => navigation.goBack()}>
      <Text variant="muted" className="mb-6 mt-2">
        Noble keeps normal search and indexing on your device.
      </Text>
      <View style={styles.group}>
        <LinkRow
          title="Terms of Service"
          onPress={() => navigation.navigate('TermsOfService')}
        />
        <LinkRow
          title="Privacy Policy"
          onPress={() => navigation.navigate('PrivacyPolicy')}
        />
        <LinkRow
          title="Seran Improvement Program"
          onPress={() => navigation.navigate('SeranImprovement')}
        />
      </View>
    </Shell>
  );
}
function DraftDisclosure() {
  return (
    <>
      <Text style={styles.draft}>DRAFT — REQUIRES LEGAL REVIEW</Text>
      <Text className="mt-6 text-[17px] font-semibold">
        Seran Improvement Program
      </Text>
      <Text variant="muted" className="mt-3 leading-6">
        Noble's normal media indexing and search run locally on your device.
        If you separately choose to participate, eligible media and minimized
        analysis data may be securely contributed to research, train, evaluate,
        and improve future Seran models.
      </Text>
      <Text variant="muted" className="mt-3 leading-6">
        Participation is optional, disabled by default, and can be withdrawn.
        The final approved policy must define collection, original-media use,
        metadata, security, retention, deletion requests, and completed-training
        implications.
      </Text>
    </>
  );
}
export function TermsOfServiceScreen() {
  const navigation = useNavigation();
  return (
    <Shell title="Terms of Service" onBack={() => navigation.goBack()}>
      <DraftDisclosure />
    </Shell>
  );
}
export function PrivacyPolicyScreen() {
  const navigation =
    useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  return (
    <PreviewPrivacyNotice
      onBack={() => navigation.goBack()}
      onPrivacyData={() => navigation.navigate('PrivacyData')}
    />
  );
}
export function PreviewPrivacyNotice({
  onBack,
  onPrivacyData,
}: {
  onBack: () => void;
  onPrivacyData?: () => void;
}) {
  return (
    <Shell title="Privacy Policy" onBack={onBack}>
      <Text style={styles.draft}>NOBLE PREVIEW</Text>
      <Text accessibilityRole="header" className="mt-6 text-[17px] font-semibold">
        Your library and edits
      </Text>
      <Text variant="muted" className="mt-3 leading-6">
        Noble uses the photos and videos you allow it to access. Normal
        indexing, search, and editing run on your device. If you save or share a
        copy, you choose its destination.
      </Text>
      <Text accessibilityRole="header" className="mt-6 text-[17px] font-semibold">
        Online account and support
      </Text>
      <Text variant="muted" className="mt-3 leading-6">
        When you sign in with Google, Noble's account service uses your Google
        identity, name and email to create or restore your account. The service
        keeps account, credit and session records and receives an installation
        identifier and app and device details. It also records service and
        usage events. Feedback sends the details you submit. Attaching a
        screenshot or diagnostic report requires a separate choice.
      </Text>
      <Text accessibilityRole="header" className="mt-6 text-[17px] font-semibold">
        Your choices
      </Text>
      <Text variant="muted" className="mt-3 leading-6">
        The optional Seran contribution program is off by default. This preview
        has no contribution upload service. If you sign in, use Privacy & Data
        to review your choices or send an account deletion request for review.
      </Text>
      {onPrivacyData && <LinkRow title="Privacy & Data" onPress={onPrivacyData} />}
    </Shell>
  );
}
const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  content: { padding: 20 },
  back: {
    width: 40,
    height: 40,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 8,
    backgroundColor: colors.surface,
  },
  group: {
    borderRadius: 8,
    overflow: 'hidden',
    backgroundColor: colors.surface,
  },
  row: {
    minHeight: 66,
    paddingHorizontal: 16,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.separator,
  },
  draft: {
    marginTop: 16,
    color: colors.textPrimary,
    fontSize: 10,
    fontWeight: '700',
    letterSpacing: 0.6,
  },
});
