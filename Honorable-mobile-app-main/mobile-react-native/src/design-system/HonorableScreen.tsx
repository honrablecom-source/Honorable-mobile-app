import React, { PropsWithChildren } from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import { colors, spacing, typography } from './tokens';
import { WorkspaceHeader } from './WorkspaceHeader';
type Props = PropsWithChildren<{ title: string; subtitle: string }>;
export function HonorableScreen({ title, subtitle, children }: Props) {
  const navigation = useNavigation();
  return (
    <SafeAreaView edges={['top', 'bottom']} style={s.safe}>
      <WorkspaceHeader
        title={title}
        onBack={navigation.canGoBack() ? () => navigation.goBack() : undefined}
      />
      <ScrollView contentContainerStyle={s.content}>
        <Text style={s.subtitle}>{subtitle}</Text>
        <View style={s.body}>{children}</View>
      </ScrollView>
    </SafeAreaView>
  );
}
const s = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.canvas },
  content: { padding: 20, paddingBottom: 40 },
  subtitle: { ...typography.secondary, color: colors.textSecondary },
  body: { marginTop: spacing.xl, gap: spacing.md },
});
