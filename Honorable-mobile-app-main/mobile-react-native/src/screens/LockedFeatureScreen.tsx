import React from 'react';
import { StyleSheet, Text } from 'react-native';
import {
  HonorableSection,
  HonorableScreen,
  colors,
  spacing,
  typography,
} from '../design-system';
import type { RootStackScreenProps } from '../navigation/types';
export function LockedFeatureScreen({
  route,
}: RootStackScreenProps<'LockedFeature'>) {
  return (
    <HonorableScreen
      title={route.params.feature}
      subtitle={`${route.params.feature} is available with Honorable ${route.params.requiredPlan}.`}
    >
      <HonorableSection>
        <Text style={styles.status}>COMING_SOON</Text>
        <Text style={styles.heading}>No fabricated results</Text>
        <Text style={styles.body}>
          The entitlement and navigation gate exists. The underlying feature
          remains unavailable until a real, tested engine is implemented.
        </Text>
      </HonorableSection>
    </HonorableScreen>
  );
}
const styles = StyleSheet.create({
  status: { ...typography.label, color: colors.textSecondary },
  heading: {
    ...typography.heading,
    color: colors.textPrimary,
    marginTop: spacing.sm,
  },
  body: { ...typography.body, color: colors.textMuted, marginTop: spacing.sm },
});
