import React, { useState } from 'react';
import {
  Alert,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { colors, radii } from '../design-system/tokens';
import { WorkspaceHeader } from '../design-system/WorkspaceHeader';
import { useNavigation } from '@react-navigation/native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useMemoryPass } from '../passes/MemoryPassContext';
import { memoryPasses } from '../passes/catalog';
export const productStyles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.surface },
  content: { padding: 24, paddingBottom: 40, gap: 16 },
  title: {
    fontSize: 32,
    fontWeight: '500',
    letterSpacing: -1,
    color: colors.textPrimary,
  },
  heading: {
    fontSize: 18,
    fontWeight: '600',
    color: colors.textPrimary,
    marginTop: 16,
  },
  body: { color: colors.textSecondary, fontSize: 14, lineHeight: 21 },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: 12 },
  card: {
    backgroundColor: colors.canvas,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radii.lg,
    padding: 16,
    width: '48%',
    gap: 8,
  },
  number: { color: colors.textPrimary, fontSize: 28, fontWeight: '600' },
  button: {
    padding: 14,
    borderRadius: radii.md,
    backgroundColor: colors.canvas,
    borderWidth: 1,
    borderColor: colors.border,
    minHeight: 48,
  },
  label: { color: colors.textPrimary },
  row: {
    paddingVertical: 14,
    borderBottomWidth: 1,
    borderColor: colors.border,
    gap: 5,
  },
  compact: {
    width: '100%',
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 14,
    borderBottomWidth: 1,
    borderColor: colors.border,
  },
  eyebrow: { fontSize: 12, letterSpacing: 0.5, color: colors.textSecondary },
  balance: { flex: 1, paddingVertical: 20, gap: 8 },
  bar: { height: 4, backgroundColor: colors.surfaceSelected, overflow: 'hidden' },
  hero: {
    height: 270,
    justifyContent: 'flex-end',
    padding: 20,
    overflow: 'hidden',
    borderRadius: radii.md,
  },
  heroTitle: {
    color: colors.textPrimary,
    fontSize: 36,
    fontWeight: '500',
    letterSpacing: -1,
  },
  tile: {
    width: '48%',
    minHeight: 120,
    paddingVertical: 16,
    borderTopWidth: 1,
    borderTopColor: colors.border,
    justifyContent: 'space-between',
  },
  total: {
    fontSize: 72,
    letterSpacing: -4,
    color: colors.textPrimary,
    fontWeight: '600',
  },
  spread: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    gap: 12,
  },
});
const s = productStyles;
export function ProductPage({
  title,
  children,
}: {
  title: string;
  children: React.ReactNode;
}) {
  const navigation = useNavigation();
  return (
    <SafeAreaView style={s.safe}>
      <WorkspaceHeader
        title={title}
        section="honorable / membership"
        onBack={navigation.canGoBack() ? () => navigation.goBack() : undefined}
      />
      <ScrollView contentContainerStyle={s.content}>{children}</ScrollView>
    </SafeAreaView>
  );
}
export function CreditBalances() {
  const { account } = useMemoryPass();
  return (
    <View style={s.grid}>
      <View style={s.balance}>
        <Text style={s.eyebrow}>FREE THIS MONTH</Text>
        <Text style={s.number}>
          {account?.freeMonthlyRemaining ?? '—'} /{' '}
          {account?.freeMonthlyTotal ?? '—'}
        </Text>
      </View>
      <View style={s.balance}>
        <Text style={s.eyebrow}>PURCHASED</Text>
        <Text style={s.number}>{account?.balance ?? '—'}</Text>
        <Text style={s.body}>Never expires</Text>
      </View>
    </View>
  );
}
export function ProductPassScreen() {
  const [all, setAll] = useState(false);
  const { restore } = useMemoryPass();
  return (
    <ProductPage title="Memory Credits">
      <Text style={s.body}>
        Search at your own pace. Purchased credits never expire.
      </Text>
      <CreditBalances />
      <Text style={s.heading}>{all ? 'All 31 passes' : 'Choose a pass'}</Text>
      <Text style={s.body}>
        Reference USD prices. Purchases are not enabled yet.
      </Text>
      <View style={s.grid}>
        {memoryPasses.slice(0, all ? 31 : 20).map((p, i) => (
          <Pressable
            key={p.id}
            style={i < 2 ? s.card : s.compact}
            onPress={() =>
              Alert.alert(
                'Memory Pass',
                'Store purchases are not connected. No payment collected.',
              )
            }
          >
            {i < 2 && (
              <Text style={s.eyebrow}>{['Starter', 'Everyday'][i]}</Text>
            )}
            <Text style={i < 2 ? s.number : s.label}>
              {p.credits.toLocaleString()}
              {i < 2 ? '' : ' credits'}
            </Text>
            {i < 2 && <Text style={s.body}>Memory Credits</Text>}
            <Text style={s.label}>${p.referenceUsd}</Text>
          </Pressable>
        ))}
      </View>
      <Pressable style={s.button} onPress={() => setAll(!all)}>
        <Text style={s.label}>
          {all ? 'Show recommended' : 'Show all 31 passes'}
        </Text>
      </Pressable>
      <Pressable
        style={s.button}
        onPress={() => restore().catch(e => Alert.alert('Restore', e.message))}
      >
        <Text style={s.label}>Restore purchases</Text>
      </Pressable>
    </ProductPage>
  );
}
export function ProductUsageScreen() {
  const { account } = useMemoryPass();
  const [limit, setLimit] = useState(20);
  const usage = account?.usage;
  const max = Math.max(1, ...Object.values(usage?.byModel ?? {}));
  return (
    <ProductPage title="Your usage">
      <Text style={s.total}>{account?.freeMonthlyRemaining ?? '—'}</Text>
      <Text style={s.body}>Free credits remaining</Text>
      <View style={s.bar}>
        <View
          style={{
            height: 4,
            width: `${Math.min(
              100,
              ((account?.freeMonthlyRemaining ?? 0) /
                (account?.freeMonthlyTotal || 15)) *
                100,
            )}%`,
            backgroundColor: colors.accent,
          }}
        />
      </View>
      <Text style={s.body}>
        Resets{' '}
        {account?.nextResetAt
          ? new Date(account.nextResetAt).toLocaleDateString()
          : '—'}
      </Text>
      <View style={[s.row, s.spread]}>
        <View>
          <Text style={s.label}>Purchased Memory Credits</Text>
          <Text style={s.body}>Never expire</Text>
        </View>
        <Text style={s.number}>{account?.balance ?? '—'}</Text>
      </View>
      <Text style={s.heading}>This month</Text>
      <Text style={s.body}>
        {usage?.month.searchCount ?? 0} searches ·{' '}
        {usage?.month.freeCreditsUsed ?? 0} free credits used ·{' '}
        {usage?.month.purchasedCreditsUsed ?? 0} purchased used
      </Text>
      {['FAST', 'VIDEO', 'TURBO'].map((n, i) => (
        <View key={n} style={s.spread}>
          <Text style={[s.body, { width: 52 }]}>{n}</Text>
          <View style={[s.bar, { flex: 1 }]}>
            <View
              style={{
                height: 4,
                width: `${
                  ((usage?.byModel[`SERAN_V${i + 1}`] ?? 0) / max) * 100
                }%`,
                backgroundColor: colors.textSecondary,
              }}
            />
          </View>
          <Text style={s.label}>{usage?.byModel[`SERAN_V${i + 1}`] ?? 0}</Text>
        </View>
      ))}
      <Text style={s.heading}>Recent activity</Text>
      {account?.transactions.slice(0, limit).map(t => (
        <View style={[s.row, s.spread]} key={t.id}>
          <View style={{ flex: 1 }}>
            <Text style={s.label}>{t.description}</Text>
            <Text style={s.body}>
              {new Date(t.createdAt).toLocaleDateString()}
            </Text>
          </View>
          <Text style={s.label}>
            {t.credits > 0 ? '+' : ''}
            {t.credits}
          </Text>
        </View>
      ))}
      {(account?.transactions.length ?? 0) > limit && (
        <Pressable style={s.button} onPress={() => setLimit(limit + 20)}>
          <Text style={s.label}>Load more</Text>
        </Pressable>
      )}
      <Text style={s.heading}>All time</Text>
      <Text style={s.body}>
        {usage?.allTime.searchesCompleted ?? 0} searches ·{' '}
        {usage?.allTime.passesPurchased ?? 0} passes purchased ·{' '}
        {usage?.allTime.purchasedCreditsUsed ?? 0} purchased credits used
      </Text>
    </ProductPage>
  );
}
export { StudioScreen } from './StudioScreen';
