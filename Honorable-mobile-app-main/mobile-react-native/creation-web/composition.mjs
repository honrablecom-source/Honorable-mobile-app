import { validateDrawingElements } from './scene.mjs';
import { mergeProjectElements, validateProjectGraph } from './merge-project.mjs';

const SUPPORTED = new Set(['rectangle', 'diamond', 'ellipse', 'line', 'arrow', 'freedraw', 'text', 'frame', 'image']);
function selectedObjects(elements, ids) {
  const selected = elements.filter(element => !element.isDeleted && ids[element.id]);
  if (selected.some(element => element.type === 'frame' || element.locked || element.containerId || element.frameId || element.groupIds?.length || element.boundElements?.length)) {
    throw Error('Unlock and ungroup objects, and detach bound text or arrows, before arranging.');
  }
  if (selected.some(element => !SUPPORTED.has(element.type))) throw Error('Unsupported object type.');
  return selected;
}
function bounds(element) {
  const angle = element.angle || 0;
  const width = Math.abs(element.width * Math.cos(angle)) + Math.abs(element.height * Math.sin(angle));
  const height = Math.abs(element.width * Math.sin(angle)) + Math.abs(element.height * Math.cos(angle));
  return { left: element.x + element.width / 2 - width / 2, top: element.y + element.height / 2 - height / 2, width, height };
}
/** Return patches only. The editor commits all patches as one native drawing undo transaction. */
export function arrangeObjects(elements, ids, operation) {
  validateDrawingElements(elements);
  const selected = selectedObjects(elements, ids);
  const align = ['left', 'center', 'right', 'top', 'middle', 'bottom'];
  const distribute = ['horizontal', 'vertical'];
  if (![...align, ...distribute].includes(operation)) throw Error('Unknown arrangement.');
  if (selected.length < (distribute.includes(operation) ? 3 : 2)) throw Error(distribute.includes(operation) ? 'Select at least three objects.' : 'Select at least two objects.');
  const boxes = selected.map(element => ({ element, ...bounds(element) }));
  const left = Math.min(...boxes.map(box => box.left));
  const right = Math.max(...boxes.map(box => box.left + box.width));
  const top = Math.min(...boxes.map(box => box.top));
  const bottom = Math.max(...boxes.map(box => box.top + box.height));
  if (distribute.includes(operation)) {
    const horizontal = operation === 'horizontal';
    const key = horizontal ? 'left' : 'top', size = horizontal ? 'width' : 'height', coordinate = horizontal ? 'x' : 'y';
    boxes.sort((a, b) => a[key] - b[key]);
    const start = boxes[0][key], last = boxes.at(-1);
    const gap = (last[key] + last[size] - start - boxes.reduce((sum, box) => sum + box[size], 0)) / (boxes.length - 1);
    if (gap < 0) throw Error('Spread the outer objects apart before distributing without overlap.');
    let cursor = start;
    return boxes.map(box => { const patch = { id: box.element.id, [coordinate]: box.element[coordinate] + cursor - box[key] }; cursor += box[size] + gap; return patch; });
  }
  return boxes.map(box => {
    let x = box.element.x, y = box.element.y;
    if (operation === 'left') x += left - box.left;
    if (operation === 'center') x += (left + right - box.width) / 2 - box.left;
    if (operation === 'right') x += right - box.width - box.left;
    if (operation === 'top') y += top - box.top;
    if (operation === 'middle') y += (top + bottom - box.height) / 2 - box.top;
    if (operation === 'bottom') y += bottom - box.height - box.top;
    return { id: box.element.id, x, y };
  });
}

export function layerPatch(element, action) {
  if (!element || element.isDeleted) throw Error('Object is unavailable.');
  if (element.type === 'frame' || element.groupIds?.length || element.containerId || element.frameId || element.boundElements?.length) throw Error('Ungroup this object and detach its bindings first.');
  if (action === 'lock') { if (element.customData?.nobleHidden) throw Error('Show this object before changing its lock.'); return { locked: !element.locked }; }
  if (action !== 'visibility') throw Error('Unknown object action.');
  const hidden = element.customData?.nobleHidden;
  if (hidden) return { opacity: hidden.opacity, locked: hidden.locked, customData: { ...element.customData, nobleHidden: null } };
  return { opacity: 0, locked: true, customData: { ...element.customData, nobleHidden: { opacity: element.opacity ?? 100, locked: !!element.locked } } };
}

export function reorderObject(elements, id, direction) {
  if (!['front', 'back', 'up', 'down'].includes(direction)) throw Error('Unknown object order.');
  const index = elements.findIndex(element => element.id === id && !element.isDeleted);
  if (index < 0) throw Error('Object is unavailable.');
  selectedObjects(elements, { [id]: true });
  const result = [...elements], [element] = result.splice(index, 1);
  const next = direction === 'front' ? result.length : direction === 'back' ? 0 : Math.max(0, Math.min(result.length, index + (direction === 'up' ? 1 : -1)));
  result.splice(next, 0, element);
  return result;
}

function copyPlan(elements, ids, count) {
  validateDrawingElements(elements);
  const selected = selectedObjects(elements, ids);
  if (!selected.length) throw Error('Select one or more unlocked objects first.');
  if (elements.filter(element => !element.isDeleted).length + selected.length * count > 500) throw Error('Repeated composition would exceed 500 objects. Use fewer copies.');
  const boxes = selected.map(bounds);
  const left = Math.min(...boxes.map(box => box.left)), top = Math.min(...boxes.map(box => box.top));
  const right = Math.max(...boxes.map(box => box.left + box.width)), bottom = Math.max(...boxes.map(box => box.top + box.height));
  return { selected, width: right - left, height: bottom - top };
}
function copyAt(elements, selected, offsets, makeId) {
  const used = new Set(elements.map(element => element.id));
  const copies = [];
  for (const [x, y] of offsets) for (const source of selected) {
    const id = makeId();
    if (typeof id !== 'string' || !id || used.has(id)) throw Error('Could not allocate independent object identifiers. Try again.');
    used.add(id);
    copies.push({ ...source, id, x: source.x + x, y: source.y + y, index: null, version: 1, versionNonce: 0, updated: Date.now() });
  }
  const next = [...elements, ...copies];
  validateDrawingElements(next);
  return next;
}
/** Every generated copy remains an ordinary independently editable drawing object. */
export function repeatGrid(elements, ids, columns, rows, gap, makeId) {
  if (!Number.isInteger(columns) || !Number.isInteger(rows) || columns < 1 || rows < 1 || columns > 12 || rows > 12 || columns * rows < 2 || !Number.isFinite(gap) || gap < 0 || gap > 10000) throw Error('Use 1–12 rows and columns, at least two placements, and a gap of 0–10,000.');
  const { selected, width, height } = copyPlan(elements, ids, columns * rows - 1);
  const offsets = [];
  for (let row = 0; row < rows; row++) for (let column = 0; column < columns; column++) if (row || column) offsets.push([column * (width + gap), row * (height + gap)]);
  return copyAt(elements, selected, offsets, makeId);
}
export function repeatRing(elements, ids, count, radius, makeId) {
  if (!Number.isInteger(count) || count < 2 || count > 24 || !Number.isFinite(radius) || radius < 1 || radius > 10000) throw Error('Use 2–24 ring copies and a radius of 1–10,000.');
  const { selected } = copyPlan(elements, ids, count);
  const offsets = Array.from({ length: count }, (_, index) => [Math.cos(index * Math.PI * 2 / count) * radius, Math.sin(index * Math.PI * 2 / count) * radius]);
  return copyAt(elements, selected, offsets, makeId);
}

function componentBounds(elements, measure) {
  const visible = elements.filter(element => element.opacity !== 0 && !element.customData?.nobleHidden);
  elements = visible.length ? visible : elements;
  let result;
  if (measure) result = measure(elements);
  else {
    const boxes = elements.map(bounds);
    result = [Math.min(...boxes.map(box => box.left)), Math.min(...boxes.map(box => box.top)), Math.max(...boxes.map(box => box.left + box.width)), Math.max(...boxes.map(box => box.top + box.height))];
  }
  if (!Array.isArray(result) || result.length !== 4 || result.some(value => !Number.isFinite(value)) || result[2] < result[0] || result[3] < result[1]) throw Error('Component placement is unavailable.');
  return result;
}
export function captureComponent(elements, ids, measure) {
  const active = validateDrawingElements(elements), byId = new Map(active.map(element => [element.id, element]));
  const selected = new Set(active.filter(element => Object.prototype.hasOwnProperty.call(ids || {}, element.id) && ids[element.id]).map(element => element.id));
  if (!selected.size) throw Error('Select artwork to save as a reusable component.');
  const children = new Map();
  for (const element of active) if (element.frameId != null) { const items = children.get(element.frameId) || []; items.push(element.id); children.set(element.frameId, items); }
  const queue = [...selected], add = id => { if (!byId.has(id)) throw Error('Selected artwork has a missing attached object.'); if (!selected.has(id)) { selected.add(id); queue.push(id); } };
  for (let index = 0; index < queue.length; index++) {
    const element = byId.get(queue[index]);
    if (element.type === 'text' && element.containerId != null) add(element.containerId);
    if (element.boundElements != null && (!Array.isArray(element.boundElements) || element.boundElements.some(item => !item || typeof item.id !== 'string'))) throw Error('Selected object bindings are invalid.');
    for (const bound of element.boundElements || []) if (bound.type === 'text') add(bound.id);
    if (element.type === 'frame') for (const id of children.get(element.id) || []) add(id);
  }
  const copies = active.filter(element => selected.has(element.id)).map(element => {
    const copy = JSON.parse(JSON.stringify(element));
    if (copy.frameId != null && !selected.has(copy.frameId)) copy.frameId = null;
    if (copy.boundElements) copy.boundElements = copy.boundElements.filter(item => selected.has(item.id));
    for (const field of ['startBinding', 'endBinding']) if (copy[field] != null) {
      if (typeof copy[field] !== 'object' || typeof copy[field].elementId !== 'string') throw Error('Selected arrow bindings are invalid.');
      if (!selected.has(copy[field].elementId)) copy[field] = null;
    }
    return copy;
  });
  validateProjectGraph(copies);
  const [left, top] = componentBounds(copies, measure);
  const normalized = copies.map(element => ({ ...element, x: element.x - left, y: element.y - top }));
  validateDrawingElements(normalized);
  return normalized;
}
export function insertComponent(elements, component, centerX, centerY, makeId, measure) {
  const active = validateProjectGraph(component);
  if (!active.length || !Number.isFinite(centerX) || !Number.isFinite(centerY)) throw Error('Component placement is unavailable.');
  const [left, top, right, bottom] = componentBounds(active, measure);
  return mergeProjectElements(elements, active, centerX - (left + right) / 2, centerY - (top + bottom) / 2, makeId).elements;
}

export function normalizedColor(value) {
  if (value === 'transparent') return value;
  if (typeof value !== 'string' || !/^#(?:[\da-f]{3}|[\da-f]{4}|[\da-f]{6}|[\da-f]{8})$/i.test(value)) throw Error('Use a hexadecimal color or transparent.');
  const hex = value.slice(1).toLowerCase();
  return '#' + (hex.length <= 4 ? [...hex].map(character => character + character).join('') : hex);
}
export function compositionColors(elements) {
  const colors = new Map();
  for (const element of elements) if (!element.isDeleted) for (const field of ['strokeColor', 'backgroundColor']) if (element[field]) {
    const color = normalizedColor(element[field]);
    colors.set(color, (colors.get(color) || 0) + 1);
  }
  return [...colors].sort(([a], [b]) => a.localeCompare(b)).map(([color, count]) => ({ color, count }));
}
export function replaceCompositionColor(elements, source, target, includeLocked = false) {
  validateDrawingElements(elements);
  source = normalizedColor(source); target = normalizedColor(target);
  if (source === target) throw Error('Choose a different replacement color.');
  const patches = [];
  for (const element of elements) {
    if (element.isDeleted || (!includeLocked && element.locked)) continue;
    const patch = { id: element.id };
    for (const field of ['strokeColor', 'backgroundColor']) if (element[field] && normalizedColor(element[field]) === source) patch[field] = target;
    if (Object.keys(patch).length > 1) patches.push(patch);
  }
  if (!patches.length) throw Error('No unlocked objects use that color.');
  return patches;
}

/** Graduated editable intermediates between two endpoint shapes, with the originals untouched. */
export function blendShapes(elements, ids, steps, makeId) {
  validateDrawingElements(elements);
  if (!Number.isInteger(steps) || steps < 1 || steps > 20) throw Error('Use 1–20 intermediate shapes.');
  const selected = selectedObjects(elements, ids);
  if (selected.length !== 2 || !['rectangle', 'ellipse', 'diamond'].includes(selected[0].type) || selected[0].type !== selected[1].type) throw Error('Select exactly two unlocked shapes of the same type: rectangle, ellipse or diamond.');
  if (elements.filter(element => !element.isDeleted).length + steps > 500) throw Error('This blend would exceed 500 objects.');
  const [first, last] = selected;
  if (['fillStyle', 'strokeStyle', 'roundness'].some(field => JSON.stringify(first[field]) !== JSON.stringify(last[field]))) throw Error('Use matching fill, stroke and corner styles for the endpoint shapes.');
  function color(a, b, t) {
    a = normalizedColor(a); b = normalizedColor(b);
    if (a === b) return a;
    if (a === 'transparent' || b === 'transparent' || a.length !== b.length) throw Error('Use matching opaque/transparent color formats at both endpoints.');
    return '#' + a.slice(1).match(/../g).map((channel, index) => Math.round(parseInt(channel, 16) * (1 - t) + parseInt(b.slice(1 + index * 2, 3 + index * 2), 16) * t).toString(16).padStart(2, '0')).join('');
  }
  const used = new Set(elements.map(element => element.id));
  const result = [...elements];
  const mix = (a, b, t) => a + (b - a) * t;
  for (let step = 1; step <= steps; step++) {
    const t = step / (steps + 1), id = makeId();
    if (typeof id !== 'string' || !id || used.has(id)) throw Error('Could not allocate independent shape identifiers.');
    used.add(id);
    const delta = (((last.angle || 0) - (first.angle || 0) + Math.PI * 3) % (Math.PI * 2)) - Math.PI;
    const angle = Math.atan2(Math.sin((first.angle || 0) + delta * t), Math.cos((first.angle || 0) + delta * t));
    result.push({ ...first, id, x: mix(first.x, last.x, t), y: mix(first.y, last.y, t), width: mix(first.width, last.width, t), height: mix(first.height, last.height, t), angle,
      opacity: mix(first.opacity ?? 100, last.opacity ?? 100, t), strokeWidth: mix(first.strokeWidth ?? 1, last.strokeWidth ?? 1, t),
      strokeColor: color(first.strokeColor || '#111111', last.strokeColor || '#111111', t), backgroundColor: color(first.backgroundColor || 'transparent', last.backgroundColor || 'transparent', t),
      index: null, version: 1, versionNonce: 0, updated: Date.now(), customData: undefined });
  }
  validateDrawingElements(result); return result;
}
