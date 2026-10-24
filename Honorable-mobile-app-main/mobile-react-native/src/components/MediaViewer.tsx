import { colors } from '../design-system/tokens';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import React, { useState } from 'react';
import {
  Alert,
  Share,
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
}: {
  item?: MediaItem;
  onClose: () => void;
}) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const [chrome, setChrome] = useState(true);
  if (!item) return null;
  const crop = item.cropIndex;
  const openVideo = () =>
    honorableNative
      .openMedia({
        mediaUri: item.result?.mediaUri ?? item.mediaUri ?? '',
        mediaType: item.type,
        bestTimestampMs: item.result?.bestTimestampMs,
      })
      .catch(() => undefined);
  return (
    <Modal visible animationType="fade" onRequestClose={onClose}>
      <StatusBar hidden />
      <Pressable onPress={() => setChrome(v => !v)} style={styles.canvas}>
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
        {chrome && (
          <>
            <View style={[styles.top, { top: insets.top + 8 }]}>
              <Pressable
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
              <View style={{ flexDirection: 'row', gap: 12 }}>
                <Pressable accessibilityRole="button" onPress={openVideo}>
                  <Text style={styles.counter}>Open</Text>
                </Pressable>
                <Pressable
                  onPress={() =>
                    editMemory(item).catch(e =>
                      Alert.alert(
                        item.type === 'VIDEO' ? 'Edit video' : 'Edit',
                        e.message,
                      ),
                    )
                  }
                >
                  <Text style={styles.counter}>Edit</Text>
                </Pressable>
                <Pressable
                  onPress={() =>
                    Share.share({
                      url: item.mediaUri ?? item.uri,
                      message: item.displayName ?? 'Memory',
                    })
                  }
                >
                  <Text style={styles.counter}>Share</Text>
                </Pressable>
              </View>
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
      </Pressable>
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
