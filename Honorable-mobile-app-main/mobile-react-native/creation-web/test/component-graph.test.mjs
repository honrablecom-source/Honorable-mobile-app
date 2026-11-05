import test from 'node:test';
import assert from 'node:assert/strict';
import { captureComponent, insertComponent } from '../composition.mjs';
const box=(id,x=100)=>({id,type:'rectangle',x,y:100,width:100,height:80,groupIds:['group'],boundElements:null});
const graph=[{id:'frame',type:'frame',x:80,y:80,width:400,height:200},{...box('box'),frameId:'frame',boundElements:[{id:'label',type:'text'},{id:'arrow',type:'arrow'}]},{id:'label',type:'text',text:'Card',x:110,y:120,width:60,height:20,containerId:'box',frameId:'frame',groupIds:['group']},{...box('other',300),boundElements:[{id:'arrow',type:'arrow'}]},{id:'arrow',type:'arrow',x:200,y:140,width:100,height:0,points:[[0,0],[100,0]],startBinding:{elementId:'box',focus:0,gap:1},endBinding:{elementId:'other',focus:0,gap:1},boundElements:[{id:'edge-text',type:'text'}],groupIds:[]},{id:'edge-text',type:'text',text:'Edge',x:220,y:130,width:50,height:20,containerId:'arrow',groupIds:[]},{id:'hidden-photo',type:'image',fileId:'a'.repeat(64),x:10000,y:10000,width:50,height:40,scale:[-1,1],opacity:0,locked:true,frameId:'frame',groupIds:[],customData:{nobleHidden:{opacity:100,locked:false}}}];
const allocator=()=>{let i=0;return()=>`copy-${++i}`;};
test('selected grouped card captures its bound label,detaches external frame/arrow and preserves source',()=>{
 const before=structuredClone(graph),component=captureComponent(graph,{box:true});assert.deepEqual(component.map(e=>e.id),['box','label']);assert.equal(component[0].frameId,null);assert.deepEqual(component[0].boundElements,[{id:'label',type:'text'}]);assert.equal(component[1].containerId,'box');assert.deepEqual(component[0].groupIds,['group']);assert.deepEqual([component[0].x,component[0].y],[0,0]);assert.deepEqual(graph,before);
 assert.deepEqual(captureComponent(graph,{label:true}).map(e=>e.id),['box','label']);
});
test('selected arrow keeps label and visual geometry while outside endpoint bindings are detached',()=>{
 const component=captureComponent(graph,{arrow:true});assert.deepEqual(component.map(e=>e.id),['arrow','edge-text']);assert.equal(component[0].startBinding,null);assert.equal(component[0].endBinding,null);assert.deepEqual(component[0].points,[[0,0],[100,0]]);assert.equal(component[1].containerId,'arrow');assert.equal(graph[4].startBinding.elementId,'box');
});
test('frame capture includes hidden/locked children and their image identity without letting them skew visible bounds',()=>{
 let measured;const measure=elements=>{measured=elements.map(e=>e.id);return[80,80,480,280];};const component=captureComponent(graph,{frame:true},measure);assert.deepEqual(component.map(e=>e.id),['frame','box','label','hidden-photo']);assert(!measured.includes('hidden-photo'));const image=component.find(e=>e.type==='image');assert.equal(image.fileId,'a'.repeat(64));assert.deepEqual(image.scale,[-1,1]);assert.equal(image.opacity,0);assert.equal(image.locked,true);assert.equal(image.frameId,'frame');assert.equal(component[0].x,0);assert.equal(image.x,9920);
});
test('component insertion remaps complete frame/group/label graph into independent deep copies',()=>{
 const component=captureComponent(graph,{frame:true}),before=structuredClone(component),first=insertComponent([],component,200,200,allocator()),byId=new Map(first.map(e=>[e.id,e]));assert.equal(first.length,4);assert.equal(new Set(first.map(e=>e.id)).size,4);const card=first.find(e=>e.type==='rectangle'),label=first.find(e=>e.type==='text'),frame=first.find(e=>e.type==='frame');assert.equal(card.frameId,frame.id);assert.equal(label.containerId,card.id);assert.equal(byId.get(card.boundElements[0].id),label);assert.notEqual(card.groupIds[0],'group');first[1].groupIds.push('other-group');assert.deepEqual(component,before);
 let i=100;const second=insertComponent(first,component,600,600,()=>`copy-${++i}`);assert.equal(second.length,8);assert.equal(new Set(second.map(e=>e.id)).size,8);assert.notEqual(second[1].groupIds[0],second[5].groupIds[0]);
});
test('prototype-like identifiers require an actual selection and missing labels or invalid bounds fail clearly',()=>{
 assert.throws(()=>captureComponent([box('toString')],{}),/Select/);const ids=Object.create(null);ids.toString=true;assert.equal(captureComponent([box('toString')],ids).length,1);
 assert.throws(()=>captureComponent([{...box('a'),boundElements:[{id:'missing',type:'text'}]}],{a:true}),/missing/);assert.throws(()=>captureComponent(graph,{box:true},()=>[0,0,NaN,0]));assert.throws(()=>insertComponent([],captureComponent(graph,{box:true}),0,0,allocator(),()=>[1,0,0,0]));
});
test('full connected selection keeps both arrow endpoints and source nodes while IDs are renewed at insertion',()=>{
 const component=captureComponent(graph,{box:true,other:true,arrow:true});assert.deepEqual(component.map(e=>e.id),['box','label','other','arrow','edge-text']);const result=insertComponent([],component,300,300,allocator()),map=new Map(result.map(e=>[e.id,e])),arrow=result.find(e=>e.type==='arrow');assert.equal(map.get(arrow.startBinding.elementId).type,'rectangle');assert.equal(map.get(arrow.endBinding.elementId).type,'rectangle');assert.equal(result.find(e=>e.text==='Edge').containerId,arrow.id);
});
