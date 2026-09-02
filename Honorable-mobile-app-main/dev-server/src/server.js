const http=require('node:http');const path=require('node:path');const crypto=require('node:crypto');
const{Ledger,passes,costs,available}=require('./ledger');const{SessionAuth,GoogleIdTokenVerifier}=require('./auth');
function createServer({file=process.env.HONORABLE_LEDGER_PATH||path.join(__dirname,'../data/ledger.json'),mode=process.env.HONORABLE_SERVER_MODE||'production',sessionSecret=process.env.HONORABLE_SESSION_SECRET,googleClientId=process.env.HONORABLE_GOOGLE_WEB_CLIENT_ID,googleVerifier}={}){
  const dev=mode==='development',ledger=new Ledger(file),sessions=new SessionAuth(sessionSecret||(dev?'development-session-secret-change-me-now':null));const google=googleVerifier||(googleClientId?new GoogleIdTokenVerifier({audience:googleClientId}):null);
  return http.createServer(async(req,res)=>{res.setHeader('content-type','application/json');try{let raw='';for await(const chunk of req)raw+=chunk;const body=raw?JSON.parse(raw):{};
    if(req.method==='GET'&&req.url==='/v1/catalog')return send(res,200,{passes,costs,available:[...available],creditsExpire:false});
    if(req.method==='POST'&&req.url==='/v1/auth/google'){if(!google)throw status('GOOGLE_AUTH_NOT_CONFIGURED',503);const identity=await google.verify(body.idToken);const accountId=ledger.resolveIdentity('GOOGLE',identity.sub);return send(res,200,{accessToken:sessions.issue(accountId),tokenType:'Bearer',expiresIn:3600,account:{accountId,email:identity.email,name:identity.name,provider:'GOOGLE'}})}
    if(req.method==='POST'&&req.url==='/dev/auth/token'){if(!dev)return send(res,404,{error:'NOT_FOUND'});const accountId=ledger.resolveIdentity('DEVELOPMENT',body.email);return send(res,200,{accessToken:sessions.issue(accountId,body.ttlSeconds),tokenType:'Bearer',expiresIn:body.ttlSeconds||3600})}
    if(req.url.startsWith('/dev/')&&!dev)return send(res,404,{error:'NOT_FOUND'});const id=sessions.verify(req.headers.authorization);
    if(req.method==='GET'&&req.url==='/v1/account')return send(res,200,ledger.view(id));
    if(req.method==='GET'&&req.url==='/v1/entitlements'){const account=ledger.view(id);return send(res,200,{accountId:id,memoryCredits:account.balance,creditsExpire:false,subscription:account.subscription,availableModels:[...available]})}
    if(req.method==='GET'&&req.url==='/v1/transactions')return send(res,200,{transactions:ledger.view(id).transactions});
    if(req.method==='POST'&&req.url==='/dev/purchases'){const transactionId=body.storeTransactionId||`dev-${crypto.randomUUID()}`;return send(res,200,ledger.purchase(id,body.passId,transactionId))}
    if(req.method==='POST'&&req.url==='/v1/credits/deduct')return send(res,200,ledger.deduct(id,body.model,body.requestId));
    if(req.method==='POST'&&req.url==='/v1/purchases/restore')return send(res,200,ledger.restore(id));return send(res,404,{error:'NOT_FOUND'});
  }catch(error){return send(res,error.status||400,{error:error.message})}})
}
const status=(message,code)=>Object.assign(new Error(message),{status:code});function send(res,statusCode,body){res.statusCode=statusCode;res.end(JSON.stringify(body))}
if(require.main===module)createServer().listen(Number(process.env.PORT||8787),'0.0.0.0',()=>console.log('Honorable account server listening on 8787'));
module.exports={createServer};
