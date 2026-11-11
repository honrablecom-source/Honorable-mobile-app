const {test}=require('node:test');const assert=require('node:assert/strict');const fs=require('node:fs');const path=require('node:path');const root=path.join(__dirname,'../Honorable-mobile-app-main/mobile-react-native/android/app/src/main/java/com/honorablemobile');const read=n=>fs.readFileSync(path.join(root,n),'utf8');const source=read('HonorableVideoEditorActivity.kt');const method=source.slice(source.indexOf('    private fun captionWorksheet(){'),source.indexOf('    private var batchReviewRequest:'));const core=read('VideoCaptionWorksheet.kt');
test('worksheet has guarded menu/input, explicit local copy and no automatic clipboard reads',()=>{
 assert.ok(source.includes('t("Caption translation worksheet"){captionWorksheet()}'));
 assert.ok(method.indexOf('captureCaptionReview()?:return')<method.indexOf('val panel='));assert.ok(method.includes('VideoCaptionWorksheet.export(snapshot)'));
 const copy=method.slice(method.indexOf('BUTTON_NEUTRAL).setOnClickListener'),method.indexOf('BUTTON_POSITIVE).setOnClickListener'));
 for(const s of ['canUseBatchStage(dialog,request,captured)','project!==current','ClipData.newPlainText','original','IS_SENSITIVE','setPrimaryClip(data)'])assert.ok(copy.includes(s),s);
 assert.equal((method.match(/setPrimaryClip\(/g)||[]).length,1);assert.doesNotMatch(method,/getPrimaryClip|Http|URL\(|contentResolver|openOutputStream/);
});
test('all replacements are reviewed before one-use full-project approval and private text clears',()=>{
 for(const s of ['VideoCaptionWorksheet.prepare(snapshot,field.text.toString(),music)','request.propose(dialog,result.clips)','check(project===current)','commitBatchReview(dialog,request,captured)','showBatchStage(dialog,request,captured)','field.text?.clear()','body.text=""','cue.startMs','cue.endMs','next.text','outside trimmed visibility'])assert.ok(method.includes(s),s);
 assert.ok(method.includes('ScrollView(this)'));assert.ok(source.includes('dialog.isShowing&&canApplyCaptionReview(review)'));
});
test('worksheet identity binds structured cue/timing/source data and cannot replace timing or styles',()=>{
 for(const s of ['MessageDigest.getInstance("SHA-256")','out.writeInt(data.size)','text(clip.sourceId)','out.writeLong(clip.startMs)','out.writeLong(clip.endMs)','out.writeFloat(clip.speed)','text(clip.captionSourceUri?:"")','out.writeLong(cue.startMs)','out.writeLong(cue.endMs)','text(cue.text)','values.keys==expected','!values.containsKey(key)','cue.copy(text=text)','clip.copy(captions=cues)','VideoDraftCodec.requireRecoverableCaptions(result,music?.loop==false,music?.hasExcerpt==true)'])assert.ok(core.includes(s),s);
 assert.doesNotMatch(core,/copy\(startMs|copy\(endMs|copy\(speed|copy\(sourceId|copy\(grade|Clipboard|Http/);
});
