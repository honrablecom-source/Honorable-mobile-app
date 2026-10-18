const crypto=require('node:crypto');
const encode=value=>Buffer.from(JSON.stringify(value)).toString('base64url');
const fail=(message,status=401)=>Object.assign(new Error(message),{status});
class SessionAuth{
  constructor(secret,clock=()=>Date.now()){if(!secret||secret.length<32)throw new Error('SESSION_SECRET_TOO_SHORT');this.secret=secret;this.clock=clock}
  issue(accountId,ttlSeconds=3600){const payload=encode({sub:accountId,iat:Math.floor(this.clock()/1000),exp:Math.floor(this.clock()/1000)+ttlSeconds,iss:'honorable'});return `${payload}.${this.sign(payload)}`}
  verify(header){if(typeof header!=='string'||!header.startsWith('Bearer '))throw fail('AUTH_REQUIRED');const token=header.slice(7),[payload,signature,...extra]=token.split('.');if(!payload||!signature||extra.length)throw fail('MALFORMED_AUTH');const expected=this.sign(payload),left=Buffer.from(signature),right=Buffer.from(expected);if(left.length!==right.length||!crypto.timingSafeEqual(left,right))throw fail('INVALID_AUTH');let claims;try{claims=JSON.parse(Buffer.from(payload,'base64url').toString())}catch{throw fail('MALFORMED_AUTH')}if(claims.iss!=='honorable'||!claims.sub)throw fail('INVALID_AUTH');if(claims.exp<=Math.floor(this.clock()/1000))throw fail('EXPIRED_AUTH');return claims.sub}
  sign(payload){return crypto.createHmac('sha256',this.secret).update(payload).digest('base64url')}
}
const googleCredentialError=()=>fail('INVALID_GOOGLE_CREDENTIAL');
const isRecord=value=>value!==null&&typeof value==='object'&&!Array.isArray(value);
function jwtPart(part){
  if(typeof part!=='string'||!part||!/^[A-Za-z0-9_-]+$/.test(part))throw googleCredentialError();
  const bytes=Buffer.from(part,'base64url');
  if(bytes.toString('base64url')!==part)throw googleCredentialError();
  return bytes;
}
function jwtObject(part){
  try{const parsed=JSON.parse(jwtPart(part).toString('utf8'));if(isRecord(parsed))return parsed}
  catch{};
  throw googleCredentialError();
}
class GoogleIdTokenVerifier{
  constructor({audience,fetchImpl=global.fetch,clock=()=>Date.now(),jwksUri='https://www.googleapis.com/oauth2/v3/certs',fetchTimeoutMs=5000}){
    if(typeof audience!=='string'||!audience)throw new Error('GOOGLE_CLIENT_ID_REQUIRED');
    if(typeof fetchImpl!=='function'||!Number.isInteger(fetchTimeoutMs)||fetchTimeoutMs<10||fetchTimeoutMs>10000)throw new Error('INVALID_GOOGLE_VERIFIER_CONFIG');
    this.audience=audience;this.fetch=fetchImpl;this.clock=clock;this.jwksUri=jwksUri;this.fetchTimeoutMs=fetchTimeoutMs;
    this.keys=new Map();this.expiresAt=0;this.retryAfter=0;this.unknownRefreshAfter=0;this.refreshPromise=null;
  }
  async verify(token){
    if(typeof token!=='string'||token.length>16384)throw googleCredentialError();
    const parts=token.split('.');
    if(parts.length!==3)throw googleCredentialError();
    const header=jwtObject(parts[0]),claims=jwtObject(parts[1]),signature=jwtPart(parts[2]);
    if(header.alg!=='RS256'||typeof header.kid!=='string'||!header.kid||header.kid.length>256||signature.length<128||signature.length>1024)throw googleCredentialError();
    const key=await this.key(header.kid);
    let valid=false;
    try{valid=crypto.verify('RSA-SHA256',Buffer.from(`${parts[0]}.${parts[1]}`),key,signature)}catch{throw googleCredentialError()}
    if(!valid)throw googleCredentialError();
    if(!['https://accounts.google.com','accounts.google.com'].includes(claims.iss))throw fail('INVALID_GOOGLE_ISSUER');
    if(claims.aud!==this.audience)throw fail('WRONG_GOOGLE_AUDIENCE');
    if(typeof claims.sub!=='string'||!claims.sub.trim())throw fail('MISSING_GOOGLE_SUBJECT');
    const now=Math.floor(this.clock()/1000);
    if(!Number.isSafeInteger(claims.exp)||claims.exp<=now)throw fail('EXPIRED_GOOGLE_CREDENTIAL');
    if(claims.iat!==undefined&&(!Number.isSafeInteger(claims.iat)||claims.iat>now+300||claims.iat>claims.exp))throw googleCredentialError();
    if(claims.nbf!==undefined&&(!Number.isSafeInteger(claims.nbf)||claims.nbf>now))throw googleCredentialError();
    if(claims.email!==undefined&&typeof claims.email!=='string')throw googleCredentialError();
    if(claims.email_verified!==undefined&&typeof claims.email_verified!=='boolean')throw googleCredentialError();
    if(claims.name!==undefined&&typeof claims.name!=='string')throw googleCredentialError();
    return{sub:claims.sub,email:claims.email,emailVerified:claims.email_verified===true,name:claims.name};
  }
  cacheLifetime(headers){
    const control=headers?.get?.('cache-control')||'';
    if(/(?:^|,)\s*(?:no-store|no-cache)\b/i.test(control))return 0;
    const maxAge=control.split(',').map(piece=>/^\s*max-age\s*=\s*"?(\d+)"?\s*$/i.exec(piece)?.[1]).find(Boolean);
    const age=headers?.get?.('age');
    const ageSeconds=typeof age==='string'&&/^\d+$/.test(age)?Math.min(Number(age),3600):0;
    return Math.max(0,Math.min(maxAge===undefined?60:Number(maxAge),3600)-ageSeconds)*1000;
  }
  async fetchKeys(){
    const controller=new AbortController();
    let timer;
    try{
      const timeout=new Promise((_,reject)=>{timer=setTimeout(()=>{controller.abort();reject(fail('GOOGLE_KEYS_UNAVAILABLE',503))},this.fetchTimeoutMs)});
      const response=await Promise.race([this.fetch(this.jwksUri,{signal:controller.signal}),timeout]);
      if(!response?.ok||typeof response.json!=='function')throw fail('GOOGLE_KEYS_UNAVAILABLE',503);
      const body=await Promise.race([response.json(),timeout]);
      if(!isRecord(body)||!Array.isArray(body.keys)||body.keys.length<1||body.keys.length>100)throw fail('GOOGLE_KEYS_UNAVAILABLE',503);
      const keys=new Map();
      for(const jwk of body.keys){
        if(!isRecord(jwk)||jwk.kty!=='RSA'||typeof jwk.kid!=='string'||!jwk.kid||jwk.kid.length>256||
          typeof jwk.n!=='string'||typeof jwk.e!=='string'||jwk.d!==undefined||
          (jwk.alg!==undefined&&jwk.alg!=='RS256')||(jwk.use!==undefined&&jwk.use!=='sig')||
          (jwk.key_ops!==undefined&&(!Array.isArray(jwk.key_ops)||!jwk.key_ops.includes('verify'))))continue;
        try{
          const key=crypto.createPublicKey({key:jwk,format:'jwk'});
          if(key.asymmetricKeyType!=='rsa'||!(key.asymmetricKeyDetails?.modulusLength>=2048))continue;
          if(keys.has(jwk.kid))throw fail('GOOGLE_KEYS_UNAVAILABLE',503);
          keys.set(jwk.kid,key);
        }catch(error){if(error.message==='GOOGLE_KEYS_UNAVAILABLE')throw error}
      }
      if(!keys.size)throw fail('GOOGLE_KEYS_UNAVAILABLE',503);
      return{keys,lifetimeMs:this.cacheLifetime(response.headers)};
    }finally{clearTimeout(timer)}
  }
  async refresh(){
    if(this.refreshPromise)return this.refreshPromise;
    if(this.clock()<this.retryAfter)throw fail('GOOGLE_KEYS_UNAVAILABLE',503);
    this.refreshPromise=this.fetchKeys().then(({keys,lifetimeMs})=>{
      this.keys=keys;this.expiresAt=this.clock()+lifetimeMs;this.retryAfter=0;return keys;
    }).catch(()=>{
      this.retryAfter=this.clock()+5000;throw fail('GOOGLE_KEYS_UNAVAILABLE',503);
    }).finally(()=>{this.refreshPromise=null});
    return this.refreshPromise;
  }
  async key(kid){
    const now=this.clock(),fresh=now<this.expiresAt;
    if(fresh&&this.keys.has(kid))return this.keys.get(kid);
    if(!this.keys.has(kid)&&now<this.unknownRefreshAfter)throw fail('UNKNOWN_GOOGLE_SIGNING_KEY');
    const keys=await this.refresh(),found=keys.get(kid);
    if(!found){this.unknownRefreshAfter=this.clock()+15000;throw fail('UNKNOWN_GOOGLE_SIGNING_KEY')}
    return found;
  }
}
module.exports={SessionAuth,GoogleIdTokenVerifier,fail};
