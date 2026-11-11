const {test}=require('node:test');const assert=require('node:assert/strict');const fs=require('node:fs');const path=require('node:path');const root=path.join(__dirname,'../Honorable-mobile-app-main/mobile-react-native/android/app/src/main/java/com/honorablemobile');const read=n=>fs.readFileSync(path.join(root,n),'utf8');const shelf=read('VideoProjectVersionShelf.kt');const compare=shelf.slice(shelf.indexOf('    private fun chooseComparison('),shelf.indexOf('    private fun choose(catalog:'));const core=read('VideoVersionDiff.kt');
test('new comparison menu preserves original save/open/delete/Undo indexes',()=>{
 assert.ok(shelf.includes('if(canUndo)choices.add("Undo last deletion")'));assert.ok(shelf.includes('val comparisonIndex=choices.size;choices.add("Compare saved version with current")'));assert.ok(shelf.includes('if(index==comparisonIndex){chooseComparison(catalog);return@setItems}'));assert.ok(shelf.includes('1,2->choose(catalog,index==2)'));assert.ok(shelf.includes('3->job({requireCurrent();store!!.undo('));
});
test('comparison reads exact current saved entry through owned private worker without opening media or applying edits',()=>{
 for(const s of ['if(!owns(token))return@setItems','if(!valid()){close();return@setItems}','job({requireCurrent();val saved=store!!.requireEntry(selected).draft();val report=VideoVersionDiff.compare(snapshot,saved);requireCurrent();report})','store!!.load()','if(!owns(token))return@setPositiveButton','if(!valid()){close();return@setPositiveButton}','view.setOnDismissListener{body.text=""}','show(view,token)'])assert.ok(compare.includes(s),s);
 assert.doesNotMatch(compare,/validateOpen\(|withSaveAccess\(|apply\(|\.save\(|\.delete\(|Clipboard|contentResolver|openInputStream|Http/);
 assert.ok(shelf.includes('VideoPrivateOwner.namespace(context,mode)==owner'));assert.ok(shelf.includes('releaseBusy()\n                if(!valid()){close();return@runOnUiThread}'));
});
test('bounded diff includes all source/render/selection categories and omits URI metadata from report',()=>{
 for(const s of ['const val LIMIT=100','details.size<LIMIT','beforeSources[a.sourceId]!=afterSources[b.sourceId]','a.captionSourceUri!=b.captionSourceUri','current.soundtrack!=saved.soundtrack','current.previewPositionMs!=saved.previewPositionMs','beforeSources!=afterSources'])assert.ok(core.includes(s),s);
 for(const name of ['SOURCE','WINDOW','TITLE','COLOR','FRAMING','MOTION','EDGES','AUDIO','CAPTIONS'])assert.ok(core.includes('add(Field.'+name+')'));
 const report=core.slice(core.indexOf('fun text():String'),core.indexOf('    fun compare('));assert.doesNotMatch(report,/\.uri|captionSourceUri/);assert.ok(report.includes('by timeline position'));assert.ok(report.includes('Nothing is applied'));
});
