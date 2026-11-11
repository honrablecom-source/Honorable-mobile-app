import test from 'node:test';
import assert from 'node:assert/strict';
import { scheduleDay, parseSchedule, schedulePlan } from '../schedule.mjs';
test('real calendar days include leap days and reject rollover dates', () => {
 assert.equal(scheduleDay('2024-03-01') - scheduleDay('2024-02-28'), 2);
 assert.equal(scheduleDay('2000-03-01') - scheduleDay('2000-02-28'), 2);
 for (const date of ['1900-02-29', '2100-02-29', '2026-02-29', '2026-04-31', '2026-00-01', '2026-13-01', '2026-1-01', '2026-01-00', '2026-01-32', '1899-12-31', '2300-01-01', '2026-01-01T00:00:00Z', null]) assert.throws(() => scheduleDay(date));
 assert.equal(scheduleDay('2026-11-02') - scheduleDay('2026-11-01'), 1);
});
test('parser preserves input order, repeated names, Unicode and overlapping inclusive ranges', () => {
 const result = parseSchedule(' Local plan ', '\r\nEdit | 2026-11-03 | 2026-11-05\r\n作品🙂 | 2026-11-01 | 2026-11-03\r\nEdit | 2026-11-05 | 2026-11-05\r\n');
 assert.equal(result.title, 'Local plan');assert.equal(result.days, 5);
 assert.deepEqual(result.tasks.map(task => task.name), ['Edit', '作品🙂', 'Edit']);
 assert.deepEqual(result.tasks.map(task => task.days), [3, 3, 1]);
});
test('bar lengths and positions share an exact inclusive day scale, not row-order spacing', () => {
 const shapes = schedulePlan('Plan', 'Later | 2026-01-04 | 2026-01-05\nEarlier | 2026-01-01 | 2026-01-05\nSingle | 2026-01-03 | 2026-01-03');
 const bars = shapes.filter(shape => shape.type === 'rectangle');
 assert.equal(bars.length, 3);assert.equal(bars[0].x, 300 + 3 * 144);assert.equal(bars[0].width, 288);
 assert.equal(bars[1].x, 300);assert.equal(bars[1].width, 720);assert.equal(bars[2].x, 588);assert.equal(bars[2].width, 144);
 assert(bars.every(bar => bar.x >= 300 && bar.x + bar.width <= 1020));
 assert(shapes.some(shape => shape.text === '2026-01-03 → 2026-01-03 · 1 day'));
});
test('one-day plan fills the scale and keeps duplicate tasks as independent rows', () => {
 const input = Array(2).fill('Same | 2026-01-01 | 2026-01-01').join('\n');
 const bars = schedulePlan('Same day', input).filter(shape => shape.type === 'rectangle');
 assert.equal(bars.length, 2);assert(bars.every(bar => bar.x === 300 && bar.width === 720));assert.notEqual(bars[0].y, bars[1].y);
});
test('whole span accepts exactly 366 days and refuses 367 even with gaps', () => {
 assert.equal(parseSchedule('Leap', 'Full | 2024-01-01 | 2024-12-31').days, 366);
 assert.throws(() => parseSchedule('Long', 'A | 2024-01-01 | 2024-01-01\nB | 2025-01-01 | 2025-01-01'), /366/);
});
test('malformed or excessive inputs are refused without producing partial plans', () => {
 for (const input of ['', ' ', 'A | 2026-01-01', 'A | 2026-01-01 | 2026-01-02 | Extra', ' | 2026-01-01 | 2026-01-02', 'A | 2026-01-02 | 2026-01-01', 'A\x00 | 2026-01-01 | 2026-01-02', 'A | 2026-01-01 | 2026-01-02\n\nB | 2026-01-01 | 2026-01-02', '🙂'.repeat(49) + ' | 2026-01-01 | 2026-01-02', 'x'.repeat(4097), Array(17).fill('A | 2026-01-01 | 2026-01-01').join('\n')]) assert.throws(() => schedulePlan('Plan', input));
 for (const title of ['', ' ', 'x'.repeat(61), 'Line\nbreak', null]) assert.throws(() => schedulePlan(title, 'A | 2026-01-01 | 2026-01-01'));
});
test('maximum plan has bounded editable geometry and deterministic text with no links', () => {
 const input = Array.from({length:16}, (_, index) => `${'🙂'.repeat(45)}${index} | 2024-01-01 | 2024-12-31`).join('\n');
 const first = schedulePlan('Maximum', input);
 assert.deepEqual(first, schedulePlan('Maximum', input));assert(first.length < 100);
 assert(first.every(shape => Number.isFinite(shape.x) && Number.isFinite(shape.y) && !shape.link));
 assert(first.filter(shape => shape.type === 'text').every(shape => shape.strokeColor === '#111111'));
 assert.equal(first.filter(shape => shape.type === 'rectangle').length, 16);
 assert(first.filter(shape => shape.text?.includes('🙂')).every(shape => shape.text.split('\n').every(line => Array.from(line).length <= 16)));
});
