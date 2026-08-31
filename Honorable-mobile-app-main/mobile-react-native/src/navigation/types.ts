import type {NativeStackScreenProps} from '@react-navigation/native-stack';
export type RootStackParamList = {MainTabs: undefined; Plans: undefined; Usage: undefined; AboutLegal: undefined; TermsOfService: undefined; PrivacyPolicy: undefined; LockedFeature: {feature: string; requiredPlan: 'SUPER'|'ULTIMATE'}};
export type MainTabParamList = {Home: undefined; Memories: undefined; Storage: undefined; Settings: undefined};
export type RootStackScreenProps<T extends keyof RootStackParamList> = NativeStackScreenProps<RootStackParamList,T>;
