import test from 'node:test';
import assert from 'node:assert/strict';
import { mosaicPixels, MAX_MOSAIC_TRIANGLES } from '../photo-mosaic.mjs';
import { encodeDraft, decodeDraft } from '../scene.mjs';

const pixels = (w, h, rgba = [43, 95, 177, 255]) => Uint8ClampedArray.from({ length: w * h * 4 }, (_, i) => rgba[i % 4]);
const vertices = shape => shape.points.slice(0, 3).map(([x, y]) => [x + shape.x, y + shape.y]);
const area = shape => {
  const [a, b, c] = vertices(shape);
  return Math.abs((b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])) / 2;
};
// Independent barycentric oracle, rather than the kernel's oriented-edge implementation.
function inside(shape, x, y) {
  const [a, b, c] = vertices(shape), v0 = [c[0] - a[0], c[1] - a[1]], v1 = [b[0] - a[0], b[1] - a[1]], v2 = [x - a[0], y - a[1]];
  const dot = (u, v) => u[0] * v[0] + u[1] * v[1];
  const d00 = dot(v0, v0), d01 = dot(v0, v1), d11 = dot(v1, v1), d02 = dot(v0, v2), d12 = dot(v1, v2), denominator = d00 * d11 - d01 * d01;
  const u = (d11 * d02 - d01 * d12) / denominator, v = (d00 * d12 - d01 * d02) / denominator;
  return u >= -1e-10 && v >= -1e-10 && u + v <= 1 + 1e-10;
}

test('constant photo produces closed filled editable triangle lines and preserves the entire source', () => {
  const input = pixels(64, 48), old = input.slice(), result = mosaicPixels(input, 64, 48, 6);
  assert(result.plan.length > 2 && result.plan.length <= MAX_MOSAIC_TRIANGLES);
  for (const shape of result.plan) {
    assert.equal(shape.type, 'line'); assert.equal(shape.backgroundColor, '#2b5fb1');
    assert.equal(shape.strokeColor, 'transparent'); assert.equal(shape.fillStyle, 'solid');
    assert.equal(shape.opacity, 100); assert.equal(shape.roughness, 0); assert.equal(shape.angle, 0);
    assert.equal(shape.startArrowhead, null); assert.equal(shape.endArrowhead, null);
    assert.equal(shape.points.length, 4); assert.deepEqual(shape.points[0], [0, 0]); assert.deepEqual(shape.points[0], shape.points[3]);
    assert(area(shape) > 0);
  }
  assert.deepEqual(input, old);
});

test('opaque mesh covers exact image bounds and area across square portrait landscape and narrow sources', () => {
  for (const [width, height] of [[8, 8], [64, 48], [48, 64], [8, 128], [128, 8]]) for (const detail of [3, 6, 12]) {
    const { plan } = mosaicPixels(pixels(width, height), width, height, detail);
    assert(Math.abs(plan.reduce((sum, shape) => sum + area(shape), 0) - width * height) < 1e-6);
    for (const shape of plan) for (const [x, y] of vertices(shape)) assert(x >= 0 && x <= width && y >= 0 && y <= height);
    const all = plan.flatMap(vertices);
    assert.deepEqual([Math.min(...all.map(p => p[0])), Math.min(...all.map(p => p[1])), Math.max(...all.map(p => p[0])), Math.max(...all.map(p => p[1]))], [0, 0, width, height]);
    assert(plan.length <= 288);
  }
});

test('independent coverage oracle finds every source pixel inside the generated mesh', () => {
  const { plan } = mosaicPixels(pixels(40, 32), 40, 32, 6);
  for (let y = 0; y < 32; y++) for (let x = 0; x < 40; x++) assert(plan.some(shape => inside(shape, x + .5, y + .5)), `uncovered ${x},${y}`);
});

test('facet colors match independent alpha weighted source averaging on a varying analytic image', () => {
  const width = 32, height = 24, input = new Uint8ClampedArray(width * height * 4);
  for (let y = 0; y < height; y++) for (let x = 0; x < width; x++) input.set([x * 7, y * 9, (x + y) * 4, (x + y) % 3 ? 255 : 64], (y * width + x) * 4);
  const { plan } = mosaicPixels(input, width, height, 3);
  for (const shape of plan) {
    let count = 0, alpha = 0; const sums = [0, 0, 0];
    for (let y = 0; y < height; y++) for (let x = 0; x < width; x++) if (inside(shape, x + .5, y + .5)) {
      const i = (y * width + x) * 4; count++; alpha += input[i + 3];
      for (let channel = 0; channel < 3; channel++) sums[channel] += input[i + channel] * input[i + 3];
    }
    assert(count > 0);
    assert.equal(shape.backgroundColor, '#' + sums.map(sum => Math.round(sum / alpha).toString(16).padStart(2, '0')).join(''));
    assert.equal(shape.opacity, Math.max(1, Math.round(100 * alpha / (count * 255))));
  }
});

test('hidden transparent RGB never contaminates visible colors and empty facets are omitted', () => {
  const input = pixels(64, 64, [0, 0, 255, 0]);
  for (let y = 0; y < 64; y++) for (let x = 0; x < 24; x++) input.set([255, 0, 0, 255], (y * 64 + x) * 4);
  const result = mosaicPixels(input, 64, 64, 6);
  assert(result.omittedTransparent > 0); assert(result.plan.some(shape => shape.opacity < 100));
  assert(result.plan.every(shape => shape.backgroundColor === '#ff0000'));
  assert.equal(result.plan.length + result.omittedTransparent, result.triangleCount);
});

test('partial opacity and faint source signals remain represented without an opaque background', () => {
  for (const [alpha, expected] of [[128, 50], [1, 1]]) {
    const { plan } = mosaicPixels(pixels(32, 32, [24, 48, 72, alpha]), 32, 32, 6);
    assert(plan.every(shape => shape.opacity === expected && shape.backgroundColor === '#183048'));
  }
  assert.throws(() => mosaicPixels(pixels(32, 32, [255, 255, 255, 0]), 32, 32, 6), /No visible colors/);
});

test('mesh and colors are deterministic and detail increases facets within the object budget', () => {
  const input = pixels(64, 64), low = mosaicPixels(input, 64, 64, 3), high = mosaicPixels(input, 64, 64, 12);
  assert.deepEqual(mosaicPixels(input, 64, 64, 3), low);
  assert(high.plan.length > low.plan.length); assert.equal(high.plan.length, 288);
});

test('maximum dimensions and extreme aspect ratios stay bounded and cover all source area', () => {
  for (const [width, height] of [[1024, 1024], [8, 1024], [1024, 8]]) {
    const result = mosaicPixels(pixels(width, height), width, height, 12);
    assert(result.plan.length <= 288);
    assert(Math.abs(result.plan.reduce((sum, shape) => sum + area(shape), 0) - width * height) < 1e-4);
  }
});

test('invalid source sizes buffers and detail refuse before processing', () => {
  for (const [input, width, height] of [[[], 8, 8], [new Uint8Array(12), 8, 8], [pixels(8, 8), 0, 8], [pixels(8, 8), 8.1, 8], [new Uint8Array(1025 * 8 * 4), 1025, 8]]) assert.throws(() => mosaicPixels(input, width, height, 6), /local photo/);
  for (const detail of [0, 2, 13, 6.5, NaN, Infinity, '6', null]) assert.throws(() => mosaicPixels(pixels(8, 8), 8, 8, detail), /detail/);
});

test('current source guard aborts before work or between facets without mutating pixels', () => {
  for (const stop of [1, 2, 17, 40]) {
    const input = pixels(64, 64), old = input.slice(); let calls = 0;
    assert.throws(() => mosaicPixels(input, 64, 64, 6, () => { if (++calls === stop) throw Error('Synthetic stale owner'); }), /Synthetic stale owner/);
    assert.equal(calls, stop); assert.deepEqual(input, old);
  }
});

test('ordinary polygon objects survive current bounded document codec without additional image assets', () => {
  const { plan } = mosaicPixels(pixels(64, 64), 64, 64, 6);
  const elements = plan.map((shape, index) => ({ ...shape, id: `triangle-${index}`, groupIds: ['mosaic'], version: 1, versionNonce: index + 1 }));
  const restored = decodeDraft(encodeDraft(elements));
  assert.deepEqual(restored.elements, elements); assert.equal(restored.version, 1); assert.equal(restored.files, undefined);
});
