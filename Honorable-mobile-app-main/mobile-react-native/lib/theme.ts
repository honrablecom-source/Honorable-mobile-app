import { DefaultTheme, type Theme } from '@react-navigation/native';
import { colors } from '../src/design-system/tokens';

export const HONORABLE_THEME: Theme = {
  ...DefaultTheme,
  colors: {
    ...DefaultTheme.colors,
    primary: colors.textPrimary,
    background: colors.background,
    card: colors.surfaceRaised,
    text: colors.textPrimary,
    border: colors.border,
    notification: colors.accent,
  },
};
export const NAV_THEME = {
  light: HONORABLE_THEME,
  dark: HONORABLE_THEME,
} as const;
