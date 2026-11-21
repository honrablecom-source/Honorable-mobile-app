import React from 'react';
import {
  Alert,
  Linking,
  Pressable,
  ScrollView,
  StyleSheet,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import {
  ChevronRight,
  Images,
  HardDrive,
  ShieldCheck,
  CreditCard,
  ChartNoAxesColumn,
  MessageSquare,
  FileText,
  LockKeyhole,
} from 'lucide-react-native';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { Text } from '@/components/ui/text';
import { AccountPanel } from '../auth/AccountGate';
import { colors } from '../design-system/tokens';
import { WorkspaceHeader } from '../design-system/WorkspaceHeader';
import type { RootStackParamList } from '../navigation/types';
const icons = {
  'Photo & video access': Images,
  'Storage & indexing': HardDrive,
  'Privacy & Data': ShieldCheck,
  'Memory Passes': CreditCard,
  Usage: ChartNoAxesColumn,
  'Beta feedback': MessageSquare,
  'Terms of Service': FileText,
  'Privacy Policy': LockKeyhole,
};

export function SettingsScreen({
  tabRoot = false,
}: { tabRoot?: boolean } = {}) {
  const navigation =
    useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  const row = (
    title: keyof typeof icons,
    detail: string,
    onPress: () => void,
  ) => {
    const Icon = icons[title];
    return (
      <Pressable
        key={title}
        accessibilityRole="button"
        accessibilityLabel={title}
        accessibilityHint={detail}
        onPress={onPress}
        style={s.row}
      >
        <View style={s.rowIcon}>
          <Icon size={20} color={colors.textPrimary} strokeWidth={1.6} />
        </View>
        <View style={s.rowCopy}>
          <Text style={s.label}>{title}</Text>
          <Text style={s.detail}>{detail}</Text>
        </View>
        <ChevronRight size={18} color={colors.textSecondary} />
      </Pressable>
    );
  };
  return (
    <SafeAreaView edges={['top']} style={s.safe}>
      <WorkspaceHeader
        title="Account"
        onBack={
          !tabRoot && navigation.canGoBack()
            ? () => navigation.goBack()
            : undefined
        }
      />
      <ScrollView contentContainerStyle={s.content}>
        <AccountPanel />
        <Text accessibilityRole="header" style={s.section}>
          Library
        </Text>
        <View style={s.group}>
          {row(
            'Photo & video access',
            'Manage access in device settings',
            async () => {
              try {
                await Linking.openSettings();
              } catch {
                Alert.alert(
                  'Settings unavailable',
                  'Open your device settings to change photo access.',
                );
              }
            },
          )}
          {row('Storage & indexing', 'Manage your local library', () =>
            navigation.navigate('Activity'),
          )}
        </View>
        <Text accessibilityRole="header" style={s.section}>
          Credits
        </Text>
        <View style={s.group}>
          {row('Memory Passes', 'Purchased credits never expire', () =>
            navigation.navigate('Pass'),
          )}
          {row('Usage', 'Credits and recent searches', () =>
            navigation.navigate('Usage'),
          )}
        </View>
        <Text accessibilityRole="header" style={s.section}>
          Privacy and support
        </Text>
        <View style={s.group}>
          {row('Privacy & Data', 'Privacy choices and account requests', () =>
            navigation.navigate('PrivacyData'),
          )}
          {row('Terms of Service', 'Product disclosure', () =>
            navigation.navigate('TermsOfService'),
          )}
          {row('Privacy Policy', 'How your information is handled', () =>
            navigation.navigate('PrivacyPolicy'),
          )}
          {row(
            'Beta feedback',
            'Report a problem or check release status',
            () => navigation.navigate('BetaFeedback'),
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
  rowCopy: { flex: 1, minWidth: 0 },
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
