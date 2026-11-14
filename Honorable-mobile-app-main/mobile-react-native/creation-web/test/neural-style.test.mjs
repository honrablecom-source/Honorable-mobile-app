import {test} from 'node:test';
import assert from 'node:assert/strict';
import {STYLE_SIDE, checkedStyleImage, styleTensor, stylePixels, blendStyle} from '../neural-style.mjs';
import {startNeuralStyle} from '../neural-style-client.mjs';

const image = () => ({width: 2, height: 1, rgba: new Uint8ClampedArray([1,2,3,0,201,202,203,127])});
test('fixed graph preprocessing centers aspect-preserved RGB and repeats edge pixels', () => {
  const original = image(), before = original.rgba.slice(), {tensor, left, top} = styleTensor(original);
  assert.equal(left,111); assert.equal(top,111); assert.equal(tensor.length,3*224*224);
  assert.deepEqual([...tensor.slice(0,3)],[1,1,1]);
  assert.equal(tensor[top*224+left+1],201);
  assert.equal(tensor[224*224+top*224+left+1],202);
  assert.equal(tensor[2*224*224+223*224+223],203);
  assert.deepEqual(original.rgba,before);
});
test('output crops back to source aspect, clips/truncates RGB and preserves exact alpha', () => {
  const input=image(), output=new Float32Array(3*STYLE_SIDE*STYLE_SIDE), offset=111*224+111;
  output[offset]=-3;output[offset+1]=300;output[224*224+offset]=23.9;output[224*224+offset+1]=7.5;
  output[2*224*224+offset]=99.2;output[2*224*224+offset+1]=12.8;
  assert.deepEqual([...stylePixels(input,output,[1,3,224,224])],[0,23,99,0,255,7,12,127]);
  output[offset]=NaN;assert.throws(()=>stylePixels(input,output,[1,3,224,224]),/invalid pixels/);
  assert.throws(()=>stylePixels(input,new Float32Array(3),[1,3,1,1]),/unsupported/);
});
test('invalid inputs and neural blend changes fail closed without changing source or alpha',()=>{
  for (const value of [null,{width:0,height:1,rgba:new Uint8ClampedArray(4)},
    {width:225,height:1,rgba:new Uint8ClampedArray(900)}, {width:1,height:1,rgba:new Uint8Array(4)}]) assert.throws(()=>checkedStyleImage(value));
  const input=image(),paint=new Uint8ClampedArray([100,100,100,255,100,100,100,0]);
  assert.deepEqual(blendStyle(input,paint,0),input.rgba);
  assert.deepEqual([...blendStyle(input,paint,1)],[100,100,100,0,100,100,100,127]);
  for (const strength of [-1,2,NaN,Infinity]) assert.throws(()=>blendStyle(input,paint,strength));
});
function workerHarness() {
  let worker;
  const factory=()=>worker={terminateCount:0,terminate(){this.terminateCount++;},postMessage(value){this.value=value;}};
  return {factory,get worker(){return worker;}};
}
globalThis.location={origin:'http://localhost:4176'};
test('worker result must match request and exact image size; original transfer remains intact',async()=>{
  const h=workerHarness(),input=image(),events=[],job=startNeuralStyle('test',input,location.origin+'/drawing/',x=>events.push(x),h.factory),w=h.worker;
  assert.notEqual(w.value.rgba,input.rgba.buffer);
  w.onmessage({data:{id:w.value.id+1,error:'wrong owner'}});
  w.onmessage({data:{id:w.value.id,phase:'Working'}});
  const buffer=new Uint8ClampedArray([9,8,7,0,6,5,4,127]).buffer;
  w.onmessage({data:{id:w.value.id,width:2,height:1,pixels:buffer}});
  assert.deepEqual([...await job.promise],[9,8,7,0,6,5,4,127]);
  assert.deepEqual(events,['Working']); assert.equal(w.terminateCount,1);job.stop();assert.equal(w.terminateCount,1);
  assert.deepEqual(input.rgba,image().rgba);
});
test('Stop terminates actual worker ownership and ignores all subsequent output',async()=>{
  const h=workerHarness(),job=startNeuralStyle('test',image(),location.origin+'/drawing/',()=>{},h.factory),w=h.worker;
  const rejection=assert.rejects(job.promise,/stopped/);job.stop();job.stop();
  w.onmessage({data:{id:w.value.id,width:2,height:1,pixels:new ArrayBuffer(8)}});
  await rejection;assert.equal(w.terminateCount,1);
});
test('remote assets, malformed replies and worker failures never produce a fallback image',async()=>{
  assert.throws(()=>startNeuralStyle('x',image(),'https://example.com/drawing/'),/bundled local/);
  for (const kind of ['shape','error']) {
    const h=workerHarness(),job=startNeuralStyle('test',image(),location.origin+'/drawing/',()=>{},h.factory),w=h.worker;
    const rejection=assert.rejects(job.promise);
    if(kind==='shape') w.onmessage({data:{id:w.value.id,width:2,height:1,pixels:new ArrayBuffer(7)}}); else w.onerror({});
    await rejection;assert.equal(w.terminateCount,1);
  }
});
