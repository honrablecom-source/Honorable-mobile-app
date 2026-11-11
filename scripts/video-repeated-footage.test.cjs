const {test}=require('node:test');const assert=require('node:assert/strict');const fs=require('node:fs');const path=require('node:path');const root=path.join(__dirname,'../Honorable-mobile-app-main/mobile-react-native/android/app/src/main/java/com/honorablemobile');const read=n=>fs.readFileSync(path.join(root,n),'utf8');const source=read('HonorableVideoEditorActivity.kt');const method=source.slice(source.indexOf('    private fun repeatedFootage(){'),source.indexOf('    private fun playbackCuts(){'));const core=read('VideoRepeatedFootage.kt');
test('guarded project tool runs source-range metadata inspection before building widgets',()=>{
 assert.ok(source.includes('t("Repeated source footage"){repeatedFootage()}'));assert.ok(method.indexOf('captureCaptionReview()?:return')<method.indexOf('AlertDialog.Builder'));assert.ok(method.includes('VideoRepeatedFootage.inspect(clips)'));assert.ok(method.includes('project===current&&canRequestTimelineCaptionImport()'));assert.ok(method.includes('if(!canApplyCaptionReview(review))return'));
 assert.doesNotMatch(method,/contentResolver|Http|setPrimaryClip|openOutputStream/);
});
test('every navigation callback rechecks current ownership and consumes before source/player selection',()=>{
 assert.ok(method.includes('canUseInspectorReview(list,review)||project!==current||current.segments!=clips'));
 const jump=method.slice(method.indexOf('fun jump('),method.indexOf('                dialog=android.app.AlertDialog.Builder'));
 assert.ok(jump.includes('canUseInspectorReview(dialog,review)||project!==current||current.segments!=clips'));
 const consume=jump.indexOf('inspectorReviewDialog=null;invalidateCaptionReviews()');assert.ok(consume>=0&&consume<jump.indexOf('selectedClip=occurrence.clipIndex'));
 for(const s of ['pendingSeekMs=occurrence.sourceMs','video.seekTo(occurrence.sourceMs.toInt())','playheadUs=occurrence.outputUs','jump(pair.first)','jump(pair.second)','canUseInspectorReview(info,review)&&project===current&&current.segments==clips','canUseInspectorReview(dialog,review)&&project===current&&current.segments==clips'])assert.ok(method.includes(s),s);
 assert.doesNotMatch(method,/applyDraft|\.trim\(|\.split\(|\.undo\(/);
});
test('bounded report accounts for all overlaps and discloses omitted pairs and source-only limitations',()=>{
 for(const s of ['const val LIMIT=100','PriorityQueue(LIMIT,order.reversed())','if(a.sourceId!=b.sourceId)continue','if(from>=to)continue','total++','best.size==LIMIT','best.toList().sortedWith(order)','starts[index]+((from-clip.startMs)*1000.0/clip.speed).toLong()'])assert.ok(core.includes(s),s);
 for(const s of ['result.totalPairs','result.omittedPairs','not visual duplicate detection','Different speed, color, crop','overlapPlaybackUs','Jump to first','Jump to second'])assert.ok(method.includes(s),s);
 assert.doesNotMatch(core,/VideoEditProject|contentResolver|Uri|File\(|Http/);
});
