const test=require('node:test');
const assert=require('node:assert/strict');
const crypto=require('node:crypto');
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const {createServer}=require('../src/server');
const {GoogleIdTokenVerifier}=require('../src/auth');

const audience='synthetic-web-client.apps.googleusercontent.com';
const subjectA='synthetic-google-subject-A';
const subjectB='synthetic-google-subject-B';
const {publicKey,privateKey}=crypto.generateKeyPairSync('rsa',{modulusLength:2048});
const jwk={...publicKey.export({format:'jwk'}),kid:'synthetic-rotation-key',alg:'RS256',use:'sig'};
const encode=value=>Buffer.from(JSON.stringify(value)).toString('base64url');

function idToken({sub=subjectA,email='first@example.test',name='First Person',aud=audience,exp=Math.floor(Date.now()/1000)+3600}={}){
  const claims={iss:'https://accounts.google.com',aud,sub,email,email_verified:true,name,iat:Math.floor(Date.now()/1000),exp};
  const input=`${encode({alg:'RS256',kid:jwk.kid,typ:'JWT'})}.${encode(claims)}`;
  return `${input}.${crypto.sign('RSA-SHA256',Buffer.from(input),privateKey).toString('base64url')}`;
}

async function fixture(){
  const directory=fs.mkdtempSync(path.join(os.tmpdir(),'honorable-production-auth-'));
  const file=path.join(directory,'ledger.json');
  let keyDownloads=0;
  const googleVerifier=new GoogleIdTokenVerifier({
    audience,
    jwksUri:'https://synthetic-keys.example.test/certs',
    fetchImpl:async(uri,{signal})=>{
      assert.equal(uri,'https://synthetic-keys.example.test/certs');
      assert.equal(signal.aborted,false);
      keyDownloads++;
      return new Response(JSON.stringify({keys:[jwk]}),{status:200,headers:{'Cache-Control':'public, max-age=60'}});
    },
  });
  const server=createServer({file,mode:'production',releaseChannel:'PRODUCTION',betaRequired:false,
    adminConfig:null,adminUsers:null,webOrigins:[],googleVerifier});
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  const base=`http://127.0.0.1:${server.address().port}`;
  async function call(route,body,accessToken,extraHeaders={}){
    const response=await fetch(base+route,{method:body===undefined?'GET':'POST',headers:{
      'content-type':'application/json',...(accessToken?{authorization:`Bearer ${accessToken}`}:{}),...extraHeaders,
    },...(body===undefined?{}:{body:JSON.stringify(body)})});
    return{status:response.status,body:await response.json()};
  }
  return{call,get keyDownloads(){return keyDownloads},close:async()=>{
    await new Promise(resolve=>server.close(resolve));
    await server.analytics.close();
    fs.rmSync(directory,{recursive:true,force:true});
  }};
}

test('production HTTP sign-in uses signed Google identity and isolates opaque accounts',async()=>{
  const app=await fixture();
  try{
    const initial=await app.call('/v1/auth/google',{idToken:idToken(),installationId:'synthetic_installation_A_0001'});
    assert.equal(initial.status,200);
    const accountId=initial.body.account.accountId;
    assert.match(accountId,/^[a-f0-9-]{36}$/);
    assert.notEqual(accountId,subjectA);
    assert.equal(initial.body.account.freeMonthlyRemaining,15);
    assert.equal(initial.body.account.profile.email,'first@example.test');
    assert.equal(app.keyDownloads,1);

    const updated=await app.call('/v1/auth/google',{idToken:idToken({email:'renamed@example.test',name:'Updated Person'})});
    assert.equal(updated.status,200);
    assert.equal(updated.body.account.accountId,accountId);
    assert.equal(updated.body.account.freeMonthlyRemaining,15);
    assert.deepEqual(updated.body.account.profile,{name:'Updated Person',email:'renamed@example.test'});
    assert.equal(app.keyDownloads,1);

    const other=await app.call('/v1/auth/google',{idToken:idToken({sub:subjectB,email:'other@example.test'}),
      installationId:'synthetic_installation_B_0001',accountId});
    assert.equal(other.status,200);
    assert.notEqual(other.body.account.accountId,accountId);
    const forgedRead=await app.call('/v1/account',undefined,other.body.accessToken,{'x-honorable-account-id':accountId});
    assert.equal(forgedRead.status,200);
    assert.equal(forgedRead.body.accountId,other.body.account.accountId);
    assert.equal(forgedRead.body.profile.email,'other@example.test');

    const requestId=crypto.randomUUID();
    assert.equal((await app.call('/v1/search/start',{model:'SERAN_V1',requestId},updated.body.accessToken)).status,200);
    assert.equal((await app.call('/v1/search/start',{model:'SERAN_V1',requestId,accountId},other.body.accessToken)).status,409);
    assert.equal((await app.call('/v1/search/complete',{requestId,outcome:'FAILED',accountId},other.body.accessToken)).status,404);
    const forgedSpend=await app.call('/v1/search/start',{model:'SERAN_V1',requestId:crypto.randomUUID(),accountId},other.body.accessToken);
    assert.equal(forgedSpend.status,200);
    assert.equal(forgedSpend.body.accountId,other.body.account.accountId);
    const firstAccount=await app.call('/v1/account',undefined,updated.body.accessToken);
    assert.equal(firstAccount.body.accountId,accountId);
    assert.equal(firstAccount.body.freeMonthlyRemaining,15);
    assert.equal(firstAccount.body.transactions.filter(row=>row.type==='DEBIT').length,0);
  }finally{await app.close()}
});

test('production HTTP rejects bad credentials, replay, development routes and untrusted success',async()=>{
  const app=await fixture();
  try{
    const original=await app.call('/v1/auth/google',{idToken:idToken(),installationId:'synthetic_installation_C_0001'});
    assert.equal(original.status,200);
    const invalid=[
      idToken({aud:'wrong-client.apps.googleusercontent.com'}),
      idToken().slice(0,-2)+'xx',
      idToken({exp:Math.floor(Date.now()/1000)-1}),
    ];
    for(const credential of invalid)assert.equal((await app.call('/v1/auth/google',{idToken:credential})).status,401);
    assert.equal((await app.call('/dev/auth/token',{email:'someone@example.test'})).status,404);
    assert.equal((await app.call('/dev/purchases',{passId:'memory_pass_040'},original.body.accessToken)).status,404);

    const requestId=crypto.randomUUID();
    assert.equal((await app.call('/v1/search/start',{model:'SERAN_V1',requestId},original.body.accessToken)).status,200);
    const clientSuccess=await app.call('/v1/search/complete',{requestId,outcome:'SUCCESS',proof:'synthetic-client-claim'},original.body.accessToken);
    assert.equal(clientSuccess.status,503);
    assert.equal(clientSuccess.body.error,'SEARCH_COMPLETION_VERIFICATION_REQUIRED');
    assert.equal((await app.call('/v1/credits/deduct',{model:'SERAN_V1',requestId:crypto.randomUUID()},original.body.accessToken)).status,503);
    const unchanged=await app.call('/v1/account',undefined,original.body.accessToken);
    assert.equal(unchanged.body.freeMonthlyRemaining,15);
    assert.equal(unchanged.body.transactions.filter(row=>row.type==='DEBIT').length,0);

    const rotated=await app.call('/v1/auth/refresh',{refreshToken:original.body.refreshToken});
    assert.equal(rotated.status,200);
    assert.notEqual(rotated.body.refreshToken,original.body.refreshToken);
    assert.equal((await app.call('/v1/account',undefined,original.body.accessToken)).status,401);
    assert.equal((await app.call('/v1/account',undefined,rotated.body.accessToken)).status,200);
    assert.equal((await app.call('/v1/auth/refresh',{refreshToken:original.body.refreshToken})).status,401);
    assert.equal((await app.call('/v1/account',undefined,rotated.body.accessToken)).status,401,'Refresh replay must revoke the rotated session family');
    assert.equal((await app.call('/v1/auth/refresh',{refreshToken:rotated.body.refreshToken})).status,401,'Revoked family must not rotate again');

    const signedInAgain=await app.call('/v1/auth/google',{idToken:idToken()});
    assert.equal(signedInAgain.body.account.accountId,original.body.account.accountId);
    assert.equal((await app.call('/v1/auth/logout',{refreshToken:signedInAgain.body.refreshToken},signedInAgain.body.accessToken)).status,200);
    assert.equal((await app.call('/v1/account',undefined,signedInAgain.body.accessToken)).status,401);
    assert.equal((await app.call('/v1/auth/refresh',{refreshToken:signedInAgain.body.refreshToken})).status,401);
  }finally{await app.close()}
});
