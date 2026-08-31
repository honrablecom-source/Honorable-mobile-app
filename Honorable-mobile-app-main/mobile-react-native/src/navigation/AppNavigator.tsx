import React from'react';
import{StyleSheet,Text}from'react-native';
import{createBottomTabNavigator}from'@react-navigation/bottom-tabs';
import{createNativeStackNavigator}from'@react-navigation/native-stack';
import{HomeScreen}from'../screens/HomeScreen';import{MemoriesScreen}from'../screens/MemoriesScreen';import{StorageScreen}from'../screens/StorageScreen';import{SettingsScreen}from'../screens/SettingsScreen';
import{AboutLegalScreen,PrivacyPolicyScreen,TermsOfServiceScreen}from'../screens/LegalScreens';import{PlansScreen}from'../screens/PlansScreen';import{UsageScreen}from'../screens/UsageScreen';import{LockedFeatureScreen}from'../screens/LockedFeatureScreen';
import{colors}from'../design-system';import type{MainTabParamList,RootStackParamList}from'./types';
const Tabs=createBottomTabNavigator<MainTabParamList>();const Stack=createNativeStackNavigator<RootStackParamList>();
const symbols:Record<keyof MainTabParamList,string>={Home:'⌂',Memories:'▦',Storage:'◫',Settings:'⚙'};
function TabIcon({name,color}:{name:keyof MainTabParamList;color:string}){return <Text style={[styles.icon,{color}]}>{symbols[name]}</Text>}
function MainTabs(){return <Tabs.Navigator screenOptions={({route})=>({headerShown:false,tabBarActiveTintColor:colors.ice,tabBarInactiveTintColor:colors.textMuted,tabBarStyle:styles.tabBar,tabBarLabelStyle:styles.label,tabBarIcon:({color})=><TabIcon color={color} name={route.name}/>})}><Tabs.Screen name="Home" component={HomeScreen}/><Tabs.Screen name="Memories" component={MemoriesScreen}/><Tabs.Screen name="Storage" component={StorageScreen}/><Tabs.Screen name="Settings" component={SettingsScreen}/></Tabs.Navigator>}
export function AppNavigator(){return <Stack.Navigator screenOptions={{headerShown:false,animation:'slide_from_right'}}><Stack.Screen name="MainTabs" component={MainTabs}/><Stack.Screen name="Plans" component={PlansScreen}/><Stack.Screen name="Usage" component={UsageScreen}/><Stack.Screen name="AboutLegal" component={AboutLegalScreen}/><Stack.Screen name="TermsOfService" component={TermsOfServiceScreen}/><Stack.Screen name="PrivacyPolicy" component={PrivacyPolicyScreen}/><Stack.Screen name="LockedFeature" component={LockedFeatureScreen}/></Stack.Navigator>}
const styles=StyleSheet.create({icon:{fontSize:19},tabBar:{position:'absolute',left:18,right:18,bottom:12,height:62,borderRadius:31,backgroundColor:'rgba(15,17,20,.96)',borderTopColor:'transparent',paddingTop:5},label:{fontSize:10,fontWeight:'600'}});
