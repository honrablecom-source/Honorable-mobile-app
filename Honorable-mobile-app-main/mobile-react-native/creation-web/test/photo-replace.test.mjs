import test from 'node:test';
import assert from 'node:assert/strict';
import { replaceablePhoto, replacementPatch } from '../photo-replace.mjs';
const oldId='a'.repeat(64),newId='b'.repeat(64);
const photo={id:'photo',type:'image',fileId:oldId,status:'saved',scale:[-1,1],x:10,y:20,width:200,height:100,angle:.5,opacity:80,locked:false,crop:{x:0,y:0,width:100,height:50,naturalWidth:200,naturalHeight:100}};
test('replacement fits a portrait inside a landscape box,retaining center and object identity',()=>{
 const source=[photo],before=structuredClone(source),patch=replacementPatch(source,'photo',newId,50,100);
 assert.deepEqual(patch,{id:'photo',fileId:newId,width:50,height:100,x:85,y:20,crop:null,status:'saved'});assert.deepEqual(source,before);
 const next={...photo,...patch};assert.deepEqual(next.scale,[-1,1]);assert.equal(next.angle,.5);assert.equal(next.opacity,80);assert.equal(next.x+next.width/2,110);assert.equal(next.y+next.height/2,70);
});
test('same aspect fills the previous box and different aspect never extends it',()=>{
 for(const [width,height] of [[400,200],[100,100],[1024,1],[1,1024]]){const patch=replacementPatch([photo],'photo',newId,width,height);assert(patch.width<=200&&patch.height<=100);assert(Math.abs(patch.width/patch.height-width/height)<1e-8);assert.equal(patch.x+patch.width/2,110);assert.equal(patch.y+patch.height/2,70);}
});
test('unavailable or protected targets refuse replacement without modifying any source',()=>{
 for(const extra of [{isDeleted:true},{locked:true},{opacity:0},{groupIds:['g']},{frameId:'frame'},{containerId:'c'},{boundElements:[{id:'a',type:'arrow'}]},{customData:{nobleHidden:{opacity:80,locked:false}}}]){const source=[{...photo,...extra}],before=structuredClone(source);assert.throws(()=>replaceablePhoto(source,'photo'));assert.deepEqual(source,before);}
 assert.throws(()=>replaceablePhoto([photo],'missing'));assert.throws(()=>replaceablePhoto([{...photo,type:'rectangle'}],'photo'));
});
test('invalid copies and dimensions fail atomically',()=>{
 for(const args of [['remote',50,100],[newId,0,100],[newId,50,1025],[newId,NaN,100],[newId,50,2.5]])assert.throws(()=>replacementPatch([photo],'photo',...args));
 assert.equal(photo.fileId,oldId);assert.notEqual(photo.crop,null);
});
