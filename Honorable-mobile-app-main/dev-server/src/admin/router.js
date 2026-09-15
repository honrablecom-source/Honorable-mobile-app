'use strict';
const fs=require('node:fs'),path=require('node:path');
const{AdminAuth}=require('./auth'),{AuditLog}=require('./audit'),{buildMetrics,accountRows,safeUser}=require('./metrics');
const bad=(message,status=400)=>Object.assign(Error(message),{status});
const publicDir=path.resolve(__dirname,'../../admin-public');
function createAdminRouter({ledger,analytics,sessions,environment,configFile,users,clock=Date.now,healthProbe,beta}){
 const auth=new AdminAuth({file:configFile,users,clock,environment}),audit=new AuditLog(ledger.file+'.admin-audit.jsonl',{clock});
 function writeOrigin(req){if(req.headers['x-honorable-admin']!=='1')throw bad('ADMIN_CSRF_REJECTED',403);let origin;try{origin=new URL(req.headers.origin)}catch{throw bad('ADMIN_CSRF_REJECTED',403)}if(origin.host!==req.headers.host||(environment==='production'&&origin.protocol!=='https:'))throw bad('ADMIN_CSRF_REJECTED',403);}
 function allowed(actor,roles){if(!roles.includes(actor.role))throw bad('ADMIN_ROLE_REQUIRED',403)}
 const json=(res,status,value)=>{res.writeHead(status,{'Content-Type':'application/json','Cache-Control':'no-store'});res.end(JSON.stringify(value));};
 const cookie=(res,token)=>res.setHeader('Set-Cookie',`__Host-honorable-admin=${token}; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=${token?28800:0}`);
 async function body(req){let raw='';for await(const chunk of req){raw+=chunk;if(Buffer.byteLength(raw)>8192)throw bad('REQUEST_TOO_LARGE',413)}return raw?JSON.parse(raw):{}}
 return async function admin(req,res){
  if(!req.url.startsWith('/admin'))return false;
  if(!['127.0.0.1','::1','::ffff:127.0.0.1'].includes(req.socket.remoteAddress)){res.writeHead(404);res.end();return true;}
  res.setHeader('Cache-Control','no-store');res.setHeader('X-Content-Type-Options','nosniff');res.setHeader('X-Frame-Options','DENY');res.setHeader('Referrer-Policy','no-referrer');res.setHeader('Content-Security-Policy',"default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
  try{
   const url=new URL(req.url,'http://localhost');
   if(url.pathname==='/admin/tokens.css'&&req.method==='GET'){const tokens=require('../../../product/design-tokens.json').colors;res.setHeader('Content-Type','text/css');res.end(':root{'+Object.entries(tokens).map(([k,v])=>'--'+k+':'+v+';').join('')+'}');return true;}
   if(['/admin','/admin/','/admin/admin.js','/admin/admin.css','/admin/beta.js'].includes(url.pathname)&&req.method==='GET'){
    const name=url.pathname.endsWith('beta.js')?'beta.js':url.pathname.endsWith('.js')?'admin.js':url.pathname.endsWith('.css')?'admin.css':'index.html';res.setHeader('Content-Type',name.endsWith('.js')?'text/javascript':name.endsWith('.css')?'text/css':'text/html');res.end(fs.readFileSync(path.join(publicDir,name)));return true;
   }
   if(req.method==='POST')writeOrigin(req);
   if(url.pathname==='/admin/api/login'&&req.method==='POST'){
    const data=await body(req);let token;try{token=await auth.login(data.username,data.password,req.socket.remoteAddress);await audit.append(data.username,'ADMIN_LOGIN',null,null)}catch(error){await audit.append('unauthenticated','ADMIN_LOGIN_REJECTED',null,null).catch(()=>{});throw error}cookie(res,token);json(res,200,{ok:true});return true;
   }
   const actor=auth.session(req);
   const requested=url.searchParams.get('environment')||environment;
   if(requested!==environment||!actor.environments.includes(requested))throw bad('ENVIRONMENT_NOT_AUTHORIZED',403);
   if(url.pathname.startsWith('/admin/api/beta')){
    if(!beta)throw bad('BETA_NOT_CONFIGURED',503);
    const {betaReport,aggregateReport}=require('../beta/report');
    const action=url.pathname.slice('/admin/api/beta'.length);
    if(req.method==='GET'&&action===''){
     const report=betaReport(beta,ledger,analytics);
     if(actor.role==='VIEWER'){report.directory=report.directory.map(({email,...t})=>t);report.feedback=[];report.requests=[];report.releases=report.releases.map(({internalNotes,checks,assets,...r})=>r);report.gate.assets=[];}
     else await audit.append(actor.username,'BETA_SUPPORT_READ',null,'SUPPORT_REVIEW');
     json(res,200,report);return true;
    }
    allowed(actor,['OWNER','ADMIN']);
    if(req.method==='GET'&&action.startsWith('/attachment/')){const attachment=beta.attachments.read(action.slice('/attachment/'.length));await audit.append(actor.username,'BETA_ATTACHMENT_READ',attachment.reportId,'CONSENTED_SUPPORT_ATTACHMENT');res.writeHead(200,{'Content-Type':'application/octet-stream','Content-Disposition':`attachment; filename="${attachment.id}.${attachment.kind==='SCREENSHOT'?'png':'json'}"`,'Cache-Control':'no-store'});res.end(Buffer.from(attachment.content,'base64'));return true;}
    if(req.method!=='POST')throw bad('NOT_FOUND',404);
    const data=await body(req);
    const actions={'/invite':()=>beta.invite(data),'/tester':()=>{const t=beta.updateTester(data);if(t.accountId&&t.status!=='ACTIVE')sessions.revokeAccount(t.accountId);return t},'/flag':()=>beta.setFlag(data),'/triage':()=>beta.triage(data),'/release-status':()=>beta.releaseStatus(data),'/asset':()=>beta.asset(data,actor.username),'/release':()=>beta.release(data),'/check':()=>beta.check(data,actor.username),'/issue':()=>beta.knownIssue(data),'/request':()=>beta.resolveRequest(data)};
    if(action==='/export'){
     await audit.append(actor.username,'BETA_EXPORT',null,'SUPPORT_REVIEW');const report=aggregateReport(betaReport(beta,ledger,analytics));
     if(data.format==='csv'){res.writeHead(200,{'Content-Type':'text/csv','Content-Disposition':'attachment; filename="honorable-beta.csv"'});res.end('metric,value\r\n'+Object.entries(report.summary).map(([k,v])=>k+','+(v??'')).join('\r\n'));return true}
     json(res,200,report);return true;
    }
    if(!actions[action])throw bad('NOT_FOUND',404);
    const target=data.id||data.releaseId||data.flag;const auditTarget=typeof target==='string'&&/^[a-zA-Z0-9_-]{1,100}$/.test(target)?target:null;const auditReason=typeof data.reason==='string'?data.reason.trim().slice(0,300):'OPERATOR_ACTION';
    await audit.append(actor.username,'BETA_'+action.slice(1).toUpperCase()+'_REQUESTED',auditTarget,auditReason);
    const value=actions[action]();await audit.append(actor.username,'BETA_'+action.slice(1).toUpperCase()+'_COMPLETED',value?.id||auditTarget,auditReason);json(res,200,value);return true;
   }
   if(url.pathname==='/admin/api/session'&&req.method==='GET'){json(res,200,{username:actor.username,role:actor.role,environment,environments:[environment]});return true}
   if(url.pathname==='/admin/api/logout'&&req.method==='POST'){await audit.append(actor.username,'ADMIN_LOGOUT');auth.logout(req);cookie(res,'');json(res,200,{ok:true});return true}
   if(url.pathname==='/admin/api/metrics'&&req.method==='GET'){
    const range=url.searchParams.get('range')||'30d';if(!['24h','7d','30d','90d','all'].includes(range))throw bad('INVALID_RANGE');
    const metrics=buildMetrics({ledger,analytics,environment,range,clock});if(healthProbe)try{Object.assign(metrics.health,await healthProbe())}catch{metrics.health.searchService='UNAVAILABLE'}
    metrics.health.audit=audit.healthy?'UP':'INTEGRITY_FAILURE';json(res,200,metrics);return true;
   }
   if(url.pathname==='/admin/api/users'&&req.method==='GET'){
    const q=(url.searchParams.get('q')||'').slice(0,100).toLowerCase(),entitlement=url.searchParams.get('entitlement'),registered=url.searchParams.get('registered');
    if(registered&&!/^\d{4}-\d{2}-\d{2}$/.test(registered))throw bad('INVALID_DATE');
    let rows=accountRows(ledger,environment).filter(a=>(!q||a.id.toLowerCase().includes(q)||(actor.role!=='VIEWER'&&a.profile?.email?.toLowerCase().includes(q)))&&(!registered||a.createdAt?.startsWith(registered))&&(!entitlement||(entitlement==='STUDIO'?a.subscription?.status==='ACTIVE':entitlement==='PASS'?(a.purchases?.length||0)>0:entitlement==='FREE'?!a.purchases?.length&&a.subscription?.status!=='ACTIVE':false)));
    const total=rows.length,offset=Math.max(0,Math.min(total,Number(url.searchParams.get('offset'))||0));
    json(res,200,{total,offset,users:rows.slice(offset,offset+50).map(a=>{const value=safeUser(ledger,a,analytics.snapshot(),{pii:actor.role!=='VIEWER'});delete value.ledger;return value})});return true;
   }
   const detail=url.pathname.match(/^\/admin\/api\/users\/([a-zA-Z0-9_-]{1,100})$/);
   if(detail&&req.method==='GET'){
    const a=accountRows(ledger,environment).find(a=>a.id===detail[1]);if(!a)throw bad('ACCOUNT_NOT_FOUND',404);await audit.append(actor.username,'VIEW_ACCOUNT',a.id,'SUPPORT_REVIEW');json(res,200,safeUser(ledger,a,analytics.snapshot(),{pii:actor.role!=='VIEWER'}));return true;
   }
   const support=url.pathname.match(/^\/admin\/api\/users\/([a-zA-Z0-9_-]{1,100})\/(restore|invalidate-sessions)$/);
   if(support&&req.method==='POST'){
    allowed(actor,['OWNER','ADMIN']);const a=accountRows(ledger,environment).find(a=>a.id===support[1]);if(!a)throw bad('ACCOUNT_NOT_FOUND',404);
    const input=await body(req);if(!['USER_REQUEST','SUSPECTED_COMPROMISE','SUPPORT_RESTORE'].includes(input.reason))throw bad('REASON_REQUIRED');
    await audit.append(actor.username,support[2].toUpperCase()+'_REQUESTED',a.id,input.reason);
    if(support[2]==='restore')ledger.restore(a.id);else sessions.revokeAccount(a.id);
    await audit.append(actor.username,support[2].toUpperCase()+'_COMPLETED',a.id,input.reason);json(res,200,{ok:true});return true;
   }
   if(url.pathname==='/admin/api/audit'&&req.method==='GET'){allowed(actor,['OWNER','ADMIN']);json(res,200,{healthy:audit.healthy,entries:audit.recent()});return true}
   if(url.pathname==='/admin/api/export'&&req.method==='POST'){
    allowed(actor,['OWNER','ADMIN']);const data=await body(req);if(!['users','search','passes','studio','models'].includes(data.report)||!['24h','7d','30d','90d','all'].includes(data.range||'30d'))throw bad('INVALID_REPORT');
    const m=buildMetrics({ledger,analytics,environment,range:data.range||'30d',clock});const rows={users:m.growth.daily,search:m.search.daily,passes:m.passes.tiers,studio:[{active:m.studio.active,testEntitlements:m.studio.testEntitlements,newSubscriptions:m.studio.newSubscriptions,cancellations:m.studio.cancellations}],models:m.models}[data.report];
    await audit.append(actor.username,'EXPORT_AGGREGATES',data.report,'SUPPORT_REVIEW');
    const keys=Object.keys(rows[0]||{status:null}),escape=value=>'"'+String(value??'').replace(/^[=+\-@\t\r]/,"'$&").replaceAll('"','""')+'"';
    const csv=[keys.map(escape).join(','),...rows.map(row=>keys.map(k=>escape(row[k])).join(','))].join('\r\n');res.writeHead(200,{'Content-Type':'text/csv','Content-Disposition':`attachment; filename="honorable-${environment}-${data.report}.csv"`});res.end(csv);return true;
   }
   throw bad('NOT_FOUND',404);
  }catch(error){json(res,error.status||500,{error:error.status?error.message:'ADMIN_SERVICE_UNAVAILABLE'});return true;}
 };
}
module.exports={createAdminRouter};
