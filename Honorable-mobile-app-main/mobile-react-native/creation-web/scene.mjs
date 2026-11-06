import { filesForElements, validPhotoId, MAX_PHOTO_SIDE } from './image-assets.mjs';
export const MAX_ELEMENTS = 500, MAX_DRAFT_BYTES = 1024 * 1024;
export function validateDrawingElements(elements) {
  if (!Array.isArray(elements) || elements.some(element => !element || typeof element !== 'object')) throw Error('Invalid drawing objects.');
  const active = elements.filter(element => !element.isDeleted);
  const ids = new Set();
  if (active.length > MAX_ELEMENTS) throw Error('Use up to 500 drawing objects.');
  for (const element of active) {
    if (typeof element.id !== 'string' || !element.id || element.id.length > 128 || ids.has(element.id)) throw Error('Drawing object identifiers must be unique.');
    ids.add(element.id);
    if (element.angle != null && (!Number.isFinite(element.angle) || Math.abs(element.angle) > Math.PI * 2)) throw Error('Invalid object rotation.');
    if (element.opacity != null && (!Number.isFinite(element.opacity) || element.opacity < 0 || element.opacity > 100)) throw Error('Invalid object opacity.');
    if (element.width < 0 || element.height < 0) throw Error('Invalid object dimensions.');
    if (element.points !== undefined && (!Array.isArray(element.points) || element.points.length > 20000 || element.points.some(point =>
      !Array.isArray(point) || point.length !== 2 || point.some(value => !Number.isFinite(value) || Math.abs(value) > 1e6),
    ))) throw Error('Drawing points exceed the supported geometry bounds.');
    if (element.pressures !== undefined && (!Array.isArray(element.pressures) ||
      (element.pressures.length !== 0 && element.pressures.length !== element.points?.length) ||
      element.pressures.some(value => !Number.isFinite(value) || value < 0 || value > 1)
    )) throw Error('Invalid drawing pressure data.');
    for (const field of ['strokeColor', 'backgroundColor']) if (element[field] != null && (typeof element[field] !== 'string' || !/^(?:#(?:[\da-f]{3}|[\da-f]{4}|[\da-f]{6}|[\da-f]{8})|transparent)$/i.test(element[field]))) throw Error('Unsupported object color.');
    if (element.link) throw Error('External links are not supported in local projects.');
    const hidden = element.customData?.nobleHidden;
    if (hidden && (!Number.isFinite(hidden.opacity) || hidden.opacity < 0 || hidden.opacity > 100 || typeof hidden.locked !== 'boolean')) throw Error('Invalid hidden object state.');
    if (!['rectangle', 'diamond', 'ellipse', 'line', 'arrow', 'freedraw', 'text', 'frame', 'image'].includes(element.type)) throw Error('This local workspace supports drawing, photos, shapes and text.');
    for (const field of ['x', 'y', 'width', 'height']) if (!Number.isFinite(element[field]) || Math.abs(element[field]) > 1e6) throw Error('Drawing coordinates exceed the supported bounds.');
    if ((element.type === 'text' || element.text != null) && (typeof element.text !== 'string' || element.text.length > 2000)) throw Error('Use shorter text.');
    // The renderer uses originalText when restoring/wrapping editable text. Validate it too; never truncate saved work.
    if (element.originalText !== undefined && (typeof element.originalText !== 'string' || element.originalText.length > 2000)) throw Error('Use shorter editable text.');
    if (element.type === 'image') {
      if (!validPhotoId(element.fileId) || element.width <= 0 || element.height <= 0 || !Array.isArray(element.scale) || element.scale.length !== 2 || element.scale.some(value => value !== 1 && value !== -1)) throw Error('Stored photo placement is invalid.');
      if (element.status != null && !['saved', 'pending', 'error'].includes(element.status)) throw Error('Stored photo status is invalid.');
      if (element.crop != null) {
        const crop = element.crop;
        if (typeof crop !== 'object' || ['x', 'y', 'width', 'height', 'naturalWidth', 'naturalHeight'].some(key => !Number.isFinite(crop[key])) || crop.x < -1e-5 || crop.y < -1e-5 || crop.width <= 0 || crop.height <= 0 || crop.naturalWidth < 1 || crop.naturalHeight < 1 || crop.naturalWidth > MAX_PHOTO_SIDE || crop.naturalHeight > MAX_PHOTO_SIDE || crop.x + crop.width > crop.naturalWidth + 1e-5 || crop.y + crop.height > crop.naturalHeight + 1e-5) throw Error('Stored photo crop is invalid.');
      }
    }
  }
  if (new TextEncoder().encode(JSON.stringify(active)).length > MAX_DRAFT_BYTES) throw Error('Drawing draft is too large.');
  return active;
}
export function encodeDraft(elements, state = {}, files = {}) {
  const active = validateDrawingElements(elements);
  const photos = filesForElements(active, files);
  const stroke = typeof state.currentItemStrokeColor === 'string' && /^#(?:[\da-f]{3}|[\da-f]{4}|[\da-f]{6}|[\da-f]{8})$/i.test(state.currentItemStrokeColor) ? state.currentItemStrokeColor : '#111111';
  const appState = { viewBackgroundColor: '#ffffff', currentItemStrokeColor: stroke, currentItemFontFamily: state.currentItemFontFamily || 2 };
  const hasPhotos = active.some(element => element.type === 'image');
  const value = JSON.stringify({ version: hasPhotos ? 2 : 1, elements: active, appState, ...(hasPhotos ? { files: photos } : {}) });
  if (new TextEncoder().encode(value).length > MAX_DRAFT_BYTES) throw Error('Drawing draft is too large.');
  return value;
}
export function decodeDraft(value) {
  if (!value) return null;
  if (new TextEncoder().encode(value).length > MAX_DRAFT_BYTES) throw Error('Drawing draft is too large.');
  const saved = JSON.parse(value);
  if (![1, 2].includes(saved.version) || !Array.isArray(saved.elements)) throw Error('Unsupported drawing draft.');
  const images = saved.elements.some(element => !element.isDeleted && element.type === 'image');
  if ((saved.version === 1 && images) || (saved.version === 2 && (!images || !saved.files))) throw Error('Photo draft data is missing.');
  return JSON.parse(encodeDraft(saved.elements, saved.appState, saved.files));
}
