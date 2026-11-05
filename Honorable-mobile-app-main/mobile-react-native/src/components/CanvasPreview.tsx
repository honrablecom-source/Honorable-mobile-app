import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { colors } from '../design-system/tokens';
import { canvasPreview } from './canvasGeometry';

type Background = 'white' | 'black' | 'transparent';
/** A visual size reference, never a rendered image or a file. */
export function CanvasPreview({ width, height, background }: { width: number; height: number; background: Background }) {
  const preview = canvasPreview(width, height);
  if (!preview) return null;
  return <View style={s.container}>
    <Text style={s.heading}>Your canvas proportions</Text>
    <View accessible accessibilityRole="image"
      accessibilityLabel={`${width} by ${height} pixels, ${preview.ratio} aspect ratio, ${preview.orientation.toLowerCase()}, ${background} background. Size preview only.`}
      style={s.stage}>
      <View pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants"
        style={[s.canvas, { width: preview.width, height: preview.height, backgroundColor: background === 'black' ? '#000000' : colors.canvas }]}>
        {background === 'transparent' && Array.from({ length: 8 }, (_, row) => <View key={row} style={s.row}>
          {Array.from({ length: 8 }, (_, column) => <View key={column} style={[s.cell, { backgroundColor: (row + column) % 2 ? colors.border : colors.canvas }]} />)}
        </View>)}
      </View>
    </View>
    <Text style={s.description}>{preview.ratio} · {preview.orientation} · {background} background</Text>
    <Text style={s.description}>{background === 'transparent' ? 'Checkerboard means transparent; it is not added to your image.' : 'Size preview only. Text and shapes are added in the editor.'}</Text>
  </View>;
}
const s = StyleSheet.create({
  container: { padding: 12, borderWidth: 1, borderColor: colors.border, borderRadius: 12, gap: 8 },
  heading: { color: colors.textPrimary, fontSize: 14, fontWeight: '600', lineHeight: 22 },
  stage: { height: 168, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.surfaceRaised, borderRadius: 8 },
  canvas: { borderWidth: 1, borderColor: colors.textPrimary, overflow: 'hidden' },
  row: { flex: 1, flexDirection: 'row' },
  cell: { flex: 1 },
  description: { color: colors.textSecondary, fontSize: 13, lineHeight: 20 },
});
