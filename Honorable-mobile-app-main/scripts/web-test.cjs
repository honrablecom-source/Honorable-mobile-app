// Local development gateway. Media routes can only reach the local Kotlin adapter.
const http = require('node:http');
const https = require('node:https');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const {spawn} = require('node:child_process');
const root = path.resolve(__dirname, '..');
const shell = path.join(root, 'android-app/test-lab/web-test-shell');
const media = process.env.HONORABLE_TEST_MEDIA_ROOT || path.join(shell, 'storage');
const port = Number(process.env.HONORABLE_WEB_PORT || 4174);
const searchPort = Number(process.env.HONORABLE_SEARCH_PORT || 4175);
const externalAccount = process.env.HONORABLE_ACCOUNT_API_URL;
const configuredAccount = new URL(externalAccount || 'http://127.0.0.1:8787');
const bundledAccount = !externalAccount || ['localhost','127.0.0.1'].includes(configuredAccount.hostname) && configuredAccount.port==='8787' || (process.env.CODESPACE_NAME && configuredAccount.hostname===`${process.env.CODESPACE_NAME}-8787.${process.env.GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN}`);
const account = bundledAccount ? new URL('http://127.0.0.1:8787') : configuredAccount;
if(account.protocol!=='https:'&&!['localhost','127.0.0.1'].includes(account.hostname))throw Error('Account API must use HTTPS');
const children = [];
const {LocalSearchCompletions}=require('../dev-server/src/search-completion');
const completions=new LocalSearchCompletions();
const searchJobs=new Map();
let accountServer;
if (bundledAccount) {
  accountServer = require('../dev-server/src/server').createServer({mode:'development',searchCompletionVerifier:completions.verify,healthProbe:async()=>{let up=false;try{up=(await fetch(`http://127.0.0.1:${searchPort}/health`,{signal:AbortSignal.timeout(1000)})).ok}catch{}return{searchService:up?'UP':'UNAVAILABLE',webAdapter:'UP'}},webOrigins:[`http://localhost:${port}`,`http://127.0.0.1:${port}`,...(process.env.CODESPACE_NAME?[`https://${process.env.CODESPACE_NAME}-${port}.${process.env.GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN}`]:[])]});
  accountServer.listen(8787, '127.0.0.1');
}
const child = spawn('bash', ['./linux-demo.sh', 'start'], {cwd:root, env:{...process.env,HONORABLE_DEMO_PORT:String(searchPort)},stdio:'inherit',detached:true});
children.push(child);
function json(res, code, data) { res.writeHead(code, {'Content-Type':'application/json'}); res.end(JSON.stringify(data)); }
function proxy(req,res,target,route) {
  const upstream = (target.protocol==='https:'?https:http).request(new URL(route,target), {method:req.method,headers:{...(route.startsWith('/admin')?{cookie:(req.headers.cookie||'').split(';').map(x=>x.trim()).filter(x=>x.startsWith('__Host-honorable-admin=')).join('; '),origin:req.headers.origin||'',host:req.headers.host,'x-honorable-admin':req.headers['x-honorable-admin']||''}:{}),...(route.startsWith('/v1/')||route.startsWith('/dev/')?{cookie:(req.headers.cookie||'').split(';').map(x=>x.trim()).filter(x=>x.startsWith('__Host-honorable=')).join('; '),origin:req.headers.origin||'','x-honorable-web':req.headers['x-honorable-web']||'','x-honorable-installation':req.headers['x-honorable-installation']||'','x-honorable-platform':req.headers['x-honorable-platform']||'WEB_TEST','x-honorable-app-version':req.headers['x-honorable-app-version']||'0.2.1','x-honorable-build':req.headers['x-honorable-build']||'2'}:{}),...(req.headers.authorization?{authorization:req.headers.authorization}:{}),...(req.headers['content-type']?{'content-type':req.headers['content-type']}:{}),...(req.headers.range?{range:req.headers.range}:{})}}, r=>{res.writeHead(r.statusCode,r.headers);r.pipe(res)});
  if(route.startsWith('/v1/')||route.startsWith('/dev/'))upstream.setTimeout(18000,()=>upstream.destroy());
  upstream.on('error',()=>json(res,503,{error:'Service starting or unavailable. Retry shortly.'}));req.pipe(upstream);
}
const server = http.createServer(async(req,res)=>{
  try {
    res.setHeader('Referrer-Policy','no-referrer-when-downgrade');
    const url = new URL(req.url,'http://localhost');
    if (req.method !== 'GET' && req.headers.origin && new URL(req.headers.origin).host !== req.headers.host) return json(res,403,{error:'Cross-origin writes refused'});
    if(url.pathname.startsWith('/admin'))return proxy(req,res,account,req.url);
    if(url.pathname==='/web/config') return json(res,200,{developmentPurchases:!!bundledAccount,searchTransport:'VERIFIED_LOCAL_ENGINE',googleClientId:process.env.HONORABLE_GOOGLE_WEB_CLIENT_ID||''});
    if(url.pathname==='/web/search'&&req.method==='POST') {
      if(!bundledAccount)return json(res,503,{error:'Trusted completion provider is not configured for this account server.'});
      if(req.headers['x-honorable-web']!=='1'||!req.headers.origin||new URL(req.headers.origin).host!==req.headers.host)return json(res,403,{error:'INVALID_WEB_ORIGIN'});
      let raw='';for await(const chunk of req){raw+=chunk;if(Buffer.byteLength(raw)>8192)return json(res,413,{error:'REQUEST_TOO_LARGE'})}
      const {query,model,requestId}=JSON.parse(raw);
      if(typeof query!=='string'||!query.trim()||query.length>2000||typeof requestId!=='string'||!requestId||requestId.length>200)return json(res,400,{error:'INVALID_SEARCH_REQUEST'});
      if(!['SERAN_V1','SERAN_V2'].includes(model))return json(res,409,{error:'MODEL_UNAVAILABLE'});
      const headers={'content-type':'application/json','x-honorable-web':'1',origin:req.headers.origin,cookie:req.headers.cookie||'','x-honorable-installation':req.headers['x-honorable-installation']||'','x-honorable-platform':req.headers['x-honorable-platform']||'WEB_TEST','x-honorable-app-version':req.headers['x-honorable-app-version']||'0.2.1','x-honorable-build':req.headers['x-honorable-build']||'2'};
      const accountCall=async(route,body)=>{const response=await fetch(new URL(route,account),{method:body?'POST':'GET',headers,...(body?{body:JSON.stringify(body)}:{}),signal:AbortSignal.timeout(18000)});const value=await response.json();if(!response.ok)throw Object.assign(Error(value.error||'ACCOUNT_UNAVAILABLE'),{status:response.status});return value};
      const controller=new AbortController();let released=false;
      res.on('close',()=>{if(!released)controller.abort()});
      const session=await accountCall('/v1/auth/session');const accountId=session.account.accountId,key=accountId+':'+requestId;
      const fingerprint=crypto.createHash('sha256').update(JSON.stringify({query,model})).digest('hex');
      for(const[k,j]of searchJobs)if(j.done&&Date.now()-j.created>300000)searchJobs.delete(k);
      const prior=searchJobs.get(key);if(prior){if(prior.fingerprint!==fingerprint)return json(res,409,{error:'SEARCH_ID_CONFLICT'});const value=await prior.promise;return json(res,200,value)}
      const job={fingerprint,created:Date.now(),done:false};
      job.promise=(async()=>{try {
        const started=await accountCall('/v1/search/start',{model,requestId});
        if(controller.signal.aborted)throw Error('SEARCH_INTERRUPTED');
        if(started.state!=='PENDING')throw Object.assign(Error('SEARCH_ALREADY_FINISHED_REOPEN_SAVED_RESULT'),{status:409});
        const engineUrl=new URL('/api/search',`http://127.0.0.1:${searchPort}`);engineUrl.searchParams.set('q',query);engineUrl.searchParams.set('model',model);engineUrl.searchParams.set('top','12');
        const response=await fetch(engineUrl,{signal:AbortSignal.any([controller.signal,AbortSignal.timeout(120000)])});
        if(!response.ok)throw Error('SHARED_SEARCH_FAILED');const result=await response.json();
        if(!Array.isArray(result.results))throw Error('INVALID_ENGINE_RESPONSE');
        if(controller.signal.aborted)throw Error('SEARCH_INTERRUPTED');
        const outcome=result.results.length?'SUCCESS':'FAILED';
        const proof=outcome==='SUCCESS'?completions.issue({accountId,requestId,model,result}):undefined;
        await accountCall('/v1/search/complete',{requestId,outcome,proof});
        const completion=await accountCall('/v1/search/start',{model,requestId});
        if(completion.state!==outcome)throw Error('SEARCH_CANCELLED');
        return {...result,requestId,execution:'REAL_SHARED_ENGINE'};
      }catch(error){await accountCall('/v1/search/complete',{requestId,outcome:controller.signal.aborted?'CANCELLED':'FAILED'}).catch(()=>{});throw error}finally{job.done=true}})();
      searchJobs.set(key,job);try{const result=await job.promise;released=true;return json(res,200,result)}catch(error){searchJobs.delete(key);throw error}
    }
    if(url.pathname.startsWith('/account/')) {
      const route=url.pathname.slice('/account'.length);
      if(!['/v1/beta/config','/v1/beta/feedback','/v1/beta/request','/v1/beta/authorize','/v1/analytics','/v1/search/start','/v1/search/complete','/dev/studio','/v1/catalog','/v1/auth/session','/v1/auth/logout','/v1/auth/google','/v1/account','/v1/entitlements','/v1/transactions','/v1/purchases/restore','/dev/auth/token','/dev/purchases'].includes(route))return json(res,404,{error:'Not found'});
      if(route.startsWith('/dev/')&&!bundledAccount)return json(res,403,{error:'Simulation is only enabled for the bundled development ledger'});
      return proxy(req,res,account,route);
    }
    if(url.pathname==='/web/import' && req.method==='POST') {
      const name=path.basename(url.searchParams.get('name')||'');
      if(!/\.(jpg|jpeg|png|webp|mp4|mov|m4v|webm|mkv)$/i.test(name))return json(res,415,{error:'Unsupported test media format'});
      fs.mkdirSync(media,{recursive:true});
      const target=path.join(media,'web-import-'+crypto.randomUUID()+'-'+name);
      let size=0;
      const output=fs.createWriteStream(target,{flags:'wx'});
      try { for await(const chunk of req) {size+=chunk.length;if(size>250*1024*1024)throw Error('File exceeds 250 MB');if(!output.write(chunk))await new Promise(resolve=>output.once('drain',resolve));} await new Promise((resolve,reject)=>{output.on('error',reject);output.end(resolve)}); }
      catch(error){output.destroy();fs.rmSync(target,{force:true});throw error}
      return json(res,201,{name});
    }
    const name=url.pathname==='/'?'index.html':url.pathname.slice(1);
    if(['beta.js','telemetry.js','design-tokens.css','honorable.css','studio.js','studio.css','project.js','index.html','auth-ui.js','auth-ui.css','android-icons.js','android-ui.js','android-ui.css','Roboto.ttf','Roboto-400.ttf','Roboto-500.ttf','Roboto-600.ttf','Roboto-700.ttf','Roboto-800.ttf','Roboto-900.ttf','phone.js','phone.css','honorable-parity.css','monochrome.css','web-shell.css','web-test.js','prompt_beach.png','prompt_birthday.png','prompt_red_car.png'].includes(name)) {
      res.setHeader('Content-Type',name.endsWith('.js')?'text/javascript':name.endsWith('.css')?'text/css':name.endsWith('.png')?'image/png':name.endsWith('.ttf')?'font/ttf':'text/html');return fs.createReadStream(path.join(shell,name)).pipe(res);
    }
    if(url.pathname==='/api/search')return json(res,403,{error:'Use authenticated /web/search for product searches'});
    if(url.pathname.startsWith('/api/')||url.pathname.startsWith('/media/')||url.pathname==='/health')return proxy(req,res,new URL(`http://127.0.0.1:${searchPort}`),req.url);
    json(res,404,{error:'Not found'});
  }catch(error){json(res,error.status||400,{error:error.message})}
});
server.listen(port,'127.0.0.1',()=>console.log(`Web test shell: http://localhost:${port}`));
function stop(){server.close();accountServer?.close();for(const child of children)try{process.kill(-child.pid,'SIGTERM')}catch{}setTimeout(()=>process.exit(),100).unref()}
server.on('error',error=>{console.error(error.message);stop()});accountServer?.on('error',error=>{console.error(error.message);stop()});
process.on('SIGINT',stop);process.on('SIGTERM',stop);child.on('exit',stop);
