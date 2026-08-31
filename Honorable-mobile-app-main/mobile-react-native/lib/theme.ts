import {DarkTheme, type Theme} from '@react-navigation/native';

export const HONORABLE_THEME: Theme = {...DarkTheme, colors: {...DarkTheme.colors, primary:'#75A7FF',background:'#08090B',card:'#111318',text:'#F5F5F3',border:'#292C31',notification:'#75A7FF'}};
export const NAV_THEME={light:HONORABLE_THEME,dark:HONORABLE_THEME} as const;
