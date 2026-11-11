import test from 'node:test';
import assert from 'node:assert/strict';
import {despeckleSettings,despecklePixels,despecklePixelsAsync} from '../photo-despeckle.mjs';
const solid=(width=8,height=8,color=[100,120,140,255])=>{const pixels=new Uint8ClampedArray(width*height*4);for(let i=0;i<pixels.length;i+=4)pixels.set(color,i);return pixels;};
const set=(pixels,x,y,value,width=8)=>pixels.set(value,(y*width+x)*4);
const pixel=(pixels,x,y,width=8)=>Array.from(pixels.slice((y*width+x)*4,(y*width+x)*4+4));
test('isolated white and black impulse speckles use immutable original neighborhood medians',()=>{
 const pixels=solid();set(pixels,3,3,[255,255,255,255]);set(pixels,5,5,[0,0,0,255]);const before=new Uint8ClampedArray(pixels);
 const result=despecklePixels(pixels,8,8,30,100);
 assert.equal(result.changed,2);assert.deepEqual(pixel(result.data,3,3),[100,120,140,255]);assert.deepEqual(pixel(result.data,5,5),[100,120,140,255]);assert.deepEqual(pixels,before);assert.notEqual(result.data.buffer,pixels.buffer);
});
test('threshold is inclusive and strength blends only qualified RGB while retaining exact alpha',()=>{
 const pixels=solid(8,8,[100,100,100,128]);set(pixels,3,3,[140,100,100,77]);
 const result=despecklePixels(pixels,8,8,40,50);assert.equal(result.changed,1);assert.deepEqual(pixel(result.data,3,3),[120,100,100,77]);
 assert.throws(()=>despecklePixels(pixels,8,8,41,100),/No pixels/);
});
test('transparent pixels and their hidden colors remain unchanged and never contaminate neighbors',()=>{
 const pixels=solid(8,8,[90,100,110,200]);set(pixels,3,3,[0,255,0,0]);set(pixels,4,3,[255,255,255,101]);const result=despecklePixels(pixels,8,8,30,100);
 assert.deepEqual(pixel(result.data,3,3),[0,255,0,0]);assert.deepEqual(pixel(result.data,4,3),[90,100,110,101]);
 for(let i=3;i<pixels.length;i+=4)assert.equal(result.data[i],pixels[i]);
 const transparent=solid(8,8,[255,0,0,0]);assert.throws(()=>despecklePixels(transparent,8,8),/No pixels/);
});
test('replicated border sampling is finite and keeps uniform areas and broad straight edges',()=>{
 const pixels=solid();set(pixels,0,0,[255,255,255,255]);const result=despecklePixels(pixels,8,8);assert.deepEqual(pixel(result.data,0,0),[100,120,140,255]);
 const edge=solid(8,8,[0,0,0,255]);for(let y=0;y<8;y++)for(let x=4;x<8;x++)set(edge,x,y,[255,255,255,255]);assert.throws(()=>despecklePixels(edge,8,8),/No pixels/);
});
test('invalid dimensions, pixels and cleanup settings refuse before processing',()=>{
 for(const size of [0,7,1025,Infinity,8.5])assert.throws(()=>despeckleSettings(size,8,30,100));
 for(const threshold of [0,256,1.1,NaN,'30'])assert.throws(()=>despeckleSettings(8,8,threshold,100));
 for(const strength of [0,101,1.5,NaN,'50'])assert.throws(()=>despeckleSettings(8,8,30,strength));
 for(const pixels of [[],new Uint8ClampedArray(4),null])assert.throws(()=>despecklePixels(pixels,8,8));
});
test('chunked cleanup matches synchronous output, yields and reports monotonic complete progress',async()=>{
 const pixels=solid(16,24);set(pixels,10,10,[255,255,255,255],16);const progress=[];let turns=0,checks=0;
 const result=await despecklePixelsAsync(pixels,16,24,30,75,()=>checks++,value=>progress.push(value),async()=>{turns++;});
 assert.deepEqual(result,despecklePixels(pixels,16,24,30,75));assert.equal(turns,3);assert(checks>=5);assert.deepEqual(progress,[1/3,2/3,1]);
});
test('asynchronous work takes a private snapshot and refuses stale ownership before returning',async()=>{
 const pixels=solid(8,16);set(pixels,3,12,[255,255,255,255]);const expected=despecklePixels(pixels,8,16);let turns=0;
 const result=await despecklePixelsAsync(pixels,8,16,30,100,()=>{},()=>{},async()=>{if(!turns++)pixels.fill(0);});assert.deepEqual(result,expected);
 const source=solid(8,16);set(source,3,3,[255,255,255,255]);let current=true;
 await assert.rejects(despecklePixelsAsync(source,8,16,30,100,()=>{if(!current)throw Error('stale');},()=>{},async()=>{current=false;}),/stale/);
});
test('async no-op refusal and callback failures never mutate the caller or return partial work',async()=>{
 const pixels=solid(),before=new Uint8ClampedArray(pixels);await assert.rejects(despecklePixelsAsync(pixels,8,8,30,100,()=>{},()=>{},async()=>{}),/No pixels/);assert.deepEqual(pixels,before);
 set(pixels,3,3,[255,255,255,255]);const captured=new Uint8ClampedArray(pixels);
 await assert.rejects(despecklePixelsAsync(pixels,8,8,30,100,()=>{},()=>{throw Error('closed');},async()=>{}),/closed/);assert.deepEqual(pixels,captured);
});
