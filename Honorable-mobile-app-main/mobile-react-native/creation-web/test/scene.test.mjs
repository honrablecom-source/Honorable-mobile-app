import test from 'node:test';
import assert from 'node:assert/strict';
import { encodeDraft, decodeDraft, MAX_ELEMENTS, MAX_DRAFT_BYTES } from '../scene.mjs';
const box = { type: 'rectangle', id: 'box', x: 0, y: 10, width: 100, height: 80 };
test('draft roundtrip retains editable objects and filters transient account state',()=>{
  const saved=decodeDraft(encodeDraft([box],{currentItemStrokeColor:'#111111',collaborators:{secret:'excluded'},selectedElementIds:{box:true}}));
  assert.deepEqual(saved.elements,[box]);assert.equal(saved.appState.collaborators,undefined);assert.equal(saved.appState.selectedElementIds,undefined);
});
test('no draft is a clean new scene',()=>assert.equal(decodeDraft(null),null));
test('deleted objects do not consume the saved scene budget',()=>assert.deepEqual(decodeDraft(encodeDraft([{...box,isDeleted:true}])).elements,[]));
test('future and malformed drafts fail closed',()=>{for(const value of ['broken','{"version":2,"elements":[]}','{"version":1,"elements":{}}'])assert.throws(()=>decodeDraft(value));});
test('object and payload bounds prevent oversized stored scenes',()=>{assert.throws(()=>encodeDraft(Array(MAX_ELEMENTS+1).fill(box)));assert.throws(()=>decodeDraft('x'.repeat(MAX_DRAFT_BYTES+1)));});
test('non-finite or extreme coordinates are rejected',()=>{for(const x of [NaN,Infinity,1e8])assert.throws(()=>encodeDraft([{...box,x}]));});
test('external content and overlong text are not persisted',()=>{assert.throws(()=>encodeDraft([{...box,type:'image',fileId:'remote'}]));assert.throws(()=>encodeDraft([{...box,type:'text',text:'x'.repeat(2001)}]));});
test('colors and text survive a supported local draft',()=>{const text={...box,type:'text',text:'A local idea',fontFamily:2};assert.equal(decodeDraft(encodeDraft([text])).elements[0].text,'A local idea');});
