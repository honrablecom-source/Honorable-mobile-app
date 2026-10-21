import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { ChevronLeft } from 'lucide-react-native';
import { colors } from './tokens';

/** Shared phone workspace chrome. Back is explicit so tab roots never show a dead control. */
export function WorkspaceHeader({
  title,
  section = 'honorable',
  onBack,
  action,
}: {
  title: string;
  section?: string;
  onBack?: () => void;
  action?: React.ReactNode;
}) {
  return (
    <View style={s.header}>
      {onBack && (
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Go back"
          onPress={onBack}
          style={s.back}
        >
          <ChevronLeft size={22} color={colors.textPrimary} />
        </Pressable>
      )}
      <View style={s.copy}>
        <Text style={s.section}>{section}</Text>
        <Text accessibilityRole="header" style={s.title}>
          {title}
        </Text>
      </View>
      {action && <View style={s.action}>{action}</View>}
    </View>
  );
}
export const workspaceStyles = StyleSheet.create({
  content: { padding: 20, paddingBottom: 32, gap: 16 },
  screen: { flex: 1, backgroundColor: colors.canvas },
});
const s = StyleSheet.create({
  header: {
    minHeight: 76,
    paddingHorizontal: 16,
    paddingVertical: 10,
    gap: 10,
    flexDirection: 'row',
    alignItems: 'center',
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
    backgroundColor: colors.canvas,
  },
  copy: { flex: 1, gap: 3 },
  section: {
    fontSize: 11,
    lineHeight: 16,
    color: colors.textSecondary,
    letterSpacing: 1.1,
  },
  title: {
    fontSize: 22,
    lineHeight: 29,
    fontWeight: '600',
    letterSpacing: -0.6,
    color: colors.textPrimary,
  },
  back: {
    minWidth: 48,
    minHeight: 48,
    alignItems: 'center',
    justifyContent: 'center',
    marginLeft: -12,
  },
  action: { flexShrink: 1 },
});
