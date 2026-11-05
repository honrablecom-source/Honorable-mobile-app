// Guest/welcome boundary only. No simulated sign-in, credits, purchases or backend requests.
const auth={signedIn:false,status:'welcome',googleConfigured:false,error:undefined,refresh:async()=>{},signInGoogle:async()=>{throw Error('Account sign-in requires the native configured app.');}};
export const useMemoryPass=()=>auth;
