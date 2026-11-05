import { validateDrawingElements } from './scene.mjs';

/** Fit existing photos into square cells; never duplicate, crop or rewrite an asset. */
export function photoSheet(elements, ids, columns, cell, gap) {
  validateDrawingElements(elements);
  if (!Number.isInteger(columns) || columns < 1 || columns > 4 || !Number.isFinite(cell) || cell < 64 || cell > 1024 || !Number.isFinite(gap) || gap < 0 || gap > 256) {
    throw Error('Use 1–4 columns, cell size 64–1,024 and gap 0–256.');
  }
  const eligible = element => !element.isDeleted && element.type === 'image' && element.opacity !== 0 && !element.customData?.nobleHidden && !element.locked;
  const selected = ids === null ? elements.filter(eligible) : elements.filter(element => !element.isDeleted && ids?.[element.id]);
  if (selected.length < 2 || selected.length > 8) throw Error('Choose 2–8 photos for a contact sheet.');
  if (selected.some(element => !eligible(element) || element.groupIds?.length || element.frameId || element.containerId || element.boundElements?.length || Math.abs(element.angle || 0) > 1e-8)) {
    throw Error('Use visible, unlocked, ungrouped photos with no rotation or bindings. Other artwork stays unchanged.');
  }
  if (columns > selected.length) throw Error('Use no more columns than photos.');
  const left = Math.min(...selected.map(element => element.x)), top = Math.min(...selected.map(element => element.y));
  const patches = selected.map((element, index) => {
    const fit = cell / Math.max(element.width, element.height), width = element.width * fit, height = element.height * fit;
    return { id: element.id, x: left + (index % columns) * (cell + gap) + (cell - width) / 2, y: top + Math.floor(index / columns) * (cell + gap) + (cell - height) / 2, width, height };
  });
  const changes = new Map(patches.map(patch => [patch.id, patch]));
  validateDrawingElements(elements.map(element => changes.has(element.id) ? { ...element, ...changes.get(element.id) } : element));
  return patches;
}
