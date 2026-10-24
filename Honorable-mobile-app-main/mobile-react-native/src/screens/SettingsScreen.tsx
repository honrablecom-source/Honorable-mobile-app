import React from 'react';
import { Linking, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import {
  ChevronRight,
  Images,
  HardDrive,
  ShieldCheck,
  CreditCard,
  ChartNoAxesColumn,
  SlidersHorizontal,
  MessageSquare,
  FileText,
  LockKeyhole,
  HeartHandshake,
} from 'lucide-react-native';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { Text } from '@/components/ui/text';
import { AccountPanel } from '../auth/AccountGate';
import { colors } from '../design-system/tokens';
import { WorkspaceHeader } from '../design-system/WorkspaceHeader';
import type { RootStackParamList } from '../navigation/types';
export function SettingsScreen() {
  const navigation =
    useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  const row = (title: string, detail: string, onPress: () => void) => (
    <Pressable
      key={title}
      accessibilityRole="button"
      onPress={onPress}
      style={s.row}
    >
      <View style={s.rowIcon}>
        {React.createElement(
          (
            {
              'Photo & video access': Images,
              'Storage & indexing': HardDrive,
              'Privacy & Data': ShieldCheck,
              'Memory Passes': CreditCard,
              Usage: ChartNoAxesColumn,
              Studio: SlidersHorizontal,
              'Beta feedback': MessageSquare,
              'Terms of Service': FileText,
              'Privacy Policy': LockKeyhole,
              'Improvement program': HeartHandshake,
            } as Record<string, typeof Images>
          )[title] ?? FileText,
          { size: 20, color: colors.textPrimary, strokeWidth: 1.6 },
        )}
      </View>
      <View style={{ flex: 1 }}>
        <Text style={s.label}>{title}</Text>
        <Text style={s.detail}>{detail}</Text>
      </View>
      <ChevronRight size={18} color={colors.textSecondary} />
    </Pressable>
  );
  return (
    <SafeAreaView edges={['top']} style={s.safe}>
      <WorkspaceHeader
        title="Account"
        section="Library, privacy and membership"
        onBack={navigation.canGoBack() ? () => navigation.goBack() : undefined}
      />
      <ScrollView contentContainerStyle={s.content}>
        <AccountPanel />
        <Text style={s.section}>Your library</Text>
        <View style={s.group}>
          {row('Photo & video access', 'Choose what Honorable can access', () =>
            Linking.openSettings(),
          )}
          {row('Storage & indexing', 'Manage your local library', () =>
            navigation.navigate('Activity'),
          )}
          {row('Privacy & Data', 'Your choices and participation', () =>
            navigation.navigate('PrivacyData'),
          )}
        </View>
        <Text style={s.section}>Membership & support</Text>
        <View style={s.group}>
          {row('Memory Passes', 'Purchased credits never expire', () =>
            navigation.navigate('Pass'),
          )}
          {row('Usage', 'Credits and recent searches', () =>
            navigation.navigate('Usage'),
          )}
          {row('Studio', 'Creative workspace membership', () =>
            navigation.navigate('Studio'),
          )}
          {row(
            'Beta feedback',
            'Report a problem or check release status',
            () => navigation.navigate('BetaFeedback'),
          )}
        </View>
        <Text style={s.section}>About Honorable</Text>
        <View style={s.group}>
          {row('Terms of Service', 'Product disclosure', () =>
            navigation.navigate('TermsOfService'),
          )}
          {row('Privacy Policy', 'How your information is handled', () =>
            navigation.navigate('PrivacyPolicy'),
          )}
          {row('Improvement program', 'Optional and off by default', () =>
            navigation.navigate('SeranImprovement'),
          )}
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}
const s = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.canvas },
  content: { padding: 16, paddingBottom: 32 },
  group: {
    borderRadius: 16,
    paddingHorizontal: 14,
    backgroundColor: colors.surfaceRaised,
    overflow: 'hidden',
  },
  back: { minHeight: 44, width: 44, justifyContent: 'center' },
  rowIcon: {
    width: 30,
    height: 30,
    backgroundColor: colors.canvas,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
  },
  section: {
    color: colors.textSecondary,
    fontSize: 13,
    letterSpacing: 0,
    marginTop: 30,
    marginBottom: 8,
  },
  row: {
    minHeight: 68,
    paddingVertical: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.surfaceSelected,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  label: { color: colors.textPrimary, fontSize: 15 },
  detail: {
    color: colors.textSecondary,
    fontSize: 12,
    marginTop: 4,
    lineHeight: 20,
  },
});
