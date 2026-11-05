import test from 'node:test';
import assert from 'node:assert/strict';
import { artworkPalette, palettePlan, MAX_PALETTE_COLORS } from '../palette.mjs';
const box = (id, patch = {}) => ({ id, type: 'rectangle', x: 0, y: 0, width: 30, height: 40, strokeColor: '#fff', backgroundColor: '#ffffff', ...patch });

test('artwork palette deduplicates equivalent hex colors per object and includes locked visible art', () => {
  const source = [box('a'), box('b', { locked: true, strokeColor: '#ffffffff', backgroundColor: 'transparent' })];
  const before = JSON.stringify(source);
  assert.deepEqual(artworkPalette(source), [{ color: '#ffffff', count: 2 }]);
  assert.equal(JSON.stringify(source), before);
});

test('artwork palette excludes hidden/deleted/transparent colors and combines source and object alpha', () => {
  const result = artworkPalette([
    box('a', { opacity: 50, strokeColor: '#f008', backgroundColor: '#00000000' }),
    box('b', { opacity: 0, strokeColor: '#00ff00' }),
    box('c', { customData: { nobleHidden: { opacity: 100, locked: false } }, strokeColor: '#0000ff' }),
    box('d', { isDeleted: true }),
  ]);
  assert.deepEqual(result, [{ color: '#ff000044', count: 1 }]);
  assert.throws(() => artworkPalette([box('a', { opacity: 0 })]), /Add visible/);
});

test('color-key sheet produces bounded ordinary swatches and editable exact labels', () => {
  const result = palettePlan([box('a'), box('b', { strokeColor: '#123456', backgroundColor: 'transparent' })], ' My colors ');
  assert.equal(result[0].text, 'My colors');
  const swatches = result.filter(shape => shape.type === 'rectangle');
  assert.deepEqual(swatches.map(shape => shape.backgroundColor), ['#123456', '#ffffff']);
  assert(result.some(shape => shape.text === '#ffffff\n1 object'));
  assert(result.every(shape => ['rectangle', 'text'].includes(shape.type)));
  for (const title of ['', ' ', 'x'.repeat(61), 'bad\nname']) assert.throws(() => palettePlan([box('a')], title));
});

test('color-key rejects excess colors and invalid source without partial results or mutation', () => {
  const source = Array.from({ length: MAX_PALETTE_COLORS }, (_, i) => box(`shape-${i}`, { strokeColor: '#' + i.toString(16).padStart(6, '0'), backgroundColor: 'transparent' }));
  assert.equal(palettePlan(source, 'Limit').length, 2 + MAX_PALETTE_COLORS * 2);
  const before = JSON.stringify(source);
  assert.throws(() => palettePlan([...source, box('extra', { strokeColor: '#abcdef', backgroundColor: 'transparent' })], 'Too many'), /32/);
  assert.equal(JSON.stringify(source), before);
  assert.throws(() => artworkPalette([box('bad', { strokeColor: 'url(https://example.com)' })]));
  assert.throws(() => artworkPalette([box('duplicate'), box('duplicate')]));
});
