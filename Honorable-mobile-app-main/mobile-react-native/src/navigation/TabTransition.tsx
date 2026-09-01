import React,{useEffect,useRef,useState}from'react';
import{AccessibilityInfo,Animated,Easing,StyleSheet}from'react-native';
import{useIsFocused}from'@react-navigation/native';

export function TabTransition({children}:{children:React.ReactNode}){
  const focused=useIsFocused();const progress=useRef(new Animated.Value(focused?1:0)).current;const[reduceMotion,setReduceMotion]=useState(false);
  useEffect(()=>{AccessibilityInfo.isReduceMotionEnabled().then(setReduceMotion);const subscription=AccessibilityInfo.addEventListener('reduceMotionChanged',setReduceMotion);return()=>subscription.remove()},[]);
  useEffect(()=>{if(!focused){progress.setValue(0);return}if(reduceMotion){progress.setValue(1);return}progress.setValue(0);Animated.timing(progress,{toValue:1,duration:220,easing:Easing.out(Easing.cubic),useNativeDriver:true}).start()},[focused,progress,reduceMotion]);
  return <Animated.View pointerEvents={focused?'auto':'none'} style={[styles.scene,{opacity:progress,transform:[{translateY:progress.interpolate({inputRange:[0,1],outputRange:[10,0]})},{scale:progress.interpolate({inputRange:[0,1],outputRange:[.992,1]})}]}]}>{children}</Animated.View>;
}
const styles=StyleSheet.create({scene:{flex:1}});
