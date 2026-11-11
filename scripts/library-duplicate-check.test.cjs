const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const base = path.join(__dirname, '../Honorable-mobile-app-main/mobile-react-native');
const read = file => fs.readFileSync(path.join(base, file), 'utf8');
const native = read('android/app/src/main/java/com/honorablemobile/HonorableNativePackage.kt');
const bridge = read('android/app/src/main/java/com/honorablemobile/LibraryDuplicateBridge.kt');
const ui = read('src/library/LibraryDuplicateCheck.tsx');

test('comparison reads the explicitly chosen exact bindings and revalidates before reporting', () => {
  assert.match(bridge, /items\.size\(\) in 2\.\.20/);
  assert.match(bridge, /catalog\[entry\.id]\?\.let\{it\.uri==entry\.uri&&it\.kind\.name==entry\.kind}==true/);
  assert.equal((bridge.match(/\n        requireBindings\(\)/g) || []).length, 2);
  assert.match(bridge, /LifecycleState\.RESUMED/);
  assert.match(bridge, /VideoPrivateOwner\.namespace\(context,EditorAccessMode\.ACCOUNT\)==owner/);
  assert.ok(bridge.lastIndexOf('requireBindings()') < bridge.indexOf('return Arguments.createMap()'));
});

test('provider reads are read-only, cancellable and owned until stream closure', () => {
  assert.match(bridge, /openAssetFileDescriptor\(android\.net\.Uri\.parse\(item\.uri\),"r",request\.signal\)/);
  assert.match(bridge, /request\.bind\(stream\)/);
  assert.match(bridge, /request\.detach\(stream\)/);
  assert.match(bridge, /input\.getAndSet\(null\)/);
  assert.match(bridge, /signal\.cancel\(\)/);
  assert.doesNotMatch(bridge, /openOutputStream|\.delete\(|Http|URL\(|accountSession\.action|embeddings|Bitmap|write\(/);
});

test('one native request owns cancellation and releases its own slot on every outcome', () => {
  assert.match(native, /libraryDuplicateRequest\.compareAndSet\(null,request\)/);
  assert.match(native, /finally\{request\.cancel\(\);libraryDuplicateRequest\.compareAndSet\(request,null\)}/);
  assert.match(native, /takeIf\{it\.id==requestId}\?\.cancel\(\)/);
  for (const method of ['onHostPause', 'onHostDestroy']) assert.ok(native.includes(`override fun ${method}(){libraryDuplicateRequest.get()?.cancel()`));
  assert.match(native, /override fun invalidate\(\) \{ libraryDuplicateRequest\.get\(\)\?\.cancel\(\);(?:photoDifferencePreviews\.clear\(\);)?work\.shutdownNow\(\)/);
});

test('UI snapshots only metadata for native transport and admits through the shared parent owner', () => {
  assert.match(ui, /latest\.canStart\(\)/);
  assert.match(ui, /compareLibraryOriginals\(\s*request\.items,\s*request\.id,?\s*\)/);
  assert.match(ui, /job\.current === request/);
  assert.match(ui, /ownerRef\.current === request\.owner/);
  assert.match(ui, /view\.current === viewToken/);
  assert.doesNotMatch(ui, /fetch\(|XMLHttpRequest|setPrimaryClip|AsyncStorage/);
});
