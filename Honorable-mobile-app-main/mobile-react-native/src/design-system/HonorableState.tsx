import React from 'react';
import {ActivityIndicator, StyleSheet, Text, View} from 'react-native';
import {colors, spacing, typography} from './tokens';
type Kind = 'loading' | 'empty' | 'error';
export function HonorableState({kind, title, detail}: {kind: Kind; title: string; detail: string}) { return <View accessibilityRole={kind === 'error' ? 'alert' : 'summary'} style={styles.container}>{kind === 'loading' && <ActivityIndicator color={colors.cyan} />}<Text style={styles.title}>{title}</Text><Text style={styles.detail}>{detail}</Text></View>; }
const styles = StyleSheet.create({container: {alignItems: 'center', padding: spacing.lg, gap: spacing.sm}, title: {...typography.heading, color: colors.ice, textAlign: 'center'}, detail: {...typography.body, color: colors.textMuted, textAlign: 'center'}});
