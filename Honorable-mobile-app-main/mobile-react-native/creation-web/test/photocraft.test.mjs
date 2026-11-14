import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {CORE_EFFECTS,checkedEffect,checkedCoreProject,checkedCoreJob,coreHistory} from '../photocraft-studio.mjs';
import {startPhotoCraft} from '../photocraft-client.mjs';
import {verifyCore,coreInputs,runtimePins,pinSource} from '../photocraft-core/verify.mjs';
import {initSync,apply_filter,blend_layers,core_revision} from '../photocraft-core/pkg/noble_photocraft_core.js';
const coreRoot=fileURLToPath(new URL('../photocraft-core/',import.meta.url));
initSync({module:fs.readFileSync(path.join(coreRoot,'pkg/noble_photocraft_core_bg.wasm'))});
const layer=(id=1)=>({id,name:'Layer',png:'data:image/png;base64,AQID',blend:'normal',opacity:1,visible:true});
const project=()=>({width:2,height:1,layers:[layer()]});
const job=()=>({width:2,height:1,layers:[{...layer(),pixels:new Uint8Array([30,60,90,255,200,100,50,127]).buffer}]});
test('real compiled PhotoCraft WASM exactly inverts twice and alpha-composites pixels',()=>{
  assert.match(core_revision(),/f143ce58498f201a8dfc9c07f5e24147c6a22bee/);
  const original=new Uint8Array([30,60,90,127,200,100,50,255]),result=apply_filter(2,1,original,'invert',0);
  assert.deepEqual([...result],[225,195,165,127,55,155,205,255]);
  assert.deepEqual(apply_filter(2,1,result,'invert',0),original);
  assert.deepEqual([...blend_layers(1,1,new Uint8Array([255,0,0,255]),new Uint8Array([0,0,255,128]),'normal',1)],[127,0,128,255]);
});
test('every exposed bounded effect executes actual WASM with finite complete output and preserves source',()=>{
  const original=new Uint8Array(16*12*4);for(let i=0;i<original.length;i++)original[i]=i%4===3?255:(i*17)%256;
  const copy=original.slice();
  for(const [effect,rule] of Object.entries(CORE_EFFECTS)){
    const result=apply_filter(16,12,original,effect,rule.value);assert.equal(result.length,original.length,effect);assert.deepEqual(original,copy);
  }
  for(const mode of ['normal','multiply','screen','overlay','softLight'])assert.equal(blend_layers(16,12,original,copy,mode,0.5).length,original.length);
});
test('actual WASM rejects invalid dimensions, control ranges and unknown effects/blends',()=>{
  const rgba=new Uint8Array([30,60,90,255]);
  for(const args of [[0,1,rgba,'invert',0],[513,1,rgba,'invert',0],[1,1,new Uint8Array(1),'invert',0],[1,1,rgba,'unknown',0],[1,1,rgba,'highPass',NaN]])assert.throws(()=>apply_filter(...args));
  assert.throws(()=>blend_layers(1,1,rgba,rgba,'normal',Infinity));assert.throws(()=>blend_layers(1,1,rgba,rgba,'unknown',1));
});
test('composition/effect/job limits reject oversized, duplicate, remote and malformed inputs',()=>{
  assert.doesNotThrow(()=>checkedCoreProject(project()));
  assert.throws(()=>checkedCoreProject({...project(),width:513}));
  assert.throws(()=>checkedCoreProject({...project(),layers:[layer(),layer()]}));
  assert.throws(()=>checkedCoreProject({...project(),layers:[{...layer(),png:'https://example.com/private.png'}]}));
  for(const id of ['__proto__','toString','unknown'])assert.throws(()=>checkedEffect({id,value:0,layerId:1}));
  assert.throws(()=>checkedCoreJob({...job(),effect:{id:'invert',value:0,layerId:2}}));
  assert.throws(()=>checkedCoreJob({...job(),layers:[{...job().layers[0],pixels:new ArrayBuffer(1)}]}));
});
test('working history keeps nearest exact Undo and trims older snapshots within its budget',()=>{
  let history=[];for(let n=0;n<20;n++){const previous=project(),next=project();previous.layers[0].name='Before'+n;history=coreHistory(history,previous,next);}
  assert.equal(history.length,10);assert.equal(history[history.length-1].layers[0].name,'Before19');
});
test('real product core source/runtime/provenance hashes are current',()=>{
  const receipt=verifyCore(coreRoot);assert.equal(Object.keys(receipt.runtime).length,2);assert(Object.keys(receipt.sources).length>200);
});
test('core source, runtime bytes and pin module tampering fail closed',t=>{
  const root=fs.mkdtempSync(path.join(os.tmpdir(),'noble-core-pins-'));t.after(()=>fs.rmSync(root,{recursive:true,force:true}));
  fs.mkdirSync(path.join(root,'pkg'));fs.writeFileSync(path.join(root,'UPSTREAM.json'),JSON.stringify({files:{}}));fs.writeFileSync(path.join(root,'adapter.rs'),'synthetic source');
  fs.writeFileSync(path.join(root,'pkg/noble_photocraft_core.js'),'synthetic JS');fs.writeFileSync(path.join(root,'pkg/noble_photocraft_core_bg.wasm'),'synthetic wasm');
  const runtime=runtimePins(root);fs.writeFileSync(path.join(root,'provenance.json'),JSON.stringify({version:1,sources:coreInputs(root),runtime}));fs.writeFileSync(path.join(root,'pin.mjs'),pinSource(runtime));
  assert.doesNotThrow(()=>verifyCore(root));fs.appendFileSync(path.join(root,'adapter.rs'),'changed');assert.throws(()=>verifyCore(root));
  fs.writeFileSync(path.join(root,'adapter.rs'),'synthetic source');fs.appendFileSync(path.join(root,'pkg/noble_photocraft_core_bg.wasm'),'changed');assert.throws(()=>verifyCore(root));
  fs.writeFileSync(path.join(root,'pkg/noble_photocraft_core_bg.wasm'),'synthetic wasm');fs.appendFileSync(path.join(root,'pin.mjs'),'changed');assert.throws(()=>verifyCore(root));
});
globalThis.location={origin:'http://localhost:4176'};
test('worker ownership validates complete results, stops once and ignores late replies',async()=>{
  let worker;const factory=()=>worker={count:0,postMessage(value){this.value=value;},terminate(){this.count++;}};
  const before=job(),run=startPhotoCraft('worker',before,location.origin+'/drawing/',()=>{},factory);
  assert.notEqual(worker.value.layers[0].pixels,before.layers[0].pixels);
  const rejected=assert.rejects(run.promise,/stopped/);run.stop();run.stop();
  worker.onmessage({data:{id:worker.value.id,width:2,height:1,preview:new ArrayBuffer(8)}});await rejected;assert.equal(worker.count,1);
  assert.throws(()=>startPhotoCraft('worker',job(),'https://example.com/drawing/'),/bundled local/);
});
