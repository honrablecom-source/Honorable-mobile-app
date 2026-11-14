import {test} from 'node:test';
import assert from 'node:assert/strict';
import {paintAlpha,alphaSnapshot,restoreAlpha} from './ai-mask.mjs';
const image=()=>({width:16,height:16,rgba:new Uint8ClampedArray(16*16*4).fill(180)});
test('erase/restore changes only mask alpha and preserves original transparency',()=>{
  const source=image();source.rgba[3]=0;const original=source.rgba.slice(),result={...source,rgba:source.rgba.slice()};
  paintAlpha(source,result,8,8,5,'erase');assert.equal(result.rgba[(8*16+8)*4+3],0);
  paintAlpha(source,result,8,8,5,'restore');assert.equal(result.rgba[(8*16+8)*4+3],180);
  paintAlpha(source,result,0,0,5,'restore');assert.equal(result.rgba[3],0);
  assert.deepEqual(source.rgba,original);
  for(let i=0;i<result.rgba.length;i++)if(i%4!==3)assert.equal(result.rgba[i],original[i]);
});
test('a bounded alpha-only Undo snapshot restores exact state at image edges',()=>{
  const source=image(),result={...source,rgba:source.rgba.slice()},saved=alphaSnapshot(result);
  assert.equal(saved.byteLength,16*16);
  paintAlpha(source,result,0,0,12,'erase');assert.notDeepEqual(result.rgba,source.rgba);
  restoreAlpha(result,saved);assert.deepEqual(result.rgba,source.rgba);
});
test('malformed brushes/history refuse without changing the current mask',()=>{
  const source=image(),result={...source,rgba:source.rgba.slice()};
  for(const args of [[NaN,0,5,'erase'],[0,0,0,'erase'],[0,0,129,'erase'],[0,0,5,'unknown']])assert.throws(()=>paintAlpha(source,result,...args));
  assert.throws(()=>restoreAlpha(result,new Uint8Array(1)));
  assert.deepEqual(result.rgba,source.rgba);
});
