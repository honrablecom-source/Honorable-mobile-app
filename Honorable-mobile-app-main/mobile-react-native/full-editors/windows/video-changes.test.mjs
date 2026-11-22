import assert from 'node:assert/strict';
import test from 'node:test';
import {timelineChangeRows} from './video-changes.js';

const shot = (id,item,start,duration = 24)=>({
  id,item,kind:'video',track:'V1',index:0,name:'Shot '+item,start,duration,source:0,speed:1,gain:0,reverse:false,effects:[],
});

test('a revised sequence shows only the affected range even when every clip receives a new ID',()=>{
  const before = {sequence:1,clips:[shot(3,100,0),shot(4,101,24),shot(5,102,48)]};
  const after = {sequence:2,clips:[shot(8,100,0),shot(9,103,24,12),shot(10,104,36,12),shot(11,102,48)]};
  const rows = timelineChangeRows(before,after);
  assert.deepEqual(rows.map(row=>[row.item,row.start,row.label]),[[103,24,'Added'],[101,24,'Removed'],[104,36,'Added']]);
});

test('native clip IDs identify moves, trims and effects within the same sequence',()=>{
  const clip = shot(3,100,0);
  const before = {sequence:1,clips:[clip,shot(4,101,24)]};
  const after = {sequence:1,clips:[{...clip,start:4,duration:20,effects:[{effect:'motion'}]},shot(4,101,24)]};
  const [row,...rest] = timelineChangeRows(before,after);
  assert.equal(row.label,'Moved · Length changed · Effects changed');
  assert.equal(row.start,4);
  assert.deepEqual(rest,[]);
});

test('copying a sequence with new clip IDs produces no false changes',()=>{
  const before = {sequence:1,clips:[shot(3,100,0),shot(4,100,24)]};
  const after = {sequence:2,clips:[shot(7,100,0),shot(8,100,24)]};
  assert.deepEqual(timelineChangeRows(before,after),[]);
});
