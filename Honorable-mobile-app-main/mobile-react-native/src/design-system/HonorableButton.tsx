import React from 'react';
import {Pressable, StyleSheet, Text} from 'react-native';
import {colors, radii, spacing, typography} from './tokens';
export function HonorableButton({label, onPress, disabled = false}: {label: string; onPress: () => void; disabled?: boolean}) { return <Pressable accessibilityRole="button" accessibilityState={{disabled}} disabled={disabled} onPress={onPress} style={({pressed}) => [styles.button, pressed && styles.pressed, disabled && styles.disabled]}><Text style={styles.label}>{label}</Text></Pressable>; }
const styles = StyleSheet.create({button: {minHeight: 52, paddingHorizontal: spacing.lg, borderRadius: radii.pill, backgroundColor: colors.cyan, alignItems: 'center', justifyContent: 'center'}, pressed: {opacity: 0.8, transform: [{scale: 0.98}]}, disabled: {opacity: 0.45}, label: {...typography.label, color: colors.navyDeep}});
