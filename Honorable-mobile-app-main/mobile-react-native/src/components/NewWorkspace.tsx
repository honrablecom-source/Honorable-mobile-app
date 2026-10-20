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
        Create from your media
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
              <Photo size={17} color={colors.textSecondary} />
            ) : (
              <Film size={17} color={colors.textSecondary} />
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
    minHeight: 76,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 14,
    gap: 8,
    backgroundColor: colors.canvas,
    borderColor: colors.border,
    borderWidth: 1,
    borderRadius: 10,
  },
  heading: { fontSize: 12, color: colors.textTertiary },
  label: { fontSize: 13, color: colors.textPrimary },
  busy: { opacity: 0.5 },
});
