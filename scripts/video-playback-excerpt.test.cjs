const {test}=require('node:test');const assert=require('node:assert/strict');const fs=require('node:fs');const path=require('node:path');const root=path.join(__dirname,'../Honorable-mobile-app-main/mobile-react-native/android/app/src/main/java/com/honorablemobile');const read=n=>fs.readFileSync(path.join(root,n),'utf8');const source=read('HonorableVideoEditorActivity.kt');const method=source.slice(source.indexOf('    private fun playbackExcerpt(){'),source.indexOf('    private fun matchClipAudio(){'));const core=read('VideoPlaybackExcerpt.kt');
test('guarded Project tool reviews a bounded whole-timeline playback interval',()=>{
 assert.ok(source.includes('t("Keep playback excerpt"){playbackExcerpt()}'));
 for(const s of ['captureCaptionReview()?:return','VideoBatchReview(snapshot,music)','Edited playback start seconds','Edited playback end seconds','VideoPlaybackExcerpt.prepare(snapshot,from.text.toString(),to.text.toString(),music)','canUseBatchStage(editor,request,captured)||project!==current'])assert.ok(method.includes(s),s);
 assert.ok(method.indexOf('captureCaptionReview()?:return')<method.indexOf('val panel='));assert.doesNotMatch(method,/Http|Clipboard|contentResolver|openOutputStream/);
});
test('complete source mapping/music restart/fade disclosures precede exact-project one-use approval',()=>{
 for(const s of ['result.pieces.joinToString','piece.originalIndex','piece.originalFromUs','piece.originalToUs','music restarts','edge/audio fades repeat','check(project===current)','commitBatchReview(review,request,captured)','request.propose(review,result.clips)','showBatchStage(review,request,captured)','showBatchStage(editor,request,captured)','from.text?.clear();to.text?.clear()','body.text=""'])assert.ok(method.includes(s),s);
 assert.ok(source.includes('dialog.isShowing&&canApplyCaptionReview(review)'));
});
test('inward source edges preserve full/tiny interior clips, intersections and authoritative recovery limits',()=>{
 for(const s of ['VideoPlaybackTime.durationUs(clips)','if(lo==offset&&hi==next)pieces+=Piece(index,clip','ceil((lo-offset)*clip.speed.toDouble()/1000)','floor((hi-offset)*clip.speed.toDouble()/1000)','if(hi==next)clip.endMs','>=100000','captions=clip.captions.filter{it.startMs<b&&it.endMs>a}','actualFrom>=start&&actualTo<=end','result!=clips','VideoDraftCodec.requireRecoverableCaptions(result,music?.loop==false,music?.hasExcerpt==true)'])assert.ok(core.includes(s),s);
 assert.doesNotMatch(core,/copy\([^\n]*(speed=|volume=|grade=|motion=|captionSourceUri=)|Http|File\(/);
});
