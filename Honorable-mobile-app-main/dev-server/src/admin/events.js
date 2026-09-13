'use strict';
// The only ingestion schema. Arbitrary metadata, queries, media paths and code are rejected.
const CLIENT_EVENTS = new Set(['result_opened','editor_opened','edit_tool_used','edit_saved','workspace_opened','feature_interest','client_error']);
const SERVER_EVENTS = new Set(['account_registered','session_started','session_active','search_started','search_completed','search_failed','search_cancelled','pass_purchased','free_credit_granted','free_credit_reset','studio_started','studio_cancelled','error_occurred','api_request']);
const enums = {
  platform: ['ANDROID','WEB_TEST','IOS','UNKNOWN'], capabilityClass:['LOW','MID','HIGH','UNKNOWN'],
  model:['SERAN_V1','SERAN_V2','SERAN_V3'], mediaType:['IMAGE','VIDEO','UNKNOWN'],
  tool:['crop','rotate','brightness','contrast','saturation','exposure','monochrome','undo','redo','reset'],
  workspace:['Layers','Project','Python','Java','Nodes','Video','Code'],
  feature:['generativeFill','expansion','rigAnimation','videoSequence','cameraTracking','video3d','geometry','nodes','code','basicPoseRig','subjectSelection','patch','smartFilters','raw','relight','smartLayers'],
  errorCode:['AUTH_FAILED','API_FAILED','SEARCH_FAILED','COMPLETION_REJECTED','DEBIT_FAILED','RESTORE_FAILED','SAVE_FAILED','SERVER_ERROR','CLIENT_ERROR'],
};
const fields = {
 result_opened:['mediaType','videoMoment'], editor_opened:['mediaType','fromSearch'], edit_tool_used:['tool'],
 edit_saved:['mediaType','fromSearch'], workspace_opened:['workspace'], feature_interest:['feature'], client_error:['errorCode'],
 account_registered:[], session_started:[], session_active:[], search_started:['model'],
 search_completed:['model','durationMs','mediaType','videoMoment'], search_failed:['model','durationMs'], search_cancelled:['model','durationMs'],
 pass_purchased:[],free_credit_granted:[],free_credit_reset:[],studio_started:[],studio_cancelled:[],error_occurred:['errorCode'],api_request:['durationMs','failed'],
};
function safeMetadata(type, metadata={}) {
 if (!metadata || typeof metadata!=='object' || Array.isArray(metadata)) throw Error('INVALID_METADATA');
 const allowed=new Set([...(fields[type]||[]),'platform','appVersion','buildNumber','osVersion','capabilityClass']);
 const output={};
 for(const [key,value] of Object.entries(metadata)) {
  if(!allowed.has(key)) throw Error('FORBIDDEN_ANALYTICS_FIELD');
  if(enums[key]) {if(!enums[key].includes(value)) throw Error('INVALID_ANALYTICS_VALUE');}
  else if(['appVersion','buildNumber','osVersion'].includes(key)) {if(typeof value!=='string'||!/^\d[\d.a-zA-Z_-]{0,23}$/.test(value)) throw Error('INVALID_VERSION');}
  else if(key==='durationMs') {if(!Number.isFinite(value)||value<0||value>3600000)throw Error('INVALID_DURATION');}
  else if(['videoMoment','fromSearch','failed'].includes(key)) {if(typeof value!=='boolean')throw Error('INVALID_FLAG');}
  else throw Error('FORBIDDEN_ANALYTICS_FIELD');
  output[key]=value;
 }
 return output;
}
module.exports={CLIENT_EVENTS,SERVER_EVENTS,safeMetadata};
