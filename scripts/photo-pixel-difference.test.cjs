const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.join(__dirname, '../Honorable-mobile-app-main/mobile-react-native');
const read = file => fs.readFileSync(path.join(root, file), 'utf8');
const bridge = read('android/app/src/main/java/com/honorablemobile/PhotoPixelDifferenceBridge.kt');
const native = read('android/app/src/main/java/com/honorablemobile/HonorableNativePackage.kt');
const ui = read('src/library/PhotoPixelDifference.tsx');

test('exact two-photo admission preserves account, foreground, source bindings and original protection', () => {
  assert.match(bridge, /items\.size\(\)==2/);
  assert.match(bridge, /originals\.all\{it\.kind=="IMAGE"}/);
  assert.match(bridge, /it\.uri==e\.uri&&it\.kind\.name==e\.kind/);
  assert.match(bridge, /VideoPrivateOwner\.namespace\(context,EditorAccessMode\.ACCOUNT\)==owner/);
  assert.match(bridge, /LifecycleState\.RESUMED/);
  assert.match(bridge, /ImportedPhotoOriginals\.validate\(originals\.map\{it\.uri}\)/);
});

test('decode uses owned read-only bounded streams and the same tested orientation/pixel kernel', () => {
  assert.match(bridge, /openAssetFileDescriptor\(Uri\.parse\(original\.uri\),"r",request\.signal\)/);
  assert.match(bridge, /budget\.wrap\(input\)/);
  assert.match(bridge, /limited\.finish\(\)/);
  assert.match(bridge, /request\.bind\(input\)/);
  assert.match(bridge, /request\.detach\(input\)/);
  assert.match(bridge, /inSampleSize=plan\.sample;inScaled=false;inPreferredConfig=Bitmap\.Config\.ARGB_8888/);
  assert.match(bridge, /PhotoPixelDifference\.normalize\(/);
  assert.match(bridge, /PhotoPixelDifference\.compare\(/);
  assert.doesNotMatch(bridge, /openOutputStream|Http|URL\(|accountSession\.action|embeddings/);
});

test('PNG staging is a fresh bounded private file and ownership transfers before workspace opening', () => {
  assert.match(bridge, /directory\.canonicalFile==File\(context\.cacheDir\.canonicalFile,"created-images"\)/);
  assert.match(bridge, /File\.createTempFile\("noble-pixel-difference-","\.png",directory\)/);
  assert.match(bridge, /file\.length\(\) in 1\.\.OUTPUT_LIMIT/);
  assert.match(bridge, /putStringArrayListExtra\(ImportedPhotoOriginals\.EXTRA/);
  assert.match(bridge, /EditorAccessMode\.GUEST_BASIC\.name/);
  assert.match(bridge, /beforeLaunch\(\);context\.startActivity\(intent\)/);
  const open = native.slice(native.indexOf('@ReactMethod fun openPhotoDifference('), native.indexOf('    private fun runLibraryRead('));
  assert.ok(open.indexOf('photoDifferencePreviews.take(token)') < open.indexOf('PhotoPixelDifferenceBridge.open('));
  assert.match(open, /request\.requireActive\(\)/);
  assert.match(native, /fun releasePhotoDifference\(token:String\)\{photoDifferencePreviews\.release\(token\)}/);
  for (const method of ['onHostPause', 'onHostDestroy']) assert.ok(native.includes(`override fun ${method}(){libraryDuplicateRequest.get()?.cancel();photoDifferencePreviews.clear()}`));
});

test('UI owns source selection and late PNG cleanup and never renders originals as the preview', () => {
  assert.match(ui, /selectionRef\.current === request\.selection/);
  assert.match(ui, /job\.current === request/);
  assert.match(ui, /ownerRef\.current === request\.owner/);
  assert.match(ui, /release\(result\.token\)/);
  assert.match(ui, /open && preview/);
  assert.match(ui, /source=\{\{ uri: preview\.previewUri }}/);
  assert.match(ui, /view\.current === viewToken/);
  assert.doesNotMatch(ui, /fetch\(|XMLHttpRequest|setPrimaryClip|AsyncStorage/);
});
