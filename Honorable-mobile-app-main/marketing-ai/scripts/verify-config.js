import { pathToFileURL } from 'node:url';
import { databaseStatus } from './database-check.js';

const timeoutSignal=()=>AbortSignal.timeout(8000);
const missing=(...values)=>values.some(value=>!value);

function source(env,...names) {
  if(names.some(name=>!env[name]))return 'MISSING';
  return env.CODESPACES==='true'?'CODESPACES_SECRET':'LOCAL_ENV';
}

async function responseJson(response) {
  try { return await response.json(); } catch { return {}; }
}

function googleReason(response,payload) {
  const reason=payload?.error?.errors?.[0]?.reason||payload?.error?.status||'';
  const message=String(payload?.error?.message||'').toLowerCase();
  if(response.status===429||reason==='quotaExceeded'||reason==='dailyLimitExceeded')return 'QUOTA_EXCEEDED';
  if(reason==='accessNotConfigured'||message.includes('has not been used')||message.includes('is disabled'))return 'API_NOT_ENABLED';
  if(reason==='ipRefererBlocked'||reason==='forbidden'||message.includes('referer')||message.includes('restriction'))return 'KEY_RESTRICTION';
  if(response.status===400||response.status===401||reason==='keyInvalid'||message.includes('api key not valid'))return 'INVALID_KEY';
  return 'UNKNOWN';
}

export async function verifyConfig(env=process.env,fetchImpl=fetch) {
  const databaseResult=await databaseStatus(env);
  const database={status:databaseResult.status,reason:databaseResult.reason||'CONNECTED',source:source(env,'DATABASE_URL')};
  const configuredSender=(env.EMAIL_FROM?.match(/<([^>]+)>/)?.[1]||env.EMAIL_FROM||'').trim();
  const developmentRecipient=(env.RESEND_DEV_RECIPIENT||configuredSender).trim();
  let developmentEmail={status:'CONFIGURATION REQUIRED',reason:env.RESEND_API_KEY?'MISSING_VERIFIED_ACCOUNT_EMAIL':'MISSING_API_KEY',source:source(env,'RESEND_API_KEY',env.RESEND_DEV_RECIPIENT?'RESEND_DEV_RECIPIENT':'EMAIL_FROM')};
  let productionEmail={status:'DOMAIN REQUIRED',reason:'DOMAIN_REQUIRED',source:source(env,'RESEND_API_KEY','EMAIL_FROM')};
  if(env.RESEND_API_KEY){
    try {
      const response=await fetchImpl('https://api.resend.com/domains',{headers:{authorization:`Bearer ${env.RESEND_API_KEY}`},signal:timeoutSignal()});
      const payload=await responseJson(response);
      const sender=configuredSender;
      const domain=sender.split('@')[1]?.toLowerCase();
      const domains=Array.isArray(payload?.data)?payload.data:[];
      const configured=domains.find(item=>item.name?.toLowerCase()===domain);
      if(response.status===401||response.status===403)developmentEmail={...developmentEmail,status:'FAILED',reason:'INVALID_API_KEY'};
      else if(!response.ok)developmentEmail={...developmentEmail,status:'FAILED',reason:'UNKNOWN'};
      else if(developmentRecipient)developmentEmail={...developmentEmail,status:'CONNECTED',reason:'CONNECTED'};
      if(response.ok&&configured?.status==='verified')productionEmail={...productionEmail,status:'CONNECTED',reason:'CONNECTED'};
      else if(sender)productionEmail.reason=configured?'DOMAIN_NOT_VERIFIED':'SENDER_NOT_VERIFIED';
    } catch { developmentEmail={...developmentEmail,status:'FAILED',reason:'NETWORK_FAILURE'}; }
  }
  let youtube={status:'MISSING',reason:'MISSING',source:source(env,'YOUTUBE_API_KEY')};
  if(env.YOUTUBE_API_KEY){
    const youtubeUrl=new URL('https://www.googleapis.com/youtube/v3/videos');
    youtubeUrl.search=new URLSearchParams({part:'id',id:'dQw4w9WgXcQ',key:env.YOUTUBE_API_KEY}).toString();
    try {
      const response=await fetchImpl(youtubeUrl,{signal:timeoutSignal()});
      const payload=await responseJson(response);
      youtube=response.ok&&Array.isArray(payload?.items)
        ?{...youtube,status:'CONNECTED',reason:'CONNECTED'}
        :{...youtube,status:'FAILED',reason:googleReason(response,payload)};
    } catch { youtube={...youtube,status:'FAILED',reason:'NETWORK_FAILURE'}; }
  }
  const webMissing=['GOOGLE_SEARCH_API_KEY','GOOGLE_SEARCH_ENGINE_ID'].filter(name=>!env[name]);
  const web={status:webMissing.length?'MISSING':'CONFIGURED',missing:webMissing,source:source(env,'GOOGLE_SEARCH_API_KEY','GOOGLE_SEARCH_ENGINE_ID')};
  const instagram=env.INSTAGRAM_ACCESS_TOKEN&&env.INSTAGRAM_BUSINESS_ACCOUNT_ID?'CONNECTED':'APPROVAL REQUIRED';
  const reddit=env.REDDIT_COMMERCIAL_APPROVAL==='true'&&env.REDDIT_CLIENT_ID&&env.REDDIT_CLIENT_SECRET&&env.REDDIT_USER_AGENT?'CONNECTED':'APPROVAL REQUIRED';
  return {database,emailDevelopment:developmentEmail,emailProduction:productionEmail,youtube,web,instagram,reddit,tiktok:'UNAVAILABLE / MANUAL ONLY'};
}

if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href){
  const status=await verifyConfig();
  console.log(`DATABASE: ${status.database.status} — ${status.database.reason}`);
  console.log(`DATABASE SOURCE: ${status.database.source}`);
  console.log(`EMAIL DEVELOPMENT: ${status.emailDevelopment.status} — ${status.emailDevelopment.reason}`);
  console.log(`EMAIL DEVELOPMENT SOURCE: ${status.emailDevelopment.source}`);
  console.log(`EMAIL PRODUCTION: ${status.emailProduction.status} — ${status.emailProduction.reason}`);
  console.log(`EMAIL PRODUCTION SOURCE: ${status.emailProduction.source}`);
  console.log(`YOUTUBE: ${status.youtube.status} — ${status.youtube.reason}`);
  console.log(`YOUTUBE SOURCE: ${status.youtube.source}`);
  console.log(`WEB DISCOVERY: ${status.web.status}${status.web.missing.length?` — ${status.web.missing.join(', ')}`:''}`);
  console.log(`WEB DISCOVERY SOURCE: ${status.web.source}`);
  console.log(`INSTAGRAM: ${status.instagram}`);
  console.log(`REDDIT: ${status.reddit}`);
  console.log(`TIKTOK: ${status.tiktok}`);
}
