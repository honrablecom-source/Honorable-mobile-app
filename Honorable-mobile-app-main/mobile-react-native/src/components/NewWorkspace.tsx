import React from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { Text } from '@/components/ui/text';
import { colors } from '../design-system/tokens';
import { useOpenWorkspace, WorkspaceKind } from '../native/openWorkspace';

export function NewWorkspace() {
  const { busy, open } = useOpenWorkspace();
  return (
    <View style={styles.section}>
      <Text accessibilityRole="header">New Workspace</Text>
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
            <Text style={styles.plus}>+</Text>
            <Text>{kind === 'IMAGE' ? 'Photo' : 'Video'}</Text>
          </Pressable>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  section: { marginHorizontal: 16, marginVertical: 16, gap: 12 },
  row: { flexDirection: 'row', gap: 12 },
  tile: {
    flex: 1,
    minHeight: 88,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 4,
    backgroundColor: colors.surfaceRaised,
    borderColor: colors.border,
    borderWidth: 1,
    borderRadius: 10,
  },
  plus: { fontSize: 28, color: colors.textSecondary },
  busy: { opacity: 0.5 },
});
