import {NativeModules, Platform} from 'react-native';

export type SearchResult = {rank: number; mediaId: number; mediaUri: string; mediaType: 'IMAGE'|'VIDEO'; displayName: string; score: number; semantic: number; bestTimestampMs?: number; confidence: 'STRONG'|'POSSIBLE'|'WEAK'; evidence: string[]};
export type SearchResponse = {confident: boolean; decision: string; semantic: number; margin: number; results: SearchResult[]};
export type EntitlementState = {tier: 'FREE'|'PLUS'|'PRO'|'SUPER'|'ULTIMATE'; verified: boolean; storageLimitBytes: number; videoMinutesPerWindow: number; videosPerWindow: number; familyMembersTotal: number; features: string[]};
export type LibraryItem={mediaId:number;mediaUri:string;thumbnailUri?:string;mediaType:'IMAGE'|'VIDEO';displayName:string;capturedAt:number;durationMs?:number};
export type LibraryPage={total:number;items:LibraryItem[]};
export type IndexStatus={engine:'REAL';permissionGranted:boolean;indexedCount:number;status:string;processed?:number;total?:number;failed?:number;lastCompletedAt?:number};
type NativeBoundary = {search(query:string):Promise<SearchResponse>;cancelSearch():void;getStatus():Promise<IndexStatus>;getLibrary(limit:number,offset:number,kind:string|null):Promise<LibraryPage>;refreshIndex():Promise<{added:number;updated:number;deleted:number;failed:number;skipped:number;indexedCount:number}>;getEntitlementState():Promise<EntitlementState>;getSearchHistory():Promise<{queries:string[]}>;clearSearchHistory():Promise<{cleared:boolean}>;openMedia(uri:string,kind:string,timestampMs?:number):Promise<{opened:boolean}>};

const native = NativeModules.HonorableSearchModule as NativeBoundary | undefined;
function boundary():NativeBoundary { if(!native)throw new Error(`Honorable native engine is unavailable on ${Platform.OS}`);return native; }
export const honorableNative = {search:(query:string)=>boundary().search(query),cancelSearch:()=>boundary().cancelSearch(),getStatus:()=>boundary().getStatus(),getLibrary:(limit=60,offset=0,kind:'ALL'|'IMAGE'|'VIDEO'='ALL')=>boundary().getLibrary(limit,offset,kind==='ALL'?null:kind),refreshIndex:()=>boundary().refreshIndex(),getEntitlementState:()=>boundary().getEntitlementState(),getSearchHistory:()=>boundary().getSearchHistory(),clearSearchHistory:()=>boundary().clearSearchHistory(),openMedia:(item:{mediaUri:string;mediaType:'IMAGE'|'VIDEO';bestTimestampMs?:number})=>boundary().openMedia(item.mediaUri,item.mediaType,item.bestTimestampMs)};
