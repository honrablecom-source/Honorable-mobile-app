import React from 'react';
import {
  Image,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { Film, Play } from 'lucide-react-native';
import type { MediaItem } from './MediaGrid';
import { colors } from '../design-system/tokens';

export function MediaShelf({
  items,
  onPress,
}: {
  items: MediaItem[];
  onPress: (item: MediaItem) => void;
}) {
  return (
    <ScrollView
      horizontal
      showsHorizontalScrollIndicator={false}
      contentContainerStyle={s.shelf}
    >
      {items.map(item => (
        <Pressable
          key={item.id}
          accessibilityRole="imagebutton"
          accessibilityLabel={`Open ${item.displayName ?? 'media'}`}
          onPress={() => onPress(item)}
          style={s.item}
        >
          <View style={s.image}>
            {item.uri ? (
              <Image
                source={{ uri: item.uri }}
                resizeMode="cover"
                style={s.fill}
              />
            ) : (
              <Film size={24} color={colors.textSecondary} />
            )}
            {item.type === 'VIDEO' && (
              <View style={s.badge}>
                <Play size={11} color="#fff" />
                <Text style={s.duration}>{item.duration ?? 'Video'}</Text>
              </View>
            )}
          </View>
          <Text numberOfLines={1} style={s.name}>
            {item.displayName ?? (item.type === 'IMAGE' ? 'Photo' : 'Video')}
          </Text>
        </Pressable>
      ))}
    </ScrollView>
  );
}
const s = StyleSheet.create({
  shelf: { paddingHorizontal: 16, gap: 10, paddingBottom: 4 },
  item: { width: 238, gap: 9 },
  image: {
    width: '100%',
    height: 178,
    borderRadius: 14,
    overflow: 'hidden',
    backgroundColor: colors.surfaceSelected,
    alignItems: 'center',
    justifyContent: 'center',
  },
  fill: { width: '100%', height: '100%' },
  name: { fontSize: 13, lineHeight: 18, color: colors.textSecondary },
  badge: {
    position: 'absolute',
    bottom: 10,
    right: 10,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    borderRadius: 8,
    padding: 5,
    backgroundColor: 'rgba(0,0,0,.55)',
  },
  duration: { fontSize: 11, color: '#fff', fontVariant: ['tabular-nums'] },
});
