import assert from 'node:assert/strict';
import test from 'node:test';
import {buildCinematic,validateAnalysis} from './cinematic.mjs';

const media = {
  moments:Array.from({length:6}, (_, i) => ({
    clip:i%3,label:'shot '+i,start:12*i,available:180,motion:i*.03,light:.5,
  })),
  beats:Array.from({length:39}, (_, i) => 12*(i+1)),
};

test('uses valid source segments and places all internal edit boundaries on measured beats', () => {
  const draft = buildCinematic(media,[0,1,2,3,4,5]);
  assert.equal(draft.length,6);
  assert.equal(draft.at(-1).at+draft.at(-1).frames,480);
  for (const [index,cut] of draft.entries()) {
    const source = media.moments.find(moment=>moment.clip===cut.clip && moment.start===cut.from);
    assert.ok(source && source.available>=cut.frames);
    if (index) {
      assert.ok(media.beats.includes(cut.at));
      assert.equal(cut.at,draft[index-1].at+draft[index-1].frames);
    }
  }
});

test('increases the count of middle cuts without changing the protected endpoints', () => {
  const draft = buildCinematic(media,[0,1,2,3,4,5]);
  const revision = buildCinematic(media,[4,3,2,1,0,5],draft);
  assert.ok(revision.length>draft.length);
  assert.deepEqual(revision[0],draft[0]);
  assert.deepEqual(revision.at(-1),draft.at(-1));
});

test('rejects unordered beats, unreadable moments and music without enough beats', () => {
  assert.throws(()=>validateAnalysis({...media,beats:[60,48,96,120]}),/detectable beats/);
  assert.throws(()=>buildCinematic({...media,moments:media.moments.map(m=>({...m,available:12}))},[0]),/long enough/);
  assert.throws(()=>buildCinematic({...media,beats:[12,24,36,48]},[0]),/too few beats/);
});
