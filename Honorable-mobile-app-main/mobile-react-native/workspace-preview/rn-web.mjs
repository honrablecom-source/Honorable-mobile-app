import {Platform as WebPlatform} from 'react-native-web';
export * from 'react-native-web';
export const Alert={alert:(title,message)=>window.dispatchEvent(new CustomEvent('noble-preview-error',{detail:`${title}: ${message}`}))};

// Target the Android app's welcome/guest availability checks; RN Web internals remain web.
export const Platform={...WebPlatform,OS:'android',Version:34,select:values=>values.android??values.native??values.default};

// Browser review has no Android hardware-back event. Keep the native subscription explicit and inert.
export const BackHandler={addEventListener:()=>({remove:()=>{}})};
