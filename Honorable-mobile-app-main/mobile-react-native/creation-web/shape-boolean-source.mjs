import { encodeDraft, validateDrawingElements } from './scene.mjs';
export const SHAPE_TYPES = new Set(['rectangle', 'ellipse', 'diamond']);
export const MAX_BOOLEAN_SHAPES = 8;
export const ELLIPSE_STEPS = 96;
const color = value => typeof value === 'string' && /^(?:#[\da-f]{3}|#[\da-f]{4}|#[\da-f]{6}|#[\da-f]{8}|transparent)$/i.test(value);
const finite = value => Number.isFinite(value) && Math.abs(value) <= 1e6;
export function validateBooleanInput(input) {
  if (!input || !Array.isArray(input.shapes) || input.shapes.length < 2 || input.shapes.length > MAX_BOOLEAN_SHAPES || !Number.isInteger(input.existingCount) || input.existingCount < input.shapes.length || input.existingCount > 500) throw Error('Select 2–8 shapes to combine.');
  const ids = new Set();
  const shapes = input.shapes.map(element => {
    if (!element || !SHAPE_TYPES.has(element.type) || typeof element.id !== 'string' || !element.id || element.id.length > 128 || ids.has(element.id)) throw Error('Select only rectangles, ellipses or diamonds.');
    ids.add(element.id);
    if (element.locked || element.isDeleted || element.customData?.nobleHidden || element.opacity === 0) throw Error('Show and unlock the selected shapes first.');
    if (element.frameId != null) throw Error('Move selected shapes out of frames first.');
    if (element.type === 'rectangle' && element.roundness != null) throw Error('Use sharp rectangle corners before combining shapes.');
    if (![element.x, element.y, element.width, element.height].every(finite) || element.width <= .01 || element.height <= .01 || !Number.isFinite(element.angle ?? 0) || Math.abs(element.angle ?? 0) > Math.PI * 2) throw Error('Enlarge very small shapes or move them closer to the canvas origin.');
    const strokeColor = element.strokeColor ?? '#111111', backgroundColor = element.backgroundColor ?? 'transparent', strokeWidth = element.strokeWidth ?? 1, opacity = element.opacity ?? 100, strokeStyle = element.strokeStyle ?? 'solid';
    if (!color(strokeColor) || !color(backgroundColor) || !Number.isFinite(strokeWidth) || strokeWidth < 0 || strokeWidth > 64 || !Number.isFinite(opacity) || opacity <= 0 || opacity > 100 || !['solid', 'dashed', 'dotted'].includes(strokeStyle)) throw Error('Use a supported fill, outline and opacity before combining.');
    return { id: element.id, type: element.type, x: element.x, y: element.y, width: element.width, height: element.height, angle: element.angle ?? 0, strokeColor, backgroundColor, strokeWidth, strokeStyle, opacity };
  });
  return { shapes, existingCount: input.existingCount };
}
export function booleanSelection(elements, selectedIds) {
  const active = validateDrawingElements(elements);
  if (!selectedIds || typeof selectedIds !== 'object' || Array.isArray(selectedIds) || Object.keys(selectedIds).length > 500 || Object.values(selectedIds).some(value => typeof value !== 'boolean')) throw Error('Select shapes on the canvas first.');
  const selected = Object.keys(selectedIds).filter(id => selectedIds[id]);
  const rows = active.filter(element => selected.includes(element.id));
  if (rows.length !== selected.length) throw Error('The selection changed. Select the shapes again.');
  return validateBooleanInput({ shapes: rows, existingCount: active.length });
}
export function captureBooleanSource(editor) {
  if (!editor) throw Error('Wait for the drawing to open.');
  const elements = editor.getSceneElements(), files = editor.getFiles(), raw = encodeDraft(elements, {}, files);
  const input = booleanSelection(elements, editor.getAppState().selectedElementIds || {});
  return { input, current(candidate) { try { return candidate === editor && encodeDraft(candidate.getSceneElements(), {}, candidate.getFiles()) === raw; } catch { return false; } } };
}
export function shapeRing(shape) {
  let points;
  if (shape.type === 'ellipse') points = Array.from({ length: ELLIPSE_STEPS }, (_, index) => { const angle = index * Math.PI * 2 / ELLIPSE_STEPS; return [shape.width / 2 + Math.cos(angle) * shape.width / 2, shape.height / 2 + Math.sin(angle) * shape.height / 2]; });
  else if (shape.type === 'diamond') points = [[shape.width / 2, 0], [shape.width, shape.height / 2], [shape.width / 2, shape.height], [0, shape.height / 2]];
  else points = [[0, 0], [shape.width, 0], [shape.width, shape.height], [0, shape.height]];
  const cx = shape.width / 2, cy = shape.height / 2, c = Math.cos(shape.angle), s = Math.sin(shape.angle);
  const ring = points.map(([x, y]) => [shape.x + cx + (x - cx) * c - (y - cy) * s, shape.y + cy + (x - cx) * s + (y - cy) * c]);
  return [...ring, [...ring[0]]];
}
