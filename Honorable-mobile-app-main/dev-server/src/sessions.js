const fs=require('node:fs');const crypto=require('node:crypto');const path=require('node:path');
const hash=value=>crypto.createHash('sha256').update(String(value)).digest('hex');
const invalid=()=>Object.assign(new Error('AUTH_REQUIRED'),{status:401});
class PersistentSessions{
 constructor(file,clock=()=>Date.now()){this.file=file;this.clock=clock;try{this.rows=JSON.parse(fs.readFileSync(file,'utf8'))}catch(e){if(e.code!=='ENOENT')throw e;this.rows={}}}
 save(){fs.mkdirSync(path.dirname(this.file),{recursive:true});const tmp=this.file+'.tmp';fs.writeFileSync(tmp,JSON.stringify(this.rows),{mode:0o600});fs.renameSync(tmp,this.file)}
 issue(accountId,kind='native',ttl=900){const id=crypto.randomUUID(),accessToken=crypto.randomBytes(32).toString('base64url'),refreshToken=crypto.randomBytes(48).toString('base64url');const row={accountId,kind,accessHash:hash(accessToken),refreshHash:hash(refreshToken),used:[],accessUntil:this.clock()+ttl*1000,expiresAt:this.clock()+30*86400000};this.rows[id]=row;this.save();return{accessToken,refreshToken,expiresIn:ttl,expiresAt:row.expiresAt,tokenType:'Bearer'}}
 lookup(token,kind){const digest=hash(token||'');const entry=Object.entries(this.rows).find(([,r])=>kind==='access'?r.accessHash===digest:r.refreshHash===digest);if(!entry||entry[1].revoked||entry[1].expiresAt<=this.clock())throw invalid();return entry}
 verify(header){if(!header?.startsWith('Bearer '))throw invalid();const [,row]=this.lookup(header.slice(7),'access');if(row.accessUntil<=this.clock())throw invalid();return row.accountId}
 web(token){const [,row]=this.lookup(token,'refresh');if(row.kind!=='web')throw invalid();return row.accountId}
 expiry(token){return this.lookup(token,'refresh')[1].expiresAt}
 refresh(token){const digest=hash(token||'');const reused=Object.values(this.rows).find(r=>r.used.includes(digest));if(reused){reused.revoked=true;this.save();throw invalid()}const [,row]=this.lookup(token,'refresh');if(row.kind!=='native')throw invalid();const accessToken=crypto.randomBytes(32).toString('base64url'),refreshToken=crypto.randomBytes(48).toString('base64url');row.used.push(row.refreshHash);row.refreshHash=hash(refreshToken);row.accessHash=hash(accessToken);row.accessUntil=this.clock()+900000;this.save();return{accessToken,refreshToken,expiresIn:900,expiresAt:row.expiresAt,tokenType:'Bearer'}}
 revokeAccount(accountId){for(const row of Object.values(this.rows))if(row.accountId===accountId)row.revoked=true;this.save()}
 revoke(token){const digest=hash(token||'');for(const row of Object.values(this.rows))if(row.refreshHash===digest||row.accessHash===digest||row.used.includes(digest))row.revoked=true;this.save()}
}
module.exports={PersistentSessions};
