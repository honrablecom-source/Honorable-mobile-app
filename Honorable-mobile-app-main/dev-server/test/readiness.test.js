const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),os=require('node:os'),path=require('node:path');
const {createServer}=require('../src/server');

async function withServer(options,run){
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'honorable-readiness-'));
 const server=createServer({file:path.join(dir,'ledger.json'),...options});
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 try{await run(`http://127.0.0.1:${server.address().port}`)}
 finally{await new Promise(resolve=>server.close(resolve));fs.rmSync(dir,{recursive:true,force:true})}
}
test('liveness succeeds while missing production identity and completion providers remain unready',async()=>{
 await withServer({mode:'production',googleClientId:''},async base=>{
  const health=await fetch(base+'/healthz');assert.equal(health.status,200);assert.equal((await health.json()).status,'UP');
  const ready=await fetch(base+'/readyz');assert.equal(ready.status,503);assert.equal(ready.headers.get('cache-control'),'no-store');
  const report=await ready.json();assert.equal(report.ready,false);assert.deepEqual(report.checks,{productionMode:true,identityProvider:false,trustedSearchCompletion:false});
  assert.equal((await fetch(base+'/v1/account')).status,401);
  assert.equal((await fetch(base+'/dev/auth/token',{method:'POST',body:'{}'})).status,404);
 });
});
test('identity alone cannot declare charged search ready',async()=>{
 await withServer({mode:'production',googleVerifier:{verify:async()=>({sub:'fixture'})}},async base=>{
  const response=await fetch(base+'/readyz');assert.equal(response.status,503);const report=await response.json();assert.equal(report.checks.identityProvider,true);assert.equal(report.checks.trustedSearchCompletion,false);
 });
});
test('provider configuration is explicitly scoped and development cannot pass production readiness',async()=>{
 for(const mode of ['development','production'])await withServer({mode,googleVerifier:{verify:async()=>({sub:'fixture'})},searchCompletionVerifier:async()=>false},async base=>{
  const response=await fetch(base+'/readyz');assert.equal(response.status,mode==='production'?200:503);
  const report=await response.json();assert.equal(report.scope,'ACCOUNT_API_CONFIGURATION');assert.equal(report.ready,mode==='production');
  assert.equal((await fetch(base+'/v1/account')).status,401);
 });
});
