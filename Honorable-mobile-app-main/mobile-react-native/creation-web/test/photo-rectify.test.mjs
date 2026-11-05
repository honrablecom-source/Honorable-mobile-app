import test from 'node:test';
import assert from 'node:assert/strict';
import { rectificationPlan, rectifyPixels } from '../photo-rectify.mjs';
const corners=(w,h)=>[{x:0,y:0},{x:w-1,y:0},{x:w-1,y:h-1},{x:0,y:h-1}];
const gradient=(w,h)=>Uint8ClampedArray.from({length:w*h*4},(_,i)=>i%4===3?255:i%4===0?Math.floor(i/4)%w*4:i%4===1?Math.floor(i/4/w)*4:117);
const trapezoid=[{x:8,y:0},{x:55,y:0},{x:63,y:63},{x:0,y:63}];

test('full-frame identity retains opaque pixels exactly without mutating input',()=>{
 const input=gradient(16,12),before=input.slice(),result=rectifyPixels(input,16,12,corners(16,12));
 assert.equal(result.width,16);assert.equal(result.height,12);assert.deepEqual(result.data,input);assert.deepEqual(input,before);
});

test('known trapezoid corners and analytic center map to the expected source pixels',()=>{
 const result=rectifyPixels(gradient(64,64),64,64,trapezoid,'square');assert.equal(result.width,65);assert.equal(result.height,65);
 const pixel=(x,y)=>[...result.data.slice((y*result.width+x)*4,(y*result.width+x)*4+4)];
 assert.deepEqual(pixel(0,0),[32,0,117,255]);assert.deepEqual(pixel(64,0),[220,0,117,255]);assert.deepEqual(pixel(64,64),[252,252,117,255]);assert.deepEqual(pixel(0,64),[0,252,117,255]);
 // Symmetric trapezoid: normalized center maps to x=31.5,y=23.5*63/55.
 assert.deepEqual(pixel(32,32),[126,108,117,255]);
});

test('premultiplied-alpha interpolation does not leak invisible green into a red edge',()=>{
 const input=new Uint8ClampedArray(64*64*4);for(let y=0;y<64;y++)for(let x=0;x<64;x++){const at=(y*64+x)*4;input[at]=x<32?255:0;input[at+1]=x<32?0:255;input[at+3]=x<32?255:0;}
 const before=input.slice(),result=rectifyPixels(input,64,64,trapezoid,'square'),at=(32*result.width+32)*4;
 assert.equal(result.data[at],255);assert.equal(result.data[at+1],0);assert.equal(result.data[at+2],0);assert(Math.abs(result.data[at+3]-128)<=1);assert.deepEqual(input,before);
 const clear=rectifyPixels(new Uint8ClampedArray(8*8*4),8,8,corners(8,8));assert(clear.data.every(value=>value===0));
});

test('crossed, concave, reversed, tiny, missing and out-of-bounds corners are refused',()=>{
 const valid=corners(64,64);
 for(const points of [null,[],valid.slice(1),[valid[0],valid[2],valid[1],valid[3]],[valid[0],valid[3],valid[2],valid[1]],[valid[0],valid[1],{x:20,y:10},valid[3]],corners(4,4),valid.map((p,i)=>i? p:{x:-1,y:0}),valid.map((p,i)=>i? p:{x:NaN,y:0}),valid.map((p,i)=>i? p:{x:64,y:0})])assert.throws(()=>rectificationPlan(64,64,points));
});

test('source dimensions, byte lengths and formats stay strictly bounded',()=>{
 for(const [w,h]of [[0,64],[7,64],[64,1025],[NaN,64],[64.5,64]])assert.throws(()=>rectificationPlan(w,h,corners(64,64)));
 assert.throws(()=>rectificationPlan(64,64,corners(64,64),'__proto__'));
 assert.throws(()=>rectifyPixels(new Uint8Array(12),64,64,corners(64,64)));
 assert.throws(()=>rectifyPixels(new Uint16Array(64*64*4),64,64,corners(64,64)));
});

test('all output formats have bounded dimensions and preserve independent corner input',()=>{
 const points=[{x:4,y:5},{x:60,y:3},{x:61,y:60},{x:2,y:61}],before=structuredClone(points);
 for(const [format,ratio]of [['edges',null],['square',1],['portrait',4/5],['wide',16/9]]){const plan=rectificationPlan(64,64,points,format);assert(plan.width>=2&&plan.height>=2&&plan.width<=1024&&plan.height<=1024);if(ratio)assert(Math.abs(plan.width/plan.height-ratio)<.04);assert.deepEqual(points,before);assert.notEqual(plan.corners,points);}
 const large=rectificationPlan(1024,1024,corners(1024,1024));assert.equal(large.width,1024);assert.equal(large.height,1024);
});
