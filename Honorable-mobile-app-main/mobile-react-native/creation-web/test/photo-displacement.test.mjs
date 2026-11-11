import test from 'node:test';
import assert from 'node:assert/strict';
import {displacementSettings,displacementPixels,displacementPixelsAsync} from '../photo-displacement.mjs';
const solid=(color,width=8,height=8)=>new Uint8ClampedArray(Array.from({length:width*height},()=>color).flat());
const gradient=(width=8,height=8)=>{const data=solid([0,0,0,255],width,height);for(let y=0;y<height;y++)for(let x=0;x<width;x++){const at=(y*width+x)*4;data[at]=x*20;data[at+1]=y*20;}return data;};
const pixel=(data,x,y,width=8)=>Array.from(data.slice((y*width+x)*4,(y*width+x)*4+4));
test('RG map drives exact source-coordinate offsets with 128 neutral and transparent outside',()=>{
 const source=gradient(),right=displacementPixels(source,solid([255,128,0,255]),8,8,1,0);assert.deepEqual(pixel(right.data,0,0),[20,0,0,255]);assert.deepEqual(pixel(right.data,7,0),[0,0,0,0]);
 const left=displacementPixels(source,solid([0,128,255,255]),8,8,1,0);assert.deepEqual(pixel(left.data,0,0),[0,0,0,0]);assert.deepEqual(pixel(left.data,1,0),[0,0,0,255]);
 const down=displacementPixels(source,solid([128,255,0,255]),8,8,0,1);assert.deepEqual(pixel(down.data,2,0),[40,20,0,255]);
});
test('fractional bilinear sampling is premultiplied and does not darken transparent borders',()=>{
 const result=displacementPixels(solid([255,0,0,255]),solid([255,128,255,255]),8,8,.5,0);assert.deepEqual(pixel(result.data,7,0),[255,0,0,128]);assert.deepEqual(pixel(result.data,0,0),[255,0,0,255]);
});
test('map opacity scales displacement, blue is ignored and negative amplitudes reverse sampling',()=>{
 const source=gradient(),partial=displacementPixels(source,solid([255,128,0,128]),8,8,1,0);assert.deepEqual(pixel(partial.data,0,0),[10,0,0,255]);
 const a=displacementPixels(source,solid([255,128,0,255]),8,8,-1,0),b=displacementPixels(source,solid([0,128,255,255]),8,8,1,0);assert.deepEqual(a,b);
});
test('neutral or fully transparent maps and transparent sources refuse visible no-op outputs',()=>{
 const source=gradient();assert.throws(()=>displacementPixels(source,solid([128,128,255,255]),8,8,16,16),/no visible change/);assert.throws(()=>displacementPixels(source,solid([255,255,255,0]),8,8,16,16),/no visible change/);assert.throws(()=>displacementPixels(solid([0,255,0,0]),solid([255,255,255,255]),8,8,16,16),/no visible change/);
});
test('inputs are immutable and hidden colors in transparent neighbors do not create color halos',()=>{
 const source=solid([255,0,0,255]);source.set([0,255,0,0],4);const map=solid([255,128,255,255]),before=new Uint8ClampedArray(source);const result=displacementPixels(source,map,8,8,.5,0);assert.deepEqual(pixel(result.data,0,0),[255,0,0,128]);assert.deepEqual(source,before);assert.equal(map[0],255);
});
test('invalid bounds, arrays, nonfinite and zero sampling offsets refuse',()=>{
 for(const size of[7,1025,8.5,Infinity])assert.throws(()=>displacementSettings(size,8,1,1));for(const value of[97,-97,NaN,Infinity,'1'])assert.throws(()=>displacementSettings(8,8,value,1));assert.throws(()=>displacementSettings(8,8,0,0));for(const value of[[],null,new Uint8Array(4)])assert.throws(()=>displacementPixels(value,solid([255,255,0,255]),8,8,1,1));
});
test('async snapshots both arrays, yields and matches sync geometry with exact progress',async()=>{
 const source=gradient(8,32),map=solid([255,128,255,255],8,32),expected=displacementPixels(source,map,8,32,.5,0),progress=[];let turns=0;const result=await displacementPixelsAsync(source,map,8,32,.5,0,()=>{},value=>progress.push(value),async()=>{source.fill(0);map.fill(0);turns++;});assert.deepEqual(result,expected);assert.equal(turns,2);assert.deepEqual(progress,[.5,1]);
});
test('stale ownership during a yield rejects instead of returning partial pixels',async()=>{
 let valid=true;await assert.rejects(displacementPixelsAsync(gradient(8,32),solid([255,128,0,255],8,32),8,32,1,0,()=>{if(!valid)throw Error('stale');},()=>{},async()=>{valid=false;}),/stale/);
});
