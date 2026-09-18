import React from 'react';
import { StatusBar } from 'react-native';
import { NavigationContainer, DarkTheme } from '@react-navigation/native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { AppNavigator } from './src/navigation/AppNavigator';
import { colors } from './src/design-system/tokens';
import { PortalHost } from '@rn-primitives/portal';
import { LibraryProvider } from './src/library/LibraryContext';
import { LibraryGate } from './src/library/LibraryGate';
import { SearchModeProvider } from './src/search/SearchModeContext';
import { MemoryPassProvider } from './src/passes/MemoryPassContext';
import { AccountGate } from './src/auth/AccountGate';
import './global.css';

const navigationTheme = {
  ...DarkTheme,
  colors: {
    ...DarkTheme.colors,
    primary: colors.accent,
    background: colors.canvas,
    card: colors.surfaceSelected,
    text: colors.textPrimary,
    border: colors.border,
    notification: colors.textSecondary,
  },
};

export default function App() {
  return (
    <SafeAreaProvider>
      <StatusBar barStyle="light-content" />
      <MemoryPassProvider>
        <AccountGate>
          <LibraryProvider>
            <SearchModeProvider>
              <LibraryGate>
                <NavigationContainer theme={navigationTheme}>
                  <AppNavigator />
                </NavigationContainer>
              </LibraryGate>
            </SearchModeProvider>
          </LibraryProvider>
        </AccountGate>
      </MemoryPassProvider>
      <PortalHost />
    </SafeAreaProvider>
  );
}
