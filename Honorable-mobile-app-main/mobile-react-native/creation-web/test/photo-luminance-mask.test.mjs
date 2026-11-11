import test from 'node:test';
import assert from 'node:assert/strict';
import {luminanceMaskSettings,luminanceMaskPixels,luminanceMaskPixelsAsync} from '../photo-luminance-mask.mjs';
const solid=(color,width=8,height=8)=>new Uint8ClampedArray(Array.from({length:width*height},()=>color).flat());
test('white keeps, black removes, gray softens and mask alpha multiplies original alpha',()=>{
 const source=solid([50,100,150,200]),mask=solid([255,255,255,255]);mask.set([0,0,0,255],4);mask.set([128,128,128,255],8);mask.set([255,255,255,128],12);mask.set([255,255,255,0],16);
 const result=luminanceMaskPixels(source,mask,8,8);assert.deepEqual(Array.from(result.data.slice(0,20)),[50,100,150,200,0,0,0,0,50,100,150,100,50,100,150,100,0,0,0,0]);assert.equal(result.changed,4);
});
test('colored mask uses disclosed encoded RGB luminance rather than one channel or alpha alone',()=>{
 const source=solid([255,0,0,255]),mask=solid([255,0,0,255]);mask.set([0,255,0,255],4);mask.set([0,0,255,255],8);const result=luminanceMaskPixels(source,mask,8,8);assert.equal(result.data[3],54);assert.equal(result.data[7],182);assert.equal(result.data[11],18);assert.equal(result.data[0],255);
});
test('inversion reverses luminance but never makes transparent mask pixels opaque',()=>{
 const source=solid([30,60,90,255]),mask=solid([0,0,0,255]);mask.set([255,255,255,255],4);mask.set([0,0,0,0],8);const result=luminanceMaskPixels(source,mask,8,8,true);assert.equal(result.data[3],255);assert.equal(result.data[7],0);assert.equal(result.data[11],0);
});
test('source and mask buffers are preserved and all-zero output has no hidden source RGB',()=>{
 const source=solid([20,40,60,128]),mask=solid([0,0,0,255]),sourceBefore=new Uint8ClampedArray(source),maskBefore=new Uint8ClampedArray(mask);const result=luminanceMaskPixels(source,mask,8,8);assert(result.data.every(value=>value===0));assert.deepEqual(source,sourceBefore);assert.deepEqual(mask,maskBefore);assert.notEqual(result.data.buffer,source.buffer);
});
test('matching opaque white mask and already transparent source refuse no-op duplication',()=>{
 assert.throws(()=>luminanceMaskPixels(solid([1,2,3,255]),solid([255,255,255,255]),8,8),/does not change/);assert.throws(()=>luminanceMaskPixels(solid([1,2,3,0]),solid([0,0,0,255]),8,8),/does not change/);
});
test('bounds, incomplete arrays and nonboolean direction reject before output',()=>{
 for(const value of[7,1025,8.5,Infinity])assert.throws(()=>luminanceMaskSettings(value,8));for(const value of['true',1,null])assert.throws(()=>luminanceMaskSettings(8,8,value));
 for(const value of[null,[],new Uint8Array(4)])assert.throws(()=>luminanceMaskPixels(value,solid([0,0,0,255]),8,8));assert.throws(()=>luminanceMaskPixels(solid([1,2,3,255]),new Uint8Array(4),8,8));
});
test('async snapshots both sources, yields and matches synchronous results with exact progress',async()=>{
 const source=solid([255,0,0,200],8,32),mask=solid([128,128,128,255],8,32),expected=luminanceMaskPixels(source,mask,8,32),progress=[];let turns=0;
 const result=await luminanceMaskPixelsAsync(source,mask,8,32,false,()=>{},value=>progress.push(value),async()=>{source.fill(0);mask.fill(255);turns++;});assert.deepEqual(result,expected);assert.equal(turns,2);assert.deepEqual(progress,[.5,1]);
});
test('late cancellation and progress failure never return a partial masked image',async()=>{
 const source=solid([255,0,0,255],8,32),mask=solid([0,0,0,255],8,32);let valid=true;
 await assert.rejects(luminanceMaskPixelsAsync(source,mask,8,32,false,()=>{if(!valid)throw Error('stale');},()=>{},async()=>{valid=false;}),/stale/);
 await assert.rejects(luminanceMaskPixelsAsync(source,mask,8,32,false,()=>{},()=>{throw Error('closed');},async()=>{}),/closed/);assert.equal(source[3],255);
});
