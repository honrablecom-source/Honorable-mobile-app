import {test} from 'node:test';
import assert from 'node:assert/strict';
import {DENOISE_SIDE,DENOISE_HALO,checkedDenoiseImage,noiseTiles,noiseTensor,mergeNoiseTile,luminance} from '../neural-denoise.mjs';
const image=(width,height)=>({width,height,rgba:new Uint8ClampedArray(width*height*4)});
test('overlapped tiles cover every output pixel exactly once and retain 18px convolution halo',()=>{
  assert(DENOISE_HALO>17);assert.equal(DENOISE_SIDE,128);
  for(const [width,height] of [[1,1],[92,92],[93,95],[256,256],[160,96]]){
    const input=image(width,height),covered=new Uint8Array(width*height),tiles=noiseTiles(input);
    for(const tile of tiles)for(let y=tile.y;y<tile.y+tile.height;y++)for(let x=tile.x;x<tile.x+tile.width;x++)covered[y*width+x]++;
    assert(covered.every(value=>value===1));assert(tiles.length<=9);
  }
});
test('luminance preprocessing uses normalized RGB and clamps input borders without mutation',()=>{
  const input=image(2,1);input.rgba.set([255,0,0,0,0,255,0,127]);const old=input.rgba.slice();
  const tensor=noiseTensor(input,noiseTiles(input)[0]);
  assert.equal(tensor.length,16384);assert(Math.abs(tensor[0]-0.299)<1e-6);
  assert(Math.abs(tensor[18*128+19]-0.587)<1e-6);assert(Math.abs(tensor.at(-1)-0.587)<1e-6);
  assert.deepEqual(input.rgba,old);assert.equal(luminance(255,255,255),1);
});
test('identity neural predictions reconstruct tiled RGB/alpha exactly, including seams and corners',()=>{
  const input=image(256,95);for(let i=0;i<input.rgba.length;i++)input.rgba[i]=(i*13+7)%256;
  const before=input.rgba.slice(),result=new Uint8ClampedArray(before.length);
  for(const tile of noiseTiles(input))mergeNoiseTile(input,result,tile,noiseTensor(input,tile),[1,1,128,128]);
  assert.deepEqual(result,before);assert.deepEqual(input.rgba,before);
});
test('learned luminance delta retains color differences until clipping and never infers alpha',()=>{
  const input=image(1,1);input.rgba.set([30,60,90,43]);const tile=noiseTiles(input)[0],output=noiseTensor(input,tile);
  output[18*128+18]+=20/255;const result=mergeNoiseTile(input,input.rgba.slice(),tile,output,[1,1,128,128]);
  assert.deepEqual([...result],[50,80,110,43]);
});
test('invalid dimensions, tile bounds, tensor shapes and nonfinite predictions fail closed',()=>{
  assert.throws(()=>checkedDenoiseImage(image(257,1)));assert.throws(()=>checkedDenoiseImage(image(0,1)));
  const input=image(1,1),tile=noiseTiles(input)[0];
  assert.throws(()=>noiseTensor(input,{x:1,y:0,width:1,height:1}));
  assert.throws(()=>mergeNoiseTile(input,input.rgba.slice(),tile,new Float32Array(1),[1,1,1,1]));
  const output=noiseTensor(input,tile);output[18*128+18]=NaN;
  assert.throws(()=>mergeNoiseTile(input,input.rgba.slice(),tile,output,[1,1,128,128]),/invalid pixels/);
});
