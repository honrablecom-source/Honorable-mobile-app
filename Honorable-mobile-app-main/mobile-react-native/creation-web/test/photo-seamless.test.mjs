import test from 'node:test';
import assert from 'node:assert/strict';
import {seamlessSettings,seamlessPixels,seamlessPixelsAsync} from '../photo-seamless.mjs';
const gradient=(width=8,height=8)=>{const data=new Uint8ClampedArray(width*height*4);for(let y=0;y<height;y++)for(let x=0;x<width;x++)data.set([x*20%256,y*30%256,(x+y)*10%256,255],(y*width+x)*4);return data;};
const pixel=(data,x,y,width)=>Array.from(data.slice((y*width+x)*4,(y*width+x)*4+4));
const edges=(result)=>{const{data,width,height}=result;for(let y=0;y<height;y++)assert.deepEqual(pixel(data,0,y,width),pixel(data,width-1,y,width));for(let x=0;x<width;x++)assert.deepEqual(pixel(data,x,0,width),pixel(data,x,height-1,width));};
test('opposite borders and all four corners match exactly without resizing or mutating the source',()=>{
 const source=gradient(),before=new Uint8ClampedArray(source),result=seamlessPixels(source,8,8,2);edges(result);assert.equal(result.width,8);assert.equal(result.height,8);assert.deepEqual(source,before);assert.notEqual(source.buffer,result.data.buffer);assert(result.changed>0);assert.deepEqual(pixel(result.data,0,0,8),[70,105,70,255]);
});
test('interior outside blend bands stays byte-identical and wider bands taper toward original pixels',()=>{
 const source=gradient(16,12),result=seamlessPixels(source,16,12,3);for(let y=3;y<9;y++)for(let x=3;x<13;x++)assert.deepEqual(pixel(result.data,x,y,16),pixel(source,x,y,16));edges(result);
 const narrow=seamlessPixels(source,16,12,1);assert.deepEqual(pixel(narrow.data,1,5,16),pixel(source,1,5,16));assert.notDeepEqual(pixel(result.data,1,5,16),pixel(source,1,5,16));
});
test('premultiplied blending retains visible colors and alpha without transparent-neighbor halos',()=>{
 const source=gradient();for(let y=0;y<8;y++){source.set([255,0,0,255],(y*8)*4);source.set([0,255,0,0],(y*8+7)*4);}const result=seamlessPixels(source,8,8,1);assert.deepEqual(pixel(result.data,0,3,8),[255,0,0,128]);assert.deepEqual(pixel(result.data,7,3,8),[255,0,0,128]);edges(result);
});
test('odd rectangular dimensions and maximal legal blend preserve matching alpha borders',()=>{
 for(const [width,height]of[[9,13],[15,8],[8,15],[8,8]]){const source=gradient(width,height);for(let at=3;at<source.length;at+=4)source[at]=(at*13)%256;const result=seamlessPixels(source,width,height,Math.floor(Math.min(width,height)/2));edges(result);assert.equal(result.data.length,source.length);}
});
test('solid or invisible originals refuse no-op duplicates, including hidden transparent RGB changes',()=>{
 const solid=new Uint8ClampedArray(256);for(let at=0;at<solid.length;at+=4)solid.set([100,120,140,255],at);assert.throws(()=>seamlessPixels(solid,8,8,2),/no visible change/);const invisible=gradient();for(let at=3;at<invisible.length;at+=4)invisible[at]=0;assert.throws(()=>seamlessPixels(invisible,8,8,2),/no visible change/);
});
test('invalid dimensions, incomplete pixels and overlapping blend bands are refused',()=>{
 for(const size of[7,1025,8.5,Infinity])assert.throws(()=>seamlessSettings(size,8,1));for(const blend of[0,5,1.5,NaN,'2'])assert.throws(()=>seamlessSettings(8,8,blend));for(const value of[null,[],new Uint8Array(4)])assert.throws(()=>seamlessPixels(value,8,8,2));
});
test('chunked work snapshots source, matches sync output and yields with monotonic complete progress',async()=>{
 const source=gradient(16,24),expected=seamlessPixels(source,16,24,8),progress=[];let turns=0;const result=await seamlessPixelsAsync(source,16,24,8,()=>{},value=>progress.push(value),async()=>{source.fill(0);turns++;});assert.deepEqual(result,expected);assert.equal(turns,4);assert.equal(progress.at(-1),1);assert(progress.every((value,i)=>!i||value>progress[i-1]));edges(result);
});
test('late ownership loss and progress failures refuse partial output without caller mutation',async()=>{
 const source=gradient(16,24),before=new Uint8ClampedArray(source);let live=true;await assert.rejects(seamlessPixelsAsync(source,16,24,4,()=>{if(!live)throw Error('stale');},()=>{},async()=>{live=false;}),/stale/);assert.deepEqual(source,before);await assert.rejects(seamlessPixelsAsync(source,16,24,4,()=>{},()=>{throw Error('closed');},async()=>{}),/closed/);
});
