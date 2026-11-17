const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {createServer} = require('../src/server');
const {LocalSearchCompletions} = require('../src/search-completion');

async function fixture(verifier, clock = Date.now) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'noble-completion-retry-'));
  const server = createServer({file:path.join(directory,'ledger.json'), mode:'production',
    betaRequired:false, releaseChannel:'PRODUCTION', adminConfig:null, adminUsers:null,
    googleVerifier:{verify:async sub=>({sub,email:`${sub}@example.test`,emailVerified:true})},
    searchCompletionVerifier:verifier, clock});
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  const post = async (route, body, token) => {
    const response = await fetch(base+route,{method:'POST',headers:{'content-type':'application/json',
      ...(token?{authorization:'Bearer '+token}:{})},body:JSON.stringify(body)});
    return {status:response.status,body:await response.json()};
  };
  const login = async sub => {
    const response=await post('/v1/auth/google',{idToken:sub,installationId:`installation_${sub}_fixture`});
    assert.equal(response.status,200);return response.body;
  };
  return {server,post,login,async close(){
    await new Promise(resolve=>server.close(resolve));
    assert.equal(path.dirname(path.resolve(directory)),path.resolve(os.tmpdir()));
    fs.rmSync(directory,{recursive:true,force:true});
  }};
}

test('settled production success survives expired/missing proof without another verification or debit',async()=>{
  let now=Date.now(),calls=0;
  const receipts=new LocalSearchCompletions({clock:()=>now});
  const f=await fixture(fields=>{calls++;return receipts.verify(fields);},()=>now);
  try {
    const user=await f.login('retry');
    const requestId='settled-success',model='SERAN_V2';
    assert.equal((await f.post('/v1/search/start',{requestId,model},user.accessToken)).status,200);
    const proof=receipts.issue({accountId:user.account.accountId,requestId,model,result:{results:[{type:'IMAGE'}]}});
    const first=await f.post('/v1/search/complete',{requestId,outcome:'SUCCESS',proof},user.accessToken);
    assert.equal(first.status,200);assert.equal(first.body.freeMonthlyRemaining,12);
    now+=60001;
    assert.equal(receipts.verify({accountId:user.account.accountId,requestId,model,proof}),false);
    for(const body of [{requestId,outcome:'SUCCESS',proof},{requestId,outcome:'SUCCESS'}]){
      const retry=await f.post('/v1/search/complete',body,user.accessToken);
      assert.equal(retry.status,200);assert.equal(retry.body.freeMonthlyRemaining,12);
      assert.equal(retry.body.usage.allTime.searchesCompleted,1);
    }
    assert.equal(calls,1);
  } finally {await f.close();}
});

test('foreign and nonexistent requests are rejected before invoking the completion provider',async()=>{
  let calls=0;const f=await fixture(()=>{calls++;return true;});
  try {
    const a=await f.login('owner'),b=await f.login('other');
    await f.post('/v1/search/start',{requestId:'private-job',model:'SERAN_V1'},a.accessToken);
    for(const requestId of ['private-job','does-not-exist']){
      const response=await f.post('/v1/search/complete',{requestId,outcome:'SUCCESS',proof:'untrusted'},b.accessToken);
      assert.equal(response.status,404);assert.equal(response.body.error,'SEARCH_NOT_FOUND');
    }
    assert.equal(calls,0);assert.equal(f.server.ledger.data.searches['private-job'].state,'PENDING');
  } finally {await f.close();}
});

test('settled cancellation/failure cannot be changed into a charge and still validates outcomes',async()=>{
  let calls=0;const f=await fixture(()=>{calls++;return false;});
  try {
    const user=await f.login('cancel');
    for(const outcome of ['FAILED','CANCELLED']){
      const requestId=outcome;
      await f.post('/v1/search/start',{requestId,model:'SERAN_V1'},user.accessToken);
      await f.post('/v1/search/complete',{requestId,outcome},user.accessToken);
      const retry=await f.post('/v1/search/complete',{requestId,outcome:'SUCCESS'},user.accessToken);
      assert.equal(retry.status,200);assert.equal(retry.body.freeMonthlyRemaining,15);
      assert.equal(f.server.ledger.data.searches[requestId].state,outcome);
      assert.equal((await f.post('/v1/search/complete',{requestId,outcome:'INVALID'},user.accessToken)).status,400);
    }
    assert.equal(calls,0);
  } finally {await f.close();}
});

test('cancellation during external verification returns authoritative cancellation without charging',async()=>{
  let entered,release;
  const started=new Promise(resolve=>{entered=resolve;});
  const waiting=new Promise(resolve=>{release=resolve;});
  const f=await fixture(async()=>{entered();return waiting;});
  try {
    const user=await f.login('race');const requestId='cancel-race';
    await f.post('/v1/search/start',{requestId,model:'SERAN_V1'},user.accessToken);
    const completion=f.post('/v1/search/complete',{requestId,outcome:'SUCCESS',proof:'pending'},user.accessToken);
    await started;
    const cancel=await f.post('/v1/search/complete',{requestId,outcome:'CANCELLED'},user.accessToken);
    assert.equal(cancel.status,200);release(false);
    const settled=await completion;
    assert.equal(settled.status,200);assert.equal(settled.body.freeMonthlyRemaining,15);
    assert.equal(f.server.ledger.data.searches[requestId].state,'CANCELLED');
    assert.equal(settled.body.usage.allTime.searchesCompleted,0);
  } finally {release(false);await f.close();}
});

test('untrusted pending completion still fails closed and preserves all credits',async()=>{
  const f=await fixture(()=>false);
  try {
    const user=await f.login('pending');const requestId='unverified';
    await f.post('/v1/search/start',{requestId,model:'SERAN_V2'},user.accessToken);
    const response=await f.post('/v1/search/complete',{requestId,outcome:'SUCCESS',proof:'invented'},user.accessToken);
    assert.equal(response.status,503);assert.equal(response.body.error,'SEARCH_COMPLETION_VERIFICATION_REQUIRED');
    assert.equal(f.server.ledger.data.searches[requestId].state,'PENDING');
    assert.equal(f.server.ledger.view(user.account.accountId).freeMonthlyRemaining,15);
  } finally {await f.close();}
});
