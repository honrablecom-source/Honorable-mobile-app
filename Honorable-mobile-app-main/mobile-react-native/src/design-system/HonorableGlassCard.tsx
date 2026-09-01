import React, {PropsWithChildren} from 'react';
import {StyleSheet, View, ViewStyle} from 'react-native';
import {colors, radii, spacing} from './tokens';
export function HonorableGlassCard({children, style}: PropsWithChildren<{style?: ViewStyle}>) { return <View style={[styles.card, style]}>{children}</View>; }
const styles = StyleSheet.create({card: {padding:spacing.lg,borderRadius:radii.lg,borderWidth:1,borderColor:colors.border,backgroundColor:colors.glass,shadowColor:'#000',shadowOffset:{width:0,height:14},shadowOpacity:.24,shadowRadius:24,elevation:8}});
