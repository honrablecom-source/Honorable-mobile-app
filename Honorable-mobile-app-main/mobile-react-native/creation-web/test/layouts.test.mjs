import { test } from 'node:test';
import assert from 'node:assert/strict';
import { chartPlan, calendarPlan, storyboardPlan } from '../layouts.mjs';
test('chart shares a zero origin and proportional positive/negative scale', () => {
  const shapes = chartPlan('Change', 'A | -10\nB | 20\nC | 0');
  const bars = shapes.filter(shape => shape.type === 'rectangle');
  assert.equal(bars.length, 2); assert.equal(bars[1].width, bars[0].width * 2);
  assert.equal(bars[0].x + bars[0].width, bars[1].x);
  assert(shapes.some(shape => shape.text === '0'));
  assert(chartPlan('Zero', 'A | 0').every(shape => Number.isFinite(shape.x) && Number.isFinite(shape.y)));
});
test('chart rejects ambiguous or unbounded inputs before producing shapes', () => {
  for (const value of ['A|NaN', 'A|Infinity', 'A|1e3', 'A|1,000', 'A|1|2', '|1', 'A|1000000001', Array(13).fill('A|1').join('\n')]) assert.throws(() => chartPlan('Title', value));
  assert.throws(() => chartPlan('Title\nNext', 'A|1'));
});
test('calendar computes leap years and selected week origins in UTC', () => {
  const feb = calendarPlan('2024-02');
  assert.equal(feb.filter(shape => shape.type === 'rectangle').length, 35);
  assert.equal(feb.find(shape => shape.text === '1').x, 308);
  assert(feb.some(shape => shape.text === '29'));
  assert(!calendarPlan('1900-02').some(shape => shape.text === '29'));
  assert(calendarPlan('2000-02').some(shape => shape.text === '29'));
  assert.equal(calendarPlan('2026-11', false).find(shape => shape.text === '1').x, 8);
  assert.equal(calendarPlan('2026-11', true).find(shape => shape.text === '1').x, 608);
  for (const input of ['2026-00', '2026-13', '26-11', '2026-1', 'bad']) assert.throws(() => calendarPlan(input));
});
test('storyboard keeps numbered frames and editable wrapped briefs', () => {
  const shapes = storyboardPlan('Story', 'Opening\nA long shot that moves across the horizon toward the village\nEnding');
  assert.equal(shapes.filter(shape => shape.type === 'rectangle').length, 3);
  assert(shapes.some(shape => shape.text === 'Scene 3' && shape.x === 0 && shape.y === 404));
  assert(shapes.some(shape => shape.type === 'text' && shape.text.includes('\n')));
  assert.throws(() => storyboardPlan('Story', 'Opening\n\nEnding'));
  assert.throws(() => storyboardPlan('Story', 'a'.repeat(101)));
});

import { blendShapes } from '../composition.mjs';
const endpoint = (id, x, size, color) => ({ id, type: 'rectangle', x, y: 0, width: size, height: size, angle: 0, opacity: 100, strokeColor: '#111111', backgroundColor: color, fillStyle: 'solid', strokeStyle: 'solid' });
test('shape blend creates independent proportional intermediates without changing endpoints', () => {
  const first = endpoint('a', 0, 10, '#000000'), last = endpoint('b', 100, 30, '#ffffff');
  const before = JSON.stringify([first, last]);
  const result = blendShapes([first, last], { a: true, b: true }, 1, () => 'mid');
  assert.equal(result.length, 3); assert.equal(result[2].x, 50); assert.equal(result[2].width, 20); assert.equal(result[2].backgroundColor, '#808080');
  assert.equal(JSON.stringify([first, last]), before); assert.equal(result[2].id, 'mid');
  assert.throws(() => blendShapes([first, last], { a: true }, 1, () => 'mid'));
  assert.throws(() => blendShapes([first, { ...last, locked: true }], { a: true, b: true }, 1, () => 'mid'));
  assert.throws(() => blendShapes([first, last], { a: true, b: true }, 1, () => 'a'));
});
test('blend takes shortest rotation and rejects unsafe bindings and incompatible colors', () => {
  const first = { ...endpoint('a', 0, 10, '#000000'), angle: 170 * Math.PI / 180 };
  const last = { ...endpoint('b', 100, 30, '#ffffff'), angle: -170 * Math.PI / 180 };
  assert(Math.abs(Math.abs(blendShapes([first,last], {a:true,b:true}, 1, ()=>'mid')[2].angle) - Math.PI) < 1e-9);
  assert.throws(() => blendShapes([first,{...last,boundElements:[{id:'text'}]}],{a:true,b:true},1,()=>'mid'));
  assert.throws(() => blendShapes([first,{...last,backgroundColor:'transparent'}],{a:true,b:true},1,()=>'mid'));
});
