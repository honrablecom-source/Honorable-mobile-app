import test from 'node:test';
import assert from 'node:assert/strict';
import { framingCrop, framingPatch, FRAMING_FORMATS } from '../photo-framing.mjs';
const fileId='a'.repeat(64);
const photo={id:'photo',type:'image',fileId,status:'saved',scale:[-1,1],x:10,y:20,width:320,height:180,angle:.5,opacity:80,locked:false,crop:null};
const other={id:'other',type:'rectangle',x:-40,y:-30,width:20,height:20,angle:0,opacity:100};
const crop={x:200,y:0,width:600,height:600};

test('crop patch fits inside the old box and preserves center, source and appearance',()=>{
 const source=[photo,other],before=structuredClone(source),patch=framingPatch(source,'photo',fileId,800,600,crop);
 assert.equal(patch.width,180);assert.equal(patch.height,180);assert.equal(patch.x,80);assert.equal(patch.y,20);
 const next={...photo,...patch};assert.equal(next.x+next.width/2,170);assert.equal(next.y+next.height/2,110);
 assert.equal(next.fileId,fileId);assert.equal(next.angle,.5);assert.deepEqual(next.scale,[-1,1]);assert.equal(next.opacity,80);assert.equal(next.status,'saved');assert.deepEqual(source,before);
});

test('an identical suggestion is a no-op and prior crops are replaced from full source coordinates',()=>{
 const patch=framingPatch([photo],'photo',fileId,800,600,crop),next={...photo,...patch};
 assert.equal(framingPatch([next],'photo',fileId,800,600,crop),null);
 const different=framingPatch([next],'photo',fileId,800,600,{x:0,y:0,width:800,height:450});
 assert.equal(different.crop.x,0);assert.equal(different.crop.naturalWidth,800);assert(different.width<=next.width&&different.height<=next.height);
});

test('invalid crop geometry and source dimensions fail instead of clamping or mutating',()=>{
 for(const value of [null,{}, {...crop,x:-1},{...crop,y:-1},{...crop,width:0},{...crop,height:0},{...crop,x:201},{...crop,y:1},{...crop,width:Infinity},{...crop,x:NaN}])assert.throws(()=>framingCrop(value,800,600));
 for(const [width,height]of [[0,600],[800,0],[1025,600],[800,NaN],[800.5,600]])assert.throws(()=>framingCrop(crop,width,height));
 assert.equal(framingCrop({...crop,height:600+1e-9},800,600).height,600);
 assert.deepEqual(framingCrop({x:0,y:0,width:.5,height:.5},1,1),{x:0,y:0,width:.5,height:.5,naturalWidth:1,naturalHeight:1});
});

test('changed or protected photo targets are rejected atomically',()=>{
 for(const extra of [{locked:true},{isDeleted:true},{opacity:0},{groupIds:['group']},{frameId:'frame'},{containerId:'container'},{boundElements:[{id:'label',type:'text'}]},{customData:{nobleHidden:{opacity:80}}}]){
  const source=[{...photo,...extra}],before=structuredClone(source);assert.throws(()=>framingPatch(source,'photo',fileId,800,600,crop));assert.deepEqual(source,before);
 }
 assert.throws(()=>framingPatch([photo],'photo','b'.repeat(64),800,600,crop));assert.throws(()=>framingPatch([photo],'missing',fileId,800,600,crop));
});

test('supported ratios produce matching bounded geometry without changing unrelated artwork',()=>{
 for(const [width,height] of Object.values(FRAMING_FORMATS)){
  const factor=Math.min(800/width,600/height),region={x:0,y:0,width:width*factor,height:height*factor};
  const patch=framingPatch([photo,other],'photo',fileId,800,600,region);
  assert(Math.abs(patch.width/patch.height-width/height)<1e-10);assert(patch.width<=photo.width+1e-8&&patch.height<=photo.height+1e-8);
 }
});
