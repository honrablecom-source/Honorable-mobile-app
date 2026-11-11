const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const activity = fs.readFileSync(path.join(__dirname, '../Honorable-mobile-app-main/mobile-react-native/android/app/src/main/java/com/honorablemobile/HonorableVideoEditorActivity.kt'), 'utf8');
function method(name) {
  const start = activity.indexOf(`    private fun ${name}(`);
  assert.ok(start >= 0, name);
  const end = activity.indexOf('\n    private ', start + 1);
  return activity.slice(start, end < 0 ? undefined : end);
}

test('EDL is discoverable and its chooser requires the current tracked full review', () => {
  assert.ok(activity.includes('t("Copy CMX3600 cut list"){videoEdl()}'));
  const chooser = method('videoEdl');
  assert.ok(chooser.indexOf('captureCaptionReview()') < chooser.indexOf('setItems('));
  assert.ok(chooser.indexOf('canUseInspectorReview(dialog,review)||project!==current') < chooser.indexOf('reviewVideoEdl(review,current,rates[choice])'));
  assert.ok(chooser.includes('showInspectorReview(dialog,review)'));
});

test('EDL formatter receives only bounded cut metadata and review explains omissions', () => {
  const review = method('reviewVideoEdl');
  assert.ok(review.indexOf('project!==current||!canApplyCaptionReview(review)') < review.indexOf('VideoEdl.prepare('));
  assert.match(review, /VideoEdl\.Cut\(it\.sourceId,it\.startMs,it\.endMs,it\.speed\)/);
  assert.doesNotMatch(review, /sourceUris\(|\.uri|\.title|\.captions/);
  for (const disclosure of ['rounded inward', 'embedded camera timecode is not used', 'Relink each reel', 'soundtrack are omitted', 'compatibility with other editors is unverified', 'clipboard sync']) {
    assert.ok(review.includes(disclosure), disclosure);
  }
});

test('clipboard write requires existing one-use inspector approval and requests sensitive handling', () => {
  const review = method('reviewVideoEdl');
  const commit = review.indexOf('commitInspectorReview(dialog,review,current,index,clip){');
  const service = review.indexOf('getSystemService(');
  const sensitive = review.indexOf('putBoolean("android.content.extra.IS_SENSITIVE",true)');
  const write = review.indexOf('clipboard.setPrimaryClip(data)');
  assert.ok(commit >= 0 && service > commit && sensitive > service && write > sensitive);
  assert.match(review, /showCaptionReview\(dialog,review\)\{prepared=null;body\.text="";if\(inspectorReviewDialog===dialog\)inspectorReviewDialog=null}/);
  assert.doesNotMatch(review, /applyDraft\(|setCaptions\(|setSoundtrack\(|\.undo\(|\.redo\(/);
  const helper = method('commitInspectorReview');
  assert.ok(helper.includes('if(inspectorReviewDialog!==dialog)return false'));
  assert.ok(helper.indexOf('canUseInspectorReview(dialog,review)') < helper.indexOf('commit();inspectorReviewDialog=null'));
});
