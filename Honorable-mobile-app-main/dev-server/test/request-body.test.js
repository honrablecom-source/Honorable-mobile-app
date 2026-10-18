const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),os=require('node:os'),path=require('node:path'),http=require('node:http');
const {createServer}=require('../src/server');
async function fixture(run){
 const directory=fs.mkdtempSync(path.join(os.tmpdir(),'honorable-json-body-'));
 const server=createServer({file:path.join(directory,'ledger.json'),mode:'development',googleClientId:''});
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 try{await run(server,`http://127.0.0.1:${server.address().port}`)}finally{await new Promise(resolve=>server.close(resolve));fs.rmSync(directory,{recursive:true,force:true})}
}
test('malformed JSON and non-object bodies return fixed errors without echoing credentials',async()=>{
 await fixture(async(_server,base)=>{
  for(const raw of ['{"idToken":"synthetic-private-credential",','null','[]','"synthetic-private-credential"','123']){
   const response=await fetch(base+'/v1/auth/google',{method:'POST',headers:{'content-type':'application/json'},body:raw});
   assert.equal(response.status,400);const result=await response.text();assert(!result.includes('synthetic-private-credential'));
   assert(['INVALID_JSON_BODY','INVALID_REQUEST_BODY'].includes(JSON.parse(result).error));
  }
 });
});
test('UTF-8 split across request chunks preserves the exact identity input',async()=>{
 await fixture(async(server,base)=>{
  const email='synthetic-é@example.test',bytes=Buffer.from(JSON.stringify({email}));
  const split=bytes.indexOf(Buffer.from('é'))+1;
  const result=await new Promise((resolve,reject)=>{
   const request=http.request(base+'/dev/auth/token',{method:'POST',headers:{'content-type':'application/json'}},response=>{
    let text='';response.on('data',chunk=>text+=chunk);response.on('end',()=>resolve({status:response.statusCode,body:JSON.parse(text)}));
   });request.on('error',reject);request.write(bytes.subarray(0,split));setTimeout(()=>request.end(bytes.subarray(split)),15);
  });
  assert.equal(result.status,200);assert.equal(server.ledger.data.identities['DEVELOPMENT:'+email],result.body.account.accountId);
 });
});
test('invalid UTF-8 and oversized account requests reject before identity verification',async()=>{
 await fixture(async(_server,base)=>{
  const malformed=await fetch(base+'/v1/auth/google',{method:'POST',body:Buffer.from([123,34,120,34,58,34,0xff,34,125])});
  assert.equal(malformed.status,400);assert.equal((await malformed.json()).error,'INVALID_JSON_BODY');
  const oversized=await fetch(base+'/v1/auth/google',{method:'POST',body:JSON.stringify({idToken:'a'.repeat(65536)})});
  assert.equal(oversized.status,413);assert.equal((await oversized.json()).error,'REQUEST_TOO_LARGE');
 });
});
