import test from 'node:test';
import assert from 'node:assert/strict';
import {redactionPlan,redactPixels} from '../photo-redaction.mjs';
const pixels=()=>new Uint8ClampedArray(Array.from({length:8*8},()=>[100,120,140,128]).flat());
test('privacy regions replace underlying RGBA with opaque black and preserve every outside pixel',()=>{
 const source=pixels(),before=new Uint8ClampedArray(source),result=redactPixels(source,8,8,[{x:2,y:3,width:3,height:2}]);assert.equal(result.changed,6);assert.notEqual(result.data.buffer,source.buffer);
 for(let y=0;y<8;y++)for(let x=0;x<8;x++){const at=(y*8+x)*4;assert.deepEqual(Array.from(result.data.slice(at,at+4)),x>=2&&x<5&&y>=3&&y<5?[0,0,0,255]:[100,120,140,128]);}assert.deepEqual(source,before);
});
test('overlapping regions count unique changed pixels, cover edge pixels and do not leak transparent RGB',()=>{
 const source=pixels();source.set([255,255,0,0],0);const result=redactPixels(source,8,8,[{x:0,y:0,width:2,height:2},{x:1,y:1,width:2,height:2},{x:7,y:7,width:1,height:1}]);assert.equal(result.changed,8);assert.deepEqual(Array.from(result.data.slice(0,4)),[0,0,0,255]);assert.deepEqual(Array.from(result.data.slice(-4)),[0,0,0,255]);
});
test('full-photo region supports complete replacement without any original color or alpha remaining',()=>{
 const result=redactPixels(pixels(),8,8,[{x:0,y:0,width:8,height:8}]);assert.equal(result.changed,64);for(let i=0;i<result.data.length;i+=4)assert.deepEqual(Array.from(result.data.slice(i,i+4)),[0,0,0,255]);
});
test('plan snapshots only bounded region coordinates and never retains caller objects',()=>{
 const region={x:0,y:0,width:1,height:1,label:'private'};const result=redactionPlan(8,8,[region]);region.x=7;assert.deepEqual(result,[{x:0,y:0,width:1,height:1}]);assert.equal(redactionPlan(1024,1024,Array(24).fill({x:0,y:0,width:1,height:1})).length,24);
});
test('invalid, fractional, overflowing or excessive blocks reject the complete proposal',()=>{
 for(const regions of[null,[],Array(25).fill({x:0,y:0,width:1,height:1}),[{x:-1,y:0,width:2,height:2}],[{x:0,y:0,width:0,height:1}],[{x:7,y:0,width:2,height:1}],[{x:0,y:7,width:1,height:2}],[{x:0.5,y:0,width:1,height:1}],[{x:NaN,y:0,width:1,height:1}],[{x:0,y:0,width:1,height:1},{x:0,y:0,width:Infinity,height:1}]])assert.throws(()=>redactPixels(pixels(),8,8,regions));
 for(const size of[7,1025,8.5,Infinity])assert.throws(()=>redactionPlan(size,8,[{x:0,y:0,width:1,height:1}]));
 for(const source of[[],null,new Uint8Array(3)])assert.throws(()=>redactPixels(source,8,8,[{x:0,y:0,width:1,height:1}]));
});
test('no-op black regions refuse rather than adding a duplicate copy',()=>{
 const source=new Uint8ClampedArray(256);for(let i=3;i<source.length;i+=4)source[i]=255;assert.throws(()=>redactPixels(source,8,8,[{x:0,y:0,width:8,height:8}]),/already opaque black/);
});
