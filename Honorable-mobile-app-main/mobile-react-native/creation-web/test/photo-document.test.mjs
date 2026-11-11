import test from 'node:test';
import assert from 'node:assert/strict';
import { documentPixels, documentPixelsAsync, documentSettings } from '../photo-document.mjs';
function oracle(input, w, h, radius, sensitivity, reverse) {
  const gray = Array.from({ length: w * h }, (_, p) => {
    const [r,g,b,a] = input.slice(p * 4, p * 4 + 4);
    const luma = (54*r + 183*g + 19*b)/256;
    return Math.round((reverse ? 255-luma : luma)*a/255 + 255-a);
  });
  const result = new Uint8ClampedArray(input.length);
  for(let y=0;y<h;y++) for(let x=0;x<w;x++) {
    const local=[];
    for(let yy=Math.max(0,y-radius);yy<=Math.min(h-1,y+radius);yy++) for(let xx=Math.max(0,x-radius);xx<=Math.min(w-1,x+radius);xx++) local.push(gray[yy*w+xx]);
    const mean=local.reduce((a,b)=>a+b,0)/local.length;
    const sd=Math.sqrt(local.reduce((a,b)=>a+(b-mean)**2,0)/local.length);
    const value=gray[y*w+x]<=mean*(1+(sensitivity/100)*(sd/128-1))?0:255;
    result.set([value,value,value,255],(y*w+x)*4);
  }
  return result;
}
const mixed=(w,h)=>Uint8ClampedArray.from({length:w*h*4},(_,i)=>(i*73+Math.floor(i/9)*31)%256);
test('clipped local windows match independent two-pass population variance for all pixels',()=>{
  const input=mixed(19,11), original=input.slice();
  for(const radius of [1,3,15,63]) for(const sensitivity of [5,20,50]) for(const reverse of [false,true]) {
    const actual=documentPixels(input,19,11,radius,sensitivity,reverse);
    assert.deepEqual(actual.data,oracle(input,19,11,radius,sensitivity,reverse));
    assert.equal(actual.blackPixels+actual.whitePixels,209);
    assert.equal(actual.changed,Array.from({length:209},(_,p)=>input.slice(p*4,p*4+4).some((v,c)=>v!==actual.data[p*4+c])).filter(Boolean).length);
  }
  assert.deepEqual(input,original);
});
test('uniform black remains black; gray and white become opaque white',()=>{
  for(const value of [0,128,255]) {
    const input=new Uint8ClampedArray(8*8*4);for(let p=0;p<64;p++)input.set([value,value,value,255],p*4);
    const result=documentPixels(input,8,8);
    assert.equal(result.blackPixels,value===0?64:0);
    for(let p=0;p<64;p++)assert.deepEqual([...result.data.slice(p*4,p*4+4)],[value===0?0:255,value===0?0:255,value===0?0:255,255]);
  }
});
test('transparent hidden RGB remains white with either polarity',()=>{
  const input=mixed(8,8);for(let p=0;p<64;p++)input[p*4+3]=0;
  for(const reverse of [false,true])assert.ok(documentPixels(input,8,8,3,20,reverse).data.every(v=>v===255));
});
test('async privately captures source and agrees with synchronous processing',async()=>{
  const input=mixed(19,65),expected=documentPixels(input,19,65,4,30,true),progress=[];let yields=0;
  const actual=await documentPixelsAsync(input,19,65,4,30,true,()=>{},p=>progress.push(p),async()=>{input.fill(0);yields++;});
  assert.deepEqual(actual,expected);assert.equal(yields,6);assert.equal(progress.at(-1),1);assert.ok(progress.every((p,i)=>i===0||p>progress[i-1]));
});
test('cancellation is honored before capture and after final yield',async()=>{
  await assert.rejects(documentPixelsAsync(mixed(8,8),8,8,1,20,false,()=>{throw Error('stopped');}),/stopped/);
  let stop=false;
  await assert.rejects(documentPixelsAsync(mixed(8,8),8,8,1,20,false,()=>{if(stop)throw Error('stopped');},()=>{},async()=>{stop=true;}),/stopped/);
  let turns=0;
  await assert.rejects(documentPixelsAsync(mixed(8,8),8,8,1,20,false,()=>{if(turns===2)throw Error('final');},()=>{},async()=>{turns++;}),/final/);
});
test('invalid dimensions, incomplete buffers and invalid controls refuse',()=>{
  for(const [w,h] of [[7,8],[8,1025],[8.5,8],[NaN,8]])assert.throws(()=>documentSettings(w,h));
  for(const r of [0,64,1.5,NaN])assert.throws(()=>documentSettings(8,8,r));
  for(const k of [4,51,20.1,Infinity])assert.throws(()=>documentSettings(8,8,1,k));
  assert.throws(()=>documentSettings(8,8,1,20,1));
  for(const input of [[],new Uint8Array(255),new Float32Array(256)])assert.throws(()=>documentPixels(input,8,8));
});
test('maximum grid preserves alternating partially transparent black and white stripes',()=>{
  const w=1024,h=1024,input=new Uint8ClampedArray(w*h*4);
  for(let p=0;p<w*h;p++)input.set(p%2?[255,255,255,255]:[0,0,0,128],p*4);
  const result=documentPixels(input,w,h,63,20);
  assert.equal(result.blackPixels,w*h/2);
  for(let p=0;p<w*h;p++) {const v=p%2?255:0;assert.equal(result.data[p*4],v);assert.equal(result.data[p*4+1],v);assert.equal(result.data[p*4+2],v);assert.equal(result.data[p*4+3],255);}
});
