const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.join(__dirname, '../Honorable-mobile-app-main/mobile-react-native');
const read = name => fs.readFileSync(path.join(root, name), 'utf8');
const bridge = read('android/app/src/main/java/com/honorablemobile/LibraryIntegrityBridge.kt');
const native = read('android/app/src/main/java/com/honorablemobile/HonorableNativePackage.kt');
const ui = read('src/library/LibraryIntegrity.tsx');

test('baseline metadata uses an owner-hashed encrypted preserving record and canonical bounds', () => {
  assert.match(bridge, /SecureAccountStore\(context,"noble-library-integrity-\$digest"\)/);
  assert.match(bridge, /MessageDigest\.getInstance\("SHA-256"\)\.digest\(owner/);
  assert.match(bridge, /val saved=store\.readPreserving\(\)\?:return null/);
  assert.match(bridge, /val value=saved\.opt\("baselines"\)/);
  assert.match(bridge, /require\(value is String/);
  assert.match(bridge, /Base64\.encodeToString\(it,Base64\.NO_WRAP\)==value/);
  assert.doesNotMatch(bridge, /\.clear\(|getSharedPreferences|\.delete\(|Log\.|println/);
});

test('recording hashes selected originals natively and rechecks bindings before atomic metadata writes', () => {
  const create = bridge.slice(bridge.indexOf('if(action=="create")'), bridge.indexOf('} else if(action=="delete")'));
  assert.ok(create.indexOf('catalog.requireNew(name,chosen)') < create.indexOf('LibraryDuplicateBridge.scan('));
  assert.match(create, /minimumItems=1/);
  assert.ok(create.indexOf('LibraryDuplicateBridge.scan(') < create.indexOf('LibraryIntegrityBaseline.create('));
  assert.match(create, /catalog=store\.save\(baseline,::bindings\)/);
  assert.match(create, /it\.uri==entry\.uri&&it\.kind\.name==entry\.kind/);
  assert.doesNotMatch(create, /getString\("sha256"\)|getDouble\("bytes"\)/);
});

test('verification keeps the baseline unchanged and checks both coverage and current stored identity', () => {
  const verify = bridge.slice(bridge.indexOf('else if(action=="verify")'), bridge.indexOf('\n        guard()\n        return Arguments'));
  assert.match(verify, /val absent=missing\(\)/);
  assert.match(verify, /filter\{it\.original\.id !in absent}/);
  assert.match(verify, /check\(missing\(\)==absent\)/);
  assert.match(verify, /catalog=store\.load\(::guard\)/);
  assert.match(verify, /latest\.entries==baseline\.entries&&latest\.name==baseline\.name&&latest\.recordedAt==baseline\.recordedAt/);
  assert.match(verify, /LibraryIntegrityVerification\.compare\(baseline,report,absent\)/);
  assert.doesNotMatch(verify, /store\.save\(|store\.remove\(|\.write\(/);
});

test('integrity reuses the owned read slot and full foreground/account/shelf admission', () => {
  assert.match(native, /fun libraryIntegrity\([^\n]+\)=runLibraryRead\(requestId,promise\)/);
  assert.match(bridge, /request\.requireActive\(\)/);
  assert.match(bridge, /LifecycleState\.RESUMED/);
  assert.match(bridge, /action=="list"\|\|owner==expectedOwner/);
  assert.match(ui, /shelf\.current === data/);
  assert.match(ui, /job\.current === request/);
  assert.match(ui, /view\.current === viewToken/);
  assert.match(ui, /latest\.canStart\(\)/);
  assert.doesNotMatch(ui, /fetch\(|XMLHttpRequest|setPrimaryClip|AsyncStorage/);
});
