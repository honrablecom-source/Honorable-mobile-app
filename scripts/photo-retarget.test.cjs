const {test}=require('node:test');const assert=require('node:assert/strict');const fs=require('node:fs');const path=require('node:path');const root=path.join(__dirname,'../Honorable-mobile-app-main/mobile-react-native');const read=p=>fs.readFileSync(path.join(root,p),'utf8');const bridge=read('android/app/src/main/java/com/honorablemobile/PhotoPixelDifferenceBridge.kt');const native=read('android/app/src/main/java/com/honorablemobile/HonorableNativePackage.kt');
test('retarget admits one current original, samples it and stages actual narrowed output dimensions',()=>{
 for(const s of ['if(retarget>0)require(items.size()==1)','if(retarget>0)PhotoPixelDifference.plan(metadata[0],metadata[0])','PhotoRetarget.percent(plan.size,frames[0],retarget,::active)','plan.copy(size=PhotoPixelDifference.Size(carved.width,carved.height))','Bitmap.createBitmap(result.pixels,outputPlan.size.width,outputPlan.size.height','File.createTempFile("noble-retarget-",".png",directory)','carved?.removedColumns?:0,plan.sampled','putBoolean("sampled",decodedSampled)'])assert.ok(bridge.includes(s),s);
 assert.ok(bridge.includes('ImportedPhotoOriginals.EXTRA'));assert.doesNotMatch(bridge,/openOutputStream|Http|URL\(/);
});
test('new route validates bounded percentage and retains request/account/lease ownership',()=>{
 const route=native.slice(native.indexOf('@ReactMethod fun preparePhotoRetarget'),native.indexOf('@ReactMethod fun releasePhotoDifference'));
 for(const s of ['runLibraryRead','percent.isFinite()','percent in 1.0..12.0','retarget=percent.toInt()','request.requireActive()','requireCurrent','photoDifferencePreviews.offer(preview)','preview.dispose()'])assert.ok(route.includes(s),s);
});
test('reviewed single-photo UI binds mode/default/percent and validates operation and removed columns',()=>{
 const ui=read('src/library/PhotoPixelDifference.tsx');for(const s of ['narrowingPercent(threshold)','honorableNative.preparePhotoRetarget(',"result.operation !== 'retarget'",'Number.isSafeInteger(result.removedColumns)',"setThreshold(retarget ? '10' : '0')",'preview.removedColumns',"retarget ? ':retarget' : ''"] )assert.ok(ui.includes(s),s);
 assert.ok(read('src/screens/MemoriesScreen.tsx').includes('<PhotoRetargetPanel'));assert.doesNotMatch(ui,/fetch\(|XMLHttpRequest/);
});
