import smartcrop from 'smartcrop';
import { photoBytes, MAX_PHOTO_SIDE } from './image-assets.mjs';
import { replaceablePhoto } from './photo-replace.mjs';
import { validateDrawingElements } from './scene.mjs';

export const FRAMING_FORMATS = Object.freeze({ square: [1, 1], portrait: [4, 5], wide: [16, 9], vertical: [9, 16] });

export function framingCrop(value, width, height) {
  if (!Number.isInteger(width) || !Number.isInteger(height) || width < 1 || height < 1 || width > MAX_PHOTO_SIDE || height > MAX_PHOTO_SIDE || !value || ['x', 'y', 'width', 'height'].some(key => !Number.isFinite(value[key])) || value.x < 0 || value.y < 0 || value.x >= width || value.y >= height || value.width <= 0 || value.height <= 0 || value.x + value.width > width + 1e-5 || value.y + value.height > height + 1e-5) throw Error('The suggested crop is outside this photo. Try a different format.');
  return { x: value.x, y: value.y, width: Math.min(value.width, width - value.x), height: Math.min(value.height, height - value.y), naturalWidth: width, naturalHeight: height };
}

/** Immutable embedded source stays attached. Fit the crop inside the old box, retaining its center. */
export function framingPatch(elements, id, expectedFileId, width, height, suggestion) {
  const photo = replaceablePhoto(elements, id);
  if (photo.fileId !== expectedFileId) throw Error('The photo changed. Open suggested framing again.');
  const crop = framingCrop(suggestion, width, height);
  const factor = Math.min(photo.width / crop.width, photo.height / crop.height);
  const nextWidth = crop.width * factor, nextHeight = crop.height * factor;
  const patch = { id, crop, width: nextWidth, height: nextHeight, x: photo.x + (photo.width - nextWidth) / 2, y: photo.y + (photo.height - nextHeight) / 2 };
  validateDrawingElements(elements.map(element => element.id === id ? { ...element, ...patch } : element));
  if (photo.crop && Object.keys(crop).every(key => photo.crop[key] === crop[key]) && ['x', 'y', 'width', 'height'].every(key => Math.abs(photo[key] - patch[key]) < 1e-8)) return null;
  return patch;
}

/** Smartcrop uses local canvas heuristics. The callback fences each asynchronous phase. */
export async function suggestFraming(dataURL, format, requireCurrent = () => {}) {
  if (!Object.prototype.hasOwnProperty.call(FRAMING_FORMATS, format)) throw Error('Choose a supported crop format.');
  const info = photoBytes(dataURL);
  if (info.width < 32 || info.height < 32) throw Error('Use a photo at least 32 pixels on each side for suggested framing.');
  requireCurrent();
  const image = new Image(); image.decoding = 'async';
  await new Promise((resolve, reject) => { image.onload = resolve; image.onerror = () => reject(Error('This embedded photo could not be opened.')); image.src = dataURL; });
  requireCurrent();
  if (image.naturalWidth !== info.width || image.naturalHeight !== info.height) throw Error('The embedded photo dimensions changed.');
  const [width, height] = FRAMING_FORMATS[format];
  const result = await smartcrop.crop(image, { width, height, minScale: 1, maxScale: 1 });
  requireCurrent();
  return { image, crop: framingCrop(result.topCrop, info.width, info.height), width: info.width, height: info.height };
}

/** Match the object's crop, flip, rotation and opacity in a bounded, local photo-only preview. */
export function drawFramingPreview(canvas, image, crop, photo) {
  const angle = photo.angle || 0, cosine = Math.abs(Math.cos(angle)), sine = Math.abs(Math.sin(angle));
  const width = crop.width * cosine + crop.height * sine, height = crop.width * sine + crop.height * cosine;
  const factor = 320 / Math.max(width, height);
  canvas.width = Math.max(1, Math.ceil(width * factor)); canvas.height = Math.max(1, Math.ceil(height * factor));
  const context = canvas.getContext('2d'); if (!context) throw Error('Crop preview is unavailable on this device.');
  context.fillStyle = '#ffffff'; context.fillRect(0, 0, canvas.width, canvas.height);
  context.translate(canvas.width / 2, canvas.height / 2); context.scale(factor, factor); context.rotate(angle); context.scale(...photo.scale); context.globalAlpha = photo.opacity / 100;
  context.drawImage(image, crop.x, crop.y, crop.width, crop.height, -crop.width / 2, -crop.height / 2, crop.width, crop.height);
}
