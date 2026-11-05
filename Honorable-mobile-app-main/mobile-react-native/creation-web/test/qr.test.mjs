import test from 'node:test';
import assert from 'node:assert/strict';
import jsQR from 'jsqr';
import qrcode from 'qrcode-generator';
import { qrPlan, QR_MAX_BYTES, QR_MODULE_SIZE, QR_QUIET_MODULES } from '../qr.mjs';
import { MAX_ELEMENTS } from '../scene.mjs';

// Independent decoder reads rasterized output rectangles, not the encoder's module array.
function decode(elements) {
  const side = elements[0].width;
  const pixels = new Uint8ClampedArray(side * side * 4).fill(255);
  for (const element of elements.filter(item => item.type === 'rectangle')) {
    const color = element.backgroundColor === '#111111' ? 17 : 255;
    for (let y = element.y; y < element.y + element.height; y++) for (let x = element.x; x < element.x + element.width; x++) {
      const offset = (y * side + x) * 4;
      pixels[offset] = pixels[offset + 1] = pixels[offset + 2] = color;
    }
  }
  const result = jsQR(pixels, side, side);
  assert(result, 'Generated editable rectangles must decode as a QR code');
  return result;
}

test('short links and plain messages round-trip through an independent QR decoder', () => {
  for (const value of ['https://example.com/work?id=42', 'Noble made this', '  preserve spaces  ', 'Line one\nLine two']) {
    const elements = qrPlan(value, 'My artwork');
    assert.equal(decode(elements).data, value);
    assert.equal(elements.at(-1).text, 'My artwork');
  }
});

test('UTF-8 text and emoji survive QR encoding without global encoder changes', () => {
  const charset = qrcode.stringToBytes;
  for (const value of ['Noble 🖤', '你好，世界', 'مرحبا بالعالم', 'café résumé']) {
    const result = decode(qrPlan(value));
    assert.equal(result.data, value);
    assert.deepEqual(result.binaryData, [...new TextEncoder().encode(value)]);
  }
  assert.equal(qrcode.stringToBytes, charset);
});

test('the exact byte limit is honored without truncation for ASCII and multibyte text', () => {
  for (const value of ['a'.repeat(QR_MAX_BYTES), '🙂'.repeat(QR_MAX_BYTES / 4)]) {
    assert.equal(new TextEncoder().encode(value).length, QR_MAX_BYTES);
    assert.equal(decode(qrPlan(value)).data, value);
  }
  assert.throws(() => qrPlan('a'.repeat(QR_MAX_BYTES + 1)), /shorter/);
  assert.throws(() => qrPlan('🙂'.repeat(QR_MAX_BYTES / 4 + 1)), /shorter/);
});

test('quiet zone and exact module edges are preserved by bounded editable rectangles', () => {
  const elements = qrPlan('https://example.com');
  const background = elements[0], quiet = QR_QUIET_MODULES * QR_MODULE_SIZE;
  assert.equal(background.backgroundColor, '#ffffff');
  assert.equal(background.width, background.height);
  assert(elements.length <= MAX_ELEMENTS);
  for (const shape of elements.slice(1)) {
    assert.equal(shape.type, 'rectangle');
    assert.equal(shape.backgroundColor, '#111111');
    assert.equal(shape.strokeColor, 'transparent');
    assert.equal(shape.roughness, 0); assert.equal(shape.opacity, 100);
    for (const field of ['x', 'y', 'width', 'height']) assert.equal(shape[field] % QR_MODULE_SIZE, 0);
    assert(shape.width > 0 && shape.height > 0);
    assert(shape.x >= quiet && shape.y >= quiet);
    assert(shape.x + shape.width <= background.width - quiet);
    assert(shape.y + shape.height <= background.height - quiet);
  }
});

test('repeated calls are deterministic, bounded and omit payload metadata', () => {
  const value = 'https://example.com/private-reference';
  const first = qrPlan(value, 'Caption');
  assert.deepEqual(qrPlan(value, 'Caption'), first);
  assert(!JSON.stringify(first).includes(value));
  let state = 42;
  for (let sample = 0; sample < 40; sample++) {
    const payload = Array.from({ length: 96 }, () => {
      state = (Math.imul(state, 1664525) + 1013904223) >>> 0;
      return String.fromCharCode(33 + state % 90);
    }).join('');
    const plan = qrPlan(payload);
    assert(plan.length <= MAX_ELEMENTS);
    if (sample < 3) assert.equal(decode(plan).data, payload);
  }
});

test('empty, malformed and overlong inputs fail before a drawing is produced', () => {
  for (const value of ['', ' ', null, undefined, 42, {}, '\ud800', '\udfff']) assert.throws(() => qrPlan(value));
  for (const caption of [null, {}, 'x'.repeat(61), 'line\nbreak', '\u0000']) assert.throws(() => qrPlan('Valid payload', caption));
  assert.equal(qrPlan('Valid payload', '  ').some(element => element.type === 'text'), false);
});
