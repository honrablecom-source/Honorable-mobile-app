const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.join(__dirname, '../Honorable-mobile-app-main/mobile-react-native');
const native = fs.readFileSync(path.join(root, 'android/app/src/main/java/com/honorablemobile/HonorableNativePackage.kt'), 'utf8');
const start = native.indexOf('@ReactMethod fun libraryCollections(');
const bridge = native.slice(start, native.indexOf('@ReactMethod', start + 1));
const record = fs.readFileSync(path.join(root, 'android/app/src/main/java/com/honorablemobile/LibraryCollectionStore.kt'), 'utf8');

test('native collections use worker admission with current account and foreground checks', () => {
  assert.ok(start > 0);
  assert.match(bridge, /work\.execute\{guarded\(promise\)/);
  assert.match(bridge, /VideoPrivateOwner\.namespace\(context,EditorAccessMode\.ACCOUNT\)/);
  assert.match(bridge, /lifecycleState==com\.facebook\.react\.common\.LifecycleState\.RESUMED/);
  assert.match(bridge, /action=="list"\|\|owner==namespace/);
  assert.ok(bridge.indexOf('requireCurrent()\n        val store=') > 0);
  assert.match(bridge, /store\.load\(::requireCurrent\)/);
  assert.match(bridge, /store\.remove\(id,::requireCurrent\)/);
  assert.ok(bridge.lastIndexOf('requireCurrent()') < bridge.indexOf('Arguments.createMap()'));
});

test('save rechecks exact Library identities under the guarded model-store transaction', () => {
  const create = bridge.slice(bridge.indexOf('store.create(name,chosen){'), bridge.indexOf('else->store.remove('));
  assert.match(create, /requireCurrent\(\)/);
  assert.match(create, /database\.records\(\)\.associateBy\{it\.id\.toString\(\)}/);
  assert.ok(create.includes('originals[entry.id]?.let{it.uri==entry.uri&&it.kind.name==entry.kind}==true'));
  assert.doesNotMatch(bridge, /contentResolver|openInputStream|delete\(|Http|URL\(|accountSession\.action|embeddings|coordinator/);
});

test('record uses owner-separated encrypted preserving storage with bounded canonical encoding', () => {
  assert.match(record, /MessageDigest\.getInstance\("SHA-256"\)\.digest\(owner/);
  assert.match(record, /SecureAccountStore\(context,"noble-library-collections-\$digest"\)/);
  assert.match(record, /store\.readPreserving\(\)/);
  assert.match(record, /bytes\.size in 1\.\.LibraryCollectionCodec\.MAX_BYTES/);
  assert.match(record, /Base64\.encodeToString\(bytes,Base64\.NO_WRAP\)==text/);
  assert.doesNotMatch(record, /store\.clear\(|\.delete\(|getSharedPreferences|print|Log\./);
});
