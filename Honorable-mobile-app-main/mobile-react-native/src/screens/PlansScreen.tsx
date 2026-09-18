import React from 'react';
import { StyleSheet, Text } from 'react-native';
import {
  HonorableSection,
  HonorableScreen,
  colors,
  spacing,
  typography,
} from '../design-system';
import { planPresentation } from '../subscriptions/planPresentation';
export function PlansScreen() {
  return (
    <HonorableScreen
      title="Subscriptions"
      subtitle="Subscriptions are separate from one-time Memory Passes. Store-localized prices appear when billing is configured."
    >
      <Text style={styles.coming}>PRICES · COMING SOON</Text>
      {planPresentation.map(plan => (
        <HonorableSection key={plan.tier}>
          <Text style={styles.tier}>HONORABLE {plan.tier}</Text>
          <Text style={styles.heading}>{plan.tagline}</Text>
          <Text style={styles.body}>
            {plan.storage} · {plan.video}
          </Text>
          <Text style={styles.body}>{plan.features.join(' · ')}</Text>
        </HonorableSection>
      ))}
    </HonorableScreen>
  );
}
const styles = StyleSheet.create({
  coming: { ...typography.label, color: colors.accent },
  tier: { ...typography.label, color: colors.textSecondary },
  heading: {
    ...typography.heading,
    color: colors.textPrimary,
    marginTop: spacing.sm,
  },
  body: { ...typography.body, color: colors.textMuted, marginTop: spacing.sm },
});
