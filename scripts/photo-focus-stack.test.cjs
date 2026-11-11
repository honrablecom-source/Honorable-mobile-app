const {test}=require('node:test');const assert=require('node:assert/strict');const fs=require('node:fs');const path=require('node:path');const root=path.join(__dirname,'../Honorable-mobile-app-main/mobile-react-native');const read=p=>fs.readFileSync(path.join(root,p),'utf8');const bridge=read('android/app/src/main/java/com/honorablemobile/PhotoPixelDifferenceBridge.kt');const native=read('android/app/src/main/java/com/honorablemobile/HonorableNativePackage.kt');
test('focus stack requires bounded matching originals and shares production sample grid',()=>{
 assert.ok(bridge.includes('if(focus)require(items.size() in 2..5)'));
 assert.ok(bridge.includes('require(!(median&&focus))'));
 assert.ok(bridge.indexOf('metadata.drop(2)')<bridge.indexOf('val frames='));
 assert.ok(bridge.includes('PhotoFocusStack.create(plan.size,frames,::active)'));
 assert.ok(bridge.includes('if(median||focus)96L else 64L'));
 assert.ok(bridge.includes('if(focus)"focus-stack"'));
});
test('focus route shares account and source revalidation, request ownership and private output',()=>{
 const route=native.slice(native.indexOf('@ReactMethod fun preparePhotoFocusStack'),native.indexOf('@ReactMethod fun releasePhotoDifference'));
 for(const s of ['runLibraryRead','focus=true','requireCurrent','photoDifferencePreviews.offer(preview)','request.requireActive()','preview.dispose()'])assert.ok(route.includes(s),s);
 assert.ok(bridge.includes('File.createTempFile("noble-focus-stack-",".png",directory)'));
 assert.ok(bridge.includes('putStringArrayListExtra(ImportedPhotoOriginals.EXTRA'));
 assert.doesNotMatch(bridge,/Http|URL\(|openOutputStream/);
});
test('review reaches distinct computation and validates returned operation and capture bindings',()=>{
 const ui=read('src/library/PhotoPixelDifference.tsx');assert.ok(ui.includes('honorableNative.preparePhotoFocusStack('));assert.ok(ui.includes("result.operation !== 'focus-stack'"));assert.ok(ui.includes('result.frames !== latest.items.length'));assert.ok(ui.includes('JSON.stringify({ clean, focus, items: props.items })'));assert.ok(ui.includes('PhotoFocusStackPanel'));assert.ok(read('src/screens/MemoriesScreen.tsx').includes('<PhotoFocusStackPanel'));
});
