import test from 'node:test';
import assert from 'node:assert/strict';
import { createSignInCheck, verifyLiveSession } from '../tools/live-signin-check.mjs';

function fixture() {
  const calls = [], account = {accountId:'fixture-account',balance:40,freeMonthlyRemaining:12,totalAvailable:52};
  let revoked = false;
  const fetchImpl = async (url, options) => {
    const route = new URL(url).pathname;
    const body = options.body ? JSON.parse(options.body) : undefined;
    calls.push({route,body});
    let result, status = 200;
    if (route === '/v1/auth/google') result = {accessToken:'fixture-access',refreshToken:'fixture-refresh',account};
    else if (route === '/v1/auth/refresh') result = {accessToken:'rotated-access',refreshToken:'rotated-refresh'};
    else if (route === '/v1/auth/logout') {revoked = true; result = {signedOut:true};}
    else if (revoked) {status = 401; result = {error:'AUTH_REQUIRED'};}
    else if (route === '/v1/auth/session') result = {account};
    else if (route === '/v1/account') result = account;
    else throw new Error('UNEXPECTED_ROUTE');
    return Response.json(result,{status});
  };
  return {calls,fetchImpl};
}

test('live sign-in helper tests a session then revokes it without granting credits or returning tokens', async () => {
  const {calls,fetchImpl} = fixture();
  const checks = await verifyLiveSession({idToken:'fixture-google-token',apiUrl:'https://account.example.test',fetchImpl});
  assert(Object.values(checks).every(Boolean));
  assert.deepEqual(calls[0].body,{idToken:'fixture-google-token'});
  assert(!calls.some(call => /search|purchase|deduct/.test(call.route)));
  const serialized = JSON.stringify(checks);
  for (const privateValue of ['fixture-google-token','fixture-access','fixture-refresh','fixture-account','rotated-access']) assert(!serialized.includes(privateValue));
});

test('helper revokes issued session when a later verification step fails', async () => {
  const {calls,fetchImpl:original} = fixture();
  const fetchImpl = (url, options) => new URL(url).pathname === '/v1/auth/refresh' ? Promise.reject(new Error('fixture outage')) : original(url,options);
  const checks = await verifyLiveSession({idToken:'fixture-token',apiUrl:'https://account.example.test',fetchImpl});
  assert.equal(checks.googleSignIn,true);
  assert.equal(checks.refreshVerified,false);
  assert.equal(checks.logoutVerified,true);
  assert(calls.some(call => call.route === '/v1/auth/logout'));
});

test('loopback browser helper rejects foreign Host, CSRF, oversized bodies and repeats before forwarding credentials', async () => {
  const {calls,fetchImpl} = fixture();
  const server = createSignInCheck({clientId:'1234567890-fixture.apps.googleusercontent.com',apiUrl:'https://account.example.test',fetchImpl});
  await new Promise(resolve => server.listen(0,'127.0.0.1',resolve));
  const port = server.address().port, base = 'http://localhost:' + port, host = 'localhost:' + port;
  try {
    assert.equal((await fetch('http://127.0.0.1:' + port)).status,403);
    const page = await fetch(base,{headers:{host}}), html = await page.text();
    assert.equal(page.status,200);
    assert(page.headers.get('content-security-policy').includes("frame-ancestors 'none'"));
    assert(!html.includes('onload='));
    const csrf = /'x-noble-check':"([a-f0-9]+)"/.exec(html)[1];
    const headers = {host,origin:'http://' + host,'content-type':'application/json','x-noble-check':csrf};
    assert.equal((await fetch(base+'/credential',{method:'POST',headers:{...headers,origin:'https://foreign.test'},body:'{}'})).status,403);
    assert.equal((await fetch(base+'/credential',{method:'POST',headers:{...headers,'x-noble-check':'wrong'},body:'{}'})).status,403);
    assert.equal((await fetch(base+'/credential',{method:'POST',headers,body:'x'.repeat(20001)})).status,413);
    assert.equal(calls.length,0);
    const success = await fetch(base+'/credential',{method:'POST',headers,body:JSON.stringify({idToken:'fixture-token'})});
    assert.equal(success.status,200);
    const report = await success.json();
    assert.equal(report.status,'passed');
    assert.equal(report.androidDeviceVerified,false);
    assert(!JSON.stringify(report).includes('fixture-token'));
    assert.equal((await fetch(base+'/credential',{method:'POST',headers,body:JSON.stringify({idToken:'fixture-token'})})).status,409);
  } finally {await new Promise(resolve => server.close(resolve));}
});
