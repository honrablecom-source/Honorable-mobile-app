const {test}=require('node:test');const assert=require('node:assert/strict');const fs=require('node:fs');const path=require('node:path');const root=path.join(__dirname,'../Honorable-mobile-app-main/mobile-react-native/android/app/src/main/java/com/honorablemobile');const read=n=>fs.readFileSync(path.join(root,n),'utf8');const source=read('HonorableVideoEditorActivity.kt');const method=source.slice(source.indexOf('    private fun playbackCuts(){'),source.indexOf('    private fun captionWorksheet(){'));const core=read('VideoPlaybackCuts.kt');
test('project tool reaches guarded input before any widgets and clears pasted text',()=>{
 assert.ok(source.includes('t("Split at pasted playback times"){playbackCuts()}'));
 assert.ok(method.indexOf('captureCaptionReview()?:return')<method.indexOf('val panel='));assert.ok(method.indexOf('project?:return')<method.indexOf('val panel='));
 assert.ok(method.includes('VideoBatchReview(snapshot,music)'));assert.ok(method.includes('canUseBatchStage(dialog,request,captured)'));assert.ok(method.includes('VideoPlaybackCuts.prepare(snapshot,field.text.toString(),music)'));assert.ok(method.includes('field.text?.clear()'));assert.ok(method.includes('ScrollView(this)'));
});
test('review discloses precise cuts and retains immutable one-use batch approval',()=>{
 for(const s of ['result.cuts.joinToString','cut.requestedUs','cut.actualUs','cut.sourceTimeMs','result.durationDriftUs','edge/audio fades repeat','request.propose(dialog,result.clips)','check(project===current)','commitBatchReview(dialog,request,captured)','showBatchStage(dialog,request,captured)'])assert.ok(method.includes(s),s);
 const commit=source.slice(source.indexOf('    private fun commitBatchReview('),source.indexOf('    private fun batchOptions('));assert.ok(commit.includes('canUseBatchStage'));assert.ok(commit.includes('request.apply'));assert.ok(commit.includes('activeCaptionReviewDialog(dialog,review)'));
 assert.ok(source.includes('dialog.isShowing&&canApplyCaptionReview(review)'));assert.ok(source.includes('batchReviewRequest?.close();batchReviewRequest=null;batchReviewDialog=null'));
});
test('pure proposals preserve contiguous original ranges and use authoritative duration and recovery limits',()=>{
 for(const s of ['VideoPlaybackTime.durationUs(clips)','times.zipWithNext()','clip.copy(startMs=from,endMs=to,captions=','it.startMs<to&&it.endMs>from','out.size<=1000','MAX_PROJECT_CUES','MAX_PROJECT_TEXT_BYTES','VideoDraftCodec.requireRecoverableCaptions(out,music?.loop==false,music?.hasExcerpt==true)','VideoPlaybackTime.durationUs(out)-duration'])assert.ok(core.includes(s),s);
 assert.doesNotMatch(core,/openOutputStream|Http|contentResolver|setTitle\(/);assert.doesNotMatch(method,/setPrimaryClip|openOutputStream|Http/);
});
