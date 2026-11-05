import test from 'node:test';
import assert from 'node:assert/strict';
import { photoSheet } from '../photo-sheet.mjs';
const photo=(id,x,y,width,height,extra={})=>({id,type:'image',fileId:'a'.repeat(64),status:'saved',scale:[1,1],x,y,width,height,angle:0,opacity:100,...extra});
const photos=[photo('landscape',10,20,200,100),photo('portrait',300,300,50,100),photo('square',500,40,100,100)];
test('mixed aspects fit equal cells without overlap or changing input assets',()=>{
 const before=structuredClone(photos),p=photoSheet(photos,null,2,120,20);
 assert.deepEqual(p,[{id:'landscape',x:10,y:50,width:120,height:60},{id:'portrait',x:180,y:20,width:60,height:120},{id:'square',x:10,y:160,width:120,height:120}]);
 assert.deepEqual(photos,before);assert(p.every((change,i)=>change.width/change.height===photos[i].width/photos[i].height));
});
test('selection preserves scene order, cropped proportions, flips and all unselected fields',()=>{
 const source=[...photos,{id:'text',type:'text',x:0,y:0,width:10,height:10,text:'Keep'}];
 source[0]={...source[0],scale:[-1,1],crop:{x:0,y:0,width:50,height:25,naturalWidth:100,naturalHeight:100}};
 const before=structuredClone(source),patches=photoSheet(source,{square:true,landscape:true},1,100,0);
 assert.deepEqual(patches.map(p=>p.id),['landscape','square']);assert.equal(patches[0].width/patches[0].height,2);assert.deepEqual(source,before);
 assert(!patches.some(p=>'fileId'in p||'crop'in p||'scale'in p));
});
test('automatic scope excludes hidden, locked and deleted photos; selection rejects mixed artwork',()=>{
 const omitted=[photo('hidden',0,0,20,20,{opacity:0}),photo('locked',0,0,20,20,{locked:true}),photo('deleted',0,0,20,20,{isDeleted:true})];
 assert.equal(photoSheet([...photos,...omitted],null,2,100,10).length,3);
 for(const e of omitted)assert.throws(()=>photoSheet([...photos,e],{landscape:true,[e.id]:true},2,100,10));
 assert.throws(()=>photoSheet([...photos,{id:'shape',type:'rectangle',x:0,y:0,width:20,height:20}],{landscape:true,shape:true},2,100,10),/visible/);
});
test('unsupported transformations and invalid bounds fail atomically',()=>{
 for(const extra of [{angle:Math.PI/2},{groupIds:['g']},{frameId:'f'},{boundElements:[{id:'x',type:'arrow'}]}])assert.throws(()=>photoSheet([{...photos[0],...extra},photos[1]],null,2,100,10));
 for(const args of [[0,100,10],[5,100,10],[2.5,100,10],[2,63,10],[2,1025,10],[2,100,-1],[2,100,257],[2,NaN,10],[2,100,Infinity],[4,100,10]])assert.throws(()=>photoSheet(photos,null,...args));
 assert.throws(()=>photoSheet(photos,{},2,100,10));assert.throws(()=>photoSheet([photos[0]],null,1,100,10));
 assert.throws(()=>photoSheet(Array.from({length:9},(_,i)=>photo(`p${i}`,0,0,20,20)),null,3,100,10));
 assert.deepEqual(photos.map(p=>p.width),[200,50,100]);
});
