import { imageInfo, photoBytes, photoFile, MAX_SOURCE_BYTES, MAX_SOURCE_PIXELS, MAX_PHOTO_SIDE, MAX_PHOTO_BYTES } from './image-assets.mjs';

async function bitmapFor(blob) {
  if (typeof createImageBitmap === 'function') return createImageBitmap(blob, { imageOrientation: 'from-image' });
  const url = URL.createObjectURL(blob), image = new Image();
  try {
    await new Promise((resolve, reject) => { image.onload = resolve; image.onerror = () => reject(Error('The photo could not be decoded.')); image.src = url; });
    return { width: image.naturalWidth, height: image.naturalHeight, image, close: () => URL.revokeObjectURL(url) };
  } catch (failure) { URL.revokeObjectURL(url); throw failure; }
}
const blobURL = blob => new Promise((resolve, reject) => {
  const reader = new FileReader();
  reader.onload = () => resolve(String(reader.result));
  reader.onerror = () => reject(Error('The local photo copy could not be read.'));
  reader.readAsDataURL(blob);
});
const encoded = (canvas, mimeType) => new Promise((resolve, reject) => canvas.toBlob(blob => blob ? resolve(blob) : reject(Error('The photo copy could not be prepared.')), mimeType, 0.86));

/** Verify actual bounded bitmap decoding before a project/component changes the current drawing. */
export async function verifyPhotoCopies(files) {
  for (const file of Object.values(files)) {
    const { bytes, width, height, mimeType } = photoBytes(file.dataURL);
    const bitmap = await bitmapFor(new Blob([bytes], { type: mimeType }));
    try {
      if (bitmap.width !== width || bitmap.height !== height) throw Error('Stored photo dimensions could not be verified. Import that photo again.');
    } finally { bitmap.close?.(); }
  }
}

/** Re-encode local input to strip original metadata and fit the existing document budget. */
export async function prepareLocalPhoto(file, accepts = () => true) {
  if (!file || !Number.isFinite(file.size) || file.size <= 0 || file.size > MAX_SOURCE_BYTES || typeof file.arrayBuffer !== 'function') throw Error('Choose a local PNG or JPEG photo up to 12 MB.');
  const bytes = new Uint8Array(await file.arrayBuffer());
  const info = imageInfo(bytes);
  const bitmap = await bitmapFor(new Blob([bytes], { type: info.mimeType }));
  try {
    if (bitmap.width < 1 || bitmap.height < 1 || bitmap.width * bitmap.height > MAX_SOURCE_PIXELS || Math.max(bitmap.width, bitmap.height) > 16384) throw Error('Choose a photo up to 16 megapixels.');
    let side = Math.min(MAX_PHOTO_SIDE, Math.max(bitmap.width, bitmap.height));
    while (true) {
      const scale = Math.min(1, side / Math.max(bitmap.width, bitmap.height));
      const canvas = document.createElement('canvas');
      canvas.width = Math.max(1, Math.floor(bitmap.width * scale)); canvas.height = Math.max(1, Math.floor(bitmap.height * scale));
      const context = canvas.getContext('2d');
      if (!context) throw Error('Photo preparation is unavailable on this device.');
      context.imageSmoothingEnabled = true; context.imageSmoothingQuality = 'high';
      context.drawImage(bitmap.image || bitmap, 0, 0, canvas.width, canvas.height);
      const blob = await encoded(canvas, info.mimeType);
      if (blob.size <= MAX_PHOTO_BYTES) {
        const candidate = photoFile(await blobURL(blob));
        if (accepts(candidate)) return candidate;
      }
      if (side <= 64) throw Error('There is not enough room for another photo in this drawing. Remove an object or use a new composition.');
      side = Math.max(64, Math.floor(side * 0.75));
    }
  } finally { bitmap.close?.(); }
}

/** Native input is already a fitted, metadata-free copy; retain its bytes unless the document needs a smaller copy. */
export async function prepareNativePhoto(dataURL, accepts = () => true) {
  const file = photoFile(dataURL);
  await verifyPhotoCopies({ [file.id]: file });
  if (accepts(file)) return file;
  const { bytes, mimeType } = photoBytes(dataURL);
  return prepareLocalPhoto(new Blob([bytes], { type: mimeType }), accepts);
}
