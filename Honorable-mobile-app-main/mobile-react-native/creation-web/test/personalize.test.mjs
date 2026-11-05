import test from 'node:test';
import assert from 'node:assert/strict';
import { parsePersonalization, personalizationFields, personalizedCopies } from '../personalize.mjs';
import { validateProjectGraph } from '../merge-project.mjs';
import { encodeDraft, decodeDraft } from '../scene.mjs';

const rect = (id = 'card', extra = {}) => ({ id, type: 'rectangle', x: 0, y: 0, width: 280, height: 120, angle: 0, opacity: 100, strokeColor: '#111111', backgroundColor: '#ffffff', groupIds: ['template-group'], boundElements: [{ id: 'name', type: 'text' }], ...extra });
const label = (extra = {}) => ({ id: 'name', type: 'text', text: 'Hi {{name}}', originalText: 'Hi {{name}}', x: 10, y: 20, width: 260, height: 50, fontSize: 20, fontFamily: 2, lineHeight: 1.25, angle: 0, opacity: 100, strokeColor: '#111111', groupIds: ['template-group'], containerId: 'card', ...extra });
const template = () => [rect(), label()];
const measure = elements => [Math.min(...elements.map(e => e.x)), Math.min(...elements.map(e => e.y)), Math.max(...elements.map(e => e.x + e.width)), Math.max(...elements.map(e => e.y + e.height))];
const args = (extra = {}) => { let number = 0; return { elements: template(), template: template(), input: 'name\nAva\nNoah', columns: 2, measure, fit: async elements => elements, makeId: () => `new-${++number}`, ...extra }; };

test('bounded pipe and pasted tab rows keep Unicode, empty cells and literal formula-like values', () => {
  assert.deepEqual(parsePersonalization('\r\nname\tmessage\r\nZoë 日本語\t=1+2\r\n🙂\t\r\n'), { headers: ['name', 'message'], rows: [['Zoë 日本語', '=1+2'], ['🙂', '']] });
  assert.deepEqual(parsePersonalization('name | role\n Ava | Artist'), { headers: ['name', 'role'], rows: [['Ava', 'Artist']] });
});

test('reject malformed duplicate excessive ragged and control-bearing input before layout', () => {
  for (const input of ['', 'name', 'name|name\na|b', '1name\na', 'name|role\na', 'name\n', 'name\n\nAva', 'name\n' + '🙂'.repeat(65), 'name\nA\0B', 'name\nA\u2028B', 'name\nA\u0085B', 'name\n\ud800', 'x'.repeat(8193), Array(9).fill('field').join('|') + '\na', 'name\n' + Array(25).fill('A').join('\n')]) assert.throws(() => parsePersonalization(input), Error, input.slice(0, 20));
});

test('find unique repeated fields in original unwrapped text and reject incomplete syntax', () => {
  assert.deepEqual(personalizationFields([rect(), label({ text: 'Hi {{na\nme}}', originalText: '{{ name }} / {{role}} / {{name}}' })]), ['name', 'role']);
  for (const text of ['Hello', '{{name', '{{bad-name}}', 'name}}']) assert.throws(() => personalizationFields([rect(), label({ text, originalText: text })]));
});

test('protected field text and hidden template elements refuse without changing source', () => {
  for (const source of [[rect(), label({ locked: true })], [rect('card', { locked: true }), label()], [rect('card', { opacity: 0 }), label()], [rect(), label({ fontSize: 7 })]]) {
    const old = JSON.stringify(source); assert.throws(() => personalizationFields(source)); assert.equal(JSON.stringify(source), old);
  }
});

test('make different per-row copies with independent objects groups and internal text binding', async () => {
  const input = args(), old = JSON.stringify(input.elements), result = await personalizedCopies(input);
  assert.equal(result.copyCount, 2); assert.equal(result.insertedIds.length, 4); assert.equal(new Set(result.groups).size, 2);
  assert.equal(JSON.stringify(input.elements), old); assert.deepEqual(result.elements.slice(0, 2), input.elements);
  const copies = result.elements.slice(2); assert.deepEqual(copies.filter(e => e.type === 'text').map(e => e.originalText), ['Hi Ava', 'Hi Noah']);
  for (const [index, group] of result.groups.entries()) {
    const pair = copies.filter(e => e.groupIds.includes(group)); assert.equal(pair.length, 2);
    const shape = pair.find(e => e.type === 'rectangle'), text = pair.find(e => e.type === 'text');
    assert.equal(text.containerId, shape.id); assert.equal(shape.boundElements[0].id, text.id);
    assert(!pair.some(e => e.groupIds.includes('template-group'))); assert.equal(shape.y, 0); if (index) assert(shape.x > copies[0].x + copies[0].width);
  }
  validateProjectGraph(result.elements);
});

test('replacement is literal once-only text including dollar braces HTML-looking input and prototype names', async () => {
  const source = [rect(), label({ text: '{{__proto__}} / {{constructor}}', originalText: '{{__proto__}} / {{constructor}}' })];
  const result = await personalizedCopies(args({ elements: source, template: source, input: '__proto__|constructor\n$&{{name}}|<img src=x>' }));
  assert.equal(result.elements.at(-1).originalText, '$&{{name}} / <img src=x>'); assert.equal({}.polluted, undefined);
});

test('missing data fields refuse the whole batch before fitting or identifier allocation', async () => {
  let calls = 0; await assert.rejects(personalizedCopies(args({ input: 'other\nAva', fit: () => { calls++; }, makeId: () => { calls++; } })), /name column/); assert.equal(calls, 0);
});

test('whole object budget and unsupported columns refuse before fitting', async () => {
  let called = false; const current = Array.from({ length: 499 }, (_, i) => rect(`old-${i}`, { groupIds: [], boundElements: null }));
  await assert.rejects(personalizedCopies(args({ elements: current, fit: () => { called = true; } })), /500 objects/); assert.equal(called, false);
  for (const columns of [0, 5, 1.5, NaN, '2']) await assert.rejects(personalizedCopies(args({ columns })), /1–4/);
});

test('24 data rows stay ordered and form separated rows using the maximum fitted cell size', async () => {
  const result = await personalizedCopies(args({ input: 'name\n' + Array.from({ length: 24 }, (_, i) => 'Person ' + i).join('\n'), columns: 3 }));
  assert.equal(result.copyCount, 24); assert.equal(result.elements.length, 50);
  const cards = result.elements.slice(2).filter(e => e.type === 'rectangle');
  for (let i = 0; i < cards.length; i++) { assert.equal(cards[i].x, cards[0].x + i % 3 * 312); assert.equal(cards[i].y, Math.floor(i / 3) * 152); }
});

test('embedded images frames and arrow bindings retain their internal graphs with fresh identifiers', async () => {
  const source = template(); source[0].frameId = 'frame'; source[1].frameId = 'frame';
  source.push({ ...rect('frame', { type: 'frame', width: 300, height: 180, boundElements: null }) }, { id: 'photo', type: 'image', x: 10, y: 80, width: 40, height: 20, angle: 0, opacity: 100, fileId: 'a'.repeat(64), scale: [-1, 1], groupIds: [], frameId: 'frame' }, { id: 'arrow', type: 'arrow', x: 300, y: 0, width: 30, height: 30, angle: 0, opacity: 100, points: [[0, 0], [30, 30]], groupIds: [], startBinding: { elementId: 'card', focus: 0, gap: 1 }, endBinding: null });
  source[0].boundElements.push({ id: 'arrow', type: 'arrow' });
  const old = JSON.stringify(source), result = await personalizedCopies(args({ elements: source, template: source }));
  assert.equal(JSON.stringify(source), old); validateProjectGraph(result.elements);
  const photos = result.elements.filter(e => e.type === 'image'); assert.equal(photos.length, 3); assert(photos.every(e => e.fileId === 'a'.repeat(64) && e.scale[0] === -1));
  assert(result.elements.slice(source.length).every(e => !source.some(old => old.id === e.id)));
});

test('failed fitting dropped text changed image or binding refuse atomically', async () => {
  const original = args(); const old = JSON.stringify(original.elements);
  for (const fit of [async () => { throw Error('Will not fit'); }, async elements => elements.slice(1), async elements => elements.map(e => e.type === 'text' ? { ...e, originalText: 'Lost value' } : e), async elements => elements.map(e => e.type === 'text' ? { ...e, containerId: null } : e)]) {
    await assert.rejects(personalizedCopies({ ...original, fit })); assert.equal(JSON.stringify(original.elements), old);
  }
});

test('stale guards stop between row layout and graph creation without source changes', async () => {
  for (const stop of [1, 2, 3, 4, 5, 6, 7]) { const options = args(), old = JSON.stringify(options.elements); let calls = 0;
    await assert.rejects(personalizedCopies({ ...options, requireCurrent: () => { if (++calls === stop) throw Error('Stale source'); } }), /Stale source/);
    assert.equal(calls, stop); assert.equal(JSON.stringify(options.elements), old);
  }
});

test('identifier collisions reject all copies and normal output survives the document codec', async () => {
  const options = args(); await assert.rejects(personalizedCopies({ ...options, makeId: () => 'card' }), /independent/);
  const result = await personalizedCopies(args()); const draft = decodeDraft(encodeDraft(result.elements));
  assert.deepEqual(draft.elements, result.elements); validateProjectGraph(draft.elements);
});


test('unused data columns stay out of artwork and persisted project content', async () => {
  const result = await personalizedCopies(args({ input: 'name|private_note\nAva|PRIVATE_UNUSED_FIXTURE' }));
  assert.equal(result.rows[0][1], 'PRIVATE_UNUSED_FIXTURE');
  assert(!JSON.stringify(result.elements).includes('PRIVATE_UNUSED_FIXTURE'));
  assert(!encodeDraft(result.elements).includes('PRIVATE_UNUSED_FIXTURE'));
});
