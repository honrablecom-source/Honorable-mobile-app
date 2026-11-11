const {test}=require('node:test');const assert=require('node:assert/strict');const fs=require('node:fs');const path=require('node:path');
const root=path.join(__dirname,'../Honorable-mobile-app-main/mobile-react-native');const read=p=>fs.readFileSync(path.join(root,p),'utf8');
const bridge=read('android/app/src/main/java/com/honorablemobile/PhotoPixelDifferenceBridge.kt');const native=read('android/app/src/main/java/com/honorablemobile/HonorableNativePackage.kt');const ui=read('src/library/PhotoPixelDifference.tsx');
test('clean plate checks every original before using shared bounded sampling',()=>{
 assert.ok(bridge.includes('items.size()==3||items.size()==5'));
 assert.ok(bridge.includes('metadata.drop(2).forEach{PhotoPixelDifference.plan(metadata[0],it)}'));
 assert.ok(bridge.indexOf('metadata.drop(2)')<bridge.indexOf('val frames='));
 assert.ok(bridge.includes('PhotoCleanPlate.create(plan.size,frames,::active)'));
 assert.ok(bridge.includes('if(median||focus)96L else 64L'));
});
test('new native route shares owned staging and protected handoff',()=>{
 const route=native.slice(native.indexOf('@ReactMethod fun preparePhotoCleanPlate'),native.indexOf('@ReactMethod fun releasePhotoDifference'));
 for(const text of ['runLibraryRead','median=true','requireCurrent','photoDifferencePreviews.offer(preview)','request.requireActive()','preview.dispose()']) assert.ok(route.includes(text),text);
 assert.ok(bridge.includes('File.createTempFile("noble-clean-plate-",".png",directory)'));
 assert.ok(bridge.includes('putStringArrayListExtra(ImportedPhotoOriginals.EXTRA'));
 assert.ok(bridge.includes('originals.map{it.uri}'));
});
test('reviewed clean plate uses distinct native computation and validates its response',()=>{
 assert.ok(ui.includes('honorableNative.preparePhotoCleanPlate('));assert.match(ui,/result\.operation !== 'clean-plate'/);assert.match(ui,/result\.frames !== latest\.items\.length/);
 assert.ok(ui.includes('PhotoCleanPlatePanel'));assert.ok(read('src/screens/MemoriesScreen.tsx').includes('<PhotoCleanPlatePanel'));
 assert.doesNotMatch(ui,/fetch\(|XMLHttpRequest/);
});
