import React from 'react';
import {
  ActivityIndicator,
  Pressable,
  StyleSheet,
  TextInput,
  View,
} from 'react-native';
import { ArrowUp, Search, X } from 'lucide-react-native';
import { colors, radii } from '../design-system/tokens';

export function HonorableSearchBar({
  value,
  onChangeText,
  onSubmit,
  compact = false,
  footer,
  busy = false,
}: {
  value: string;
  onChangeText: (value: string) => void;
  onSubmit: () => void;
  compact?: boolean;
  footer?: React.ReactNode;
  busy?: boolean;
}) {
  const canSubmit = !!value.trim() && !busy;
  return (
    <View style={[styles.shell, compact && styles.compact]}>
      <View style={styles.inputRow}>
        <Search color={colors.iconMuted} size={18} strokeWidth={1.8} />
        <TextInput
          accessibilityLabel={
            compact
              ? 'Search filenames in your library'
              : 'Describe a photo or video'
          }
          returnKeyType="search"
          blurOnSubmit={false}
          onSubmitEditing={() => canSubmit && onSubmit()}
          placeholder={compact ? 'Search filenames…' : 'Describe a moment…'}
          placeholderTextColor={colors.textTertiary}
          value={value}
          onChangeText={onChangeText}
          style={styles.input}
        />
        {!!value && (
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Clear search"
            onPress={() => onChangeText('')}
            style={styles.clear}
          >
            <X color={colors.iconMuted} size={16} />
          </Pressable>
        )}
      </View>
      {!compact && (
        <View style={styles.footer}>
          <View style={styles.footerContent}>{footer}</View>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Search memories"
            accessibilityState={{ disabled: !canSubmit, busy }}
            disabled={!canSubmit}
            onPress={onSubmit}
            style={[styles.submit, !canSubmit && styles.inactive]}
          >
            {busy ? (
              <ActivityIndicator size="small" color={colors.textTertiary} />
            ) : (
              <ArrowUp
                size={20}
                color={canSubmit ? colors.canvas : colors.textTertiary}
              />
            )}
          </Pressable>
        </View>
      )}
    </View>
  );
}
const styles = StyleSheet.create({
  shell: {
    marginHorizontal: 16,
    padding: 12,
    borderRadius: 18,
    backgroundColor: colors.canvas,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.border,
  },
  inputRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    minHeight: 48,
  },
  input: {
    flex: 1,
    minHeight: 48,
    paddingVertical: 8,
    fontSize: 16,
    color: colors.textPrimary,
  },
  compact: {
    paddingVertical: 0,
    borderRadius: radii.md,
    paddingHorizontal: 12,
    borderWidth: 0,
    backgroundColor: colors.surfaceSelected,
  },
  clear: {
    minWidth: 44,
    minHeight: 44,
    alignItems: 'center',
    justifyContent: 'center',
  },
  footer: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginTop: 4,
  },
  footerContent: { flex: 1 },
  submit: {
    width: 44,
    height: 44,
    borderRadius: 22,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colors.textPrimary,
    borderWidth: 1,
    borderColor: colors.border,
  },
  inactive: { backgroundColor: colors.surfaceSelected },
});
