import test from 'node:test';
import assert from 'node:assert/strict';
import { starterPlan, STARTER_STYLES } from '../starters.mjs';
test('all starters create independent editable white cards with bounded labels and neutral colors', () => {
 for (const style of STARTER_STYLES) {
  const plan = starterPlan(style, 'My headline', 'Details\nA second line', 'Signed');
  assert.equal(plan[0].width, 640); assert.equal(plan[0].height, 960); assert.equal(plan[0].backgroundColor, '#ffffff');
  assert.deepEqual(plan.filter(s => s.label).map(s => s.label.text).slice(1), ['My headline', 'Details\nA second line', 'Signed']);
  for (const s of plan) { assert.equal(s.type, 'rectangle'); assert(s.x >= 0 && s.y >= 0 && s.x + s.width <= 640 && s.y + s.height <= 960); assert(!s.locked); assert(['transparent','#ffffff','#111111','#eeeeee'].includes(s.backgroundColor)); }
  plan[0].width = 1; assert.equal(starterPlan(style,'My headline','','')[0].width,640);
 }
});
test('optional details/signoff omit their blocks and plain Unicode/literal markup stays text', () => {
 const headline='作品🙂'.repeat(20);
 const plan=starterPlan('quote',headline,'<b>R&D</b> https://example.com','=1+2');
 assert.equal(plan.find(s=>s.label?.text===headline).label.text,headline);
 assert(plan.some(s=>s.label?.text==='<b>R&D</b> https://example.com'));
 assert.equal(starterPlan('flyer',' Only a headline ','','').length,4);
 assert.equal(starterPlan('flyer',' Only a headline ','','')[3].label.text,'Only a headline');
});
test('invalid fields and unsupported styles fail without partially creating a layout', () => {
 for(const args of [ ['unknown','Title','',''], ['quote','','',''], ['quote',null,'',''], ['quote','a'.repeat(61),'',''], ['quote','ok','x'.repeat(181),''], ['quote','ok','','x'.repeat(61)], ['quote','bad\nline','',''], ['quote','ok','a\nb\nc\nd',''], ['quote','ok','bad\tvalue',''], ['quote','ok','','bad\u0000value'] ]) assert.throws(()=>starterPlan(...args));
 assert.doesNotThrow(()=>starterPlan('announcement','作'.repeat(60),'作'.repeat(180),'作'.repeat(60)));
});
