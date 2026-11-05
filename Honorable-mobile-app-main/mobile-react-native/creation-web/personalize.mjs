import { validateDrawingElements } from './scene.mjs';
import { validateProjectGraph, mergeProjectElements } from './merge-project.mjs';

const FIELD = /\{\{\s*([A-Za-z_][A-Za-z0-9_]{0,31})\s*\}\}/g;
const original = element => typeof element.originalText === 'string' ? element.originalText : element.text;
export function parsePersonalization(input) {
  if (typeof input !== 'string' || input.length > 8192) throw Error('Use up to 8,192 characters of local row data.');
  const lines = input.replace(/\r\n/g, '\n').split('\n');
  while (lines.length && !lines[0].trim()) lines.shift();
  while (lines.length && !lines[lines.length - 1].trim()) lines.pop();
  if (lines.length < 2 || lines.length > 25) throw Error('Use one header row and 1–24 rows of values.');
  const separator = lines[0].includes('\t') ? '\t' : '|';
  const split = line => line.split(separator).map(value => {
    if (/[\x00-\x1f\x7f-\x9f\u2028\u2029]/.test(value) || Array.from(value).some(character => { const point = character.codePointAt(0); return point >= 0xd800 && point <= 0xdfff; })) throw Error('Use plain single-line values without control characters.');
    return value.trim();
  });
  const headers = split(lines[0]);
  if (headers.length < 1 || headers.length > 8 || headers.some(value => !/^[A-Za-z_][A-Za-z0-9_]{0,31}$/.test(value)) || new Set(headers).size !== headers.length) {
    throw Error('Use 1–8 unique field names, starting with a letter or underscore, then letters, digits or underscores.');
  }
  const rows = lines.slice(1).map(split);
  if (rows.some(row => row.length !== headers.length || row.every(value => !value) || row.some(value => Array.from(value).length > 64))) throw Error('Use matching columns and up to 64 characters per value. Empty rows are not supported.');
  return { headers, rows };
}

export function personalizationFields(template) {
  const active = validateProjectGraph(template);
  if (!active.length || active.length > 100) throw Error('Select a design with 1–100 objects.');
  if (active.some(element => element.opacity === 0 || element.customData?.nobleHidden)) throw Error('Show the entire selected design before making personalized copies.');
  const byId = new Map(active.map(element => [element.id, element])), fields = new Set();
  for (const element of active) if (element.type === 'text') {
    const text = original(element);
    if (typeof text !== 'string') throw Error('The selected design has invalid text.');
    const matches = [...text.matchAll(FIELD)];
    if (text.replace(FIELD, '').includes('{{') || text.replace(FIELD, '').includes('}}')) throw Error('Use complete fields such as {{name}} in the selected text.');
    if (!matches.length) continue;
    if (element.locked || byId.get(element.containerId)?.locked) throw Error('Unlock the design’s field text and its container before personalizing.');
    if (!Number.isFinite(element.fontSize) || element.fontSize < 8 || element.fontSize > 256) throw Error('Use field text from 8 to 256 pixels in the source design.');
    matches.forEach(match => fields.add(match[1]));
  }
  if (!fields.size) throw Error('Select a design containing text fields such as Hi {{name}}. Add fields by editing its text first.');
  return [...fields];
}

const references = element => JSON.stringify({
  container: element.containerId ?? null, frame: element.frameId ?? null,
  bound: (element.boundElements || []).map(value => [value.id, value.type]).sort(),
  start: element.startBinding?.elementId ?? null, end: element.endBinding?.elementId ?? null,
});
function box(elements, measure) {
  const result = measure(elements);
  if (!Array.isArray(result) || result.length !== 4 || result.some(value => !Number.isFinite(value)) || result[2] <= result[0] || result[3] <= result[1]) throw Error('The design has no usable layout bounds.');
  return result;
}

/** Pure graph/layout coordinator. Renderer fitting is injected; nothing is published until all rows pass. */
export async function personalizedCopies({ elements, template, input, columns = 2, measure, fit, makeId, requireCurrent = () => {} }) {
  const existing = validateDrawingElements(elements), source = validateProjectGraph(template);
  if (!Number.isInteger(columns) || columns < 1 || columns > 4 || typeof measure !== 'function' || typeof fit !== 'function' || typeof makeId !== 'function') throw Error('Choose 1–4 copies per row and a valid local layout.');
  const fields = personalizationFields(source), data = parsePersonalization(input);
  for (const field of fields) if (!data.headers.includes(field)) throw Error(`Add the ${field} column to your header row.`);
  if (existing.length + source.length * data.rows.length > 500) throw Error('These copies would exceed 500 objects. Use fewer rows or a smaller design.');
  requireCurrent();
  const prepared = [];
  for (const row of data.rows) {
    requireCurrent();
    const changed = new Set();
    const copy = JSON.parse(JSON.stringify(source)).map(element => {
      if (element.type !== 'text') return element;
      const text = original(element), resolved = text.replace(FIELD, (_, field) => row[data.headers.indexOf(field)]);
      if (resolved.length > 2000) throw Error('A personalized text block is too long. Shorten its values.');
      if (resolved === text) return element;
      changed.add(element.id);
      return { ...element, text: resolved, originalText: resolved };
    });
    const fitted = await fit(copy, changed); requireCurrent();
    const active = validateProjectGraph(fitted), byId = new Map(active.map(element => [element.id, element]));
    if (active.length !== source.length || copy.some(element => !byId.has(element.id))) throw Error('The personalized layout lost artwork. Your original was kept.');
    for (const expected of copy) {
      const actual = byId.get(expected.id);
      if (actual.type !== expected.type || actual.fileId !== expected.fileId || references(actual) !== references(expected) ||
          (expected.type === 'text' && original(actual) !== original(expected))) throw Error('The personalized layout changed source content or connections. Your original was kept.');
    }
    prepared.push({ elements: active, bounds: box(active, measure) });
  }
  const width = Math.max(...prepared.map(value => value.bounds[2] - value.bounds[0]));
  const height = Math.max(...prepared.map(value => value.bounds[3] - value.bounds[1]));
  const visible = existing.filter(element => element.opacity !== 0 && !element.customData?.nobleHidden);
  const base = visible.length ? box(visible, measure) : [0, 0, 0, 0], gap = 32;
  const used = new Set([...elements, ...source].flatMap(element => [element.id, ...(element.groupIds || [])]));
  const fresh = () => {
    const id = makeId();
    if (typeof id !== 'string' || !id || id.length > 128 || used.has(id)) throw Error('Could not allocate independent copy identifiers. Try again.');
    used.add(id); return id;
  };
  let next = [...elements]; const groups = [], insertedIds = [];
  for (let index = 0; index < prepared.length; index++) {
    requireCurrent();
    const value = prepared[index], offsetX = base[2] + gap + (index % columns) * (width + gap) - value.bounds[0];
    const offsetY = base[1] + Math.floor(index / columns) * (height + gap) - value.bounds[1];
    const result = mergeProjectElements(next, value.elements, offsetX, offsetY, fresh), group = fresh();
    const inserted = new Set(result.insertedIds);
    next = result.elements.map(element => inserted.has(element.id) ? { ...element, groupIds: [...(element.groupIds || []), group] } : element);
    groups.push(group); insertedIds.push(...result.insertedIds);
  }
  requireCurrent(); validateDrawingElements(next);
  return { elements: next, insertedIds, groups, fields, headers: data.headers, rows: data.rows, copyCount: prepared.length };
}
