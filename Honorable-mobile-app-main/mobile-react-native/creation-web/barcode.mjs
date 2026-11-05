import JsBarcode from 'jsbarcode';
import { MAX_ELEMENTS } from './scene.mjs';

export const BARCODE_MAX_CHARACTERS = 80;
export const BARCODE_MODULE_SIZE = 2;
export const BARCODE_QUIET_MODULES = 10;
export const BARCODE_BAR_HEIGHT = 96;

/** Local CODE128 encoding via MIT JsBarcode; output contains editable geometry, not payload metadata. */
export function barcodePlan(content, caption = '') {
  if (typeof content !== 'string' || !content.trim()) throw Error('Enter the text or identifier for your barcode.');
  if (content.length > BARCODE_MAX_CHARACTERS || /[^\x20-\x7e]/.test(content)) throw Error('Use up to 80 printable English letters, numbers, spaces or symbols.');
  if (typeof caption !== 'string' || caption.length > 60 || /[\x00-\x1f\x7f]/.test(caption)) throw Error('Use a caption of up to 60 characters on one line.');
  const encoded = {};
  JsBarcode(encoded, content, { format: 'CODE128', displayValue: false, margin: 0 });
  const bits = encoded.encodings?.[0]?.data;
  if (encoded.encodings?.length !== 1 || typeof bits !== 'string' || !/^[01]{1,2000}$/.test(bits)) throw Error('Could not create this barcode. Try a shorter identifier.');
  const quiet = BARCODE_QUIET_MODULES * BARCODE_MODULE_SIZE;
  const width = bits.length * BARCODE_MODULE_SIZE + quiet * 2;
  const height = BARCODE_BAR_HEIGHT + quiet * 2;
  const rectangle = (x, y, w, h, color) => ({ type: 'rectangle', x, y, width: w, height: h, angle: 0, opacity: 100, strokeColor: 'transparent', strokeWidth: 0, backgroundColor: color, fillStyle: 'solid', roughness: 0, roundness: null });
  const elements = [rectangle(0, 0, width, height, '#ffffff')];
  for (let index = 0; index < bits.length;) {
    if (bits[index] === '0') { index++; continue; }
    const start = index;
    while (bits[index] === '1') index++;
    elements.push(rectangle(quiet + start * BARCODE_MODULE_SIZE, quiet, (index - start) * BARCODE_MODULE_SIZE, BARCODE_BAR_HEIGHT, '#111111'));
  }
  if (caption.trim()) elements.push({ type: 'text', text: caption.trim(), x: 0, y: height + 12, fontSize: 16, fontFamily: 2, strokeColor: '#111111', opacity: 100 });
  if (elements.length > MAX_ELEMENTS) throw Error('This barcode needs too many drawing objects. Use a shorter identifier.');
  return elements;
}
