import{Platform,Vibration}from'react-native';

/** Light navigation acknowledgement. Android only until an iOS haptics bridge is bundled. */
export function selectionHaptic(){
  if(Platform.OS==='android')Vibration.vibrate(8);
}
