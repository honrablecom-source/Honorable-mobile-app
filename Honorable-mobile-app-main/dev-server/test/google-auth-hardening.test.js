const test=require('node:test');
const assert=require('node:assert/strict');
const crypto=require('node:crypto');
const {GoogleIdTokenVerifier}=require('../src/auth');

const audience='synthetic-client.apps.googleusercontent.com';
const epoch=1_800_000_000_000;
const claims={iss:'https://accounts.google.com',aud:audience,sub:'synthetic-stable-subject',exp:1_800_000_100};
const enc=value=>Buffer.from(JSON.stringify(value)).toString('base64url');
function signer(kid){
  const {publicKey,privateKey}=crypto.generateKeyPairSync('rsa',{modulusLength:2048});
  const jwk={...publicKey.export({format:'jwk'}),kid,alg:'RS256',use:'sig'};
  return{jwk,token(overrides={},header={alg:'RS256',kid}){
    const body=`${enc(header)}.${enc({...claims,...overrides})}`;
    return `${body}.${crypto.sign('RSA-SHA256',Buffer.from(body),privateKey).toString('base64url')}`;
  },raw(header,body){
    const input=`${header}.${body}`;
    return `${input}.${crypto.sign('RSA-SHA256',Buffer.from(input),privateKey).toString('base64url')}`;
  }};
}
const first=signer('first'),second=signer('second');
const response=(keys,control='public, max-age=60',age='0')=>({ok:true,headers:{get(name){return name==='cache-control'?control:name==='age'?age:null}},json:async()=>({keys})});
const verifier=(fetchImpl,clock=()=>epoch,other={})=>new GoogleIdTokenVerifier({audience,fetchImpl,clock,...other});

test('Cache-Control expiry rotates signing keys and never reuses expired keys',async()=>{
  let now=epoch,calls=0;
  const verify=verifier(async()=>{calls++;return response(calls===1?[first.jwk]:[second.jwk],'public, max-age=2')},()=>now);
  assert.equal((await verify.verify(first.token())).sub,claims.sub);
  now+=1999;await verify.verify(first.token());assert.equal(calls,1);
  now+=2;await verify.verify(second.token());assert.equal(calls,2);
  await assert.rejects(verify.verify(first.token()),error=>error.message==='UNKNOWN_GOOGLE_SIGNING_KEY');
  assert.equal(calls,3);
});

test('concurrent unknown kid refreshes once and repeated misses have a bounded cooldown',async()=>{
  let calls=0;
  const verify=verifier(async()=>{calls++;await new Promise(resolve=>setTimeout(resolve,10));return response(calls===1?[first.jwk]:[first.jwk,second.jwk])});
  await verify.verify(first.token());
  const results=await Promise.all(Array.from({length:20},()=>verify.verify(second.token())));
  assert.equal(results.length,20);assert.equal(calls,2);
  await assert.rejects(verify.verify(first.token({}, {alg:'RS256',kid:'missing'})),/UNKNOWN_GOOGLE_SIGNING_KEY/);
  assert.equal(calls,3);
  await assert.rejects(verify.verify(first.token({}, {alg:'RS256',kid:'another-missing'})),/UNKNOWN_GOOGLE_SIGNING_KEY/);
  assert.equal(calls,3);
});

test('network failure, malformed JWKS and stale cache fail closed with retry backoff',async()=>{
  let now=epoch,calls=0,mode='offline';
  const verify=verifier(async()=>{calls++;if(mode==='offline')throw Error('synthetic network failure');if(mode==='malformed')return response([{kid:'first',kty:'RSA',n:'bad',e:'AQAB'}],'max-age=1');return response([first.jwk],'max-age=1')},()=>now);
  await assert.rejects(verify.verify(first.token()),error=>error.message==='GOOGLE_KEYS_UNAVAILABLE'&&error.status===503);
  await assert.rejects(verify.verify(first.token()),/GOOGLE_KEYS_UNAVAILABLE/);assert.equal(calls,1);
  now+=5001;mode='malformed';await assert.rejects(verify.verify(first.token()),/GOOGLE_KEYS_UNAVAILABLE/);
  now+=5001;mode='online';await verify.verify(first.token());assert.equal(calls,3);
  now+=1001;mode='offline';await assert.rejects(verify.verify(first.token()),/GOOGLE_KEYS_UNAVAILABLE/);
  assert.equal(calls,4);
});

test('HTTP failure and malformed JWKS JSON are controlled provider failures',async()=>{
  for(const fetchImpl of [
    async()=>({ok:false}),
    async()=>({ok:true,json:async()=>{throw new SyntaxError('synthetic malformed JSON')}}),
    async()=>({ok:true,json:async()=>({keys:{kid:'not-an-array'}})}),
  ]){
    await assert.rejects(verifier(fetchImpl).verify(first.token()),error=>error.message==='GOOGLE_KEYS_UNAVAILABLE'&&error.status===503);
  }
});

test('key fetch and JSON parsing have a bounded timeout even when transport ignores abort',async()=>{
  let calls=0,aborted=false;
  const verify=verifier(async(_url,{signal})=>{calls++;signal.addEventListener('abort',()=>{aborted=true});return new Promise(()=>{})},()=>epoch,{fetchTimeoutMs:20});
  await assert.rejects(verify.verify(first.token()),error=>error.message==='GOOGLE_KEYS_UNAVAILABLE'&&error.status===503);
  assert.equal(calls,1);assert.equal(aborted,true);
  await assert.rejects(verify.verify(first.token()),/GOOGLE_KEYS_UNAVAILABLE/);assert.equal(calls,1);
  const hangingJson=verifier(async()=>({ok:true,json:()=>new Promise(()=>{})}),()=>epoch,{fetchTimeoutMs:20});
  await assert.rejects(hangingJson.verify(first.token()),/GOOGLE_KEYS_UNAVAILABLE/);
});

test('malformed token objects and ineligible RSA keys reject without uncontrolled exceptions',async()=>{
  let calls=0;
  const verify=verifier(async()=>{calls++;return response([first.jwk])});
  for(const value of [null,{},'a.b.c','%%%.'+enc(claims)+'.a',first.raw(enc(null),enc(claims)),first.raw(enc({alg:'RS256',kid:'first'}),enc([]))]){
    await assert.rejects(verify.verify(value),error=>error.message==='INVALID_GOOGLE_CREDENTIAL'&&error.status===401);
  }
  assert.equal(calls,0);
  await assert.rejects(verify.verify(first.token({email:{value:'malformed'}})),/INVALID_GOOGLE_CREDENTIAL/);
  await assert.rejects(verify.verify(first.token({email_verified:'true'})),/INVALID_GOOGLE_CREDENTIAL/);
  await assert.rejects(verify.verify(first.token({iat:1_800_100_000})),/INVALID_GOOGLE_CREDENTIAL/);
  await assert.rejects(verify.verify(first.token({exp:'1800000100'})),/EXPIRED_GOOGLE_CREDENTIAL/);
  const ineligible=verifier(async()=>response([{...first.jwk,use:'enc'}]));
  await assert.rejects(ineligible.verify(first.token()),error=>error.message==='GOOGLE_KEYS_UNAVAILABLE'&&error.status===503);
  const wrongAlgorithm=verifier(async()=>response([{...first.jwk,alg:'RS512'}]));
  await assert.rejects(wrongAlgorithm.verify(first.token()),/GOOGLE_KEYS_UNAVAILABLE/);
});

test('provider Age and no-store directives reduce key lifetime',async()=>{
  let now=epoch,calls=0;
  const aged=verifier(async()=>{calls++;return response([first.jwk],'max-age=10','9')},()=>now);
  await aged.verify(first.token());now+=1001;await aged.verify(first.token());assert.equal(calls,2);
  calls=0;
  const noStore=verifier(async()=>{calls++;return response([first.jwk],'no-store, max-age=3600')},()=>now);
  await noStore.verify(first.token());await noStore.verify(first.token());assert.equal(calls,2);
  await assert.rejects(noStore.verify(first.token({}, {alg:'RS256',kid:'unknown-one'})),/UNKNOWN_GOOGLE_SIGNING_KEY/);
  await assert.rejects(noStore.verify(first.token({}, {alg:'RS256',kid:'unknown-two'})),/UNKNOWN_GOOGLE_SIGNING_KEY/);
  assert.equal(calls,3);
  calls=0;
  const capped=verifier(async()=>{calls++;return response([first.jwk],'max-age=7200')},()=>now);
  const longLivedToken=first.token({exp:1_800_010_000});
  await capped.verify(longLivedToken);now+=3600001;await capped.verify(longLivedToken);assert.equal(calls,2);
});
