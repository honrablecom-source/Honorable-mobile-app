import type {EntitlementState} from '../native/HonorableNative';

export enum SearchMode{QUICK='QUICK',SMART='SMART',DEEP='DEEP'}
export type SearchModeId=SearchMode;
export type SearchModeOption={id:SearchMode;name:string;description:string;requiredFeature?:string;requirementLabel?:string};

export const searchModes:SearchModeOption[]=[
  {id:SearchMode.QUICK,name:'Quick',description:'Fast and lightweight'},
  {id:SearchMode.SMART,name:'Smart',description:'Better understanding of what you remember',requiredFeature:'smart_search',requirementLabel:'Upgrade'},
  {id:SearchMode.DEEP,name:'Deep Search',description:'Most detailed local search',requiredFeature:'deep_search',requirementLabel:'Pro'},
];

export function isSearchModeAvailable(mode:SearchModeOption,entitlement?:EntitlementState){
  return !mode.requiredFeature||!!entitlement?.features.includes(mode.requiredFeature);
}
