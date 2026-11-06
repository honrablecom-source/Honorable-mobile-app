import test from 'node:test';
import assert from 'node:assert/strict';
import { printArtworkSnapshot, sendPrintArtwork, PRINT_CHUNK_CHARS, MAX_PRINT_BYTES } from '../print-artwork.mjs';
const box = (id, x=0) => ({id,type:'rectangle',x,y:0,width:100,height:80,opacity:100,strokeColor:'#111111',boundElements:null});
const data = 'data:image/png;base64,' + 'AAAA'.repeat(17000);
function fixture() {
  let elements = [box('first'),box('other',200),{...box('hidden',400),opacity:0}];
  let state = {selectedElementIds:{first:true}}, files = {};
  const editor = {getSceneElements:()=>elements,getFiles:()=>files,getAppState:()=>state};
  return {editor,setElements:value=>elements=value,setState:value=>state=value,setFiles:value=>files=value};
}
function bridge(fail='') {
  const calls=[];
  return {calls,
    beginPrintImage:(...args)=>{calls.push(['begin',...args]);if(fail==='begin')return false;return true;},
    appendPrintImageChunk:(...args)=>{calls.push(['chunk',...args]);if(fail==='chunk')return false;return true;},
    finishPrintImage:(...args)=>{calls.push(['finish',...args]);if(fail==='finish')throw Error('Native finish failure');},
    releasePrintImage:(...args)=>calls.push(['release',...args]),
  };
}
test('print snapshot keeps only visible artwork and leaves current geometry and bindings untouched',()=>{
  const f=fixture(), before=structuredClone(f.editor.getSceneElements());
  const whole=printArtworkSnapshot(f.editor);assert.deepEqual(whole.elements.map(e=>e.id),['first','other']);
  const selected=printArtworkSnapshot(f.editor,true);assert.deepEqual(selected.elements.map(e=>e.id),['first']);
  assert.deepEqual(f.editor.getSceneElements(),before);assert(selected.current(f.editor));
  assert.throws(()=>printArtworkSnapshot(null),/Wait/);
});
test('empty hidden or inherited selection cannot silently print the whole drawing',()=>{
  const f=fixture();
  for(const ids of [{},{hidden:true},Object.create({first:true})]) {f.setState({selectedElementIds:ids});assert.throws(()=>printArtworkSnapshot(f.editor,true),/Select|visible/);}
  f.setElements([]);assert.throws(()=>printArtworkSnapshot(f.editor),/visible/);
});
test('document changes invalidate preparation; viewport/selection changes never retarget the captured artwork',()=>{
  const f=fixture(), selected=printArtworkSnapshot(f.editor,true), whole=printArtworkSnapshot(f.editor);
  f.setState({selectedElementIds:{first:true},scrollX:200,zoom:{value:2}});assert(selected.current(f.editor));assert(whole.current(f.editor));
  f.setState({selectedElementIds:{other:true}});assert(selected.current(f.editor));assert.deepEqual(selected.elements.map(e=>e.id),['first']);assert(whole.current(f.editor));
  f.setElements([box('first',100),box('other',200)]);assert.equal(whole.current(f.editor),false);
  assert.equal(whole.current({...f.editor}),false);
});
test('external/invalid scene references fail before a print snapshot is returned',()=>{
  const f=fixture();f.setElements([{...box('first'),link:'https://example.com'}]);assert.throws(()=>printArtworkSnapshot(f.editor),/External/);
  f.setElements([{...box('first'),boundElements:[{id:'missing',type:'text'}]}]);assert.throws(()=>printArtworkSnapshot(f.editor,true),/missing/);
});
test('bounded bridge chunks reconstruct exactly one request without carrying a scene or source URI',()=>{
  const b=bridge();sendPrintArtwork(b,7,data);
  assert.deepEqual(b.calls[0],['begin',7,data.length]);assert.deepEqual(b.calls.at(-1),['finish',7]);
  const chunks=b.calls.filter(c=>c[0]==='chunk');assert(chunks.length>1);assert(chunks.every(c=>c[1]===7&&c[2].length<=PRINT_CHUNK_CHARS));assert.equal(chunks.map(c=>c[2]).join(''),data);
  assert(!b.calls.some(c=>c[0]==='release'));
});
test('absent native support, request bounds and invalid payloads fail without starting a transfer',()=>{
  assert.throws(()=>sendPrintArtwork(null,1,data),/Android/);
  const b=bridge();
  for(const id of [0,-1,1.5,1_000_000_001,NaN])assert.throws(()=>sendPrintArtwork(b,id,data),/request/);
  for(const raw of ['https://example.com','data:image/jpeg;base64,AAAA','data:image/png;base64,A','data:image/png;base64,!!!!','data:image/png;base64,','data:image/png;base64,'+'A'.repeat(Math.ceil(MAX_PRINT_BYTES/3)*4+4)])assert.throws(()=>sendPrintArtwork(b,1,raw),/large|invalid/);
  assert.deepEqual(b.calls,[]);
});
test('rejected begin never releases another operation; later transfer failures release only their own request',()=>{
  const rejected=bridge('begin');assert.throws(()=>sendPrintArtwork(rejected,10,data),/operation/);assert.deepEqual(rejected.calls,[['begin',10,data.length]]);
  for(const phase of ['chunk','finish']) {const b=bridge(phase);assert.throws(()=>sendPrintArtwork(b,11,data),/stopped|failure/);assert.deepEqual(b.calls.at(-1),['release',11]);}
});
test('throwing release does not replace the useful transfer failure',()=>{
  const b=bridge('chunk');b.releasePrintImage=()=>{throw Error('cleanup');};assert.throws(()=>sendPrintArtwork(b,12,data),/transfer stopped/);
});
