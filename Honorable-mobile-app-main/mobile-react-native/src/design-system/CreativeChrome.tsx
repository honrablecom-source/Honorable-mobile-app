import React from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { colors } from './tokens';

export function FilterTabs<T extends string>({
  items,
  value,
  onChange,
}: {
  items: readonly T[];
  value: T;
  onChange: (value: T) => void;
}) {
  return (
    <ScrollView
      horizontal
      showsHorizontalScrollIndicator={false}
      contentContainerStyle={s.tabs}
    >
      {items.map(item => (
        <Pressable
          key={item}
          accessibilityRole="tab"
          accessibilityLabel={`${item} filter`}
          accessibilityState={{ selected: item === value }}
          onPress={() => onChange(item)}
          style={[s.tab, item === value && s.tabActive]}
        >
          <Text style={[s.tabText, item === value && s.tabTextActive]}>
            {item}
          </Text>
        </Pressable>
      ))}
    </ScrollView>
  );
}
export function SectionHeading({
  title,
  detail,
  action,
}: {
  title: string;
  detail?: string;
  action?: React.ReactNode;
}) {
  return (
    <View style={s.headingRow}>
      <View style={s.headingCopy}>
        <Text accessibilityRole="header" style={s.heading}>
          {title}
        </Text>
        {detail && <Text style={s.detail}>{detail}</Text>}
      </View>
      {action}
    </View>
  );
}
export const creativeStyles = StyleSheet.create({
  iconButton: {
    minWidth: 44,
    minHeight: 44,
    borderRadius: 22,
    backgroundColor: colors.surfaceRaised,
    alignItems: 'center',
    justifyContent: 'center',
  },
  eyebrow: {
    fontSize: 11,
    fontWeight: '500',
    letterSpacing: 1.2,
    color: colors.textTertiary,
  },
  separator: { height: 1, backgroundColor: colors.surfaceSelected },
});
const s = StyleSheet.create({
  tabs: { paddingHorizontal: 16, gap: 8, paddingBottom: 4 },
  tab: {
    minHeight: 44,
    paddingHorizontal: 14,
    borderRadius: 22,
    backgroundColor: colors.surfaceRaised,
    justifyContent: 'center',
  },
  tabActive: { backgroundColor: colors.surfaceSelected },
  tabText: { fontSize: 14, color: colors.textTertiary, fontWeight: '500' },
  tabTextActive: { color: colors.textPrimary, fontWeight: '600' },
  headingRow: {
    paddingHorizontal: 16,
    marginTop: 24,
    marginBottom: 14,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 12,
  },
  headingCopy: { flex: 1, gap: 4 },
  heading: {
    fontSize: 20,
    fontWeight: '600',
    letterSpacing: -0.4,
    color: colors.textPrimary,
  },
  detail: { fontSize: 12, lineHeight: 18, color: colors.textTertiary },
});
