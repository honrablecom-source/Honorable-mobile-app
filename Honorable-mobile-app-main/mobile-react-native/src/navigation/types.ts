import type {NativeStackScreenProps} from '@react-navigation/native-stack';
export type RootStackParamList = {MainTabs: undefined; Plans: undefined; Usage: undefined; LockedFeature: {feature: string; requiredPlan: 'SUPER'|'ULTIMATE'}};
export type MainTabParamList = {Search: undefined; Settings: undefined};
export type RootStackScreenProps<T extends keyof RootStackParamList> = NativeStackScreenProps<RootStackParamList,T>;
