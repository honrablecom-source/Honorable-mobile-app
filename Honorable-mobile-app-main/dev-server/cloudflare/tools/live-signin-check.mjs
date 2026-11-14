import http from 'node:http';
import crypto from 'node:crypto';
import { pathToFileURL } from 'node:url';
import { writeFile } from 'node:fs/promises';

export async function verifyLiveSession({ idToken, apiUrl, fetchImpl = fetch }) {
  const checks = { googleSignIn:false, sessionVerified:false, refreshVerified:false, sameAccountAndCredits:false, logoutVerified:false };
  let accessToken, refreshToken;
  const call = async (route, body, access) => {
    const response = await fetchImpl(new URL(route, apiUrl), {
      method: body === undefined ? 'GET' : 'POST', redirect:'error', signal:AbortSignal.timeout(10000),
      headers:{'content-type':'application/json', ...(access ? {authorization:'Bearer '+access} : {})},
      ...(body === undefined ? {} : {body:JSON.stringify(body)}),
    });
    return {status:response.status, body:await response.json()};
  };
  try {
    // No installation grant, purchase, media, query or search request is submitted.
    const login = await call('/v1/auth/google', {idToken});
    if (login.status !== 200 || !login.body.accessToken || !login.body.refreshToken || !login.body.account?.accountId) return checks;
    ({accessToken,refreshToken} = login.body);
    checks.googleSignIn = true;
    const session = await call('/v1/auth/session', undefined, accessToken);
    checks.sessionVerified = session.status === 200 && session.body.account?.accountId === login.body.account.accountId;
    const rotated = await call('/v1/auth/refresh', {refreshToken});
    if (rotated.status !== 200 || !rotated.body.accessToken || !rotated.body.refreshToken) return checks;
    checks.refreshVerified = rotated.body.refreshToken !== refreshToken;
    ({accessToken,refreshToken} = rotated.body);
    const account = await call('/v1/account', undefined, accessToken);
    checks.sameAccountAndCredits = account.status === 200 && ['accountId','balance','freeMonthlyRemaining','totalAvailable'].every(key => account.body[key] === login.body.account[key]);
  } catch { /* Never expose tokens, identity fields or transport details. */ }
  finally {
    if (refreshToken) {
      try {
        const logout = await call('/v1/auth/logout', {refreshToken}, accessToken);
        const after = await call('/v1/auth/session', undefined, accessToken);
        checks.logoutVerified = logout.status === 200 && after.status === 401;
      } catch { checks.logoutVerified = false; }
    }
    accessToken = refreshToken = idToken = undefined;
  }
  return checks;
}

export function createSignInCheck({ clientId, apiUrl, port = 4174, fetchImpl, onResult = () => {} }) {
  if (!/^\d+-[A-Za-z0-9_-]+\.apps\.googleusercontent\.com$/.test(clientId || '')) throw new Error('GOOGLE_CLIENT_CONFIGURATION_REQUIRED');
  const api = new URL(apiUrl);
  if (api.protocol !== 'https:' || api.username || api.password || api.hash || api.search) throw new Error('HTTPS_ACCOUNT_API_REQUIRED');
  const csrf = crypto.randomBytes(32).toString('hex'), nonce = crypto.randomBytes(24).toString('base64');
  let attempted = false, result = {status:'waiting'};
  const server = http.createServer(async (req,res) => {
    const actualPort = server.address().port, origin = 'http://localhost:' + actualPort;
    const send = (status, body) => {res.writeHead(status, {'content-type':'application/json','cache-control':'no-store','X-Content-Type-Options':'nosniff'});res.end(JSON.stringify(body));};
    if (req.headers.host !== 'localhost:' + actualPort) return send(403,{error:'INVALID_HOST'});
    if (req.method === 'GET' && req.url === '/result') return send(200,result);
    if (req.method === 'GET' && req.url === '/') {
      res.writeHead(200, {
        'content-type':'text/html; charset=utf-8','cache-control':'no-store','X-Content-Type-Options':'nosniff',
        'Referrer-Policy':'no-referrer-when-downgrade','Cross-Origin-Opener-Policy':'same-origin-allow-popups',
        'Content-Security-Policy':`default-src 'none'; script-src 'nonce-${nonce}' https://accounts.google.com/gsi/client; frame-src https://accounts.google.com; connect-src 'self' https://accounts.google.com; style-src 'unsafe-inline' https://accounts.google.com; img-src 'self' data: https://*.googleusercontent.com; base-uri 'none'; form-action 'none'; frame-ancestors 'none'`,
      });
      return res.end(`<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Noble — live Google sign-in check</title><style>body{font:18px system-ui;color:#111;background:white;max-width:620px;margin:8vh auto;padding:24px;line-height:1.6}h1{font-size:30px}pre{white-space:pre-wrap;font:15px system-ui;border:1px solid #aaa;padding:16px}</style><h1>Noble account sign-in check</h1><p>Sign in with your Google account to verify Noble's deployed account service. This checks sign-in, session refresh and sign-out. It does not access your photos, run searches or grant/spend credits.</p><p>Your login tokens stay in memory for this check and are revoked afterward. This is browser evidence; Android sign-in still needs device testing.</p><div id="google"></div><pre id="status">Waiting for Google sign-in.</pre><script nonce="${nonce}">window.addEventListener("load",start);function start(){if(!window.google){document.getElementById("status").textContent="Google sign-in could not load.";return;}google.accounts.id.initialize({client_id:${JSON.stringify(clientId)},auto_select:false,callback:async response=>{document.getElementById('google').hidden=true;const out=document.getElementById('status');out.textContent='Checking the live account service…';try{const r=await fetch('/credential',{method:'POST',headers:{'content-type':'application/json','x-noble-check':${JSON.stringify(csrf)}},body:JSON.stringify({idToken:response.credential})});const result=await r.json();response.credential=undefined;out.textContent=result.status==='passed'?'PASS: Google sign-in, session, refresh, unchanged credits and sign-out verified.': 'Check did not complete successfully. No Android acceptance is claimed.';}catch{response.credential=undefined;out.textContent='The check could not finish. Please return to Codex.';}}});google.accounts.id.renderButton(document.getElementById('google'),{theme:'outline',size:'large',text:'signin_with'});}</script><script nonce="${nonce}" src="https://accounts.google.com/gsi/client" async></script></html>`);
    }
    if (req.method !== 'POST' || req.url !== '/credential') return send(404,{error:'NOT_FOUND'});
    if (req.headers.origin !== origin || req.headers['x-noble-check'] !== csrf || req.headers['content-type'] !== 'application/json') return send(403,{error:'INVALID_ORIGIN_OR_CSRF'});
    if (attempted) return send(409,{error:'CHECK_ALREADY_ATTEMPTED'});
    let body = '', size = 0;
    try {
      for await (const chunk of req) {size += chunk.length;if(size > 20000) return send(413,{error:'REQUEST_TOO_LARGE'});body += chunk;}
      const data = JSON.parse(body);
      if (typeof data?.idToken !== 'string' || !data.idToken || data.idToken.length > 16384) return send(400,{error:'INVALID_CREDENTIAL'});
      attempted = true;
      const checks = await verifyLiveSession({idToken:data.idToken, apiUrl:api.href, fetchImpl});
      data.idToken = body = undefined;
      result = {status:Object.values(checks).every(Boolean)?'passed':'failed',checks,androidDeviceVerified:false,searchCompletionVerified:false};
      await onResult(result);
      return send(200,result);
    } catch { return send(400,{error:'CHECK_FAILED'}); }
  });
  return server;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const server = createSignInCheck({clientId:process.env.HONORABLE_GOOGLE_WEB_CLIENT_ID,apiUrl:process.env.HONORABLE_ACCOUNT_API_URL,
    onResult:async result => {if(process.env.NOBLE_SIGNIN_EVIDENCE_PATH) await writeFile(process.env.NOBLE_SIGNIN_EVIDENCE_PATH,JSON.stringify(result,null,2),{mode:0o600});console.log(JSON.stringify(result));}});
  server.listen(4174,'127.0.0.1',()=>console.log('Noble sign-in check: http://localhost:4174/'));
  server.on('error',()=>{console.error('SIGN_IN_CHECK_START_FAILED');process.exitCode=1;});
  setTimeout(()=>server.close(),15*60*1000).unref();
}
