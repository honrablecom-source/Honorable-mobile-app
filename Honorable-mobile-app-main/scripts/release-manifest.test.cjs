const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const crypto = require('node:crypto');
const {createManifest} = require('./release-manifest.cjs');
function fixture(t) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'honorable-manifest-'));
  t.after(() => {
    if (!path.resolve(directory).startsWith(path.resolve(os.tmpdir()) + path.sep)) throw new Error('Unsafe temporary cleanup path');
    fs.rmSync(directory, {recursive: true, force: true});
  });
  const root = path.join(directory, 'mobile-react-native');
  const apk = path.join(root, 'android/app/build/outputs/apk/internal/app-internal.apk');
  fs.mkdirSync(path.dirname(apk), {recursive: true});
  fs.writeFileSync(path.join(root, 'android/app/build.gradle'), 'versionName "1.0.3"\nversionCode 4\n');
  fs.mkdirSync(path.join(directory, 'beta-operations'));
  fs.writeFileSync(path.join(directory, 'beta-operations/RELEASE-NOTES-1.0.3.md'), 'Test fixture only.');
  fs.writeFileSync(apk, 'test fixture artifact bytes');
  return {root, apk};
}
test('manifest identifies the actual artifact bytes and keeps readiness unverified', t => {
  const {root, apk} = fixture(t);
  const manifest = createManifest(root, 'internal', {GITHUB_SHA: 'a'.repeat(40)});
  assert.equal(manifest.version, '1.0.3');
  assert.equal(manifest.buildNumber, 4);
  assert.equal(manifest.commit, 'a'.repeat(40));
  assert.equal(manifest.requiresMetro, false);
  assert.equal(manifest.signing, 'PUBLIC_DEBUG_KEY');
  assert.equal(manifest.deviceVerified, false);
  assert.equal(manifest.storeReady, false);
  assert.equal(manifest.apk.sha256, crypto.createHash('sha256').update(fs.readFileSync(apk)).digest('hex'));
  assert.equal(manifest.releaseNotes, 'beta-operations/RELEASE-NOTES-1.0.3.md');
  fs.appendFileSync(apk, 'changed');
  assert.notEqual(createManifest(root, 'internal').apk.sha256, manifest.apk.sha256);
});
test('missing and empty APKs cannot generate success metadata', t => {
  const {root, apk} = fixture(t);
  assert.throws(() => createManifest(root, 'debug'), /ENOENT/);
  fs.writeFileSync(apk, '');
  assert.throws(() => createManifest(root, 'internal'), /empty APK/);
});
test('only configuration presence is recorded, never credential values', t => {
  const {root} = fixture(t);
  const manifest = createManifest(root, 'internal', {
    HONORABLE_GOOGLE_WEB_CLIENT_ID: 'fixture-google-private-value',
    HONORABLE_ACCOUNT_API_URL: 'https://fixture-private-service.invalid',
    HONORABLE_RELEASE_STORE_PASSWORD: 'fixture-store-secret',
  });
  assert.equal(manifest.signInConfigured, true);
  for (const value of ['fixture-google-private-value', 'fixture-private-service', 'fixture-store-secret']) {
    assert.equal(JSON.stringify(manifest).includes(value), false);
  }
  assert.equal(createManifest(root, 'internal').signInConfigured, false);
});
test('unknown variants and invalid commit identifiers fail closed', t => {
  const {root} = fixture(t);
  assert.throws(() => createManifest(root, 'release'), /Only debug\/internal/);
  assert.throws(() => createManifest(root, 'internal', {GITHUB_SHA: 'bad-value'}), /Invalid CI source commit/);
});
test('candidate release notes must match the application version', t => {
  const {root} = fixture(t);
  fs.writeFileSync(path.join(root, 'android/app/build.gradle'), 'versionName "1.0.4"\nversionCode 5\n');
  assert.throws(() => createManifest(root, 'internal'), /release notes are missing/);
});
