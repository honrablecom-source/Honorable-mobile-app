import Delaunator from 'delaunator';
import { photoBytes, MAX_PHOTO_SIDE } from './image-assets.mjs';

export const MAX_MOSAIC_TRIANGLES = 288;
const cross = (a, b, x, y) => (b[0] - a[0]) * (y - a[1]) - (b[1] - a[1]) * (x - a[0]);

/** Stable jittered mesh, including the exact rectangular boundary; no random or remote state. */
function mesh(width, height, detail) {
  const longest = Math.max(width, height), divisions = Math.min(detail, Math.floor(longest / 2));
  const columns = Math.max(1, Math.round(divisions * width / longest));
  const rows = Math.max(1, Math.round(divisions * height / longest));
  const points = [];
  const jitter = (index, salt) => (((Math.imul(index + salt, 1597334677) ^ Math.imul(index, 3812015801)) >>> 0) / 0xffffffff - .5) * .5;
  for (let row = 0; row <= rows; row++) for (let column = 0; column <= columns; column++) {
    const interior = row > 0 && row < rows && column > 0 && column < columns;
    const index = row * (columns + 1) + column;
    points.push([(column + (interior ? jitter(index, 17) : 0)) * width / columns,
      (row + (interior ? jitter(index, 83) : 0)) * height / rows]);
  }
  return points;
}

/** Average encoded RGB by source alpha; transparent hidden colors cannot tint a facet. */
function color(input, width, height, vertices) {
  const [a, b, c] = vertices, sign = Math.sign(cross(a, b, c[0], c[1]));
  if (!sign) throw Error('The triangle mesh is invalid. Try a different detail level.');
  const left = Math.max(0, Math.floor(Math.min(...vertices.map(p => p[0]))));
  const top = Math.max(0, Math.floor(Math.min(...vertices.map(p => p[1]))));
  const right = Math.min(width, Math.ceil(Math.max(...vertices.map(p => p[0]))));
  const bottom = Math.min(height, Math.ceil(Math.max(...vertices.map(p => p[1]))));
  let pixels = 0, alpha = 0, red = 0, green = 0, blue = 0;
  const add = (x, y) => {
    const i = (y * width + x) * 4, weight = input[i + 3];
    pixels++; alpha += weight;
    red += input[i] * weight; green += input[i + 1] * weight; blue += input[i + 2] * weight;
  };
  for (let y = top; y < bottom; y++) for (let x = left; x < right; x++) {
    if (cross(a, b, x + .5, y + .5) * sign >= -1e-8 &&
        cross(b, c, x + .5, y + .5) * sign >= -1e-8 &&
        cross(c, a, x + .5, y + .5) * sign >= -1e-8) add(x, y);
  }
  // A subpixel-thin facet may contain no pixel centers: use its source centroid instead.
  if (!pixels) add(Math.min(width - 1, Math.max(0, Math.floor((a[0] + b[0] + c[0]) / 3))),
    Math.min(height - 1, Math.max(0, Math.floor((a[1] + b[1] + c[1]) / 3))));
  if (!alpha) return null;
  const hex = '#' + [red, green, blue].map(sum => Math.round(sum / alpha).toString(16).padStart(2, '0')).join('');
  return { hex, opacity: Math.max(1, Math.round(100 * alpha / (pixels * 255))) };
}

/** Yield between small batches so the browser can cancel work and respond to drawing changes. */
function* steps(input, width, height, detail, requireCurrent) {
  if (!(input instanceof Uint8Array || input instanceof Uint8ClampedArray) ||
      !Number.isInteger(width) || !Number.isInteger(height) || width < 8 || height < 8 ||
      width > MAX_PHOTO_SIDE || height > MAX_PHOTO_SIDE || input.length !== width * height * 4) {
    throw Error('Use a local photo from 8 to 1024 pixels on each side.');
  }
  if (!Number.isInteger(detail) || detail < 3 || detail > 12) throw Error('Choose triangle detail from 3 to 12.');
  requireCurrent();
  const points = mesh(width, height, detail), { triangles } = Delaunator.from(points), plan = [];
  if (triangles.length / 3 > MAX_MOSAIC_TRIANGLES) throw Error('The mosaic has too many triangles. Lower the detail.');
  for (let i = 0; i < triangles.length; i += 3) {
    requireCurrent();
    const vertices = [points[triangles[i]], points[triangles[i + 1]], points[triangles[i + 2]]];
    const sampled = color(input, width, height, vertices);
    if (sampled) {
      // Excalidraw line geometry is anchored at its first point. Store that canonical form
      // immediately so reopening cannot rewrite the points and object origin.
      const x = vertices[0][0], y = vertices[0][1];
      plan.push({ type: 'line', x, y,
        width: Math.max(...vertices.map(p => p[0])) - Math.min(...vertices.map(p => p[0])),
        height: Math.max(...vertices.map(p => p[1])) - Math.min(...vertices.map(p => p[1])),
        points: [...vertices, vertices[0]].map(p => [p[0] - x, p[1] - y]),
        strokeColor: 'transparent', backgroundColor: sampled.hex, fillStyle: 'solid',
        strokeWidth: 1, roughness: 0, opacity: sampled.opacity, angle: 0,
        startArrowhead: null, endArrowhead: null, roundness: null });
    }
    if (i % 24 === 21) yield i / 3 + 1;
  }
  requireCurrent();
  if (!plan.length) throw Error('No visible colors to turn into a mosaic. Choose a visible photo.');
  return { width, height, plan, triangleCount: triangles.length / 3, omittedTransparent: triangles.length / 3 - plan.length };
}

export function mosaicPixels(input, width, height, detail = 6, requireCurrent = () => {}) {
  const iterator = steps(input, width, height, detail, requireCurrent);
  let next = iterator.next();
  while (!next.done) next = iterator.next();
  return next.value;
}

export async function mosaicPhoto(dataURL, detail, requireCurrent = () => {}) {
  requireCurrent();
  const info = photoBytes(dataURL), image = new Image();
  image.decoding = 'async';
  await new Promise((resolve, reject) => { image.onload = resolve; image.onerror = () => reject(Error('The embedded source photo could not be opened.')); image.src = dataURL; });
  requireCurrent();
  if (image.naturalWidth !== info.width || image.naturalHeight !== info.height) throw Error('The source dimensions changed.');
  const canvas = document.createElement('canvas');
  try {
    canvas.width = info.width; canvas.height = info.height;
    const context = canvas.getContext('2d', { willReadFrequently: true });
    if (!context) throw Error('Local photo processing is unavailable.');
    context.drawImage(image, 0, 0);
    const input = context.getImageData(0, 0, info.width, info.height).data;
    await new Promise(resolve => setTimeout(resolve, 0)); requireCurrent();
    const iterator = steps(input, info.width, info.height, detail, requireCurrent);
    let next = iterator.next();
    while (!next.done) { await new Promise(resolve => setTimeout(resolve, 0)); requireCurrent(); next = iterator.next(); }
    requireCurrent(); return next.value;
  } finally { canvas.width = canvas.height = 0; }
}
