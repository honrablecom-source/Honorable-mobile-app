'use strict';
const crypto=require('node:crypto'),fs=require('node:fs'),{promisify}=require('node:util');
const scrypt=promisify(crypto.scrypt);
async function passwordHash(password){const salt=crypto.randomBytes(16).toString('hex');return{salt,hash:(await scrypt(password,salt,64)).toString('hex')}}
class AdminAuth {
 constructor({file,users,clock=Date.now,environment}){
  this.clock=clock;this.environment=environment;this.sessions=new Map();this.attempts=new Map();
  this.users=users||JSON.parse(fs.readFileSync(file,'utf8')).users;
  if(!Array.isArray(this.users)||!this.users.length)throw Error('ADMIN_CONFIGURATION_REQUIRED');
  const seen=new Set();for(const user of this.users){if(!/^[a-zA-Z0-9._-]{1,64}$/.test(user.username)||seen.has(user.username)||!['OWNER','ADMIN','VIEWER'].includes(user.role)||!Array.isArray(user.environments)||user.environments.some(e=>!['development','staging','production'].includes(e))||!/^[a-f0-9]{32}$/.test(user.password?.salt)||!/^[a-f0-9]{128}$/.test(user.password?.hash))throw Error('INVALID_ADMIN_CONFIGURATION');seen.add(user.username);}
 }
 async login(username,password,address){
  const now=this.clock();for(const[k,v]of this.attempts)if(v.until<now)this.attempts.delete(k);
  if(this.attempts.size>2000)throw Object.assign(Error('LOGIN_RATE_LIMIT'),{status:429});
  const attempt=this.attempts.get(address)||{count:0,until:now+600000};attempt.count++;this.attempts.set(address,attempt);if(attempt.count>10)throw Object.assign(Error('LOGIN_RATE_LIMIT'),{status:429});
  const user=this.users.find(u=>u.username===username);const saved=user?.password||{salt:'0'.repeat(32),hash:'0'.repeat(128)};
  const derived=await scrypt(typeof password==='string'&&password.length<=512?password:'',saved.salt,64);
  if(!user||!crypto.timingSafeEqual(derived,Buffer.from(saved.hash,'hex'))||!user.environments.includes(this.environment))throw Object.assign(Error('ADMIN_LOGIN_FAILED'),{status:401});
  this.attempts.delete(address);const token=crypto.randomBytes(32).toString('base64url'),key=this.digest(token);
  for(const[k,s]of this.sessions)if(s.expiresAt<now)this.sessions.delete(k);
  this.sessions.set(key,{username:user.username,role:user.role,environments:user.environments,expiresAt:now+8*3600000});return token;
 }
 digest(token){return crypto.createHash('sha256').update(token||'').digest('hex')}
 session(req){const token=(req.headers.cookie||'').split(';').map(x=>x.trim()).find(x=>x.startsWith('__Host-honorable-admin='))?.slice('__Host-honorable-admin='.length);const session=this.sessions.get(this.digest(token));if(!session||session.expiresAt<this.clock())throw Object.assign(Error('ADMIN_AUTH_REQUIRED'),{status:401});return session}
 logout(req){const token=(req.headers.cookie||'').split(';').map(x=>x.trim()).find(x=>x.startsWith('__Host-honorable-admin='))?.slice('__Host-honorable-admin='.length);this.sessions.delete(this.digest(token))}
}
module.exports={AdminAuth,passwordHash};
