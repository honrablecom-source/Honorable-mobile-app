import React from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { Image as Photo, Film, ArrowUpRight } from 'lucide-react-native';
import { Text } from '@/components/ui/text';
import { colors } from '../design-system/tokens';
import { useOpenWorkspace, WorkspaceKind } from '../native/openWorkspace';

export function NewWorkspace() {
  const { busy, open } = useOpenWorkspace();
  return (
    <View style={styles.section}>
      <Text accessibilityRole="header" style={styles.heading}>
        Create
      </Text>
      <View style={styles.row}>
        {(['IMAGE', 'VIDEO'] as WorkspaceKind[]).map(kind => (
          <Pressable
            key={kind}
            accessibilityRole="button"
            accessibilityLabel={
              kind === 'IMAGE'
                ? 'Choose a photo to edit'
                : 'Choose a video to edit'
            }
            accessibilityState={{ disabled: busy, busy }}
            disabled={busy}
            onPress={() => open(kind)}
            style={[styles.tile, busy && styles.busy]}
          >
            {kind === 'IMAGE' ? (
              <Photo size={21} color={colors.textSecondary} />
            ) : (
              <Film size={21} color={colors.textSecondary} />
            )}
            <Text style={styles.label}>
              {kind === 'IMAGE' ? 'Edit photo' : 'Edit video'}
            </Text>
            <ArrowUpRight size={13} color={colors.textTertiary} />
          </Pressable>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  section: { marginHorizontal: 20, marginVertical: 20, gap: 12 },
  row: { flexDirection: 'row', gap: 12 },
  tile: {
    flex: 1,
    minHeight: 70,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 14,
    gap: 8,
    backgroundColor: colors.surfaceRaised,
    borderColor: colors.border,
    borderWidth: 0,
    borderRadius: 18,
  },
  heading: {
    fontSize: 19,
    fontWeight: '600',
    letterSpacing: -0.4,
    color: colors.textPrimary,
  },
  label: { fontSize: 14, fontWeight: '500', color: colors.textPrimary },
  busy: { opacity: 0.5 },
});
