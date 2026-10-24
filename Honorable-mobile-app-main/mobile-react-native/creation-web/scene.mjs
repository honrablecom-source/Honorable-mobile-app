export const MAX_ELEMENTS = 500, MAX_DRAFT_BYTES = 1024 * 1024;
export function encodeDraft(elements, state = {}) {
  const active = elements.filter(element => !element.isDeleted);
  if (active.length > MAX_ELEMENTS) throw Error('Use up to 500 drawing objects.');
  for (const element of active) {
    if (!['rectangle', 'diamond', 'ellipse', 'line', 'arrow', 'freedraw', 'text', 'frame'].includes(element.type)) throw Error('This local workspace supports drawing, shapes and text.');
    for (const field of ['x', 'y', 'width', 'height']) if (!Number.isFinite(element[field]) || Math.abs(element[field]) > 1e6) throw Error('Drawing coordinates exceed the supported bounds.');
    if (element.text && (typeof element.text !== 'string' || element.text.length > 2000)) throw Error('Use shorter text.');
  }
  const appState = { viewBackgroundColor: '#ffffff', currentItemStrokeColor: state.currentItemStrokeColor || '#111111', currentItemFontFamily: state.currentItemFontFamily || 2 };
  const value = JSON.stringify({ version: 1, elements: active, appState });
  if (new TextEncoder().encode(value).length > MAX_DRAFT_BYTES) throw Error('Drawing draft is too large.');
  return value;
}
export function decodeDraft(value) {
  if (!value) return null;
  if (new TextEncoder().encode(value).length > MAX_DRAFT_BYTES) throw Error('Drawing draft is too large.');
  const saved = JSON.parse(value);
  if (saved.version !== 1 || !Array.isArray(saved.elements)) throw Error('Unsupported drawing draft.');
  return JSON.parse(encodeDraft(saved.elements, saved.appState));
}
