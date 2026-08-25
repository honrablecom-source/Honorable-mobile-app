import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { createMarketingApp } from '../src/app.js';
import { config } from '../src/config.js';
import { ResendDevelopmentEmailService } from '../src/core/email.js';

async function fixture(t,dependencies={}){
  const dir=await mkdtemp(path.join(os.tmpdir(),'honorable-marketing-test-'));
  const server=http.createServer(createMarketingApp({dataDir:dir,publicBaseUrl:'http://127.0.0.1',credentials:{},discoveryMode:'mock',waitlistStore:'local-json',emailProvider:'test'},dependencies));
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));const base=`http://127.0.0.1:${server.address().port}`;
  t.after(async()=>{await new Promise(resolve=>server.close(resolve));await rm(dir,{recursive:true,force:true});});return {base,dir};
}
function request(base,pathname,{method='GET',data}={}){
  return new Promise((resolve,reject)=>{const payload=data===undefined?null:JSON.stringify(data);const target=new URL(pathname,base);const req=http.request(target,{method,headers:payload?{'content-type':'application/json','content-length':Buffer.byteLength(payload)}:{}},res=>{let raw='';res.setEncoding('utf8');res.on('data',chunk=>raw+=chunk);res.on('end',()=>resolve({status:res.statusCode,headers:res.headers,text:()=>raw,json:()=>JSON.parse(raw)}));});req.on('error',reject);if(payload)req.write(payload);req.end();});
}
const post=(base,url,data)=>request(base,url,{method:'POST',data});

test('waitlist enforces consent, handles duplicates, and tracks attribution',async t=>{
  const {base,dir}=await fixture(t);
  let response=await post(base,'/api/waitlist',{email:'person@example.com',device:'Both',consent:false});assert.equal(response.status,400);
  const input={email:'Person@Example.com',device:'Android',consent:true,source:'tiktok',campaign:'find-any-memory',content:'demo-a'};
  response=await post(base,'/api/waitlist',input);assert.equal(response.status,201);let result=response.json();assert.equal(result.duplicate,false);assert.ok(result.referralCode);
  response=await post(base,'/api/waitlist',input);assert.equal(response.status,200);result=response.json();assert.equal(result.duplicate,true);
  await post(base,'/api/events',{type:'landing_view',source:'tiktok',campaign:'find-any-memory',content:'demo-a'});
  const dashboard=(await request(base,'/api/dashboard')).json();assert.equal(dashboard.analytics.signups,1);assert.equal(dashboard.analytics.visitors,1);assert.equal(dashboard.analytics.bySource[0].label,'tiktok');assert.equal(dashboard.mockData,true);
  const stored=JSON.parse(await readFile(path.join(dir,'marketing-state.json'),'utf8'));assert.ok(stored.waitlist[0].consentAt);assert.equal(stored.waitlist[0].consent,true);assert.equal(stored.emailDeliveries.length,1);
  response=await post(base,'/api/unsubscribe',{token:stored.waitlist[0].unsubscribeToken});assert.equal(response.status,200);const after=(await request(base,'/api/dashboard')).json();assert.equal(after.analytics.unsubscribeCount,1);
});

test('honeypot is accepted silently but stores no signup',async t=>{
  const {base}=await fixture(t);const response=await post(base,'/api/waitlist',{email:'bot@example.com',device:'Both',consent:true,website:'spam'});assert.equal(response.status,202);
  const dashboard=(await request(base,'/api/dashboard')).json();assert.equal(dashboard.analytics.signups,0);
});

test('drafts require approval and approval does not publish',async t=>{
  const {base}=await fixture(t);let response=await post(base,'/api/content/drafts',{format:'YouTube Shorts'});assert.equal(response.status,201);let draft=response.json();assert.equal(draft.status,'DRAFT');
  response=await post(base,`/api/content/drafts/${draft.id}/approve`,{});draft=response.json();assert.equal(draft.status,'APPROVED — READY FOR MANUAL PUBLISHING');
  const dashboard=(await request(base,'/api/dashboard')).json();assert.match(dashboard.publishing,/DISABLED/);
});

test('website and dashboard are served with restrictive security headers',async t=>{
  const {base}=await fixture(t);const home=await request(base,'/');assert.equal(home.status,200);assert.match(home.text(),/You remember/);assert.match(home.headers['content-security-policy'],/frame-ancestors 'none'/);
  const dashboard=await request(base,'/dashboard');assert.equal(dashboard.status,200);assert.match(dashboard.text(),/data-label/);
});

test('production email absence skips welcome without failing signup',async t=>{
  const {base,dir}=await fixture(t,{});
  const settings=config({RESEND_API_KEY:'secret',RESEND_DEV_RECIPIENT:'owner@example.com'},dir);
  const server=http.createServer(createMarketingApp({...settings,dataDir:dir,waitlistStore:'local-json',discoveryMode:'mock'}));
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  t.after(()=>new Promise(resolve=>server.close(resolve)));
  const isolatedBase=`http://127.0.0.1:${server.address().port}`;
  const response=await post(isolatedBase,'/api/waitlist',{email:'arbitrary@example.com',device:'Both',consent:true});
  assert.equal(response.status,201);assert.equal(response.json().emailDelivery,'skipped');
  const dashboard=(await request(isolatedBase,'/api/dashboard')).json();
  assert.equal(dashboard.email.development,'CONNECTED');assert.equal(dashboard.email.production,'DOMAIN REQUIRED');
});

test('resend development mode only permits the configured account recipient',async()=>{
  const calls=[];const service=new ResendDevelopmentEmailService({apiKey:'secret',recipient:'Owner@Example.com',fetchImpl:async(url,options)=>{calls.push(JSON.parse(options.body));return {ok:true,json:async()=>({id:'test-id'})};}});
  await assert.rejects(()=>service.sendTest({to:'waitlist-user@example.com'}),error=>error.code==='recipient_not_allowed');
  assert.equal(calls.length,0);
  const result=await service.sendTest({to:'owner@example.com'});assert.equal(result.status,'success');assert.deepEqual(calls[0].to,['owner@example.com']);assert.match(calls[0].from,/@resend\.dev>$/);
});

test('auto-reply dashboard is dry-run, globally off, and exposes no authorized publisher',async t=>{
  const {base}=await fixture(t);const page=await request(base,'/dashboard/social/autoreply');assert.equal(page.status,200);assert.match(page.text(),/STOP ALL AUTO-REPLIES/);
  const status=(await request(base,'/api/autoreply')).json();assert.equal(status.dryRun,true);assert.equal(status.settings.globalEnabled,false);assert.ok(status.platforms.every(x=>x.authenticated===false));assert.ok(status.platforms.every(x=>x.apiSupport!=='REPLY_API_SUPPORTED'));
  const stopped=await post(base,'/api/autoreply/settings',{killSwitch:true,globalEnabled:true});assert.equal(stopped.json().globalEnabled,false);
});
