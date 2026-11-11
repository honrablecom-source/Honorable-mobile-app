// Local document thresholding. Encoded RGB is composited over white before analysis.
export function documentSettings(width, height, radius = 15, sensitivity = 20, lightText = false) {
  if (![width, height].every(n => Number.isInteger(n) && n >= 8 && n <= 1024)) throw Error('Use embedded artwork from 8 to 1024 pixels per side.');
  if (!Number.isInteger(radius) || radius < 1 || radius > 63) throw Error('Use a whole window radius from 1 to 63 pixels.');
  if (!Number.isInteger(sensitivity) || sensitivity < 5 || sensitivity > 50) throw Error('Use whole sensitivity from 5 to 50 percent.');
  if (typeof lightText !== 'boolean') throw Error('Choose a valid text polarity.');
  return { width, height, radius, sensitivity, lightText };
}
function capture(input, width, height, radius, sensitivity, lightText) {
  const settings = documentSettings(width, height, radius, sensitivity, lightText);
  if (!(input instanceof Uint8Array || input instanceof Uint8ClampedArray) || !(input.buffer instanceof ArrayBuffer) || input.length !== width * height * 4) throw Error('The document source RGBA pixels are incomplete.');
  const cells = (width + 1) * (height + 1);
  return { settings, source: new Uint8ClampedArray(input), gray: new Uint8Array(width * height), sum: new Uint32Array(cells), squared: new Float64Array(cells), data: new Uint8ClampedArray(input.length), blackPixels: 0, changed: 0 };
}
function integrate(t, first, last) {
  const { width, lightText } = t.settings, stride = width + 1;
  for (let y = first; y < last; y++) {
    let sum = 0, squared = 0;
    for (let x = 0; x < width; x++) {
      const p = y * width + x, i = p * 4, a = t.source[i + 3];
      let luma = 54 * t.source[i] + 183 * t.source[i + 1] + 19 * t.source[i + 2];
      if (lightText) luma = 65280 - luma;
      const g = Math.round((a * luma + (255 - a) * 65280) / 65280);
      t.gray[p] = g; sum += g; squared += g * g;
      const at = (y + 1) * stride + x + 1;
      t.sum[at] = t.sum[at - stride] + sum;
      t.squared[at] = t.squared[at - stride] + squared;
    }
  }
}
function region(buffer, stride, left, top, right, bottom) {
  return buffer[bottom * stride + right] - buffer[top * stride + right] - buffer[bottom * stride + left] + buffer[top * stride + left];
}
function threshold(t, first, last) {
  const { width, height, radius, sensitivity } = t.settings, stride = width + 1;
  for (let y = first; y < last; y++) for (let x = 0; x < width; x++) {
    const left = Math.max(0, x - radius), right = Math.min(width, x + radius + 1), top = Math.max(0, y - radius), bottom = Math.min(height, y + radius + 1), n = (right - left) * (bottom - top);
    const mean = region(t.sum, stride, left, top, right, bottom) / n;
    const variance = Math.max(0, region(t.squared, stride, left, top, right, bottom) / n - mean * mean);
    const limit = mean * (1 + sensitivity / 100 * (Math.sqrt(variance) / 128 - 1));
    const p = y * width + x, at = p * 4, value = t.gray[p] <= limit ? 0 : 255;
    if (!value) t.blackPixels++;
    t.data[at] = t.data[at + 1] = t.data[at + 2] = value; t.data[at + 3] = 255;
    if (t.source[at] !== value || t.source[at + 1] !== value || t.source[at + 2] !== value || t.source[at + 3] !== 255) t.changed++;
  }
}
const finish = t => ({ ...t.settings, data: t.data, blackPixels: t.blackPixels, whitePixels: t.settings.width * t.settings.height - t.blackPixels, changed: t.changed });
export function documentPixels(input, width, height, radius = 15, sensitivity = 20, lightText = false) {
  const t = capture(input, width, height, radius, sensitivity, lightText);
  integrate(t, 0, height); threshold(t, 0, height); return finish(t);
}
export async function documentPixelsAsync(input, width, height, radius = 15, sensitivity = 20, lightText = false, current = () => {}, onProgress = () => {}, yieldTurn = () => new Promise(resolve => setTimeout(resolve, 0))) {
  current(); const t = capture(input, width, height, radius, sensitivity, lightText);
  for (const [stage, operation] of [integrate, threshold].entries()) for (let y = 0; y < height; y += 32) {
    current(); const end = Math.min(height, y + 32); operation(t, y, end); onProgress((stage + end / height) / 2); await yieldTurn();
  }
  current(); return finish(t);
}
