import test from 'node:test';
import assert from 'node:assert/strict';
import { mergeProjectElements } from '../merge-project.mjs';
const shape=(id,x=0)=>({id,type:'rectangle',x,y:0,width:100,height:80,groupIds:['group'],boundElements:null});
const graph=[{...shape('box'),boundElements:[{id:'label',type:'text'},{id:'arrow',type:'arrow'}],frameId:'frame'},{id:'label',type:'text',text:'Bound',x:10,y:20,width:60,height:20,containerId:'box',frameId:'frame',groupIds:['group']},{...shape('other',200),boundElements:[{id:'arrow',type:'arrow'}]},{id:'arrow',type:'arrow',x:100,y:40,width:100,height:0,points:[[0,0],[100,0]],startBinding:{elementId:'box',focus:0,gap:1,fixedPoint:[1,.5]},endBinding:{elementId:'other',focus:0,gap:1},groupIds:['group']},{id:'frame',type:'frame',x:-10,y:-10,width:350,height:120}];
const generator=()=>{let i=0;return()=>`fresh-${++i}`;};
test('complete bound graph receives independent element/group/frame/arrow references and preserves originals',()=>{
 const current=[shape('box',-500)],before=structuredClone({current,graph}),result=mergeProjectElements(current,graph,50,60,generator()),copies=result.elements.slice(1),map=new Map(graph.map((e,i)=>[e.id,copies[i]]));
 assert.equal(result.elements.length,6);assert.equal(new Set(result.elements.map(e=>e.id)).size,6);assert.equal(map.get('box').x,50);assert.equal(map.get('box').y,60);assert.equal(map.get('label').containerId,map.get('box').id);assert.equal(map.get('box').frameId,map.get('frame').id);assert.equal(map.get('arrow').startBinding.elementId,map.get('box').id);assert.equal(map.get('arrow').endBinding.elementId,map.get('other').id);assert.deepEqual(map.get('arrow').startBinding.fixedPoint,[1,.5]);assert.deepEqual(map.get('box').boundElements.map(b=>b.id),[map.get('label').id,map.get('arrow').id]);assert.equal(map.get('box').groupIds[0],map.get('arrow').groupIds[0]);assert.notEqual(map.get('box').groupIds[0],'group');assert.deepEqual({current,graph},before);
});
test('deep copied nested geometry cannot modify source and repeated insertions remain independent',()=>{
 const first=mergeProjectElements([],graph,0,0,generator());first.elements.find(e=>e.type==='arrow').points[1][0]=999;assert.equal(graph[3].points[1][0],100);
 let i=100;const second=mergeProjectElements(first.elements,graph,400,500,()=>`fresh-${++i}`);assert.equal(new Set(second.elements.map(e=>e.id)).size,10);assert.notEqual(second.elements[0].groupIds[0],second.elements[5].groupIds[0]);
});
test('image content IDs,crops,flips,hidden state and locks remain unchanged',()=>{
 const photo={id:'image',type:'image',fileId:'a'.repeat(64),x:20,y:30,width:100,height:80,scale:[-1,1],opacity:0,locked:true,crop:{x:0,y:0,width:100,height:80,naturalWidth:100,naturalHeight:80},customData:{nobleHidden:{opacity:50,locked:false}},groupIds:[]};const copy=mergeProjectElements([], [photo],10,20,generator()).elements[0];assert.equal(copy.fileId,photo.fileId);assert.deepEqual(copy.scale,photo.scale);assert.deepEqual(copy.crop,photo.crop);assert.deepEqual(copy.customData,photo.customData);assert.equal(copy.locked,true);assert.equal(copy.opacity,0);
});
test('missing graph references,malformed groups and ID collisions fail before mutating source',()=>{
 const before=structuredClone(graph);for(const patch of [{frameId:'missing'},{containerId:'missing'},{boundElements:[{id:'missing',type:'text'}]},{startBinding:{elementId:'missing'}},{endBinding:'bad'},{groupIds:[null]}])assert.throws(()=>mergeProjectElements([], [{...shape('a'),...patch}],0,0,generator()));
 for(const id of ['box','group','',null,'x'.repeat(129)])assert.throws(()=>mergeProjectElements([],graph,0,0,()=>id));assert.deepEqual(graph,before);
});
test('object and geometry budgets reject atomically while deleted history remains untouched',()=>{
 const current=Array.from({length:496},(_,i)=>shape(`current-${i}`));assert.throws(()=>mergeProjectElements(current,graph,0,0,generator()),/500/);assert.throws(()=>mergeProjectElements([],graph,Infinity,0,generator()));assert.throws(()=>mergeProjectElements([],graph,1e7,0,generator()));assert.throws(()=>mergeProjectElements([],[],0,0,generator()));
 const deleted={...shape('old'),isDeleted:true};const result=mergeProjectElements([deleted],[shape('new')],0,0,generator());assert.equal(result.elements[0],deleted);assert.equal(result.insertedIds.length,1);
});

test('cyclic frames and structurally wrong reference types are refused',()=>{
 const frame=id=>({id,type:'frame',x:0,y:0,width:100,height:100});
 assert.throws(()=>mergeProjectElements([],[{...frame('a'),frameId:'b'},{...frame('b'),frameId:'a'}],0,0,generator()),/cycle/);
 assert.throws(()=>mergeProjectElements([],[{...shape('a'),frameId:'b'},shape('b')],0,0,generator()),/frames/);
 assert.throws(()=>mergeProjectElements([],[{...shape('a'),containerId:'b'},shape('b')],0,0,generator()),/containers/);
 assert.throws(()=>mergeProjectElements([],[{...shape('a'),boundElements:[{id:'b',type:'text'}]},shape('b')],0,0,generator()),/binding types/);
 assert.throws(()=>mergeProjectElements([],[{...shape('a'),startBinding:{elementId:'b'}},shape('b')],0,0,generator()),/arrow targets/);
});
