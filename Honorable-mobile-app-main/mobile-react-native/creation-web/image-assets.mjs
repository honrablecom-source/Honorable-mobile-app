import { sha256 } from '@noble/hashes/sha2.js';
import { bytesToHex } from '@noble/hashes/utils.js';

export const MAX_PHOTOS = 8;
export const MAX_PHOTO_BYTES = 192 * 1024;
export const MAX_PHOTO_SIDE = 1024;
export const MAX_SOURCE_BYTES = 12 * 1024 * 1024;
export const MAX_SOURCE_PIXELS = 16 * 1024 * 1024;
const ID = /^[a-f0-9]{64}$/;
const fail = message => { throw Error(message); };

/** Inspect bounded local bytes only. No URL resolution, original names or account metadata. */
export function imageInfo(bytes, prepared = false) {
  if (!(bytes instanceof Uint8Array) || !bytes.length || bytes.length > (prepared ? MAX_PHOTO_BYTES : MAX_SOURCE_BYTES)) fail('This photo is too large for the drawing workspace.');
  let width, height, mimeType;
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  const png = [137, 80, 78, 71, 13, 10, 26, 10];
  if (bytes.length >= 33 && png.every((value, index) => bytes[index] === value)) {
    mimeType = 'image/png';
    if (view.getUint32(8) !== 13 || String.fromCharCode(...bytes.subarray(12, 16)) !== 'IHDR') fail('The PNG header is invalid.');
    width = view.getUint32(16); height = view.getUint32(20);
    let position = 8, ended = false, pixels = false;
    while (position + 12 <= bytes.length) {
      const length = view.getUint32(position), end = position + 12 + length;
      if (end > bytes.length) fail('The PNG photo is incomplete.');
      const kind = String.fromCharCode(...bytes.subarray(position + 4, position + 8));
      if (['acTL', 'fcTL', 'fdAT'].includes(kind)) fail('Choose a still PNG or JPEG photo.');
      if (kind === 'IHDR' && position !== 8) fail('The PNG header is invalid.');
      if (kind === 'IDAT') pixels = true;
      if (kind === 'IEND') {
        if (length !== 0 || end !== bytes.length || !pixels) fail('The PNG photo is invalid.');
        ended = true; break;
      }
      position = end;
    }
    if (!ended) fail('The PNG photo is incomplete.');
  } else if (bytes.length >= 4 && bytes[0] === 0xff && bytes[1] === 0xd8) {
    mimeType = 'image/jpeg';
    let position = 2;
    while (position + 3 < bytes.length) {
      if (bytes[position++] !== 0xff) fail('The JPEG photo is invalid.');
      while (bytes[position] === 0xff) position++;
      const marker = bytes[position++];
      if (marker === 0xda || marker === 0xd9) break;
      if (marker === 0x01 || (marker >= 0xd0 && marker <= 0xd8)) continue;
      if (position + 2 > bytes.length) break;
      const length = view.getUint16(position);
      if (length < 2 || position + length > bytes.length) fail('The JPEG photo is incomplete.');
      if ([0xc0, 0xc1, 0xc2, 0xc3, 0xc5, 0xc6, 0xc7, 0xc9, 0xca, 0xcb, 0xcd, 0xce, 0xcf].includes(marker)) {
        if (length < 8 || width !== undefined) fail('The JPEG dimensions are invalid.');
        height = view.getUint16(position + 3); width = view.getUint16(position + 5);
      }
      position += length;
    }
    if (prepared && (bytes[bytes.length - 2] !== 0xff || bytes[bytes.length - 1] !== 0xd9)) fail('The JPEG photo is incomplete.');
  } else fail('Choose a PNG or JPEG photo.');
  const side = prepared ? MAX_PHOTO_SIDE : 16384;
  const pixels = prepared ? MAX_PHOTO_SIDE ** 2 : MAX_SOURCE_PIXELS;
  if (!Number.isInteger(width) || !Number.isInteger(height) || width < 1 || height < 1 || width > side || height > side || width * height > pixels) fail(prepared ? 'Stored photo dimensions are invalid.' : 'Choose a photo up to 16 megapixels.');
  return { width, height, mimeType };
}

export function photoBytes(dataURL) {
  if (typeof dataURL !== 'string' || dataURL.length > Math.ceil(MAX_PHOTO_BYTES / 3) * 4 + 32) fail('Stored photo is too large.');
  const match = /^data:(image\/(?:png|jpeg));base64,([A-Za-z0-9+/]+={0,2})$/.exec(dataURL);
  if (!match || match[2].length % 4 !== 0) fail('Only embedded PNG or JPEG photo copies are supported.');
  let binary;
  try { binary = atob(match[2]); } catch { fail('Stored photo encoding is invalid.'); }
  if (btoa(binary) !== match[2]) fail('Stored photo encoding is invalid.');
  const bytes = Uint8Array.from(binary, character => character.charCodeAt(0));
  const info = imageInfo(bytes, true);
  if (info.mimeType !== match[1]) fail('Stored photo type does not match its bytes.');
  return { bytes, ...info };
}

export function photoFile(dataURL, created = Date.now()) {
  const { bytes, mimeType } = photoBytes(dataURL);
  if (!Number.isSafeInteger(created) || created < 0) fail('Stored photo date is invalid.');
  const id = bytesToHex(sha256(bytes));
  return { id, dataURL, mimeType, created };
}

export function validPhotoId(id) { return typeof id === 'string' && ID.test(id); }

export function validatePhotoFile(key, file) {
  if (!validPhotoId(key) || !file || file.id !== key) fail('Stored photo identifier is invalid.');
  const normalized = photoFile(file.dataURL, file.created);
  if (normalized.id !== key || normalized.mimeType !== file.mimeType) fail('Stored photo integrity check failed.');
  return normalized;
}

/** Return only referenced copies, so hidden history/unrelated pictures never leak into project transfer. */
export function filesForElements(elements, files = {}) {
  if (!files || typeof files !== 'object' || Array.isArray(files) || Object.keys(files).length > MAX_PHOTOS) fail('Use up to eight photo copies in this session. Save and reopen to release older photos.');
  const ids = new Set(elements.filter(element => !element.isDeleted && element.type === 'image').map(element => element.fileId));
  if (ids.size > MAX_PHOTOS) fail('Use up to eight photos in one composition.');
  const selected = {};
  for (const id of ids) {
    if (!Object.prototype.hasOwnProperty.call(files, id)) fail('A referenced photo copy is missing.');
    selected[id] = validatePhotoFile(id, files[id]);
    const info = photoBytes(selected[id].dataURL);
    for (const element of elements.filter(element => element.type === 'image' && element.fileId === id && !element.isDeleted)) {
      const crop = element.crop;
      if (crop && (crop.naturalWidth !== info.width || crop.naturalHeight !== info.height)) fail('Photo crop dimensions do not match the stored copy.');
    }
  }
  return selected;
}

/** Content IDs make duplicate pictures reusable without replacing an earlier drawing's bytes or Undo data. */
export function mergePhotoFiles(current = {}, incoming = {}) {
  const merged = {};
  for (const source of [current, incoming]) {
    if (!source || typeof source !== 'object' || Array.isArray(source)) fail('Photo copies are unavailable.');
    for (const [id, file] of Object.entries(source)) {
      const normalized = validatePhotoFile(id, file);
      if (merged[id] && merged[id].dataURL !== normalized.dataURL) fail('Conflicting photo copies were refused.');
      merged[id] ||= normalized;
      if (Object.keys(merged).length > MAX_PHOTOS) fail('This session has eight photo copies. Save and reopen the drawing before adding more.');
    }
  }
  return merged;
}
