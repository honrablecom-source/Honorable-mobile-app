export type ReleaseChannel = 'DEV'|'INTERNAL'|'BETA'|'PRODUCTION';
export type BetaStatus = 'INVITED'|'ACTIVE'|'PAUSED'|'REMOVED';
export type BetaCohort = 'FOUNDERS'|'FRIENDS_FAMILY'|'EARLY_TESTERS'|'CREATORS'|'HEAVY_MEDIA'|'LOW_END_DEVICE'|'VIDEO_TESTERS';
export type BugStatus = 'NEW'|'TRIAGED'|'IN_PROGRESS'|'FIXED'|'VERIFIED'|'CLOSED';
export type Severity = 'S1'|'S2'|'S3'|'S4';
export type FeatureFlag = 'studio_enabled'|'video_search_enabled'|'v3_visible'|'rig_preview_enabled'|'nodes_visible'|'code_workspace_visible';
export interface FlagRule { flag:FeatureFlag; scope:'GLOBAL'|'COHORT'|'ACCOUNT'; target:string; enabled:boolean; reason:string; }
export interface SearchAssessment { outcome:'CORRECT'|'WRONG'|'NOT_FOUND'|'TIMESTAMP_WRONG'|'TOO_SLOW'; model?:'SERAN_V1'|'SERAN_V2'|'SERAN_V3'; latencyMs?:number; ranks?:number[]; resultIds?:string[]; }
export interface FeedbackInput {eventId:string;category:'BUG'|'SEARCH RESULT'|'VIDEO SEARCH'|'EDITOR'|'PASS/USAGE'|'STUDIO'|'PERFORMANCE'|'UI'|'OTHER'; title:string;description:string;context?:Record<string,string>;diagnosticConsent?:boolean;search?:SearchAssessment;}
