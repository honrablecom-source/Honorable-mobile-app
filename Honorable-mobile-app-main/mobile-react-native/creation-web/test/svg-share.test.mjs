import test from 'node:test';
import assert from 'node:assert/strict';
import { sendSvgArtwork, MAX_SVG_BYTES, SVG_CHUNK_CHARS } from '../svg-share.mjs';
const svg='<svg xmlns="http://www.w3.org/2000/svg"><text>café 🎬</text></svg>';
function bridge(fail='') {const calls=[];return{calls,beginSvgShare:(...a)=>{calls.push(['begin',...a]);return fail!=='begin';},appendSvgShareChunk:(...a)=>{calls.push(['chunk',...a]);return fail!=='chunk';},finishSvgShare:(...a)=>{calls.push(['finish',...a]);if(fail==='finish')throw Error('Synthetic finish failure');},releaseSvgShare:(...a)=>calls.push(['release',...a])};}
test('actual SVG text reaches one native request without project/source metadata',()=>{const b=bridge();sendSvgArtwork(b,3,svg);assert.deepEqual(b.calls,[['begin',3,svg.length],['chunk',3,svg],['finish',3]]);});
test('Unicode split boundaries remain well formed while chunks reconstruct the exact vector file',()=>{
 const value='<svg><text>'+'A'.repeat(SVG_CHUNK_CHARS-'<svg><text>'.length-1)+'🎬</text></svg>',b=bridge();sendSvgArtwork(b,4,value);
 const chunks=b.calls.filter(x=>x[0]==='chunk').map(x=>x[2]);assert(chunks.length>1);assert.equal(chunks.join(''),value);
 for(const chunk of chunks){assert(chunk.length<=SVG_CHUNK_CHARS);assert.equal(new TextDecoder().decode(new TextEncoder().encode(chunk)),chunk);}
});
test('request and UTF-8 payload budgets fail before native admission',()=>{
 const b=bridge();for(const id of [0,-1,1.5,1_000_000_001,NaN])assert.throws(()=>sendSvgArtwork(b,id,svg),/request/);
 for(const value of ['',null,'not SVG','<svg>'+'é'.repeat(MAX_SVG_BYTES/2)+'</svg>','<svg>\uD800</svg>','x'.repeat(MAX_SVG_BYTES+1)])assert.throws(()=>sendSvgArtwork(b,1,value),/Prepare|large|invalid/);
 assert.deepEqual(b.calls,[]);
});
test('browser or older native support remains actionable without changing any file',()=>{assert.throws(()=>sendSvgArtwork(null,1,svg),/Android/);assert.throws(()=>sendSvgArtwork({beginSvgShare:()=>true},1,svg),/Android/);});
test('rejected admission never releases another operation; transfer failures release only the admitted request',()=>{
 const denied=bridge('begin');assert.throws(()=>sendSvgArtwork(denied,8,svg),/operation/);assert.deepEqual(denied.calls,[['begin',8,svg.length]]);
 for(const failure of ['chunk','finish']){const b=bridge(failure);assert.throws(()=>sendSvgArtwork(b,9,svg),/stopped|failure/);assert.deepEqual(b.calls.at(-1),['release',9]);}
});
test('cleanup failure retains the original useful transfer error',()=>{const b=bridge('chunk');b.releaseSvgShare=()=>{throw Error('cleanup');};assert.throws(()=>sendSvgArtwork(b,7,svg),/transfer stopped/);});
