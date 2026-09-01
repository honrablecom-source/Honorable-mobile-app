import React,{createContext,useContext,useEffect,useMemo,useState}from'react';
import{honorableNative,EntitlementState}from'../native/HonorableNative';
import{isSearchModeAvailable,searchModes,SearchMode}from'./searchModes';

type Value={mode:SearchMode;setMode:(mode:SearchMode)=>void;entitlement?:EntitlementState;availableModes:SearchMode[]};
const Context=createContext<Value|undefined>(undefined);
export function SearchModeProvider({children}:{children:React.ReactNode}){const[mode,setRequestedMode]=useState(SearchMode.QUICK);const[entitlement,setEntitlement]=useState<EntitlementState>();useEffect(()=>{honorableNative.getEntitlementState().then(setEntitlement).catch(()=>undefined)},[]);const availableModes=useMemo(()=>searchModes.filter(item=>isSearchModeAvailable(item,entitlement)).map(item=>item.id),[entitlement]);const setMode=(next:SearchMode)=>{if(availableModes.includes(next))setRequestedMode(next)};return <Context.Provider value={{mode,setMode,entitlement,availableModes}}>{children}</Context.Provider>}
export function useSearchMode(){const value=useContext(Context);if(!value)throw new Error('useSearchMode must be used inside SearchModeProvider');return value}
