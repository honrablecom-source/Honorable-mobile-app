import React from 'react';
import { StyleSheet, Text } from 'react-native';
import {
  HonorableSection,
  HonorableScreen,
  colors,
  typography,
} from '../design-system';
export type PlaceholderProps = {
  title: string;
  subtitle: string;
  next: string;
};
export function PlaceholderScreen({ title, subtitle, next }: PlaceholderProps) {
  return (
    <HonorableScreen title={title} subtitle={subtitle}>
      <HonorableSection>
        <Text style={styles.status}>UI_ONLY</Text>
        <Text style={styles.heading}>Foundation route</Text>
        <Text style={styles.body}>{next}</Text>
      </HonorableSection>
    </HonorableScreen>
  );
}
const styles = StyleSheet.create({
  status: { ...typography.label, color: colors.accent, letterSpacing: 1 },
  heading: { ...typography.heading, color: colors.textPrimary, marginTop: 12 },
  body: { ...typography.body, color: colors.textMuted, marginTop: 8 },
});
