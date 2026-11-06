import { useReducedMotion } from '../hooks/useReducedMotion';
import { colors } from '../design-system/tokens';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import React, { useEffect, useRef, useState } from 'react';
import {
  Alert,
  Image,
  Modal,
  Pressable,
  StatusBar,
  StyleSheet,
  Text,
  View,
  useWindowDimensions,
} from 'react-native';
import { ChevronLeft, Play } from 'lucide-react-native';
import type { MediaItem } from './MediaGrid';
import { honorableNative } from '../native/HonorableNative';
import { editMemory } from '../native/editMemory';
export function MediaViewer({
  item,
  onClose,
  navigation,
}: {
  item?: MediaItem;
  onClose: () => void;
  navigation?: {
    position: number;
    total: number;
    onPrevious?: () => void;
    onNext?: () => void;
  };
}) {
  const reduceMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const [chrome, setChrome] = useState(true);
  const [busy, setBusy] = useState(false);
  const pending = useRef(false);
  const lifecycle = useRef({ active: false, generation: 0, item });
  useEffect(() => {
    const session = lifecycle.current;
    session.active = true;
    session.item = item;
    session.generation++;
    return () => {
      session.active = false;
      session.generation++;
    };
  }, [item]);
  if (!item) return null;
  const crop = item.cropIndex;
  const sourceUri =
    item.result?.mediaUri ??
    item.mediaUri ??
    (item.type === 'IMAGE' ? item.uri : undefined);
  const run = async (title: string, action: () => Promise<unknown>) => {
    if (
      pending.current ||
      !lifecycle.current.active ||
      lifecycle.current.item !== item
    )
      return;
    pending.current = true;
    const generation = lifecycle.current.generation;
    setBusy(true);
    try {
      await action();
    } catch (reason) {
      if (
        lifecycle.current.active &&
        lifecycle.current.generation === generation
      ) {
        Alert.alert(
          title,
          reason instanceof Error
            ? reason.message
            : 'This action could not complete. Try again.',
        );
      }
    } finally {
      pending.current = false;
      if (lifecycle.current.active) setBusy(false);
    }
  };
  const navigate = (action?: () => void) => {
    if (
      !action ||
      pending.current ||
      !lifecycle.current.active ||
      lifecycle.current.item !== item
    )
      return;
    setChrome(true);
    action();
  };
  const openVideo = () =>
    run('Open media', async () => {
      if (!sourceUri)
        throw new Error(
          'This source is unavailable. Choose it again from your library.',
        );
      const timestamp = item.result?.bestTimestampMs;
      if (
        timestamp !== undefined &&
        (!Number.isFinite(timestamp) || timestamp < 0)
      ) {
        throw new Error(
          'This video moment is unavailable. Open the video from your library.',
        );
      }
      await honorableNative.openMedia({
        mediaUri: sourceUri,
        mediaType: item.type,
        bestTimestampMs: item.result?.bestTimestampMs,
      });
    });
  return (
    <Modal
      visible
      animationType={reduceMotion ? 'none' : 'fade'}
      onRequestClose={onClose}
    >
      <StatusBar hidden />
      <View
        style={styles.canvas}
        accessibilityViewIsModal
        onAccessibilityEscape={onClose}
      >
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={
            chrome ? 'Hide viewer controls' : 'Show viewer controls'
          }
          onPress={() => setChrome(v => !v)}
          style={styles.mediaSurface}
        >
          {crop === undefined && item.uri ? (
            <Image
              source={item.source ?? { uri: item.uri }}
              resizeMode="contain"
              style={styles.fill}
            />
          ) : crop !== undefined ? (
            <View style={{ width, height, overflow: 'hidden' }}>
              <Image
                source={item.source}
                style={{
                  position: 'absolute',
                  width: width * 3,
                  height: width * 3,
                  left: -(crop % 3) * width,
                  top: (height - width) / 2 - Math.floor(crop / 3) * width,
                }}
              />
            </View>
          ) : null}
        </Pressable>
        {chrome && (
          <>
            <View style={[styles.top, { top: insets.top + 8 }]}>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel="Close viewer"
                onPress={onClose}
                style={styles.control}
              >
                <ChevronLeft color={colors.textPrimary} />
              </Pressable>
              <Text style={styles.counter}>
                {item.type === 'VIDEO' ? 'Video' : 'Photo'}
              </Text>
              <View style={styles.controlPlaceholder} />
            </View>
            {item.type === 'VIDEO' && (
              <Pressable
                accessibilityRole="button"
                accessibilityLabel="Play video"
                onPress={openVideo}
                disabled={busy}
                accessibilityState={{ disabled: busy, busy }}
                style={styles.play}
              >
                <Play color="#000000" fill="#000000" size={25} />
                {item.result?.bestTimestampMs !== undefined && (
                  <Text style={styles.moment}>
                    Play from {formatDuration(item.result.bestTimestampMs)}
                  </Text>
                )}
              </Pressable>
            )}
            <View style={[styles.details, { bottom: insets.bottom + 8 }]}>
              {navigation && (
                <View style={styles.navigation}>
                  <Pressable
                    accessibilityRole="button"
                    accessibilityLabel="Previous library item"
                    disabled={busy || !navigation.onPrevious}
                    accessibilityState={{
                      disabled: busy || !navigation.onPrevious,
                    }}
                    style={[
                      styles.action,
                      (busy || !navigation.onPrevious) && styles.actionDisabled,
                    ]}
                    onPress={() => navigate(navigation.onPrevious)}
                  >
                    <Text style={styles.counter}>Previous</Text>
                  </Pressable>
                  <Text
                    accessibilityLiveRegion="polite"
                    style={styles.position}
                  >
                    {navigation.position} of {navigation.total} loaded
                  </Text>
                  <Pressable
                    accessibilityRole="button"
                    accessibilityLabel="Next library item"
                    disabled={busy || !navigation.onNext}
                    accessibilityState={{
                      disabled: busy || !navigation.onNext,
                    }}
                    style={[
                      styles.action,
                      (busy || !navigation.onNext) && styles.actionDisabled,
                    ]}
                    onPress={() => navigate(navigation.onNext)}
                  >
                    <Text style={styles.counter}>Next</Text>
                  </Pressable>
                </View>
              )}
              <View style={styles.actions}>
                <Pressable
                  accessibilityRole="button"
                  accessibilityLabel="Open media"
                  style={[styles.action, busy && styles.actionDisabled]}
                  disabled={busy}
                  accessibilityState={{ disabled: busy, busy }}
                  onPress={openVideo}
                >
                  <Text style={styles.counter}>Open</Text>
                </Pressable>
                <Pressable
                  accessibilityRole="button"
                  accessibilityLabel="Edit media"
                  style={[styles.action, busy && styles.actionDisabled]}
                  disabled={busy}
                  accessibilityState={{ disabled: busy, busy }}
                  onPress={() =>
                    run(
                      item.type === 'VIDEO' ? 'Edit video' : 'Edit photo',
                      () => editMemory(item),
                    )
                  }
                >
                  <Text style={styles.counter}>Edit</Text>
                </Pressable>
                <Pressable
                  accessibilityRole="button"
                  accessibilityLabel="Share media"
                  style={[styles.action, busy && styles.actionDisabled]}
                  disabled={busy}
                  accessibilityState={{ disabled: busy, busy }}
                  onPress={() =>
                    run('Share media', async () => {
                      if (!sourceUri)
                        throw new Error(
                          'This source is unavailable. Choose it again from your library.',
                        );
                      await honorableNative.shareMedia({
                        mediaUri: sourceUri,
                        mediaType: item.type,
                      });
                    })
                  }
                >
                  <Text style={styles.counter}>Share</Text>
                </Pressable>
              </View>
              {busy && (
                <Text accessibilityLiveRegion="polite" style={styles.counter}>
                  Working…
                </Text>
              )}
              <Text style={styles.date}>
                Share sends the full original file, including metadata. Export
                edits from the editor first.
              </Text>
              <Text style={styles.name} numberOfLines={1}>
                {item.displayName ??
                  item.result?.displayName ??
                  (item.type === 'VIDEO' ? 'Video' : 'Photo')}
              </Text>
              {item.capturedAt && (
                <Text style={styles.date}>
                  {new Date(item.capturedAt).toLocaleDateString()}
                </Text>
              )}
            </View>
          </>
        )}
      </View>
    </Modal>
  );
}
function formatDuration(ms: number) {
  const seconds = Math.floor(ms / 1000);
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
}
const styles = StyleSheet.create({
  canvas: { flex: 1, backgroundColor: colors.canvas, justifyContent: 'center' },
  fill: { width: '100%', height: '100%' },
  mediaSurface: { position: 'absolute', top: 0, right: 0, bottom: 0, left: 0 },
  navigation: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 8,
  },
  position: { color: colors.textSecondary, fontSize: 12, flexShrink: 1 },
  actions: { flexDirection: 'row', flexWrap: 'wrap', gap: 12 },
  action: {
    minWidth: 48,
    minHeight: 48,
    alignItems: 'center',
    justifyContent: 'center',
  },
  actionDisabled: { opacity: 0.45 },
  top: {
    position: 'absolute',
    top: 18,
    left: 14,
    right: 14,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  control: {
    width: 48,
    height: 48,
    borderRadius: 6,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colors.canvas,
    borderWidth: 1,
    borderColor: colors.border,
  },
  controlPlaceholder: { width: 44, height: 44 },
  counter: {
    color: colors.textPrimary,
    fontSize: 13,
    fontWeight: '600',
    backgroundColor: colors.canvas,
    borderWidth: 1,
    borderColor: colors.border,
    paddingHorizontal: 12,
    paddingVertical: 12,
    borderRadius: 6,
  },
  play: {
    position: 'absolute',
    alignSelf: 'center',
    minWidth: 72,
    minHeight: 72,
    padding: 18,
    borderRadius: 36,
    backgroundColor: '#fff',
    alignItems: 'center',
    justifyContent: 'center',
  },
  moment: { marginTop: 6, color: '#000000', fontSize: 10, fontWeight: '700' },
  details: {
    position: 'absolute',
    left: 12,
    right: 12,
    bottom: 28,
    padding: 12,
    gap: 10,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.canvas,
  },
  name: { color: colors.textPrimary, fontSize: 16, fontWeight: '700' },
  date: { marginTop: 4, color: colors.textSecondary, fontSize: 12 },
});
