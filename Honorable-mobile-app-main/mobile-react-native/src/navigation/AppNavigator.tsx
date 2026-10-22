import React from 'react';
import { BetaFeedbackScreen } from '../screens/BetaFeedbackScreen';
import { StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import {
  Home,
  Images,
  SlidersHorizontal,
  UserRound,
} from 'lucide-react-native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { HomeScreen } from '../screens/HomeScreen';
import { MemoriesScreen } from '../screens/MemoriesScreen';
import { StorageScreen } from '../screens/StorageScreen';
import { SettingsScreen } from '../screens/SettingsScreen';
import { ModelsScreen } from '../screens/ModelsScreen';
import { StudioScreen } from '../screens/ProductScreens';
import { PassScreen } from '../screens/PassScreen';
import {
  AboutLegalScreen,
  PrivacyPolicyScreen,
  TermsOfServiceScreen,
} from '../screens/LegalScreens';
import { PlansScreen } from '../screens/PlansScreen';
import { UsageScreen } from '../screens/UsageScreen';
import { LockedFeatureScreen } from '../screens/LockedFeatureScreen';
import {
  PrivacyDataScreen,
  SeranConsentScreen,
  SeranImprovementScreen,
} from '../screens/PrivacyDataScreen';
import { colors } from '../design-system';
import type { MainTabParamList, RootStackParamList } from './types';
import { selectionHaptic } from '../feedback/haptics';
import { TabTransition } from './TabTransition';
const Tabs = createBottomTabNavigator<MainTabParamList>();
const Stack = createNativeStackNavigator<RootStackParamList>();
const icons = {
  Home,
  Memories: Images,
  Studio: SlidersHorizontal,
  Account: UserRound,
};
function TabIcon({
  name,
  color,
  focused,
}: {
  name: keyof MainTabParamList;
  color: string;
  focused: boolean;
}) {
  const Icon = icons[name];
  return (
    <View style={[styles.icon, focused && styles.iconSelected]}>
      <Icon color={color} size={22} strokeWidth={focused ? 2 : 1.7} />
    </View>
  );
}
const HomeTab = () => (
  <TabTransition>
    <HomeScreen />
  </TabTransition>
);
const MemoriesTab = () => (
  <TabTransition>
    <MemoriesScreen />
  </TabTransition>
);
function MainTabs() {
  const insets = useSafeAreaInsets();
  return (
    <Tabs.Navigator
      screenListeners={({ navigation, route }) => ({
        tabPress: () => {
          const state = navigation.getState();
          if (state.routes[state.index]?.key !== route.key) selectionHaptic();
        },
      })}
      screenOptions={({ route }) => ({
        headerShown: false,
        tabBarActiveTintColor: colors.textPrimary,
        tabBarInactiveTintColor: colors.textMuted,
        tabBarStyle: [
          styles.tabBar,
          { height: 62 + insets.bottom, paddingBottom: insets.bottom + 4 },
        ],
        tabBarLabelStyle: styles.label,
        tabBarActiveBackgroundColor: colors.canvas,
        tabBarIcon: ({ color, focused }) => (
          <TabIcon color={color} focused={focused} name={route.name} />
        ),
      })}
    >
      <Tabs.Screen name="Home" component={HomeTab} />
      <Tabs.Screen
        name="Memories"
        component={MemoriesTab}
        options={{ title: 'Library' }}
      />
      <Tabs.Screen name="Studio" component={StudioScreen} />
      <Tabs.Screen name="Account" component={SettingsScreen} />
    </Tabs.Navigator>
  );
}
export function AppNavigator() {
  return (
    <Stack.Navigator
      screenOptions={{ headerShown: false, animation: 'slide_from_right' }}
    >
      <Stack.Screen name="MainTabs" component={MainTabs} />
      <Stack.Screen name="Pass" component={PassScreen} />
      <Stack.Screen name="Studio" component={StudioScreen} />
      <Stack.Screen name="BetaFeedback" component={BetaFeedbackScreen} />
      <Stack.Screen name="Settings" component={SettingsScreen} />
      <Stack.Screen name="Models" component={ModelsScreen} />
      <Stack.Screen name="Activity" component={StorageScreen} />
      <Stack.Screen name="Plans" component={PlansScreen} />
      <Stack.Screen name="Usage" component={UsageScreen} />
      <Stack.Screen name="AboutLegal" component={AboutLegalScreen} />
      <Stack.Screen name="TermsOfService" component={TermsOfServiceScreen} />
      <Stack.Screen name="PrivacyPolicy" component={PrivacyPolicyScreen} />
      <Stack.Screen name="PrivacyData" component={PrivacyDataScreen} />
      <Stack.Screen
        name="SeranImprovement"
        component={SeranImprovementScreen}
      />
      <Stack.Screen name="SeranConsent" component={SeranConsentScreen} />
      <Stack.Screen name="LockedFeature" component={LockedFeatureScreen} />
    </Stack.Navigator>
  );
}
const styles = StyleSheet.create({
  tabBar: {
    height: 64,
    backgroundColor: colors.surface,
    borderTopWidth: 1,
    borderTopColor: colors.surfaceSelected,
    paddingTop: 8,
  },
  icon: {
    width: 48,
    height: 30,
    borderRadius: 15,
    alignItems: 'center',
    justifyContent: 'center',
  },
  iconSelected: { backgroundColor: colors.surfaceSelected },
  label: { fontSize: 11, fontWeight: '500', letterSpacing: 0 },
});
