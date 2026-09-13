'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const {CLIENT_EVENTS,SERVER_EVENTS,safeMetadata}=require('./events');
const ACTIVE=new Set(['session_started','session_active','search_started','result_opened','editor_opened','edit_saved','edit_tool_used','workspace_opened']);
class AnalyticsStore {
 constructor(file,{clock=Date.now,rawDays=30,aggregateDays=400,maxEvents=50000}={}) {
  this.file=file;this.clock=clock;this.rawDays=rawDays;this.aggregateDays=aggregateDays;this.maxEvents=maxEvents;this.available=true;this.dropped=0;this.writing=null;this.scheduled=null;
  this.state={startedAt:new Date(clock()).toISOString(),events:[],days:{},seen:{}};
  try{const saved=JSON.parse(fs.readFileSync(file,'utf8'));if(!saved.events||!saved.days||!saved.seen)throw Error('Invalid analytics store');this.state=saved;}catch(e){if(e.code!=='ENOENT')this.available=false;}
 }
 record({id,type,accountId=null,environment,metadata={},authority='server'}) {
  try {
   if(!this.available)return false;
   if(!['development','staging','production'].includes(environment))throw Error('INVALID_ENVIRONMENT');
   if(!(authority==='client'?CLIENT_EVENTS:SERVER_EVENTS).has(type))throw Error('INVALID_EVENT');
   const safe=safeMetadata(type,metadata),now=this.clock(),date=new Date(now).toISOString(),day=date.slice(0,10);
   const key=crypto.createHash('sha256').update(`${environment}:${accountId||''}:${id}`).digest('hex');
   if(this.state.seen[key])return false;
   this.state.seen[key]=now;
   const event={id:key,type,accountId,environment,metadata:safe,authority,at:date};this.state.events.push(event);
   const bucketKey=environment+':'+day,bucket=this.state.days[bucketKey]??={date:day,environment,counts:{},active:[],usersByEvent:{},platforms:{}};
   bucket.counts[type]=(bucket.counts[type]||0)+1;
   if(accountId){const users=bucket.usersByEvent[type]??=[];if(!users.includes(accountId))users.push(accountId);if(ACTIVE.has(type)){if(!bucket.active.includes(accountId))bucket.active.push(accountId);bucket.lastActive??={};bucket.lastActive[accountId]=date;}if(safe.platform&&ACTIVE.has(type)){const group=bucket.platforms[safe.platform]??=[];if(!group.includes(accountId))group.push(accountId);}}
   if(!this.scheduled)this.scheduled=setTimeout(()=>{this.scheduled=null;void this.flush()},25).unref();
   return true;
  }catch{this.dropped++;return false;}
 }
 snapshot(){return this.state;}
 async flush() {
  if(this.writing){await this.writing;return this.flush()}
  if(!this.available)return;
  const now=this.clock(),rawCutoff=now-this.rawDays*86400000,aggregateCutoff=new Date(now-this.aggregateDays*86400000).toISOString().slice(0,10);
  this.state.events=this.state.events.filter(e=>Date.parse(e.at)>=rawCutoff).slice(-this.maxEvents);
  for(const[k,b]of Object.entries(this.state.days))if(b.date<aggregateCutoff)delete this.state.days[k];
  for(const[k,t]of Object.entries(this.state.seen))if(t<now-90*86400000)delete this.state.seen[k];
  const data=JSON.stringify(this.state),temp=this.file+'.tmp';
  this.writing=(async()=>{try{await fs.promises.mkdir(path.dirname(this.file),{recursive:true});await fs.promises.writeFile(temp,data,{mode:0o600});await fs.promises.rename(temp,this.file);}catch{this.available=false;this.dropped++;}finally{this.writing=null;}})();
  return this.writing;
 }
 close(){if(this.scheduled)clearTimeout(this.scheduled);this.scheduled=null;return this.flush();}
}
module.exports={AnalyticsStore,ACTIVE};
