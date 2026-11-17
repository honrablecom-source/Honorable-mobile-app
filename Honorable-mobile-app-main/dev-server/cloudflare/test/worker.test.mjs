import test from 'node:test';
import assert from 'node:assert/strict';
import { Miniflare, createFetchMock } from 'miniflare';
import { mkdtemp, rm } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';

const audience = '1234567890-noble-test.apps.googleusercontent.com';
const { privateKey, publicKey } = crypto.generateKeyPairSync('rsa', { modulusLength: 2048 });
const jwk = { ...publicKey.export({ format: 'jwk' }), kid: 'noble-test', alg: 'RS256', use: 'sig' };
const token = (subject = 'subject-one', aud = audience) => {
  const now = Math.floor(Date.now() / 1000);
  const part = value => Buffer.from(JSON.stringify(value)).toString('base64url');
  const payload = part({ alg: 'RS256', kid: jwk.kid }) + '.' + part({ iss: 'https://accounts.google.com', aud, sub: subject, iat: now, exp: now + 3600, email: 'fixture@example.test', email_verified: true });
  return payload + '.' + crypto.sign('RSA-SHA256', Buffer.from(payload), privateKey).toString('base64url');
};

test('real Worker runtime: durable identity, credit grants, session rotation and production gates survive restart', async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), 'noble-account-worker-'));
  const mock = createFetchMock();
  mock.disableNetConnect();
  mock.get('https://www.googleapis.com').intercept({ path: '/oauth2/v3/certs', method: 'GET' })
    .reply(200, { keys: [jwk] }, { headers: { 'content-type': 'application/json', 'cache-control': 'max-age=3600' } }).persist();
  const options = {
    modules: true, scriptPath: new URL('../.wrangler/build/worker.js', import.meta.url).pathname.replace(/^\/([A-Za-z]:\/)/, '$1'),
    compatibilityDate: '2026-07-30', compatibilityFlags: ['nodejs_compat', 'enable_nodejs_http_server_modules'],
    durableObjects: { ACCOUNT: { className: 'NobleAccount', useSQLite: true } },
    durableObjectsPersist: directory, bindings: { HONORABLE_GOOGLE_WEB_CLIENT_ID: audience }, fetchMock: mock,
  };
  let mf = new Miniflare(options);
  const call = async (route, body, access) => {
    const response = await mf.dispatchFetch('https://account.example.test' + route, {
      method: body === undefined ? 'GET' : 'POST',
      headers: { 'content-type': 'application/json', ...(access ? { authorization: 'Bearer ' + access } : {}) },
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    });
    return { status: response.status, data: await response.json() };
  };
  try {
    assert.deepEqual(await call('/healthz'), { status: 200, data: { service: 'honorable-account', status: 'UP' } });
    const ready = await call('/readyz');
    assert.equal(ready.status, 503);
    assert.equal(ready.data.ready, false);
    assert.equal(ready.data.checks.identityProvider, true);
    assert.equal(ready.data.checks.trustedSearchCompletion, false);
    assert.equal((await call('/dev/auth/token', { email: 'fixture@example.test' })).status, 404);
    assert.equal((await call('/admin')).status, 404);
    const wrongAudience = await call('/v1/auth/google', { idToken: token('subject-one', 'wrong-audience') });
    assert.equal(wrongAudience.status, 401, JSON.stringify(wrongAudience.data));
    const login = await call('/v1/auth/google', { idToken: token(), installationId: 'worker-test-installation-0001' });
    assert.equal(login.status, 200);
    const { accessToken, refreshToken, account } = login.data;
    assert.equal(account.totalAvailable, 15);
    assert.equal((await call('/v1/search/start', { model: 'SERAN_V3', requestId: 'locked-v3' }, accessToken)).status, 409);
    assert.equal((await call('/v1/search/start', { model: 'SERAN_V1', requestId: 'search-one' }, accessToken)).status, 503);
    assert.equal((await call('/v1/search/complete', { requestId: 'search-one', outcome: 'SUCCESS' }, accessToken)).status, 404);
    assert.equal((await call('/v1/account', undefined, accessToken)).data.totalAvailable, 15);

    await mf.dispose();
    mf = new Miniflare(options);
    assert.equal((await call('/v1/account', undefined, accessToken)).data.accountId, account.accountId);
    const secondLogin = await call('/v1/auth/google', { idToken: token(), installationId: 'worker-test-installation-0001' });
    assert.equal(secondLogin.data.account.accountId, account.accountId);
    assert.equal(secondLogin.data.account.totalAvailable, 15);
    const rotated = await call('/v1/auth/refresh', { refreshToken });
    assert.equal(rotated.status, 200);
    await mf.dispose();
    mf = new Miniflare(options);
    assert.equal((await call('/v1/auth/refresh', { refreshToken })).status, 401);
    assert.equal((await call('/v1/account', undefined, rotated.data.accessToken)).status, 401);
    assert.equal((await call('/v1/account', undefined, secondLogin.data.accessToken)).data.totalAvailable, 15);
  } finally {
    await mf.dispose();
    await mock.close();
    await rm(directory, { recursive: true, force: true });
  }
});
