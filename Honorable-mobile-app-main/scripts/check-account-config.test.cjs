const test = require('node:test');
const assert = require('node:assert/strict');
const {checkConfiguration} = require('./check-account-config.cjs');
const config = {HONORABLE_GOOGLE_WEB_CLIENT_ID:'123456-fixture.apps.googleusercontent.com', HONORABLE_ACCOUNT_API_URL:'https://account.example.test/account'};

test('missing or invalid existing settings fail without printing their values', async () => {
  for (const env of [{}, {...config,HONORABLE_GOOGLE_WEB_CLIENT_ID:'not-an-oauth-id'}, {...config,HONORABLE_ACCOUNT_API_URL:'http://account.example.test'}, {...config,HONORABLE_ACCOUNT_API_URL:'https://name:private-password@account.example.test'}, {...config,HONORABLE_ACCOUNT_API_URL:'https://account.example.test?token=private-token'}]) {
    const report = await checkConfiguration(env);
    assert.equal(report.ready,false);
    const text = JSON.stringify(report);
    for (const value of Object.values(env)) assert(!text.includes(value));
    assert(!text.includes('private-password'));assert(!text.includes('private-token'));
  }
});

test('configuration alone does not claim a service probe or device acceptance', async () => {
  const report = await checkConfiguration(config);
  assert.equal(report.ready,true);assert.equal(report.probed,false);
  assert.equal(report.deviceVerified,false);assert.equal(report.storeReady,false);
});

test('HTTPS probes keep the configured base path and refuse redirects with a timeout', async () => {
  const routes = [];
  const report = await checkConfiguration(config,{probe:true,fetchImpl:async(url,options)=>{
    assert.equal(options.redirect,'error');assert(options.signal instanceof AbortSignal);
    routes.push(url.pathname);
    return {status:200,json:async()=>({service:'honorable-account',status:'UP',scope:'ACCOUNT_API_CONFIGURATION',ready:true})};
  }});
  assert.deepEqual(routes,['/account/healthz','/account/readyz']);assert.equal(report.ready,true);assert.equal(report.probed,true);
});

test('unconfigured providers, wrong services and network errors fail closed', async () => {
  for (const fetchImpl of [async()=>({status:503,json:async()=>({service:'honorable-account',ready:false})}), async()=>({status:200,json:async()=>({service:'some-other-service',ready:true})}), async()=>{throw Error(config.HONORABLE_ACCOUNT_API_URL)}]) {
    const report = await checkConfiguration(config,{probe:true,fetchImpl});
    assert.equal(report.ready,false);assert(!JSON.stringify(report).includes(config.HONORABLE_ACCOUNT_API_URL));
  }
});

test('invalid configuration never causes an outbound request', async () => {
  let calls=0;
  const report = await checkConfiguration({}, {probe:true,fetchImpl:async()=>{calls++;throw Error('must not fetch')}});
  assert.equal(calls,0);assert.equal(report.ready,false);
});
