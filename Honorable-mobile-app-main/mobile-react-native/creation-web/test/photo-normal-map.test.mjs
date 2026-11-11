import test from 'node:test';
import assert from 'node:assert/strict';
import {normalMapSettings,normalMapPixels,normalMapPixelsAsync} from '../photo-normal-map.mjs';
const solid=(color,width=8,height=8)=>new Uint8ClampedArray(Array.from({length:width*height},()=>color).flat());
const step=(vertical=false)=>{const data=solid([0,0,0,255]);for(let y=0;y<8;y++)for(let x=0;x<8;x++)if((vertical?y:x)>=4)data.set([255,255,255,255],(y*8+x)*4);return data;};
const pixel=(data,x,y)=>Array.from(data.slice((y*8+x)*4,(y*8+x)*4+4));
test('constant height and zero influence produce an opaque flat RGB normal texture',()=>{
 for(const color of[[0,0,0,255],[255,255,255,255],[100,30,80,128],[255,0,0,0]]){const result=normalMapPixels(solid(color),8,8,4);for(let at=0;at<result.data.length;at+=4)assert.deepEqual(Array.from(result.data.slice(at,at+4)),[128,128,255,255]);}
 assert.deepEqual(normalMapPixels(step(),8,8,0),normalMapPixels(solid([0,0,0,255]),8,8,4));
});
test('a horizontal height step encodes the derived normalized slope rather than a color effect',()=>{
 const result=normalMapPixels(step(),8,8,2,true,false);assert.deepEqual(pixel(result.data,3,3),[37,128,218,255]);assert.deepEqual(pixel(result.data,4,3),[37,128,218,255]);assert.deepEqual(pixel(result.data,2,3),[128,128,255,255]);
});
test('green direction reverses vertical slope while red and blue remain identical',()=>{
 const source=step(true),up=normalMapPixels(source,8,8,2,true,false),down=normalMapPixels(source,8,8,2,false,false);assert.deepEqual(pixel(up.data,3,3),[128,218,218,255]);assert.deepEqual(pixel(down.data,3,3),[128,37,218,255]);for(let at=0;at<up.data.length;at+=4){assert.equal(up.data[at],down.data[at]);assert.equal(up.data[at+2],down.data[at+2]);}
});
test('wrap and clamp explicitly change edge sampling without changing dimensions or source bytes',()=>{
 const source=step(),before=new Uint8ClampedArray(source),wrap=normalMapPixels(source,8,8,2,true,true),clamp=normalMapPixels(source,8,8,2,true,false);assert.deepEqual(pixel(wrap.data,0,3),[218,128,218,255]);assert.deepEqual(pixel(clamp.data,0,3),[128,128,255,255]);assert.equal(wrap.width,8);assert.equal(wrap.height,8);assert.deepEqual(source,before);
});
test('encoded brightness is multiplied by source alpha and transparent hidden colors do not affect height',()=>{
 const a=step(),b=new Uint8ClampedArray(a);for(let y=0;y<8;y++){a.set([255,255,255,0],(y*8+4)*4);b.set([255,0,255,0],(y*8+4)*4);}assert.deepEqual(normalMapPixels(a,8,8,2),normalMapPixels(b,8,8,2));
 const half=step();for(let y=0;y<8;y++)for(let x=4;x<8;x++)half[(y*8+x)*4+3]=128;const normal=normalMapPixels(half,8,8,2,true,false);assert(pixel(normal.data,3,3)[0]>37);assert(pixel(normal.data,3,3)[2]>218);
});
test('all decoded vectors have positive Z and approximately unit length after 8-bit quantization',()=>{
 const source=solid([0,0,0,255],19,13);let seed=7;for(let i=0;i<source.length;i++){seed=(Math.imul(seed,1664525)+1013904223)>>>0;source[i]=seed>>>24;}
 const result=normalMapPixels(source,19,13,32,false,true);for(let at=0;at<result.data.length;at+=4){const vector=[0,1,2].map(c=>result.data[at+c]/127.5-1);assert(Math.abs(Math.hypot(...vector)-1)<.012);assert(result.data[at+2]>=128);assert.equal(result.data[at+3],255);}
});
test('invalid arrays, dimensions, influences and conventions refuse before allocation of output',()=>{
 for(const size of[7,1025,8.5,Infinity])assert.throws(()=>normalMapSettings(size,8,1));for(const strength of[-1,33,NaN,'2'])assert.throws(()=>normalMapSettings(8,8,strength));assert.throws(()=>normalMapSettings(8,8,1,'up'));assert.throws(()=>normalMapSettings(8,8,1,true,1));for(const value of[null,[],new Uint8Array(4)])assert.throws(()=>normalMapPixels(value,8,8));
});
test('async work snapshots caller, yields across height and vector passes and reports complete progress',async()=>{
 const source=solid([100,120,140,255],8,24);source.set([255,255,255,255],0);const expected=normalMapPixels(source,8,24,8),progress=[];let turns=0;const result=await normalMapPixelsAsync(source,8,24,8,true,true,()=>{},value=>progress.push(value),async()=>{source.fill(0);turns++;});assert.deepEqual(result,expected);assert.equal(turns,6);assert.equal(progress.at(-1),1);assert(progress.every((value,i)=>!i||value>progress[i-1]));
});
test('stale ownership or failed progress does not return partial normal pixels',async()=>{
 let valid=true;const source=step(),before=new Uint8ClampedArray(source);await assert.rejects(normalMapPixelsAsync(source,8,8,4,true,true,()=>{if(!valid)throw Error('stale');},()=>{},async()=>{valid=false;}),/stale/);await assert.rejects(normalMapPixelsAsync(source,8,8,4,true,true,()=>{},()=>{throw Error('closed');},async()=>{}),/closed/);assert.deepEqual(source,before);
});
