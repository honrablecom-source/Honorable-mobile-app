import test from 'node:test';
import assert from 'node:assert/strict';
import { combineShapes, connectedFill, signedArea } from '../shape-boolean.mjs';
import { booleanSelection, captureBooleanSource, shapeRing } from '../shape-boolean-source.mjs';
const shape = (id, x = 0, y = 0, width = 100, height = 100, extra = {}) => ({ id, type: 'rectangle', x, y, width, height, angle: 0, strokeColor: '#111111', backgroundColor: '#ff0000', strokeWidth: 1, strokeStyle: 'solid', opacity: 100, ...extra });
const input = (shapes, existingCount = shapes.length) => ({ shapes, existingCount });
const near = (a, b) => assert(Math.abs(a - b) < Math.max(1e-6, Math.abs(b) * 1e-8), `${a} differs from ${b}`);
function winding(ring, x, y) { let n = 0; for (let i = 0; i < ring.length - 1; i++) { const a = ring[i], b = ring[i + 1], cross = (b[0] - a[0]) * (y - a[1]) - (x - a[0]) * (b[1] - a[1]); if (a[1] <= y && b[1] > y && cross > 0) n++; if (a[1] > y && b[1] <= y && cross < 0) n--; } return n; }
test('all four operations preserve expected overlap area without mutating inputs', () => {
  const value = input([shape('a'), shape('b', 50)]), before = JSON.stringify(value);
  for (const [mode, area] of [['union', 15000], ['intersection', 5000], ['difference', 5000], ['xor', 10000]]) near(combineShapes(value, mode).area, area);
  assert.equal(JSON.stringify(value), before);
});
test('a cutout is one fill path with a transparent winding hole and separate outlines', () => {
  const result = combineShapes(input([shape('outer'), shape('cut', 20, 20, 60, 60)]), 'difference');
  near(result.area, 6400); assert.equal(result.polygons[0].length, 2); assert.equal(result.plan.length, 3);
  const fill = connectedFill(result.polygons[0]); near(signedArea(fill), 6400); assert.equal(winding(fill, 50, 50), 0); assert.equal(Math.abs(winding(fill, 10, 15)), 1);
  assert.equal(result.plan[0].strokeColor, 'transparent'); assert.equal(result.plan[0].strokeWidth, 1, 'hidden fill stroke must survive renderer reload without zero-width normalization'); assert.deepEqual(result.plan[0].points[0], [0, 0]); assert.deepEqual(result.plan[0].points.at(-1), [0, 0]);
});
test('triangulation bridges preserve several independent holes without fill seams', () => {
  const values = [shape('h0', 0, 0, 100, 10), shape('h1', 0, 45, 100, 10), shape('h2', 0, 90, 100, 10), shape('v0', 0, 0, 10, 100), shape('v1', 45, 0, 10, 100), shape('v2', 90, 0, 10, 100)];
  const result = combineShapes(input(values), 'union'); assert.equal(result.polygons.length, 1); assert.equal(result.polygons[0].length, 5);
  near(result.area, 5100); const ring = connectedFill(result.polygons[0]); near(signedArea(ring), result.area);
  for (const [x, y] of [[20, 20], [70, 20], [20, 70], [70, 70]]) assert.equal(winding(ring, x, y), 0);
  assert.equal(Math.abs(winding(ring, 5, 20)), 1);
});
test('disjoint union and XOR retain both parts; empty overlap and identical cutout refuse', () => {
  const value = input([shape('a'), shape('b', 200)]); assert.equal(combineShapes(value, 'union').polygons.length, 2); near(combineShapes(value, 'xor').area, 20000);
  assert.throws(() => combineShapes(value, 'intersection'), /no filled area/); assert.throws(() => combineShapes(input([shape('a'), shape('b')]), 'difference'), /no filled area/);
});
test('front cut uses union of remaining bases and keeps base style including alpha', () => {
  const value = input([shape('a'), shape('b', 50, 0, 100, 100, { backgroundColor: '#12345680', opacity: 60 }), shape('cut', 80, 20, 20, 60, { backgroundColor: '#00ff00' })]);
  const result = combineShapes(value, 'difference'); near(result.area, 13800); assert.equal(result.styleIndex, 1); assert.equal(result.plan[0].backgroundColor, '#12345680'); assert.equal(result.plan[0].opacity, 60);
});
test('rotated shapes and diamonds preserve area and canonical path geometry', () => {
  const rotated = shape('a', -50, 80, 100, 50, { angle: Math.PI / 3 });
  const result = combineShapes(input([rotated, { ...rotated, id: 'b' }]), 'union'); near(result.area, 5000);
  for (const path of result.plan) { assert.deepEqual(path.points[0], [0, 0]); assert.deepEqual(path.points.at(-1), [0, 0]); assert(path.points.flat().every(Number.isFinite)); }
  near(combineShapes(input([shape('a', 0, 0, 100, 100, { type: 'diamond' }), shape('b', 0, 0, 100, 100, { type: 'diamond' })]), 'union').area, 5000);
});
test('ellipses use bounded fine polygon approximation with disclosed precision', () => {
  const a = shape('a', 0, 0, 100, 50, { type: 'ellipse', angle: .25 }), b = { ...a, id: 'b' };
  assert.equal(shapeRing(a).length, 97); const result = combineShapes(input([a, b]), 'union'); assert(result.ellipseApproximation); assert(Math.abs(result.area / (Math.PI * 50 * 25) - 1) < .001);
});
test('shape selection protects locked/hidden/deleted/framed/rounded and unsupported objects', () => {
  for (const patch of [{ locked: true }, { opacity: 0 }, { customData: { nobleHidden: { opacity: 100, locked: false } } }, { isDeleted: true }, { frameId: 'frame' }, { roundness: { type: 3 } }, { type: 'text', text: 'Kept' }]) assert.throws(() => booleanSelection([shape('a'), shape('b', 50, 0, 100, 100, patch)], { a: true, b: true }));
});
test('invalid count, geometry, colors, styles and no-visible-result fail atomically', () => {
  for (const patch of [{ width: 0 }, { width: Infinity }, { angle: NaN }, { strokeWidth: -1 }, { backgroundColor: 'remote' }, { opacity: 101 }, { strokeStyle: 'invalid' }]) assert.throws(() => combineShapes(input([shape('a'), shape('b', 50, 0, 100, 100, patch)]), 'union'));
  assert.throws(() => combineShapes(input([shape('a')]), 'union')); assert.throws(() => combineShapes(input(Array.from({ length: 9 }, (_, i) => shape(String(i)))), 'union'));
  assert.throws(() => combineShapes(input([shape('a'), shape('b')]), 'not-a-mode'));
  assert.throws(() => combineShapes(input([shape('a'), shape('b', 50, 0, 100, 100, { backgroundColor: 'transparent', strokeColor: '#0000' })]), 'union'), /visible fill/);
});
test('object budget and placement limits never truncate the result', () => {
  assert.throws(() => combineShapes(input([shape('a'), shape('b', 50)], 499), 'union'), /500 drawing/);
  assert.throws(() => combineShapes(input([shape('a', 999990), shape('b', 999991)]), 'union'), /origin/);
});
test('captured source includes exact drawing but sends only selected shape geometry/style', () => {
  const elements = [shape('a'), shape('b', 50), { ...shape('private'), type: 'text', text: 'Private note', originalText: 'Private note' }]; let ids = { a: true, b: true };
  const editor = { getSceneElements: () => elements, getFiles: () => ({}), getAppState: () => ({ selectedElementIds: ids }) };
  const snapshot = captureBooleanSource(editor); assert.equal(snapshot.input.existingCount, 3); assert(!JSON.stringify(snapshot.input).includes('Private note')); assert(!JSON.stringify(snapshot.input).includes('private'));
  ids = {}; assert(snapshot.current(editor)); elements[2].text = 'Changed'; assert(!snapshot.current(editor)); assert(!snapshot.current({ ...editor }));
});
test('stale or malformed selection cannot silently retarget another shape', () => {
  for (const ids of [null, [], { a: true, missing: true }, { a: 'true', b: true }]) assert.throws(() => booleanSelection([shape('a'), shape('b')], ids));
});
