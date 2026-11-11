import test from 'node:test';
import assert from 'node:assert/strict';
import {distanceFieldSettings,distanceTransformSquared,distanceFieldPixels,distanceFieldPixelsAsync} from '../photo-distance-field.mjs';
const rgba=(mask,width,height)=>{const out=new Uint8ClampedArray(width*height*4);for(let index=0;index<mask.length;index++)out.set([index*13%256,index*19%256,90,mask[index]?255:0],index*4);return out;};
const half=(width=8,height=8)=>Uint8Array.from({length:width*height},(_,index)=>index%width<width/2?1:0);
function brute(mask,width,height,target){return Array.from({length:mask.length},(_,index)=>{let best=Infinity;for(let other=0;other<mask.length;other++)if(mask[other]===target){const dx=index%width-other%width,dy=Math.floor(index/width)-Math.floor(other/width);best=Math.min(best,dx*dx+dy*dy);}return best;});}
test('squared distances exactly equal exhaustive Euclidean minima on synthetic random rectangular masks',()=>{
 let seed=17;for(const[width,height]of[[8,8],[9,13],[15,8]])for(let trial=0;trial<20;trial++){const mask=Uint8Array.from({length:width*height},()=>{seed=(Math.imul(seed,1664525)+1013904223)>>>0;return seed>>>31;});const before=new Uint8Array(mask);for(const target of[0,1])assert.deepEqual(Array.from(distanceTransformSquared(mask,width,height,Boolean(target))),brute(mask,width,height,target));assert.deepEqual(mask,before);}
});
test('empty rows/columns and missing target classes stay finite where possible and Infinity where absent',()=>{
 const mask=new Uint8Array(64);mask[3*8+3]=1;assert.equal(distanceTransformSquared(mask,8,8)[0],18);assert.equal(distanceTransformSquared(mask,8,8)[7*8+7],32);assert(distanceTransformSquared(new Uint8Array(64),8,8).every(value=>value===Infinity));assert(distanceTransformSquared(new Uint8Array(64),8,8,false).every(value=>value===0));
});
test('signed pixel-center distances encode inside white/outside black with zero level128 and clipping',()=>{
 const source=rgba(half(),8,8),result=distanceFieldPixels(source,8,8,128,4);assert.equal(result.inside,32);assert.equal(result.data[(3*8+0)*4],255);assert.equal(result.data[(3*8+3)*4],160);assert.equal(result.data[(3*8+4)*4],96);assert.equal(result.data[(3*8+7)*4],0);for(let at=0;at<result.data.length;at+=4){assert.equal(result.data[at],result.data[at+1]);assert.equal(result.data[at],result.data[at+2]);assert.equal(result.data[at+3],255);}
});
test('diagonals use Euclidean distance and retain holes/disconnected components rather than Manhattan steps',()=>{
 const mask=new Uint8Array(64);mask[3*8+3]=1;mask[6*8+6]=1;const result=distanceFieldPixels(rgba(mask,8,8),8,8,128,4);assert.equal(result.data[(4*8+4)*4],83);assert.equal(result.data[(3*8+3)*4],160);assert.equal(result.inside,2);const ring=new Uint8Array(64).fill(1);ring[3*8+3]=0;const hole=distanceFieldPixels(rgba(ring,8,8),8,8,128,4);assert.equal(hole.data[(3*8+3)*4],96);
});
test('alpha threshold is inclusive, source colors are irrelevant and original buffers remain untouched',()=>{
 const a=rgba(half(),8,8);a[3]=128;a[7]=127;const before=new Uint8ClampedArray(a),b=new Uint8ClampedArray(a);for(let at=0;at<b.length;at+=4)b[at]=b[at+1]=b[at+2]=255;const first=distanceFieldPixels(a,8,8,128,8),second=distanceFieldPixels(b,8,8,128,8);assert.deepEqual(first,second);assert.equal(first.inside,31);assert.deepEqual(a,before);assert.notEqual(first.data.buffer,a.buffer);
});
test('thresholds producing all-one-class cutouts are refused instead of inventing an external contour',()=>{
 for(const alpha of[0,255]){const source=new Uint8ClampedArray(256);for(let at=3;at<source.length;at+=4)source[at]=alpha;assert.throws(()=>distanceFieldPixels(source,8,8),/both inside and outside/);}
});
test('bounds, class arrays, incomplete pixels, thresholds and spreads are validated',()=>{
 for(const size of[7,1025,8.5,Infinity])assert.throws(()=>distanceFieldSettings(size,8));for(const value of[0,256,1.5,NaN,'128'])assert.throws(()=>distanceFieldSettings(8,8,value,8));for(const value of[0,129,1.5,NaN])assert.throws(()=>distanceFieldSettings(8,8,128,value));for(const value of[null,[],new Uint8Array(4)])assert.throws(()=>distanceFieldPixels(value,8,8));assert.throws(()=>distanceTransformSquared(new Uint8Array(64).fill(2),8,8));assert.throws(()=>distanceTransformSquared(new Uint8Array(64),8,8,'inside'));
});
test('chunked classification and two transforms snapshot input, match sync results and report exact progress',async()=>{
 const source=rgba(half(8,24),8,24),expected=distanceFieldPixels(source,8,24,128,8),progress=[];let turns=0;const result=await distanceFieldPixelsAsync(source,8,24,128,8,()=>{},value=>progress.push(value),async()=>{source.fill(0);turns++;});assert.deepEqual(result,expected);assert.equal(turns,14);assert.equal(progress.at(-1),1);assert(progress.every((value,i)=>!i||value>progress[i-1]));
});
test('late cancellation, progress errors and invalid cutouts never return a partial field',async()=>{
 const source=rgba(half(8,24),8,24),before=new Uint8ClampedArray(source);let live=true;await assert.rejects(distanceFieldPixelsAsync(source,8,24,128,8,()=>{if(!live)throw Error('stale');},()=>{},async()=>{live=false;}),/stale/);await assert.rejects(distanceFieldPixelsAsync(source,8,24,128,8,()=>{},()=>{throw Error('closed');},async()=>{}),/closed/);assert.deepEqual(source,before);await assert.rejects(distanceFieldPixelsAsync(new Uint8Array(256),8,8,128,8,()=>{},()=>{},async()=>{}),/both inside and outside/);
});
