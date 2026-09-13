'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
class AuditLog {
 constructor(file,{clock=Date.now}={}){this.file=file;this.clock=clock;this.chain=Promise.resolve();this.rows=[];this.healthy=true;try{let previous='';for(const line of fs.readFileSync(file,'utf8').trim().split('\n').filter(Boolean)){const row=JSON.parse(line),{hash,...payload}=row;if(payload.previous!==previous||this.hash(payload)!==hash)throw Error('Audit integrity error');this.rows.push(row);previous=hash}}catch(e){if(e.code!=='ENOENT')this.healthy=false}}
 hash(payload){return crypto.createHash('sha256').update(JSON.stringify(payload)).digest('hex')}
 append(actor,action,target,reason){const job=this.chain.then(async()=>{if(!this.healthy)throw Error('AUDIT_UNAVAILABLE');const payload={id:crypto.randomUUID(),at:new Date(this.clock()).toISOString(),actor,action,target:target||null,reason:reason||null,previous:this.rows.at(-1)?.hash||''};const row={...payload,hash:this.hash(payload)};await fs.promises.mkdir(path.dirname(this.file),{recursive:true});await fs.promises.appendFile(this.file,JSON.stringify(row)+'\n',{mode:0o600});this.rows.push(row);return row});this.chain=job.catch(()=>{this.healthy=false});return job}
 recent(){return this.rows.slice(-200).reverse()}
}
module.exports={AuditLog};
