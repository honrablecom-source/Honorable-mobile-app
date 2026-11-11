const {test}=require('node:test');const assert=require('node:assert/strict');const fs=require('node:fs');const path=require('node:path');const root=path.join(__dirname,'../Honorable-mobile-app-main/mobile-react-native');const read=p=>fs.readFileSync(path.join(root,p),'utf8');const bridge=read('android/app/src/main/java/com/honorablemobile/PhotoPixelDifferenceBridge.kt');const native=read('android/app/src/main/java/com/honorablemobile/HonorableNativePackage.kt');
test('exposure mode retains two-original shared geometry and bounded private PNG ownership',()=>{
 for(const s of ['require(!exposure||(!median&&!focus&&retarget==0))','require(items.size()==2)','PhotoPixelDifference.plan(metadata[0],metadata[1])','PhotoExposureBlend.create(plan.size,frames[0],frames[1],::active)','File.createTempFile("noble-exposure-blend-",".png",directory)','ImportedPhotoOriginals.EXTRA'])assert.ok(bridge.includes(s),s);
 assert.doesNotMatch(bridge,/Http|URL\(|openOutputStream/);
});
test('new native route shares current request/account/source/lease checks',()=>{
 const route=native.slice(native.indexOf('@ReactMethod fun preparePhotoExposureBlend'),native.indexOf('@ReactMethod fun releasePhotoDifference'));
 for(const s of ['runLibraryRead','exposure=true','request.requireActive()','requireCurrent','photoDifferencePreviews.offer(preview)','preview.dispose()'])assert.ok(route.includes(s),s);
});
test('UI exposes distinct reviewed blend, preserves mode identity and rejects wrong result protocol',()=>{
 const ui=read('src/library/PhotoPixelDifference.tsx');for(const s of ['honorableNative.preparePhotoExposureBlend(',"result.operation !== 'exposure-blend'",'result.frames !== 2',"exposure ? ':exposure' : ''",'HDR reconstruction','No automatic alignment','PhotoExposureBlendPanel'])assert.ok(ui.includes(s),s);
 assert.ok(read('src/screens/MemoriesScreen.tsx').includes('<PhotoExposureBlendPanel'));assert.doesNotMatch(ui,/fetch\(|XMLHttpRequest/);
});
