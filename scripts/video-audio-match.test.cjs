const {test}=require('node:test');const assert=require('node:assert/strict');const fs=require('node:fs');const path=require('node:path');const root=path.join(__dirname,'../Honorable-mobile-app-main/mobile-react-native/android/app/src/main/java/com/honorablemobile');const read=n=>fs.readFileSync(path.join(root,n),'utf8');const source=read('HonorableVideoEditorActivity.kt');const method=source.slice(source.indexOf('    private fun matchClipAudio(){'),source.indexOf('    private fun bilingualCaptions(){'));const core=read('VideoAudioMatch.kt');
test('guarded reference picker uses the real bounded local decoder and existing lifecycle cancellation slot',()=>{
 assert.ok(source.includes('t("Match clip audio level"){matchClipAudio()}'));
 for(const s of ['captureCaptionReview()?:return','VideoAudioMatch.eligible(clip)','it!=index&&VideoAudioMatch.eligible(clips[it])','canUseInspectorReview(dialog,captured)&&project===current','quietAudioCancellation=cancellation;importing=true','VideoQuietAudio.scan(appContext,selectedUri,clip.startMs,clip.endMs)','VideoQuietAudio.scan(appContext,referenceUri,ref.startMs,ref.endMs)','quietAudioGeneration','progress.setOnCancelListener{cancellation.set(true)}'])assert.ok(method.includes(s),s);
 assert.ok(source.includes('quietAudioGeneration++;quietAudioCancellation?.set(true)'));assert.doesNotMatch(method,/Http|Clipboard|openOutputStream/);
 const decoder=read('VideoQuietAudio.kt');for(const s of ['endMs-startMs<=120_000','30_000_000_000L','decoder?.release()','extractor.release()'])assert.ok(decoder.includes(s),s);
});
test('full result approval precedes all callback messages and one-use gain application',()=>{
 const callback=method.slice(method.indexOf('                runOnUiThread{'));const guard=callback.indexOf('if(project!==current||quietAudioGeneration!=generation||!canApplyCaptionReview(captured))return@runOnUiThread');assert.ok(guard>=0);
 assert.ok(callback.indexOf('if(cancellation.get())')>guard);assert.ok(callback.indexOf('status.text=')>guard);assert.ok(callback.indexOf('result.onFailure')>guard);assert.ok(callback.indexOf('result.onFailure')>callback.indexOf('progress.dismiss()'));
 for(const s of ['check(project===current)','commitBatchReview(review,request,captured)','request.propose(review,plan.clips)','showBatchStage(review,request,captured)','quietAudioCancellation===cancellation','clip.sourceId==ref.sourceId&&clip.startMs==ref.startMs&&clip.endMs==ref.endMs','not LUFS','Target cannot be reached'])assert.ok(method.includes(s),s);
});
test('gain proposal binds both source windows and coverage, keeps mute choices, caps gain and changes volume only',()=>{
 for(const s of ['clip.volume>0f','level.sourceId==value.sourceId','level.startMs==value.startMs&&level.endMs==value.endMs','level.knownMs*100>=span*95','rms>.001','target>.001','other*ref.volume','minOf(1.0,wanted).toFloat()','gain!=clip.volume','if(i==index)value.copy(volume=gain)else value'])assert.ok(core.includes(s),s);
 assert.doesNotMatch(core,/copy\(startMs|copy\(captions|copy\(speed|copy\(sourceId|contentResolver|Http/);
});
