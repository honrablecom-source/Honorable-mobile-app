// Artifact identity only. Never serialize environment variables or credentials.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');

function createManifest(root, variant, env = {}, now = new Date()) {
  if (!['debug', 'internal'].includes(variant)) throw new Error('Only debug/internal tester artifacts are supported.');
  const gradle = fs.readFileSync(path.join(root, 'android/app/build.gradle'), 'utf8');
  const version = gradle.match(/^\s*versionName "([^"]+)"/m)?.[1];
  const build = gradle.match(/^\s*versionCode (\d+)/m)?.[1];
  if (!version || !build) throw new Error('Android version/build identity is missing.');
  const relativeApk = 'android/app/build/outputs/apk/' + variant + '/app-' + variant + '.apk';
  const bytes = fs.readFileSync(path.join(root, relativeApk));
  if (!bytes.length) throw new Error('Cannot record an empty APK.');
  const releaseNotes = 'beta-operations/RELEASE-NOTES-' + version + '.md';
  if (!fs.existsSync(path.resolve(root, '..', releaseNotes))) throw new Error('Candidate release notes are missing.');
  if (env.GITHUB_SHA && !/^[a-f0-9]{40}$/i.test(env.GITHUB_SHA)) throw new Error('Invalid CI source commit.');
  return {
    version, buildNumber: Number(build), commit: env.GITHUB_SHA || 'LOCAL_UNCOMMITTED',
    channel: 'INTERNAL', variant, builtAt: now.toISOString(),
    status: variant === 'internal' ? 'INTERNAL_TEST_ARTIFACT' : 'DEBUG_ARTIFACT',
    signing: 'PUBLIC_DEBUG_KEY', requiresMetro: variant === 'debug',
    signInConfigured: !!(env.HONORABLE_GOOGLE_WEB_CLIENT_ID && env.HONORABLE_ACCOUNT_API_URL),
    apk: {path: relativeApk, sizeBytes: bytes.length, sha256: crypto.createHash('sha256').update(bytes).digest('hex')},
    releaseNotes, deviceVerified: false, storeReady: false,
  };
}
if (require.main === module) {
  const root = path.resolve(__dirname, '../mobile-react-native');
  const manifest = createManifest(root, process.argv[2] || 'debug', process.env);
  fs.writeFileSync(path.join(root, 'release-manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
  console.log('Artifact identity and SHA-256 recorded; no credential values included.');
}
module.exports = {createManifest};
