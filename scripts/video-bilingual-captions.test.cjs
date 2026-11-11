const {test}=require('node:test');const assert=require('node:assert/strict');const fs=require('node:fs');const path=require('node:path');const root=path.join(__dirname,'../Honorable-mobile-app-main/mobile-react-native/android/app/src/main/java/com/honorablemobile');const read=n=>fs.readFileSync(path.join(root,n),'utf8');const source=read('HonorableVideoEditorActivity.kt');const method=source.slice(source.indexOf('    private fun bilingualCaptions(){'),source.indexOf('    private fun clipReplacement(){'));const core=read('VideoBilingualCaptions.kt');
test('Titles/audio tool requires current source and first track before opening a bounded second-track editor',()=>{
 assert.ok(source.includes('t("Combine bilingual captions"){bilingualCaptions()}'));
 for(const s of ['captureCaptionReview()?:return','if(clip.captions.isEmpty())','captured.durations[clip.sourceId]','VideoBatchReview(snapshot,music)','LengthFilter(VideoCaptions.MAX_SRT_BYTES)','canUseBatchStage(editor,request,captured)||project!==current','VideoBilingualCaptions.prepare(snapshot,index,duration,field.text.toString(),music)'])assert.ok(method.includes(s),s);
 assert.ok(method.indexOf('captureCaptionReview()?:return')<method.indexOf('val panel='));assert.doesNotMatch(method,/Http|contentResolver|Clipboard|openOutputStream/);
});
test('all combined cues and flattening limits are reviewed before one-use exact-project application',()=>{
 for(const s of ['next[index].captions.joinToString','source boundaries','one flattened track','outside current trim visibility','check(project===current)','commitBatchReview(review,request,captured)','request.propose(review,next)','showBatchStage(review,request,captured)','showBatchStage(editor,request,captured)','body.text=""','field.text?.clear()','selectedClip=index'])assert.ok(method.includes(s),s);
 assert.ok(source.includes('dialog.isShowing&&canApplyCaptionReview(review)'));
});
test('production merge uses strict source-time parsing and union boundaries with retained provenance and no silent drops',()=>{
 for(const s of ['VideoCaptions.parseSrt(bytes,sourceDurationMs)','bytes.toString(Charsets.UTF_8)==srt','distinct().sorted()','edges.zipWithNext()','listOfNotNull(first?.text,second?.text).joinToString("\\n")','last.endMs==from&&last.text==text','VideoCaptions.validate(merged)','value.copy(captions=merged.toList())','MAX_PROJECT_CUES','MAX_PROJECT_TEXT_BYTES','VideoDraftCodec.requireRecoverableCaptions(result,music?.loop==false,music?.hasExcerpt==true)'])assert.ok(core.includes(s),s);
 assert.doesNotMatch(core,/copy\(startMs|copy\(endMs=to,captions|captionSourceUri=|take\(200\)|drop\(|Http/);
});
