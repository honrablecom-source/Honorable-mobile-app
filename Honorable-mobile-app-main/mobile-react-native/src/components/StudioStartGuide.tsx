import { useReducedMotion } from '../hooks/useReducedMotion';
import React, { useEffect, useRef, useState } from 'react';
import { Animated, Pressable, StyleSheet, Text, View } from 'react-native';
import { colors, motion } from '../design-system/tokens';

const paths = {
  photo: {
    title: 'Polish a photo',
    action: 'Choose my photo',
    steps: [
      'Choose one photo from your device.',
      'Adjust light and color. Use Undo to try another look.',
      'Export a new copy when you are happy with it.',
    ],
  },
  video: {
    title: 'Edit a video',
    action: 'Choose my video',
    steps: [
      'Choose one video from your device.',
      'Trim your clip, then add captions or adjust the sound.',
      'Preview your changes and export a new video copy.',
    ],
  },
  story: {
    title: 'Turn photos into a video',
    action: 'Choose my story photos',
    steps: [
      'Choose 1–20 photos in the order you want.',
      'Choose a format and timing, then prepare your clips.',
      'Refine the timeline and export your video.',
    ],
  },
  canvas: {
    title: 'Start with a blank image',
    action: 'Choose my canvas size',
    steps: [
      'Choose a ready-made white canvas, or set a custom size and background.',
      'Add text or shapes in the photo editor. Use Undo to try another arrangement.',
      'Save an editable image project, or export a new image copy.',
    ],
  },
  drawing: {
    title: 'Make an original design',
    action: 'Open my drawing canvas',
    steps: [
      'Resume your local drawing draft, or use New drawing to start fresh.',
      'Add photos, shapes or text. Try a starter design in Layouts and personalize its words.',
      'Save an editable composition in Projects. Use image opens the photo editor; export a copy there.',
    ],
  },
} as const;
type Path = keyof typeof paths;

/** Guidance uses the same guarded, local entry points as the studio cards. */
export function StudioStartGuide({
  busy,
  onPhoto,
  onVideo,
  onStory,
  onDrawing,
  onCanvas,
}: {
  busy: boolean;
  onPhoto: () => unknown;
  onVideo: () => unknown;
  onStory: () => unknown;
  onDrawing: () => unknown;
  onCanvas: () => unknown;
}) {
  const [expanded, setExpanded] = useState(false);
  const [selected, setSelected] = useState<Path>('photo');
  const reduceMotion = useReducedMotion();
  const opacity = useRef(new Animated.Value(1)).current;
  useEffect(() => {
    opacity.stopAnimation();
    if (!expanded || reduceMotion) {
      opacity.setValue(1);
      return;
    }
    opacity.setValue(0.65);
    const animation = Animated.timing(opacity, {
      toValue: 1,
      duration: motion.standard,
      useNativeDriver: true,
    });
    animation.start();
    return () => animation.stop();
  }, [expanded, selected, reduceMotion, opacity]);
  const path = paths[selected];
  const launch = {
    photo: onPhoto,
    video: onVideo,
    story: onStory,
    drawing: onDrawing,
    canvas: onCanvas,
  }[selected];
  return (
    <View style={s.container}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Help me get started"
        accessibilityState={{ expanded, disabled: busy }}
        aria-expanded={expanded}
        aria-disabled={busy}
        disabled={busy}
        onPress={() => setExpanded(value => !value)}
        style={s.toggle}
      >
        <Text style={s.heading}>Help me get started</Text>
        <Text style={s.muted}>
          {expanded ? 'Hide guide −' : 'Find your first step +'}
        </Text>
      </Pressable>
      {expanded && (
        <View style={s.body}>
          <Text style={s.heading} accessibilityRole="header">
            What would you like to make?
          </Text>
          <View accessibilityRole="radiogroup">
            {(Object.keys(paths) as Path[]).map(key => (
              <Pressable
                key={key}
                accessibilityRole="radio"
                accessibilityLabel={paths[key].title}
                accessibilityState={{
                  checked: selected === key,
                  disabled: busy,
                }}
                aria-checked={selected === key}
                aria-disabled={busy}
                disabled={busy}
                onPress={() => setSelected(key)}
                style={[s.choice, selected === key && s.selected]}
              >
                <Text style={s.label}>
                  {selected === key ? '●  ' : '○  '}
                  {paths[key].title}
                </Text>
              </Pressable>
            ))}
          </View>
          <Animated.View
            style={{ opacity }}
            accessibilityLiveRegion="polite"
            aria-live="polite"
          >
            {path.steps.map((step, index) => (
              <View key={step} style={s.step}>
                <Text style={s.number}>{index + 1}</Text>
                <Text style={s.stepText}>{step}</Text>
              </View>
            ))}
          </Animated.View>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={path.action}
            accessibilityHint={
              selected === 'drawing'
                ? 'Opens your local draft. New drawing clears the current draft; named compositions stay in Projects.'
                : undefined
            }
            accessibilityState={{ disabled: busy, busy }}
            aria-disabled={busy}
            aria-busy={busy}
            disabled={busy}
            onPress={() => {
              if (!busy) return launch();
            }}
            style={[s.start, busy && s.disabled]}
          >
            <Text style={s.startText}>
              {busy ? 'Opening workspace…' : path.action}
            </Text>
          </Pressable>
          <Text style={s.muted}>
            No account needed. These editors work on your device.
          </Text>
        </View>
      )}
    </View>
  );
}
const s = StyleSheet.create({
  container: {
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 18,
    marginBottom: 20,
    overflow: 'hidden',
  },
  toggle: { padding: 16, minHeight: 64, gap: 4 },
  heading: {
    color: colors.textPrimary,
    fontSize: 16,
    fontWeight: '600',
    lineHeight: 24,
  },
  muted: { color: colors.textSecondary, fontSize: 13, lineHeight: 20 },
  body: { padding: 16, paddingTop: 0, gap: 14 },
  choice: {
    padding: 12,
    minHeight: 48,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 12,
    marginTop: 8,
  },
  selected: {
    backgroundColor: colors.surfaceSelected,
    borderColor: colors.textPrimary,
  },
  label: { color: colors.textPrimary, fontSize: 14, lineHeight: 22 },
  step: { flexDirection: 'row', gap: 10, marginBottom: 12 },
  number: {
    color: colors.textSecondary,
    fontSize: 14,
    lineHeight: 22,
    width: 18,
  },
  stepText: {
    color: colors.textPrimary,
    fontSize: 14,
    lineHeight: 22,
    flex: 1,
  },
  start: {
    backgroundColor: colors.textPrimary,
    borderRadius: 12,
    padding: 14,
    minHeight: 48,
    alignItems: 'center',
  },
  startText: {
    color: colors.canvas,
    fontSize: 15,
    fontWeight: '600',
    lineHeight: 22,
  },
  disabled: { opacity: 0.5 },
});
