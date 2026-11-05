import test from 'node:test';
import assert from 'node:assert/strict';
import { arrangeObjects, layerPatch, reorderObject } from '../composition.mjs';
import { encodeDraft, decodeDraft } from '../scene.mjs';
const box=(id,x,y,width=20,height=20)=>({id,type:'rectangle',x,y,width,height,angle:0,opacity:75,locked:false});
const elements=[box('a',0,0),box('b',50,40,40),box('c',180,80)], ids={a:true,b:true,c:true};
test('alignment preserves originals and unselected objects',()=>{const before=structuredClone(elements);assert.deepEqual(arrangeObjects(elements,ids,'left').map(p=>p.x),[0,0,0]);assert.deepEqual(elements,before);assert.deepEqual(arrangeObjects(elements,{a:true,c:true},'top').map(p=>p.id),['a','c']);});
test('edge and center alignments use common selection bounds',()=>{assert.deepEqual(arrangeObjects(elements,ids,'right').map(p=>p.x),[180,160,180]);assert.deepEqual(arrangeObjects(elements,ids,'center').map(p=>p.x),[90,80,90]);assert.deepEqual(arrangeObjects(elements,ids,'bottom').map(p=>p.y),[80,80,80]);assert.deepEqual(arrangeObjects(elements,ids,'middle').map(p=>p.y),[40,40,40]);});
test('equal edge gaps retain outer boundaries and refuse overlap',()=>{assert.deepEqual(arrangeObjects(elements,ids,'horizontal').map(p=>p.x),[0,80,180]);assert.deepEqual(arrangeObjects(elements,ids,'vertical').map(p=>p.y),[0,40,80]);assert.throws(()=>arrangeObjects([box('a',0,0,100),box('b',1,0,100),box('c',2,0,100)],ids,'horizontal'),/apart/);});
test('rotated object alignment respects rendered bounds',()=>{const rotated=[box('a',0,0),{...box('b',80,0,40,20),angle:Math.PI/2}];assert.equal(arrangeObjects(rotated,{a:true,b:true},'left')[1].x,-10);});
test('invalid selections fail without partial edits',()=>{for(const patch of [{locked:true},{containerId:'a'},{groupIds:['g']},{frameId:'frame'},{boundElements:[{id:'text',type:'text'}]}])assert.throws(()=>arrangeObjects([{...elements[0],...patch},elements[1]],{a:true,b:true},'left'));assert.throws(()=>arrangeObjects(elements,{a:true},'left'));assert.throws(()=>arrangeObjects(elements,{a:true,b:true},'horizontal'));assert.throws(()=>arrangeObjects(elements,ids,'unknown'));});
test('hidden opacity and lock status survive editable scene recovery',()=>{for(const locked of [false,true]){const original={...elements[0],locked};const hidden={...original,...layerPatch(original,'visibility')};assert.equal(hidden.opacity,0);assert.equal(hidden.locked,true);const recovered=decodeDraft(encodeDraft([hidden])).elements[0];const visible={...recovered,...layerPatch(recovered,'visibility')};assert.equal(visible.opacity,75);assert.equal(visible.locked,locked);}});
test('stacking preserves source objects and rejects locked or missing objects',()=>{for(const [id,dir,result] of [['a','front',['b','c','a']],['c','back',['c','a','b']],['b','up',['a','c','b']],['b','down',['b','a','c']]])assert.deepEqual(reorderObject(elements,id,dir).map(e=>e.id),result);assert.deepEqual(elements.map(e=>e.id),['a','b','c']);assert.throws(()=>reorderObject(elements,'missing','front'));assert.throws(()=>reorderObject([{...elements[0],locked:true}],'a','front'));});

test('grid copies retain original geometry while receiving independent identifiers',async()=>{
 const {repeatGrid}=await import('../composition.mjs');let counter=0;
 const original=[box('a',10,20)];const next=repeatGrid(original,{a:true},3,2,5,()=>`new${counter++}`);
 assert.equal(next.length,6);assert.deepEqual(next.map(e=>[e.x,e.y]),[[10,20],[35,20],[60,20],[10,45],[35,45],[60,45]]);
 assert.equal(new Set(next.map(e=>e.id)).size,6);assert.equal(original.length,1);assert.equal(original[0].x,10);
});
test('ring copies preserve originals and upright orientations',async()=>{
 const {repeatRing}=await import('../composition.mjs');let counter=0;
 const original=[box('a',0,0)];const next=repeatRing(original,{a:true},4,100,()=>`new${counter++}`);
 assert.equal(next.length,5);assert.equal(next[1].x,100);assert.ok(Math.abs(next[2].y-100)<.00001);assert.ok(Math.abs(next[3].x+100)<.00001);assert.ok(next.every(e=>e.angle===0));
});
test('procedural repeats reject excessive budgets collisions and invalid parameters atomically',async()=>{
 const {repeatGrid,repeatRing}=await import('../composition.mjs');const original=[box('a',0,0)];
 for(const values of [[0,2,5],[1,1,5],[13,2,5],[2,2,-1],[2,2,Infinity]])assert.throws(()=>repeatGrid(original,{a:true},...values,()=> 'new'));
 assert.throws(()=>repeatGrid(original,{a:true},2,2,0,()=> 'a'));
 assert.throws(()=>repeatRing(original,{a:true},25,100,()=> 'new'));
 const many=Array.from({length:100},(_,i)=>box(`b${i}`,i*30,0));const selected=Object.fromEntries(many.map(e=>[e.id,true]));assert.throws(()=>repeatRing(many,selected,6,100,()=> 'new'),/500/);assert.equal(many.length,100);
});

test('reusable components normalize selection and insert independent copies at a new center',async()=>{
 const {captureComponent,insertComponent}=await import('../composition.mjs');
 const source=[box('a',50,80),box('b',100,100)];const component=captureComponent(source,{a:true,b:true});
 assert.deepEqual(component.map(e=>[e.x,e.y]),[[0,0],[50,20]]);let counter=0;
 const next=insertComponent(source,component,300,300,()=>`inserted${counter++}`);
 assert.equal(next.length,4);assert.equal(next[2].x,265);assert.equal(next[2].y,280);assert.equal(source[0].x,50);assert.equal(component[0].x,0);assert.notEqual(next[2].id,source[0].id);
 assert.throws(()=>captureComponent(source,{}));assert.throws(()=>insertComponent(source,component,Infinity,0,()=> 'x'));assert.throws(()=>insertComponent(source,[],0,0,()=> 'x'));
});

test('global recoloring normalizes hex forms and protects locked objects by default',async()=>{
 const {compositionColors,replaceCompositionColor}=await import('../composition.mjs');
 const colored=[{...box('a',0,0),strokeColor:'#FFF',backgroundColor:'#ffffff'},{...box('b',20,0),strokeColor:'#ffffff',locked:true}];
 assert.deepEqual(compositionColors(colored),[{color:'#ffffff',count:3}]);
 const patches=replaceCompositionColor(colored,'#fff','#123456');assert.equal(patches.length,1);assert.equal(patches[0].strokeColor,'#123456');assert.equal(patches[0].backgroundColor,'#123456');assert.equal(colored[0].strokeColor,'#FFF');
 assert.equal(replaceCompositionColor(colored,'#fff','#123456',true).length,2);assert.throws(()=>replaceCompositionColor(colored,'#fff','#ffffff'));assert.throws(()=>replaceCompositionColor(colored,'#000','#fff'));assert.throws(()=>replaceCompositionColor(colored,'#fff','url(https://example.com)'));
});
