const http=require('node:http');const path=require('node:path');const crypto=require('node:crypto');
const{Ledger,passes,costs,available,catalog}=require('./ledger');const{GoogleIdTokenVerifier}=require('./auth');const{PersistentSessions}=require('./sessions');
function createServer({file=process.env.HONORABLE_LEDGER_PATH||path.join(__dirname,'../data/ledger.json'),mode=process.env.HONORABLE_SERVER_MODE||'production',googleClientId=process.env.HONORABLE_GOOGLE_WEB_CLIENT_ID,googleVerifier,clock,searchCompletionVerifier,webOrigins=(process.env.HONORABLE_WEB_ORIGINS||'').split(',').filter(Boolean)}={}){
 const dev=mode==='development',ledger=new Ledger(file,{clock}),sessions=new PersistentSessions(file+'.sessions',clock);
 const google=googleVerifier||(googleClientId?new GoogleIdTokenVerifier({audience:googleClientId}):null);
 const origins=new Set(webOrigins);if(dev){origins.add('http://localhost:4174');origins.add('http://127.0.0.1:4174')}
 const cookieName='__Host-honorable';
 const setCookie=(res,token,maxAge=30*86400)=>res.setHeader('Set-Cookie',`${cookieName}=${token}; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=${maxAge}`);
 const view=id=>({...ledger.view(id),profile:ledger.account(id).profile||{}});
 return http.createServer(async(req,res)=>{res.setHeader('content-type','application/json');res.setHeader('Cache-Control','no-store');try{
  const cookie=(req.headers.cookie||'').split(';').map(x=>x.trim()).find(x=>x.startsWith(cookieName+'='))?.slice(cookieName.length+1);
  const web=req.headers['x-honorable-web']==='1';
  if((web||cookie)&&req.method!=='GET'&&(!web||!origins.has(req.headers.origin)))throw status('INVALID_WEB_ORIGIN',403);
  let raw='';for await(const chunk of req){raw+=chunk;if(Buffer.byteLength(raw)>65536)throw status('REQUEST_TOO_LARGE',413)}const body=raw?JSON.parse(raw):{};
  if(req.method==='GET'&&req.url==='/v1/catalog')return send(res,200,{...catalog,passDetails:catalog.passes,passes,costs,available:[...available],freeMonthlyCredits:ledger.freeMonthly,creditsExpire:false});
  const establish=(id,ttl)=>{if(body.installationId)ledger.register(id,body.installationId);const value=sessions.issue(id,web?'web':'native',ttl);if(web){if(cookie)sessions.revoke(cookie);setCookie(res,value.refreshToken);return send(res,200,{account:view(id),expiresAt:value.expiresAt})}return send(res,200,{...value,account:view(id)})};
  if(req.method==='POST'&&req.url==='/v1/auth/google'){if(!google)throw status('GOOGLE_AUTH_NOT_CONFIGURED',503);const identity=await google.verify(body.idToken);const id=ledger.resolveIdentity('GOOGLE',identity.sub);ledger.account(id).profile={name:identity.name||'',email:identity.email||''};ledger.save();return establish(id)}
  if(req.method==='POST'&&req.url==='/dev/auth/token'){if(!dev)return send(res,404,{error:'NOT_FOUND'});const id=ledger.resolveIdentity('DEVELOPMENT',body.email);ledger.account(id).profile={name:'Test account'};ledger.save();return establish(id,body.ttlSeconds??900)}
  if(req.method==='POST'&&req.url==='/v1/auth/refresh'){if(web)throw status('WEB_SESSION_USES_COOKIE',400);return send(res,200,sessions.refresh(body.refreshToken))}
  if(req.method==='POST'&&req.url==='/v1/auth/logout'){if(cookie)sessions.revoke(cookie);if(body.refreshToken)sessions.revoke(body.refreshToken);if(req.headers.authorization?.startsWith('Bearer '))sessions.revoke(req.headers.authorization.slice(7));if(web)setCookie(res,'',0);return send(res,200,{signedOut:true})}
  if(req.url.startsWith('/dev/')&&!dev)return send(res,404,{error:'NOT_FOUND'});
  const id=web?sessions.web(cookie):sessions.verify(req.headers.authorization);
  if(req.headers['x-honorable-installation'])ledger.register(id,req.headers['x-honorable-installation']);
  if(req.method==='GET'&&req.url==='/v1/auth/session')return send(res,200,{account:view(id),entitlements:{memoryCredits:ledger.view(id).balance,subscription:ledger.view(id).subscription,availableModels:[...available]},expiresAt:web?sessions.expiry(cookie):undefined});
  if(req.method==='GET'&&req.url==='/v1/account')return send(res,200,view(id));
  if(req.method==='GET'&&req.url==='/v1/entitlements'){const account=ledger.view(id);return send(res,200,{accountId:id,memoryCredits:account.balance,creditsExpire:false,subscription:account.subscription,availableModels:[...available]})}
  if(req.method==='GET'&&req.url==='/v1/transactions')return send(res,200,{transactions:ledger.view(id).transactions});
  if(req.method==='POST'&&req.url==='/dev/purchases')return send(res,200,ledger.purchase(id,body.passId,body.storeTransactionId||`dev-${crypto.randomUUID()}`));
  if(req.method==='POST'&&req.url==='/dev/studio')return send(res,200,ledger.studio(id,body.active));
  if(req.method==='POST'&&req.url==='/v1/search/start')return send(res,200,ledger.startSearch(id,body.model,body.requestId));
  if(req.method==='POST'&&req.url==='/v1/search/complete'){if(body.outcome==='SUCCESS'&&!dev&&!(searchCompletionVerifier&&await searchCompletionVerifier({accountId:id,requestId:body.requestId,proof:body.proof})))throw status('SEARCH_COMPLETION_VERIFICATION_REQUIRED',503);return send(res,200,ledger.finishSearch(id,body.requestId,body.outcome))}
  if(req.method==='POST'&&req.url==='/v1/credits/deduct'){if(!dev)throw status('SEARCH_COMPLETION_VERIFICATION_REQUIRED',503);return send(res,200,ledger.deduct(id,body.model,body.requestId))}
  if(req.method==='POST'&&req.url==='/v1/purchases/restore')return send(res,200,ledger.restore(id));return send(res,404,{error:'NOT_FOUND'});
 }catch(error){send(res,error.status||400,{error:error.message})}})
}
const status=(message,code)=>Object.assign(new Error(message),{status:code});function send(res,code,body){res.statusCode=code;res.end(JSON.stringify(body))}
if(require.main===module)createServer().listen(Number(process.env.PORT||8787),'0.0.0.0');module.exports={createServer};
