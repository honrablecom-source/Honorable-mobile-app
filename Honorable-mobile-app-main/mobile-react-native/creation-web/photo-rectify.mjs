import perspectiveTransform from 'perspective-transform';
import { photoBytes, MAX_PHOTO_SIDE } from './image-assets.mjs';

export const RECTIFY_FORMATS = Object.freeze({ edges: null, square: 1, portrait: 4 / 5, wide: 16 / 9 });
const distance = (a, b) => Math.hypot(a.x - b.x, a.y - b.y);

export function rectificationPlan(width, height, corners, format = 'edges') {
  if (!Number.isInteger(width) || !Number.isInteger(height) || width < 8 || height < 8 || width > MAX_PHOTO_SIDE || height > MAX_PHOTO_SIDE) throw Error('Use a photo from 8 to 1024 pixels on each side.');
  if (!Object.prototype.hasOwnProperty.call(RECTIFY_FORMATS, format)) throw Error('Choose a supported result format.');
  if (!Array.isArray(corners) || corners.length !== 4 || corners.some(point => !point || !Number.isFinite(point.x) || !Number.isFinite(point.y) || point.x < 0 || point.y < 0 || point.x > width - 1 || point.y > height - 1)) throw Error('Choose four corners inside the photo.');
  const points = corners.map(({ x, y }) => ({ x, y }));
  let area = 0;
  for (let i = 0; i < 4; i++) {
    const a = points[i], b = points[(i + 1) % 4], c = points[(i + 2) % 4];
    if (distance(a, b) < 4 || (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x) <= 1e-6) throw Error('Choose a convex outline in order: top-left, top-right, bottom-right, bottom-left. Keep corners apart.');
    area += a.x * b.y - b.x * a.y;
  }
  if (area / 2 < 16) throw Error('Choose a larger area to straighten.');
  let outputWidth = (distance(points[0], points[1]) + distance(points[3], points[2])) / 2 + 1;
  let outputHeight = (distance(points[0], points[3]) + distance(points[1], points[2])) / 2 + 1;
  const ratio = RECTIFY_FORMATS[format];
  if (ratio !== null) { const side = Math.max(outputWidth, outputHeight); outputWidth = ratio >= 1 ? side : side * ratio; outputHeight = ratio >= 1 ? side / ratio : side; }
  const scale = Math.min(1, MAX_PHOTO_SIDE / Math.max(outputWidth, outputHeight));
  outputWidth = Math.max(2, Math.round(outputWidth * scale)); outputHeight = Math.max(2, Math.round(outputHeight * scale));
  const destination = [0, 0, outputWidth - 1, 0, outputWidth - 1, outputHeight - 1, 0, outputHeight - 1];
  const transform = perspectiveTransform(points.flatMap(point => [point.x, point.y]), destination);
  const inverse = [...transform.coeffsInv];
  if (inverse.length !== 9 || inverse.some(value => !Number.isFinite(value))) throw Error('These corners produce an unstable perspective. Choose a wider outline.');
  let denominatorSign;
  for (let i = 0; i < 4; i++) {
    const x = destination[i * 2], y = destination[i * 2 + 1], denominator = inverse[6] * x + inverse[7] * y + inverse[8];
    if (Math.abs(denominator) < 1e-8 || (denominatorSign && Math.sign(denominator) !== denominatorSign)) throw Error('These corners produce an unstable perspective.');
    denominatorSign = Math.sign(denominator);
    const sx = (inverse[0] * x + inverse[1] * y + inverse[2]) / denominator, sy = (inverse[3] * x + inverse[4] * y + inverse[5]) / denominator;
    if (Math.hypot(sx - points[i].x, sy - points[i].y) > .01) throw Error('The transform could not retain the selected corners. Choose a clearer outline.');
  }
  return { sourceWidth: width, sourceHeight: height, width: outputWidth, height: outputHeight, inverse, corners: points, format };
}

function validatePixels(input, width, height) {
  if (!(input instanceof Uint8Array || input instanceof Uint8ClampedArray) || !Number.isInteger(width) || !Number.isInteger(height) || width < 8 || height < 8 || width > MAX_PHOTO_SIDE || height > MAX_PHOTO_SIDE || input.length !== width * height * 4) throw Error('The local photo pixels are invalid.');
}

function renderRows(input, plan, output, first, last) {
  const matrix = plan.inverse, width = plan.sourceWidth, height = plan.sourceHeight;
  for (let y = first; y < last; y++) for (let x = 0; x < plan.width; x++) {
    const denominator = matrix[6] * x + matrix[7] * y + matrix[8];
    const sx = Math.max(0, Math.min(width - 1, (matrix[0] * x + matrix[1] * y + matrix[2]) / denominator));
    const sy = Math.max(0, Math.min(height - 1, (matrix[3] * x + matrix[4] * y + matrix[5]) / denominator));
    const left = Math.floor(sx), top = Math.floor(sy), right = Math.min(width - 1, left + 1), bottom = Math.min(height - 1, top + 1), dx = sx - left, dy = sy - top;
    let alpha = 0, red = 0, green = 0, blue = 0;
    for (let n = 0; n < 4; n++) {
      const px = n % 2 ? right : left, py = n >= 2 ? bottom : top;
      const weight = (n % 2 ? dx : 1 - dx) * (n >= 2 ? dy : 1 - dy), index = (py * width + px) * 4, coverage = input[index + 3] * weight;
      alpha += coverage; red += input[index] * coverage; green += input[index + 1] * coverage; blue += input[index + 2] * coverage;
    }
    const target = (y * plan.width + x) * 4;
    output[target + 3] = Math.round(alpha);
    if (alpha > 0) { output[target] = Math.round(red / alpha); output[target + 1] = Math.round(green / alpha); output[target + 2] = Math.round(blue / alpha); }
  }
}

/** Premultiplied-alpha bilinear sampling prevents invisible RGB from tinting visible edges. */
export function rectifyPixels(input, width, height, corners, format = 'edges') {
  validatePixels(input, width, height);
  const plan = rectificationPlan(width, height, corners, format), data = new Uint8ClampedArray(plan.width * plan.height * 4);
  renderRows(input, plan, data, 0, plan.height); return { ...plan, data };
}

/** Browser path yields between bounded row chunks and checks source lifetime before publishing. */
export async function rectifyPhoto(dataURL, corners, format, requireCurrent = () => {}, onProgress = () => {}) {
  requireCurrent(); const info = photoBytes(dataURL), plan = rectificationPlan(info.width, info.height, corners, format);
  const image = new Image(); image.decoding = 'async';
  await new Promise((resolve, reject) => { image.onload = resolve; image.onerror = () => reject(Error('The embedded photo could not be opened.')); image.src = dataURL; });
  requireCurrent();
  if (image.naturalWidth !== info.width || image.naturalHeight !== info.height) throw Error('The source photo dimensions changed.');
  const source = document.createElement('canvas'), result = document.createElement('canvas');
  try {
    source.width = info.width; source.height = info.height; const sourceContext = source.getContext('2d'); if (!sourceContext) throw Error('Local photo processing is unavailable.');
    sourceContext.drawImage(image, 0, 0); const input = sourceContext.getImageData(0, 0, info.width, info.height).data, output = new Uint8ClampedArray(plan.width * plan.height * 4);
    for (let row = 0; row < plan.height; row += 32) { requireCurrent(); renderRows(input, plan, output, row, Math.min(plan.height, row + 32)); onProgress(Math.min(1, (row + 32) / plan.height)); await new Promise(resolve => setTimeout(resolve, 0)); }
    requireCurrent(); result.width = plan.width; result.height = plan.height; const context = result.getContext('2d'); if (!context) throw Error('Local photo output is unavailable.');
    context.putImageData(new ImageData(output, plan.width, plan.height), 0, 0);
    const blob = await new Promise((resolve, reject) => result.toBlob(value => value ? resolve(value) : reject(Error('The straightened photo could not be encoded.')), 'image/png'));
    requireCurrent(); return { blob, width: plan.width, height: plan.height, corners: plan.corners, format };
  } finally { source.width = source.height = result.width = result.height = 0; }
}
