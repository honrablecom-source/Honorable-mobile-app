import React from 'react';
import { StatusBar } from 'react-native';
import { NavigationContainer } from '@react-navigation/native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { AppNavigator } from './src/navigation/AppNavigator';
import { HONORABLE_THEME } from './lib/theme';
import { PortalHost } from '@rn-primitives/portal';
import { LibraryProvider } from './src/library/LibraryContext';
import { LibraryGate } from './src/library/LibraryGate';
import { SearchModeProvider } from './src/search/SearchModeContext';
import { MemoryPassProvider } from './src/passes/MemoryPassContext';
import { AccountGate } from './src/auth/AccountGate';
import './global.css';

export default function App() {
  return (
    <SafeAreaProvider>
      <StatusBar barStyle="dark-content" />
      <MemoryPassProvider>
        <AccountGate>
          <LibraryProvider>
            <SearchModeProvider>
              <LibraryGate>
                <NavigationContainer theme={HONORABLE_THEME}>
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
