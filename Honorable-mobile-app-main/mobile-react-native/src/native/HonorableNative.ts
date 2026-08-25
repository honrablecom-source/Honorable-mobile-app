import {NativeModules, Platform} from 'react-native';

export type SearchResult = {rank: number; mediaId: number; mediaUri: string; mediaType: 'IMAGE'|'VIDEO'; displayName: string; score: number; semantic: number; bestTimestampMs?: number; confidence: 'STRONG'|'POSSIBLE'|'WEAK'; evidence: string[]};
export type SearchResponse = {confident: boolean; decision: string; semantic: number; margin: number; results: SearchResult[]};
export type EntitlementState = {tier: 'FREE'|'PLUS'|'PRO'|'SUPER'|'ULTIMATE'; verified: boolean; storageLimitBytes: number; videoMinutesPerWindow: number; videosPerWindow: number; familyMembersTotal: number; features: string[]};
type NativeBoundary = {search(query:string):Promise<SearchResponse>;cancelSearch():void;getStatus():Promise<{engine:'REAL';permissionGranted:boolean;indexedCount:number;status:string}>;refreshIndex():Promise<{added:number;updated:number;deleted:number;indexedCount:number}>;getEntitlementState():Promise<EntitlementState>};

const native = NativeModules.HonorableSearchModule as NativeBoundary | undefined;
function boundary():NativeBoundary { if(!native)throw new Error(`Honorable native engine is unavailable on ${Platform.OS}`);return native; }
export const honorableNative = {search:(query:string)=>boundary().search(query),cancelSearch:()=>boundary().cancelSearch(),getStatus:()=>boundary().getStatus(),refreshIndex:()=>boundary().refreshIndex(),getEntitlementState:()=>boundary().getEntitlementState()};
