const {test}=require('node:test');const assert=require('node:assert/strict');const fs=require('node:fs');const path=require('node:path');const root=path.join(__dirname,'../Honorable-mobile-app-main/mobile-react-native/android/app/src/main/java/com/honorablemobile');const read=n=>fs.readFileSync(path.join(root,n),'utf8');const source=read('HonorableVideoEditorActivity.kt');const method=source.slice(source.indexOf('    private fun clipReplacement(){'),source.indexOf('    private fun repeatedFootage(){'));const core=read('VideoClipReplacement.kt');
test('guarded Picture tool selects only already-added eligible source windows',()=>{
 assert.ok(source.includes('t("Replace clip footage"){clipReplacement()}'));
 assert.ok(method.indexOf('captureCaptionReview()?:return')<method.indexOf('val panel='));
 for(const s of ['val durations=captured.durations','VideoClipReplacement.targets(snapshot,index,durations)','canUseBatchStage(dialog,request,captured)&&project===current','VideoCaptions.seconds(field.text.toString())','VideoClipReplacement.propose(snapshot,index,durations,target.id','sources already added to this project'])assert.ok(method.includes(s),s);
 assert.doesNotMatch(method,/contentResolver|Http|setPrimaryClip|openOutputStream|importVideo\(/);
});
test('three tracked stages disclose lost annotations and preserve one-use exact-project approval',()=>{
 for(const s of ['VideoBatchReview(snapshot,music)','showBatchStage(dialog,request,captured)','showBatchStage(editor,request,captured)','showBatchStage(review,request,captured)','canUseBatchStage(editor,request,captured)||project!==current','check(project===current)','commitBatchReview(review,request,captured)','request.propose(review,next)','field.text?.clear()','selectedClip=index','subtitle binding','resets source-anchored motion','Undo restores'])assert.ok(method.includes(s),s);
 assert.ok(source.includes('dialog.isShowing&&canApplyCaptionReview(review)'));
});
test('production proposal requires a different known source, bounds whole span, clears old source bindings and proves unchanged duration',()=>{
 for(const s of ['id!=clip.sourceId&&d>=span','startMs in 0..target.latestStartMs','sourceId=targetId','startMs=startMs,endMs=startMs+(old.endMs-old.startMs)','captions=emptyList(),captionSourceUri=null,motion=VideoMotion()','if(i==index)replacement else clip','VideoPlaybackTime.durationUs(result)==VideoPlaybackTime.durationUs(clips)','VideoDraftCodec.requireRecoverableCaptions(result,music?.loop==false,music?.hasExcerpt==true)'])assert.ok(core.includes(s),s);
 assert.doesNotMatch(core,/copy\([^\n]*(?:speed=|grade=|volume=|layout=|title=)|Http|Uri\.parse|File\(/);
});
