import React from 'react';
import {StyleSheet, Text} from 'react-native';
import {HonorableGlassCard, HonorableScreen, colors, typography} from '../design-system';
export type PlaceholderProps = {title: string; subtitle: string; next: string};
export function PlaceholderScreen({title, subtitle, next}: PlaceholderProps) { return <HonorableScreen title={title} subtitle={subtitle}><HonorableGlassCard><Text style={styles.status}>UI_ONLY</Text><Text style={styles.heading}>Foundation route</Text><Text style={styles.body}>{next}</Text></HonorableGlassCard></HonorableScreen>; }
const styles = StyleSheet.create({status: {...typography.label, color: colors.mint, letterSpacing: 1}, heading: {...typography.heading, color: colors.ice, marginTop: 12}, body: {...typography.body, color: colors.textMuted, marginTop: 8}});
