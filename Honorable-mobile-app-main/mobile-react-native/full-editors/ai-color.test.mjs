import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {COLOR_TABLES,colorTensor,fusedColorLut,sampleColorLut,colorPixels,mixColorPixels} from './ai-color.mjs';
const reference=JSON.parse(readFileSync(new URL('./color-assets/reference.json',import.meta.url)));
const bytes=readFileSync(new URL('./color-assets/lut-bases.f32',import.meta.url));
assert.equal(bytes.length,COLOR_TABLES.bytes);assert.equal(createHash('sha256').update(bytes).digest('hex'),COLOR_TABLES.sha256);
const bases=new Float32Array(bytes.buffer.slice(bytes.byteOffset,bytes.byteOffset+bytes.byteLength));
test('signed learned-table fusion matches independent PyTorch trilinear golden colors',()=>{
  const lut=fusedColorLut(bases,reference.weights);let maximum=0;
  for(let i=0;i<reference.pixels.length;i++){
    const output=sampleColorLut(lut,...reference.pixels[i]);
    for(let c=0;c<3;c++)maximum=Math.max(maximum,Math.abs(output[c]-reference.mapped[i][c]));
  }
  assert.ok(maximum<2e-5,String(maximum));
});
test('blend strength preserves source and alpha, with exact original at zero',()=>{
  const source={width:2,height:2,rgba:new Uint8ClampedArray([10,20,30,0,240,80,35,128,0,255,255,200,255,255,255,255])};
  const original=source.rgba.slice();assert.deepEqual(colorPixels(source,bases,reference.weights,0),original);
  const changed=colorPixels(source,bases,reference.weights,.7);
  assert.notDeepEqual(changed,original);assert.deepEqual(source.rgba,original);
  for(let i=3;i<changed.length;i+=4)assert.equal(changed[i],original[i]);
});
test('bad neural weights, tables, pixels and strength refuse without fallback',()=>{
  const source={width:1,height:1,rgba:new Uint8ClampedArray([80,70,60,255])};
  assert.throws(()=>colorPixels(source,bases,[NaN,0,0]));
  assert.throws(()=>colorPixels(source,bases,reference.weights,1.1));
  assert.throws(()=>fusedColorLut(new Float32Array(1),reference.weights));
  assert.throws(()=>sampleColorLut(new Float32Array(3*33**3),-1,0,0));
  assert.throws(()=>colorTensor(source));
});
test('foreground blending preserves zero-mask pixels and alpha and respects soft edges',()=>{
  const source={width:3,height:1,rgba:new Uint8ClampedArray([10,20,30,255,10,20,30,128,10,20,30,90])};
  const enhanced=new Uint8ClampedArray([110,120,130,0,110,120,130,0,110,120,130,0]);
  const mask=new Uint8ClampedArray([0,0,0,0,0,0,0,128,0,0,0,255]);
  assert.deepEqual(mixColorPixels(source,enhanced,1,mask),new Uint8ClampedArray([10,20,30,255,60,70,80,128,110,120,130,90]));
  assert.deepEqual(mixColorPixels(source,enhanced,0,mask),source.rgba);
  assert.throws(()=>mixColorPixels(source,enhanced,1,new Uint8ClampedArray(1)));
});
