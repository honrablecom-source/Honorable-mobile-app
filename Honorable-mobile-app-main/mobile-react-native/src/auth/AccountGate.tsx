import React, { useState } from 'react';
import { colors } from '../design-system/tokens';
import { UserRound, ShieldCheck } from 'lucide-react-native';
import { BetaUpdateNotice } from '../screens/BetaUpdateNotice';
import {
  ActivityIndicator,
  Image,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useMemoryPass } from '../passes/MemoryPassContext';
import { GuestEditingScreen } from '../screens/GuestEditingScreen';
export function AccountGate({ children }: { children: React.ReactNode }) {
  const auth = useMemoryPass();
  const [guestEditing, setGuestEditing] = useState(false);
  if (auth.signedIn)
    return (
      <>
        <BetaUpdateNotice />
        {children}
      </>
    );
  if (guestEditing)
    return <GuestEditingScreen onSignIn={() => setGuestEditing(false)} />;
  if (auth.status === 'restoring' || auth.status === 'signing-in')
    return (
      <View style={styles.launch}>
        <Text style={styles.wordmark}>honorable</Text>
        <ActivityIndicator color={colors.textSecondary} />
        <Text style={styles.small}>Opening your memories…</Text>
      </View>
    );
  return (
    <SafeAreaView style={styles.safe}>
      <ScrollView contentContainerStyle={styles.content}>
        <Text style={styles.wordmark}>honorable</Text>
        <Image
          source={require('./welcome.png')}
          style={styles.photo}
          accessibilityLabel="A quiet beach memory"
        />
        <Text style={styles.title}>Your memories. Your workspace.</Text>
        <Text style={styles.copy}>
          Search with Seran. Edit photos and videos. Start with what you
          remember.
        </Text>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Continue with Google"
          disabled={!auth.googleConfigured}
          accessibilityState={{ disabled: !auth.googleConfigured }}
          style={[styles.button, !auth.googleConfigured && { opacity: 0.45 }]}
          onPress={auth.signInGoogle}
        >
          <Text style={styles.buttonText}>Continue with Google</Text>
        </Pressable>
        {!auth.googleConfigured && (
          <Text style={styles.small}>
            Account sign-in is not configured in this build. Local editing is
            available below.
          </Text>
        )}
        {!!auth.error && (
          <Text accessibilityRole="alert" style={styles.small}>
            {auth.error}
          </Text>
        )}
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Edit without an account"
          onPress={() => setGuestEditing(true)}
          style={styles.guestButton}
        >
          <Text style={styles.guestButtonText}>Edit without an account</Text>
        </Pressable>
        <Text style={styles.small}>
          Local photo and video editing, including on-device AI tools. Search,
          credits and account features require sign-in.
        </Text>
        <View style={styles.privacyRow}>
          <ShieldCheck size={17} color={colors.textSecondary} />
          <Text style={styles.small}>Your memories stay yours.</Text>
        </View>
        <Text style={styles.small}>
          Signing in keeps your Memory Passes, credits and account access
          connected across sessions and devices.
        </Text>
        {auth.status === 'unavailable' && (
          <Pressable onPress={auth.refresh}>
            <Text style={styles.privacy}>Try restoring again</Text>
          </Pressable>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}
export function AccountPanel() {
  const auth = useMemoryPass();
  return (
    <View style={styles.panel}>
      <View style={styles.profileRow}>
        <View style={styles.avatar}>
          <UserRound size={26} color={colors.textPrimary} />
        </View>
        <View style={{ flex: 1, gap: 4 }}>
          <Text style={styles.profileName}>
            {auth.account?.profile?.name || 'Your account'}
          </Text>
          {!!auth.account?.profile?.email && (
            <Text style={styles.small}>{auth.account.profile.email}</Text>
          )}
          <Text style={styles.small}>
            {auth.status === 'offline'
              ? 'Offline · previously signed in'
              : auth.signedIn
              ? 'Signed in'
              : 'Sign-in unavailable'}
          </Text>
        </View>
      </View>
      <View style={styles.balanceRow}>
        <View style={{ flex: 1, gap: 6 }}>
          <Text style={styles.balanceValue}>
            {auth.account?.freeMonthlyRemaining ?? 0}
            <Text style={styles.balanceUnit}>
              {' '}
              / {auth.account?.freeMonthlyTotal ?? '—'}
            </Text>
          </Text>
          <Text style={styles.small}>Monthly free credits</Text>
        </View>
        <View style={styles.balanceDivider} />
        <View style={{ flex: 1, gap: 6 }}>
          <Text style={styles.balanceValue}>{auth.account?.balance ?? 0}</Text>
          <Text style={styles.small}>Purchased credits</Text>
        </View>
      </View>
      <View style={styles.accountFooter}>
        <Text style={styles.small}>Purchased credits never expire.</Text>
        <Pressable
          onPress={auth.signOut}
          accessibilityRole="button"
          accessibilityLabel="Sign out"
          style={styles.signOut}
        >
          <Text style={styles.signOutText}>Sign out</Text>
        </Pressable>
      </View>
    </View>
  );
}
const styles = StyleSheet.create({
  profileRow: { flexDirection: 'row', alignItems: 'center', gap: 14 },
  profileName: {
    fontSize: 19,
    fontWeight: '600',
    letterSpacing: -0.3,
    color: colors.textPrimary,
  },
  balanceRow: {
    flexDirection: 'row',
    gap: 24,
    marginTop: 16,
    paddingTop: 14,
    borderTopWidth: 1,
    borderColor: colors.border,
  },
  balanceValue: {
    fontSize: 22,
    fontWeight: '500',
    letterSpacing: -0.8,
    color: colors.textPrimary,
  },
  balanceUnit: { fontSize: 16, color: colors.textTertiary },
  balanceDivider: { width: 1, backgroundColor: colors.border },
  accountFooter: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 8,
    marginTop: 12,
  },
  avatar: {
    width: 44,
    height: 44,
    backgroundColor: colors.surfaceSelected,
    borderRadius: 22,
    borderWidth: 0,
    borderColor: colors.border,
    alignItems: 'center',
    justifyContent: 'center',
  },
  privacyRow: {
    flexDirection: 'row',
    gap: 8,
    alignItems: 'center',
    marginBottom: 16,
  },
  signOut: { minHeight: 44, justifyContent: 'center', paddingHorizontal: 6 },
  signOutText: { color: colors.textPrimary, fontSize: 12, fontWeight: '500' },
  safe: { flex: 1, backgroundColor: colors.canvas },
  launch: {
    flex: 1,
    backgroundColor: colors.canvas,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 18,
  },
  content: { padding: 24, paddingBottom: 40 },
  wordmark: {
    color: colors.textPrimary,
    fontSize: 23,
    fontWeight: '700',
    letterSpacing: -0.6,
  },
  photo: { height: 180, width: '100%', marginVertical: 24, borderRadius: 12 },
  title: {
    color: colors.textPrimary,
    fontSize: 36,
    lineHeight: 42,
    fontWeight: '500',
    letterSpacing: -1.2,
  },
  copy: {
    color: colors.textSecondary,
    fontSize: 17,
    lineHeight: 25,
    marginTop: 16,
    marginBottom: 24,
  },
  button: {
    backgroundColor: colors.textPrimary,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: colors.border,
    padding: 16,
    alignItems: 'center',
    marginVertical: 16,
  },
  buttonText: { fontSize: 15, fontWeight: '600', color: colors.canvas },
  guestButton: {
    borderRadius: 10,
    borderWidth: 1,
    borderColor: colors.border,
    padding: 16,
    alignItems: 'center',
    marginVertical: 16,
  },
  guestButtonText: {
    fontSize: 15,
    fontWeight: '600',
    color: colors.textPrimary,
  },
  privacy: {
    color: colors.textPrimary,
    fontSize: 14,
    fontWeight: '700',
    marginTop: 18,
    marginBottom: 10,
  },
  small: { color: colors.textTertiary, fontSize: 12, lineHeight: 18 },
  panel: {
    backgroundColor: colors.surfaceRaised,
    borderRadius: 16,
    padding: 16,
  },
});
