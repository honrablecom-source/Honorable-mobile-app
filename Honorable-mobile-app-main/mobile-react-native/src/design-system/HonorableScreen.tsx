import React, {PropsWithChildren} from 'react';
import {ScrollView, StyleSheet, Text, View} from 'react-native';
import {SafeAreaView} from 'react-native-safe-area-context';
import {colors, spacing, typography} from './tokens';

type Props = PropsWithChildren<{title: string; subtitle: string}>;
export function HonorableScreen({title, subtitle, children}: Props) {
  return <SafeAreaView edges={['top']} style={styles.safeArea}><ScrollView contentContainerStyle={styles.grow}><View style={styles.content}><Text style={styles.brand}>honorable</Text><Text accessibilityRole="header" style={styles.title}>{title}</Text><Text style={styles.subtitle}>{subtitle}</Text><View style={styles.body}>{children}</View></View></ScrollView></SafeAreaView>;
}
const styles = StyleSheet.create({safeArea: {flex: 1, backgroundColor: colors.navyDeep}, grow: {flexGrow: 1}, content: {flex: 1, paddingHorizontal: spacing.lg, paddingTop: spacing.lg, paddingBottom: spacing.xxl}, brand: {...typography.label, color: colors.cyan, letterSpacing: 1.2}, title: {...typography.title, color: colors.ice, marginTop: spacing.lg}, subtitle: {...typography.body, color: colors.textMuted, marginTop: spacing.sm, maxWidth: 360}, body: {marginTop: spacing.xl, gap: spacing.md}});
