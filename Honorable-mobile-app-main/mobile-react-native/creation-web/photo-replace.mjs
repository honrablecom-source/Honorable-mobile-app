import { validateDrawingElements } from './scene.mjs';
import { validPhotoId, MAX_PHOTO_SIDE } from './image-assets.mjs';

export function replaceablePhoto(elements, id) {
  validateDrawingElements(elements);
  const photo = elements.find(element => element.id === id && !element.isDeleted);
  if (!photo || photo.type !== 'image') throw Error('This photo is unavailable.');
  if (photo.locked || photo.opacity === 0 || photo.customData?.nobleHidden || photo.groupIds?.length || photo.frameId || photo.containerId || photo.boundElements?.length) {
    throw Error('Show, unlock and ungroup the photo, and detach bindings, before replacing it.');
  }
  return photo;
}

/** Retain object identity and center; reset the old source crop and fit the new photo without stretching. */
export function replacementPatch(elements, id, fileId, width, height) {
  const photo = replaceablePhoto(elements, id);
  if (!validPhotoId(fileId) || !Number.isInteger(width) || !Number.isInteger(height) || width < 1 || height < 1 || width > MAX_PHOTO_SIDE || height > MAX_PHOTO_SIDE) throw Error('The replacement photo is invalid.');
  const factor = Math.min(photo.width / width, photo.height / height);
  const nextWidth = width * factor, nextHeight = height * factor;
  const patch = { id, fileId, width: nextWidth, height: nextHeight, x: photo.x + (photo.width - nextWidth) / 2, y: photo.y + (photo.height - nextHeight) / 2, crop: null, status: 'saved' };
  validateDrawingElements(elements.map(element => element.id === id ? { ...element, ...patch } : element));
  return patch;
}
