import test from 'node:test';
import assert from 'node:assert/strict';
import { imageArtwork } from '../image-export.mjs';
import { photoFile } from '../image-assets.mjs';
const box=(id,x=0)=>({id,type:'rectangle',x,y:0,width:100,height:80,opacity:100,strokeColor:'#111111',boundElements:null});
test('whole image excludes hidden/deleted artwork and selection is required explicitly',()=>{
 const all=[box('a'),{...box('hidden'),opacity:0},{...box('gone'),isDeleted:true}];
 assert.deepEqual(imageArtwork(all).elements.map(e=>e.id),['a']);
 for(const ids of [{},[],false,{missing:true}])assert.throws(()=>imageArtwork(all,{},ids),/Select/);
 assert.throws(()=>imageArtwork(all,{}, {hidden:true}),/no visible/);
 assert.throws(()=>imageArtwork([],{}),/visible/);
});
test('selection carries attached labels and detaches outside arrow endpoints in render copy without mutating source',()=>{
 const all=[{...box('a',40),boundElements:[{id:'label',type:'text'},{id:'arrow',type:'arrow'}]}, {id:'label',type:'text',text:'Card',x:50,y:20,width:60,height:20,containerId:'a',opacity:100},box('outside',300), {id:'arrow',type:'arrow',x:100,y:50,width:200,height:0,points:[[0,0],[200,0]],startBinding:{elementId:'a'},endBinding:{elementId:'outside'},opacity:100}];
 const before=structuredClone(all);
 assert.deepEqual(imageArtwork(all,{}, {a:true}).elements.map(e=>e.id),['a','label']);
 const arrow=imageArtwork(all,{}, {arrow:true}).elements[0];assert.equal(arrow.startBinding,null);assert.equal(arrow.endBinding,null);
 assert.deepEqual(all,before);
});
test('frame selection includes only visible descendants and only their referenced sanitized photo copies',()=>{
 const file=photoFile('data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLbtAAAAABJRU5ErkJggg==',0);
 const image={id:'photo',type:'image',x:20,y:20,width:40,height:40,scale:[1,1],crop:null,status:'saved',fileId:file.id,frameId:'frame',opacity:100};
 const all=[{...box('frame'),type:'frame'},image,{...box('hidden',50),frameId:'frame',opacity:0},box('outside',300)];
 const files={[file.id]:{...file,sourceUri:'private',name:'private'}},before=structuredClone({all,files});
 const result=imageArtwork(all,files,{frame:true});assert.deepEqual(result.elements.map(e=>e.id),['frame','photo']);assert.deepEqual(result.files,{[file.id]:file});
 assert.deepEqual(imageArtwork(all,files,{outside:true}).files,{});assert.deepEqual({all,files},before);
});
test('inherited selection IDs cannot select artwork and invalid sources fail closed',()=>{
 assert.throws(()=>imageArtwork([box('toString')],{},{}),/Select/);
 assert.throws(()=>imageArtwork([{...box('a'),link:'https://example.com'}],{}, {a:true}),/External/);
 assert.throws(()=>imageArtwork([{...box('a'),boundElements:[{id:'missing',type:'text'}]}],{}, {a:true}),/missing/);
});
