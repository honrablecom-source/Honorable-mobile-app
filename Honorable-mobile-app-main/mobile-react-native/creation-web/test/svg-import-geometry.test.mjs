import test from 'node:test';
import assert from 'node:assert/strict';
import { strictSvgPath, flattenSvgPath, svgFilledPolygons, vectorArtwork } from '../svg-import-geometry.mjs';
import { signedArea } from '../shape-boolean.mjs';
import { encodeDraft, decodeDraft } from '../scene.mjs';
const outer = 'M0 0H100V100H0Z', inside = 'M20 20H80V80H20Z', opposite = 'M20 20V80H80V20Z';
const item = (d = outer, more = {}) => ({ d, matrix: [1, 0, 0, 1, 0, 0], fill: '#ff000080', stroke: 'transparent', strokeWidth: 1, opacity: 100, fillRule: 'nonzero', ...more });
const input = (items = [item()], existingCount = 0) => ({ items, existingCount });
const area = polygons => polygons.reduce((sum, polygon) => sum + polygon.reduce((n, ring) => n + signedArea(ring), 0), 0);
const near = (a, b, tolerance = 1e-6) => assert(Math.abs(a - b) <= tolerance, `${a} differs from ${b}`);
function winding(path, x, y) { let n = 0; for (let i = 0; i < path.length - 1; i++) { const a = path[i], b = path[i + 1], cross = (b[0] - a[0]) * (y - a[1]) - (x - a[0]) * (b[1] - a[1]); if (a[1] <= y && b[1] > y && cross > 0) n++; if (a[1] > y && b[1] <= y && cross < 0) n--; } return n; }
test('full SVG command grammar handles relative, smooth, quadratic and elliptical commands', () => {
  for (const d of ['m10 20 10 0h10v10l-20 0z', 'M0 0C0 10 10 10 10 0S20 -10 20 0', 'M0 0Q10 20 20 0T40 0', 'M10 0A10 10 0 1 1 -10 0a10 10 0 1 1 20 0Z']) {
    const commands = strictSvgPath(d); assert(commands.length > 1); assert(commands.every(c => ['M', 'L', 'C', 'Z'].includes(c.key))); const paths = flattenSvgPath(d); assert(paths.length); assert(paths.flatMap(p => p.points).every(p => p.every(Number.isFinite)));
  }
});
test('malformed paths refuse before upstream implicit moves or zero-arity loops', () => {
  for (const d of ['', 'L0 0', 'M0 0Z 1 2', 'M0', 'M0 0 L', 'M0 0 X10 10', 'M0 0 A10 10 0 2 1 20 20', 'M0 0A-1 2 0 0 1 3 4', 'M0 0L1e999 0', 'M0 0L2 3<script>', 'M0 0L1000001 0']) assert.throws(() => strictSvgPath(d), undefined, d);
});
test('curves are sampled after affine transformation with bounded finite coordinates', () => {
  const paths = flattenSvgPath('M0 0Q50 100 100 0', [2, 0, 0, 2, 10, -5]); const p = paths[0].points; assert(p.length > 4); assert.deepEqual(p[0], [10, -5]); assert.deepEqual(p[p.length - 1], [210, -5]); near(Math.max(...p.map(p => p[1])), 95, .2);
  assert.throws(() => flattenSvgPath(outer, [1, 0, 0, NaN, 0, 0])); assert.throws(() => flattenSvgPath(outer, [1e6, 0, 0, 1e6, 0, 0]));
});
test('evenodd holes stay transparent regardless of ring direction', () => {
  for (const hole of [inside, opposite]) { const result = svgFilledPolygons(flattenSvgPath(outer + hole), 'evenodd'); near(area(result), 6400); assert.equal(result[0].length, 2); }
});
test('nonzero winding keeps same-direction nesting and cuts opposite-direction holes', () => {
  near(area(svgFilledPolygons(flattenSvgPath(outer + inside), 'nonzero')), 10000); const result = svgFilledPolygons(flattenSvgPath(outer + opposite), 'nonzero'); near(area(result), 6400); assert.equal(result[0].length, 2);
});
test('nonzero coverage retains double-positive minus one-negative regions', () => {
  const d = outer + outer + opposite; near(area(svgFilledPolygons(flattenSvgPath(d), 'nonzero')), 10000); near(area(svgFilledPolygons(flattenSvgPath(d), 'evenodd')), 3600);
  const overlap = 'M0 0H60V100H0Z M40 0H100V100H40Z M45 20V80H55V20Z'; near(area(svgFilledPolygons(flattenSvgPath(overlap), 'nonzero')), 10000);
});
test('islands, open filled subpaths and reflected holes preserve area', () => {
  const d = outer + opposite + 'M40 40H60V60H40Z'; const result = svgFilledPolygons(flattenSvgPath(d, [-1, 0, 0, 1, 100, 0]), 'nonzero'); near(area(result), 6800); assert.equal(result.length, 2);
  near(area(svgFilledPolygons(flattenSvgPath('M0 0H100V100H0'), 'nonzero')), 10000);
});
test('self-crossing, self-touching and degenerate filled rings are refused', () => {
  for (const d of ['M0 0L100 100L0 100L100 0Z', 'M0 0H100V100H0V0L50 50Z', 'M0 0L100 0L50 0Z']) assert.throws(() => svgFilledPolygons(flattenSvgPath(d), 'nonzero'), /complex|intersects/);
});
test('editable paths preserve alpha and true hole winding through draft recovery', () => {
  const value = input([item(outer + opposite)]), before = JSON.stringify(value), result = vectorArtwork(value); assert.equal(result.plan.length, 1); const fill = result.plan[0]; assert.equal(fill.strokeColor, 'transparent'); assert.equal(fill.strokeWidth, 1); assert.equal(fill.backgroundColor, '#ff000080'); assert.deepEqual(fill.points[0], [0, 0]); assert.equal(winding(fill.points, 50, 50), 0); assert.equal(Math.abs(winding(fill.points, 10, 50)), 1); assert.equal(JSON.stringify(value), before);
  const elements = result.plan.map((el, i) => ({ ...el, id: `test-${i}` })); assert.deepEqual(decodeDraft(encodeDraft(elements)).elements, elements);
});
test('open outline paths keep endpoints and uniform transforms scale their width', () => {
  const result = vectorArtwork(input([item('M10 10L40 30', { fill: 'transparent', stroke: '#000000', strokeWidth: 2, matrix: [0, 2, -2, 0, 100, 0] })])); const line = result.plan[0]; assert.equal(line.strokeWidth, 4); assert.deepEqual([line.x, line.y], [80, 20]); assert.deepEqual(line.points, [[0, 0], [-40, 60]]); assert.equal(line.backgroundColor, 'transparent'); assert(result.hasOutlines);
  assert.throws(() => vectorArtwork(input([item(outer, { stroke: '#000000', matrix: [2, 0, 0, 1, 0, 0] })])), /uniform/);
});
test('unsupported opacity compositing and invisible-only artwork refuse explicitly', () => {
  assert.throws(() => vectorArtwork(input([item(outer, { opacity: 50, stroke: '#000000' })])), /Separate partly transparent/);
  assert.throws(() => vectorArtwork(input([item(outer, { fill: 'transparent', stroke: 'transparent' })])), /no supported visible/);
  assert.throws(() => vectorArtwork(input([item(outer, { fill: 'url(https://invalid.test/a)' })])), /paint/);
  const result = vectorArtwork(input([item(outer, { opacity: 50 })])); assert.equal(result.plan[0].opacity, 50);
});
test('object, path-data and coordinate budgets reject the entire import', () => {
  const value = input([item()], 500), before = JSON.stringify(value); assert.throws(() => vectorArtwork(value), /object limit/); assert.equal(JSON.stringify(value), before);
  assert.throws(() => vectorArtwork(input(Array.from({ length: 129 }, () => item())))); assert.throws(() => strictSvgPath('M0 0' + 'L1 1'.repeat(513)), /too many/); assert.throws(() => strictSvgPath('M0 0' + ' '.repeat(16384))); assert.throws(() => vectorArtwork(input([item(outer, { opacity: Infinity })])));
});
test('signed winding partitions match independent cell coverage for overlapping contours', () => {
  let seed = 58199; const random = max => { seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0; return seed % max; };
  for (let run = 0; run < 24; run++) {
    const rectangles = Array.from({ length: 6 }, () => { const x = random(14), y = random(14), width = 1 + random(6), height = 1 + random(6), direction = random(2) ? 1 : -1; return { x, y, width, height, direction }; });
    const d = rectangles.map(({ x, y, width, height, direction }) => direction > 0 ? `M${x} ${y}H${x + width}V${y + height}H${x}Z` : `M${x} ${y}V${y + height}H${x + width}V${y}Z`).join(' ');
    for (const rule of ['nonzero', 'evenodd']) {
      const polygons = svgFilledPolygons(flattenSvgPath(d), rule); let cells = 0;
      for (let y = .5; y < 20; y++) for (let x = .5; x < 20; x++) {
        const covering = rectangles.filter(r => x > r.x && x < r.x + r.width && y > r.y && y < r.y + r.height);
        const expected = rule === 'evenodd' ? covering.length % 2 === 1 : covering.reduce((n, r) => n + r.direction, 0) !== 0;
        const actual = polygons.some(polygon => polygon.reduce((n, ring) => n + winding(ring, x, y), 0) !== 0);
        assert.equal(actual, expected, `${rule} at ${x},${y}, case ${run}`); if (expected) cells++;
      }
      near(area(polygons), cells);
    }
  }
});

test('viewport overflow refuses instead of silently changing clipped SVG artwork', () => {
  assert.equal(vectorArtwork({...input(),viewport:{width:100,height:100}}).plan.length,1);
  assert.throws(()=>vectorArtwork({...input([item('M-1 0H100V100H0Z')]),viewport:{width:100,height:100}}),/extends beyond/);
  assert.throws(()=>vectorArtwork({...input([item(outer,{stroke:'#111111'})]),viewport:{width:100,height:100}}),/extends beyond/);
  assert.throws(()=>vectorArtwork({...input(),viewport:{width:NaN,height:100}}),/canvas dimensions/);
});
