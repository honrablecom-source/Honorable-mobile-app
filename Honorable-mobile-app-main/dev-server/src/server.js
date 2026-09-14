const http=require('node:http'),path=require('node:path'),crypto=require('node:crypto');
const {BetaStore}=require('./beta/store');
const {Ledger,passes,costs,available,catalog}=require('./ledger');
const {GoogleIdTokenVerifier}=require('./auth'),{PersistentSessions}=require('./sessions');
const {AnalyticsStore}=require('./admin/analytics-store'),{CLIENT_EVENTS,safeMetadata}=require('./admin/events'),{createAdminRouter}=require('./admin/router');
function createServer({file=process.env.HONORABLE_LEDGER_PATH||path.join(__dirname,'../data/ledger.json'),mode=process.env.HONORABLE_SERVER_MODE||'production',googleClientId=process.env.HONORABLE_GOOGLE_WEB_CLIENT_ID,googleVerifier,clock=Date.now,searchCompletionVerifier,webOrigins=(process.env.HONORABLE_WEB_ORIGINS||'').split(',').filter(Boolean),analyticsStore,adminConfig=process.env.HONORABLE_ADMIN_CONFIG,adminUsers,healthProbe,releaseChannel=process.env.HONORABLE_RELEASE_CHANNEL||(mode==='development'?'DEV':'PRODUCTION'),betaRequired=process.env.HONORABLE_BETA_REQUIRED==='1'||releaseChannel==='BETA'}={}){
 const dev=mode==='development',ledger=new Ledger(file,{clock,environment:mode}),sessions=new PersistentSessions(file+'.sessions',clock);
 const analytics=analyticsStore||new AnalyticsStore(file+'.analytics.json',{clock,rawDays:bounded(process.env.HONORABLE_ANALYTICS_RAW_DAYS,30,1,90),aggregateDays:bounded(process.env.HONORABLE_ANALYTICS_AGGREGATE_DAYS,400,30,730)});
 if(ledger.data.releaseChannel&&ledger.data.releaseChannel!==releaseChannel)throw Error('LEDGER_RELEASE_CHANNEL_MISMATCH');
 ledger.data.releaseChannel=releaseChannel;ledger.save();
 const beta=new BetaStore(file+'.beta.json',{clock,environment:mode,channel:releaseChannel,required:betaRequired});
 const google=googleVerifier||(googleClientId?new GoogleIdTokenVerifier({audience:googleClientId}):null);
 const origins=new Set(webOrigins);if(dev){origins.add('http://localhost:4174');origins.add('http://127.0.0.1:4174')}
 const cookieName='__Host-honorable',ingestionLimits=new Map();
 const setCookie=(res,token,maxAge=30*86400)=>res.setHeader('Set-Cookie',`${cookieName}=${token}; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=${maxAge}`);
 const view=id=>({...ledger.view(id),profile:ledger.account(id).profile||{}});
 const record=(type,accountId,metadata={},id=crypto.randomUUID())=>{try{if(accountId&&beta.tester(accountId)?.analyticsOptOut)return;analytics.record({id,type,accountId,environment:ledger.data.accounts[accountId]?.environment||mode,metadata:{...metadata,channel:releaseChannel}})}catch{/* Analytics cannot break product operations. */}};
 const registerInstallation=(id,installation,metadata)=>{const count=ledger.account(id).transactions.length;ledger.register(id,installation);for(const tx of ledger.account(id).transactions.slice(count))if(['FREE_MONTHLY_GRANT','FREE_MONTHLY_RESET'].includes(tx.type))record(tx.type==='FREE_MONTHLY_RESET'?'free_credit_reset':'free_credit_granted',id,metadata,'free:'+tx.id)};
 let admin=null;if(adminConfig||adminUsers){try{admin=createAdminRouter({ledger,analytics,sessions,environment:mode,configFile:adminConfig,users:adminUsers,clock,healthProbe,beta})}catch{/* Invalid admin configuration leaves every admin route closed. */}}
 const server=http.createServer(async(req,res)=>{
  if(req.url.startsWith('/admin')){if(admin)return admin(req,res);return send(res,404,{error:'NOT_FOUND'})}
  const started=clock();let accountId=null;const metadata={...requestMetadata(req),channel:releaseChannel};
  res.on('finish',()=>record('api_request',accountId,{...metadata,durationMs:Math.max(0,clock()-started),failed:res.statusCode>=400}));
  res.setHeader('content-type','application/json');res.setHeader('Cache-Control','no-store');
  try{
   const cookie=(req.headers.cookie||'').split(';').map(x=>x.trim()).find(x=>x.startsWith(cookieName+'='))?.slice(cookieName.length+1);
   const web=req.headers['x-honorable-web']==='1';
   if((web||cookie)&&req.method!=='GET'&&(!web||!origins.has(req.headers.origin)))throw status('INVALID_WEB_ORIGIN',403);
   let raw='';for await(const chunk of req){raw+=chunk;if(Buffer.byteLength(raw)>65536)throw status('REQUEST_TOO_LARGE',413)}const body=raw?JSON.parse(raw):{};
   if(req.method==='GET'&&req.url==='/v1/catalog')return send(res,200,{...catalog,passDetails:catalog.passes,passes,costs,available:[...available],freeMonthlyCredits:ledger.freeMonthly,creditsExpire:false});
   const establish=(id,ttl)=>{beta.assertAccess(id);accountId=id;if(body.installationId)registerInstallation(id,body.installationId,metadata);const value=sessions.issue(id,web?'web':'native',ttl);record('session_started',id,metadata);if(web){if(cookie)sessions.revoke(cookie);setCookie(res,value.refreshToken);return send(res,200,{account:view(id),expiresAt:value.expiresAt})}return send(res,200,{...value,account:view(id)})};
   if(req.method==='POST'&&req.url==='/v1/auth/google'){
    if(!google)throw status('GOOGLE_AUTH_NOT_CONFIGURED',503);const identity=await google.verify(body.idToken),invite=beta.preflight(identity.email,identity.emailVerified===true),existing=ledger.data.identities['GOOGLE:'+identity.sub],id=ledger.resolveIdentity('GOOGLE',identity.sub);beta.accept(invite,id);ledger.account(id).profile={name:identity.name||'',email:identity.email||''};ledger.save();if(!existing)record('account_registered',id,metadata,'registration:'+id);return establish(id);
   }
   if(req.method==='POST'&&req.url==='/dev/auth/token'){
    if(!dev)return send(res,404,{error:'NOT_FOUND'});const invite=beta.preflight(body.email,true),existing=ledger.data.identities['DEVELOPMENT:'+body.email],id=ledger.resolveIdentity('DEVELOPMENT',body.email);beta.accept(invite,id);ledger.account(id).profile={name:'Test account'};ledger.save();if(!existing)record('account_registered',id,metadata,'registration:'+id);return establish(id,body.ttlSeconds??900);
   }
   if(req.method==='POST'&&req.url==='/v1/auth/refresh'){if(web)throw status('WEB_SESSION_USES_COOKIE',400);beta.assertAccess(sessions.lookup(body.refreshToken,'refresh')[1].accountId);return send(res,200,sessions.refresh(body.refreshToken))}
   if(req.method==='POST'&&req.url==='/v1/auth/logout'){if(cookie)sessions.revoke(cookie);if(body.refreshToken)sessions.revoke(body.refreshToken);if(req.headers.authorization?.startsWith('Bearer '))sessions.revoke(req.headers.authorization.slice(7));if(web)setCookie(res,'',0);return send(res,200,{signedOut:true})}
   if(req.url.startsWith('/dev/')&&!dev)return send(res,404,{error:'NOT_FOUND'});
   const id=web?sessions.web(cookie):sessions.verify(req.headers.authorization);accountId=id;beta.assertAccess(id);
   const safeContext=betaRequestContext(req);try{beta.observe(id,safeContext)}catch{/* Operational observation must not break product. */}
   if(req.url==='/v1/beta/config'&&req.method==='GET')return send(res,200,beta.config(id,safeContext));
   if(req.url==='/v1/beta/feedback'&&req.method==='POST')return send(res,201,beta.feedback(id,body,safeContext));
   if(req.url==='/v1/beta/request'&&req.method==='POST'){const result=beta.request(id,body);if(body.type==='BETA_REMOVAL')sessions.revokeAccount(id);return send(res,200,result)}
   const release=beta.config(id,safeContext).release;if(release?.updateLevel==='REQUIRED'&&(!safeContext.buildNumber||Number(safeContext.buildNumber)<Number(release.minimumBuild))&&['/v1/search/start','/dev/studio','/v1/beta/authorize'].includes(req.url))throw status('UPDATE_REQUIRED',426);
   if(req.url==='/v1/beta/authorize'&&req.method==='POST'){beta.requireFlag(body.flag,id);if(['rig_preview_enabled','nodes_visible','code_workspace_visible'].includes(body.flag)&&ledger.account(id).subscription?.status!=='ACTIVE')throw status('STUDIO_REQUIRED',403);return send(res,200,{allowed:true})}
   if(req.headers['x-honorable-installation'])registerInstallation(id,req.headers['x-honorable-installation'],metadata);
   if(req.method==='POST'&&req.url==='/v1/analytics'){
    if(Object.keys(body).some(k=>k!=='events')||!Array.isArray(body.events)||body.events.length>20)throw status('INVALID_ANALYTICS_BATCH',400);
    const minute=Math.floor(clock()/60000);for(const[key,limit]of ingestionLimits)if(limit.minute!==minute)ingestionLimits.delete(key);
    const limit=ingestionLimits.get(id)||{minute,count:0};if(limit.count+body.events.length>120)throw status('ANALYTICS_RATE_LIMIT',429);
    const events=body.events.map(e=>{if(!e||Object.keys(e).some(k=>!['id','type','metadata'].includes(k))||typeof e.id!=='string'||!/^[a-f0-9-]{36}$/i.test(e.id)||!CLIENT_EVENTS.has(e.type))throw status('INVALID_CLIENT_EVENT',400);let safe;try{safe=safeMetadata(e.type,e.metadata)}catch{throw status('FORBIDDEN_ANALYTICS_FIELD',400)}return{...e,metadata:{...safe,...metadata}}});
    limit.count+=events.length;ingestionLimits.set(id,limit);let accepted=0;if(beta.tester(id)?.analyticsOptOut)return send(res,202,{accepted});for(const e of events)try{if(analytics.record({...e,accountId:id,environment:ledger.account(id).environment||mode,authority:'client'}))accepted++}catch{}return send(res,202,{accepted});
   }
   if(req.method==='GET'&&req.url==='/v1/auth/session'){record('session_active',id,metadata,'active:'+id+':'+new Date(clock()).toISOString().slice(0,13));return send(res,200,{account:view(id),entitlements:{memoryCredits:ledger.view(id).balance,subscription:ledger.view(id).subscription,availableModels:[...available]},expiresAt:web?sessions.expiry(cookie):undefined})}
   if(req.method==='GET'&&req.url==='/v1/account')return send(res,200,view(id));
   if(req.method==='GET'&&req.url==='/v1/entitlements'){const account=ledger.view(id);return send(res,200,{accountId:id,memoryCredits:account.balance,creditsExpire:false,subscription:account.subscription,availableModels:[...available]})}
   if(req.method==='GET'&&req.url==='/v1/transactions')return send(res,200,{transactions:ledger.view(id).transactions});
   if(req.method==='POST'&&req.url==='/dev/purchases'){const receipt=body.storeTransactionId||`dev-${crypto.randomUUID()}`,previous=ledger.data.verifiedPurchases[receipt],result=ledger.purchase(id,body.passId,receipt,{test:true,environment:mode});if(!previous)record('pass_purchased',id,metadata,'purchase:'+receipt);return send(res,200,result)}
   if(req.method==='POST'&&req.url==='/dev/studio'){const before=ledger.account(id).subscription.status,result=ledger.studio(id,body.active);if(before!==result.subscription.status)record(body.active?'studio_started':'studio_cancelled',id,metadata);return send(res,200,result)}
   if(req.method==='POST'&&req.url==='/v1/search/start'){if(body.model==='SERAN_V2')beta.requireFlag('video_search_enabled',id);const previous=ledger.data.searches[body.requestId],job=ledger.startSearch(id,body.model,body.requestId);if(!previous){job.channel=releaseChannel;ledger.save()}if(!previous)record('search_started',id,{...metadata,model:job.model},'search-start:'+body.requestId);return send(res,200,job)}
   if(req.method==='POST'&&req.url==='/v1/search/complete'){
    const job=ledger.data.searches[body.requestId];let trusted={};
    if(body.outcome==='SUCCESS'&&(!dev||searchCompletionVerifier)){
     if(!(searchCompletionVerifier&&await searchCompletionVerifier({accountId:id,requestId:body.requestId,model:job?.model,proof:body.proof})))throw status('SEARCH_COMPLETION_VERIFICATION_REQUIRED',503);
     trusted=searchCompletionVerifier.metadata?.(body.proof)||{};
    }
    const before=job?.state,result=ledger.finishSearch(id,body.requestId,body.outcome,trusted);
    if(before==='PENDING')record({SUCCESS:'search_completed',FAILED:'search_failed',CANCELLED:'search_cancelled'}[job.state],id,{...metadata,model:job.model,...(job.durationMs!=null?{durationMs:job.durationMs}:{}),...(job.mediaType?{mediaType:job.mediaType,videoMoment:!!job.videoMoment}:{})},'search-end:'+body.requestId);
    return send(res,200,result);
   }
   if(req.method==='POST'&&req.url==='/v1/credits/deduct'){if(!dev||searchCompletionVerifier)throw status('SEARCH_COMPLETION_VERIFICATION_REQUIRED',503);return send(res,200,ledger.deduct(id,body.model,body.requestId))}
   if(req.method==='POST'&&req.url==='/v1/purchases/restore')return send(res,200,ledger.restore(id));return send(res,404,{error:'NOT_FOUND'});
  }catch(error){record('error_occurred',accountId,{...metadata,errorCode:classifyError(req.url,error)});send(res,error.status||400,{error:error.message})}
 });
 server.beta=beta;server.analytics=analytics;server.ledger=ledger;server.on('close',()=>{void analytics.close?.()});return server;
}
function betaRequestContext(req){const safe=requestMetadata(req);for(const [key,header]of [['manufacturer','x-honorable-manufacturer'],['modelFamily','x-honorable-model-family'],['ramClass','x-honorable-ram-class']]){try{const part=require('./beta/schema').context({[key]:req.headers[header]});Object.assign(safe,part)}catch{}}return safe}
function requestMetadata(req){const platform=req.headers['x-honorable-platform']||(req.headers['x-honorable-web']==='1'?'WEB_TEST':'UNKNOWN'),metadata={platform:['ANDROID','WEB_TEST','IOS','UNKNOWN'].includes(platform)?platform:'UNKNOWN'};for(const[key,header]of [['appVersion','x-honorable-app-version'],['buildNumber','x-honorable-build'],['osVersion','x-honorable-os-version']])if(typeof req.headers[header]==='string'&&/^\d[\d.a-zA-Z_-]{0,23}$/.test(req.headers[header]))metadata[key]=req.headers[header];return metadata}
function classifyError(route,error){if(error.message==='SEARCH_COMPLETION_VERIFICATION_REQUIRED')return'COMPLETION_REJECTED';if(route.includes('/auth/'))return'AUTH_FAILED';if(route.includes('/restore'))return'RESTORE_FAILED';if(error.message==='INSUFFICIENT_CREDITS')return'DEBIT_FAILED';if(route.includes('/search/'))return'SEARCH_FAILED';return error.status&&error.status<500?'API_FAILED':'SERVER_ERROR'}
function bounded(value,fallback,min,max){const n=Number(value);return Number.isInteger(n)&&n>=min&&n<=max?n:fallback}
const status=(message,code)=>Object.assign(new Error(message),{status:code});function send(res,code,body){res.statusCode=code;res.setHeader('Content-Type','application/json');res.end(JSON.stringify(body))}
if(require.main===module)createServer().listen(Number(process.env.PORT||8787),process.env.HONORABLE_BIND_ADDRESS||'127.0.0.1');module.exports={createServer};
