import test from 'node:test';
import assert from 'node:assert/strict';
import zxing from '@zxing/library';
import { barcodePlan, BARCODE_MAX_CHARACTERS, BARCODE_MODULE_SIZE, BARCODE_QUIET_MODULES, BARCODE_BAR_HEIGHT } from '../barcode.mjs';
import { MAX_ELEMENTS } from '../scene.mjs';

// Independent decoder consumes rasterized editable rectangles, not encoder data.
function decode(elements) {
  const { width, height } = elements[0];
  const pixels = new Uint8ClampedArray(width * height).fill(255);
  for (const rect of elements.filter(element => element.type === 'rectangle')) {
    for (let y = rect.y; y < rect.y + rect.height; y++) pixels.fill(rect.backgroundColor === '#111111' ? 17 : 255, y * width + rect.x, y * width + rect.x + rect.width);
  }
  const bitmap = new zxing.BinaryBitmap(new zxing.HybridBinarizer(new zxing.RGBLuminanceSource(pixels, width, height)));
  return new zxing.Code128Reader().decode(bitmap).getText();
}

test('editable bars independently decode identifiers, numbers, punctuation and exact spaces', () => {
  for (const text of ['NOBLE-ITEM-042', '01234567890123456789', 'Shelf A / Box #7', '  keep spaces  ', '!"#$%&\'()*+,-./:;<=>?@[\\]^_`{|}~']) assert.equal(decode(barcodePlan(text)), text);
});

test('exact content bound decodes without truncation and invalid input is refused', () => {
  for (const text of ['A'.repeat(BARCODE_MAX_CHARACTERS), '1'.repeat(BARCODE_MAX_CHARACTERS), Array.from({ length: 80 }, (_, i) => String.fromCharCode(32 + i)).join('')]) assert.equal(decode(barcodePlan(text)), text);
  for (const text of ['', '   ', 'A'.repeat(81), 'line\nnext', '\t', 'é', '你好', '🙂', '\x7f', null, 123]) assert.throws(() => barcodePlan(text));
});

test('caption is separate, optional, bounded and leaves encoded content intact', () => {
  const bare = barcodePlan('NOBLE-42'), captioned = barcodePlan('NOBLE-42', '  Package 🖤  ');
  assert.deepEqual(captioned.slice(0, -1), bare);assert.equal(captioned.at(-1).text, 'Package 🖤');assert.equal(decode(captioned), 'NOBLE-42');
  assert.deepEqual(barcodePlan('NOBLE-42', '  '), bare);
  for (const text of ['a'.repeat(61), 'two\nlines', '\x00', null]) assert.throws(() => barcodePlan('NOBLE-42', text));
});

test('quiet border and exact editable module geometry stay within the object cap', () => {
  const elements = barcodePlan('NOBLE-42'), background = elements[0], quiet = BARCODE_MODULE_SIZE * BARCODE_QUIET_MODULES;
  assert.equal(background.backgroundColor, '#ffffff');assert(elements.length < MAX_ELEMENTS);
  for (const bar of elements.slice(1)) {
    assert.equal(bar.type, 'rectangle');assert.equal(bar.backgroundColor, '#111111');assert.equal(bar.height, BARCODE_BAR_HEIGHT);
    assert.equal(bar.strokeColor, 'transparent');assert.equal(bar.roughness, 0);assert.equal(bar.opacity, 100);
    assert(bar.x >= quiet && bar.x + bar.width <= background.width - quiet);assert.equal(bar.y, quiet);
    assert.equal(background.height - bar.y - bar.height, quiet);
    for (const name of ['x', 'y', 'width', 'height']) assert.equal(bar[name] % BARCODE_MODULE_SIZE, 0);
  }
});

test('plans are deterministic and omit payload and link metadata', () => {
  const first = barcodePlan('PRIVATE-IDENTIFIER');assert.deepEqual(first, barcodePlan('PRIVATE-IDENTIFIER'));
  assert(!JSON.stringify(first).includes('PRIVATE-IDENTIFIER'));
  for (const element of first) for (const name of ['link', 'customData', 'url', 'payload']) assert.equal(element[name], undefined);
});

test('numeric compaction and mixed-case transitions stay independently scannable', () => {
  for (const text of ['ABC12345678901234abc', '00000000', 'aA1!zZ9?', '42-42-42-42-42']) assert.equal(decode(barcodePlan(text)), text);
});
