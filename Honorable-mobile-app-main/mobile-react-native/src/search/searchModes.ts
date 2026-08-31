import type {EntitlementState} from '../native/HonorableNative';

export type SearchModeId='QUICK'|'SMART'|'DEEP';
export type SearchMode={id:SearchModeId;name:string;description:string;requiredFeature?:string;requirementLabel?:string};

export const searchModes:SearchMode[]=[
  {id:'QUICK',name:'Quick',description:'Fast and lightweight'},
  {id:'SMART',name:'Smart',description:'Better understanding of what you remember',requiredFeature:'smart_search',requirementLabel:'Upgrade'},
  {id:'DEEP',name:'Deep Search',description:'Most detailed local search',requiredFeature:'deep_search',requirementLabel:'Pro'},
];

export function isSearchModeAvailable(mode:SearchMode,entitlement?:EntitlementState){
  return !mode.requiredFeature||!!entitlement?.features.includes(mode.requiredFeature);
}
