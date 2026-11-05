import qrcode from 'qrcode-generator';
import { MAX_ELEMENTS } from './scene.mjs';

export const QR_MAX_BYTES = 96;
export const QR_QUIET_MODULES = 4;
export const QR_MODULE_SIZE = 8;

/** Local byte-mode QR encoding. The payload is never opened, uploaded or stored in scene metadata. */
export function qrPlan(content, caption = '') {
  if (typeof content !== 'string' || !content.trim()) throw Error('Add a short link or message for your QR code.');
  if (Array.from(content).some(char => char.length === 1 && char.charCodeAt(0) >= 0xd800 && char.charCodeAt(0) <= 0xdfff)) throw Error('The text contains an incomplete character. Replace it and try again.');
  const bytes = new TextEncoder().encode(content);
  if (bytes.length > QR_MAX_BYTES) throw Error('Use a shorter link or message, up to 96 bytes of text.');
  if (typeof caption !== 'string' || caption.length > 60 || /[\x00-\x1f\x7f]/.test(caption)) throw Error('Use a QR caption of up to 60 characters on one line.');
  // Pass explicit UTF-8 bytes through the encoder's byte mapping without changing its global charset function.
  const code = qrcode(0, 'M');
  code.addData(String.fromCharCode(...bytes), 'Byte');
  code.make();
  const count = code.getModuleCount();
  const side = (count + QR_QUIET_MODULES * 2) * QR_MODULE_SIZE;
  const rect = (x, y, width, height, color) => ({
    type: 'rectangle', x, y, width, height, angle: 0, opacity: 100,
    strokeColor: 'transparent', strokeWidth: 0, backgroundColor: color,
    fillStyle: 'solid', roughness: 0, roundness: null,
  });
  const elements = [rect(0, 0, side, side, '#ffffff')];
  // Merge matching horizontal runs across adjacent rows; preserve exact module edges with fewer editable objects.
  let previous = new Map();
  for (let row = 0; row < count; row++) {
    const current = new Map();
    for (let col = 0; col < count;) {
      if (!code.isDark(row, col)) { col++; continue; }
      const start = col;
      while (col < count && code.isDark(row, col)) col++;
      const width = col - start, key = `${start}:${width}`;
      let run = previous.get(key);
      if (run) run.height += QR_MODULE_SIZE;
      else {
        run = rect((start + QR_QUIET_MODULES) * QR_MODULE_SIZE, (row + QR_QUIET_MODULES) * QR_MODULE_SIZE, width * QR_MODULE_SIZE, QR_MODULE_SIZE, '#111111');
        elements.push(run);
      }
      current.set(key, run);
    }
    previous = current;
  }
  if (caption.trim()) elements.push({ type: 'text', text: caption.trim(), x: 0, y: side + 16, fontSize: 16, fontFamily: 2, strokeColor: '#111111', opacity: 100 });
  if (elements.length > MAX_ELEMENTS) throw Error('This QR code needs too many drawing objects. Use a shorter link or message.');
  return elements;
}
