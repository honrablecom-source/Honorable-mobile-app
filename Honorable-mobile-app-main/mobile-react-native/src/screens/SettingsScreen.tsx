import React from 'react';
import { Linking, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { ChevronLeft, ChevronRight } from 'lucide-react-native';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { Text } from '@/components/ui/text';
import { AccountPanel } from '../auth/AccountGate';
import { colors, spacing, typography } from '../design-system/tokens';
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
      <View style={{ flex: 1 }}>
        <Text style={s.label}>{title}</Text>
        <Text style={s.detail}>{detail}</Text>
      </View>
      <ChevronRight size={18} color={colors.textSecondary} />
    </Pressable>
  );
  return (
    <SafeAreaView edges={['top']} style={s.safe}>
      <ScrollView contentContainerStyle={s.content}>
        <Pressable
          accessibilityLabel="Back"
          onPress={() => navigation.goBack()}
          style={s.back}
        >
          <ChevronLeft color={colors.textPrimary} />
        </Pressable>
        <Text accessibilityRole="header" style={s.title}>
          Account
        </Text>
        <AccountPanel />
        <Text style={s.section}>Your library</Text>
        {row('Photo & video access', 'Choose what Honorable can access', () =>
          Linking.openSettings(),
        )}
        {row('Storage & indexing', 'Manage your local library', () =>
          navigation.navigate('Activity'),
        )}
        {row('Privacy & Data', 'Your choices and participation', () =>
          navigation.navigate('PrivacyData'),
        )}
        <Text style={s.section}>Membership & support</Text>
        {row('Memory Passes', 'Purchased credits never expire', () =>
          navigation.navigate('Pass'),
        )}
        {row('Usage', 'Credits and recent searches', () =>
          navigation.navigate('Usage'),
        )}
        {row('Studio', 'Creative workspace membership', () =>
          navigation.navigate('Studio'),
        )}
        {row('Beta feedback', 'Report a problem or check release status', () =>
          navigation.navigate('BetaFeedback'),
        )}
        <Text style={s.section}>About Honorable</Text>
        {row('Terms of Service', 'Product disclosure', () =>
          navigation.navigate('TermsOfService'),
        )}
        {row('Privacy Policy', 'How your information is handled', () =>
          navigation.navigate('PrivacyPolicy'),
        )}
        {row('Improvement program', 'Optional and off by default', () =>
          navigation.navigate('SeranImprovement'),
        )}
      </ScrollView>
    </SafeAreaView>
  );
}
const s = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.canvas },
  content: { padding: spacing.xl, paddingBottom: 48 },
  back: { minHeight: 44, width: 44, justifyContent: 'center' },
  title: { ...typography.title, color: colors.textPrimary, marginTop: 16 },
  section: {
    color: colors.textSecondary,
    fontSize: 13,
    marginTop: 32,
    marginBottom: 8,
  },
  row: {
    minHeight: 72,
    paddingVertical: 16,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  label: { color: colors.textPrimary, fontSize: 16 },
  detail: {
    color: colors.textSecondary,
    fontSize: 13,
    marginTop: 4,
    lineHeight: 20,
  },
});
