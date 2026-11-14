import {test} from 'node:test';
import assert from 'node:assert/strict';
import {OBJECT_SIDE,objectTensor,objectPixels,checkedObjectImage} from './ai-object.mjs';
const image=(width,height)=>({width,height,rgba:new Uint8ClampedArray(width*height*4).fill(100)});
test('object preprocessing follows bounded CHW and per-image normalization',()=>{
  const source=image(320,320);source.rgba[0]=200;const output=objectTensor(source);
  assert.equal(output.length,3*320*320);
  assert.ok(Math.abs(output[0]-(1-.485)/.229)<1e-5);
  assert.ok(Math.abs(output[320*320]-(.5-.456)/.224)<1e-5);
  assert.equal(source.rgba[0],200);
});
test('object mask changes only alpha and never increases existing transparency',()=>{
  const source=image(4,2),original=source.rgba.slice();source.rgba[3]=0;original[3]=0;
  const mask=new Float32Array(320*320);
  for(let y=0;y<320;y++)for(let x=0;x<320;x++)mask[y*320+x]=x/319;
  const output=objectPixels(source,mask,[1,1,320,320]);
  assert.deepEqual(source.rgba,original);
  for(let i=0;i<output.length;i++)if(i%4!==3)assert.equal(output[i],source.rgba[i]);else assert.ok(output[i]<=source.rgba[i]);
  assert.equal(output[3],0);assert.ok(output[7]<output[15]);
});
test('flat, non-finite, wrong-shaped and oversized data refuse without fallback',()=>{
  const source=image(2,2),flat=new Float32Array(320*320);
  assert.throws(()=>objectPixels(source,flat,[1,1,320,320]),/No useful/);
  flat[0]=NaN;assert.throws(()=>objectPixels(source,flat,[1,1,320,320]),/invalid pixels/);
  assert.throws(()=>objectPixels(source,flat,[1,3,320,320]),/unsupported/);
  assert.throws(()=>objectTensor(source),/320x320/);
  assert.throws(()=>checkedObjectImage(image(1025,1)),/1024/);
  assert.equal(OBJECT_SIDE,320);
});
