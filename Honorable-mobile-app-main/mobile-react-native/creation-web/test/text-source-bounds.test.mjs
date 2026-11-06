import test from 'node:test';
import assert from 'node:assert/strict';
import { encodeDraft, decodeDraft } from '../scene.mjs';
import { importProject, readProjects, exportProject } from '../projects.mjs';
import { decodeProjectFile, recoveryFile, checkedRecoveryFile } from '../project-files.mjs';
import { validateProjectGraph } from '../merge-project.mjs';

const text = { id: 'title', type: 'text', x: 0, y: 0, width: 100, height: 25, text: 'Short title', originalText: 'Short title', fontFamily: 2 };
const raw = element => JSON.stringify({ version: 1, elements: [element] });
const portable = element => JSON.stringify({ format: 'noble-drawing', version: 1, name: 'Local design', scene: raw(element) });
const excessive = { ...text, originalText: 'a'.repeat(2001) };

test('editable source text cannot bypass the displayed text budget', () => {
  assert.throws(() => encodeDraft([excessive]), /text/i);
  assert.throws(() => decodeDraft(raw(excessive)), /text/i);
});
test('malformed editable source text is rejected without coercion', () => {
  for (const originalText of [null, 1, true, [], {}, ['word']]) {
    assert.throws(() => encodeDraft([{ ...text, originalText }]), /text/i);
    assert.throws(() => decodeDraft(raw({ ...text, originalText })), /text/i);
  }
});
test('both text fields retain the existing inclusive UTF16 limit and original whitespace', () => {
  for (const originalText of ['', 'a'.repeat(2000), '🎬'.repeat(1000), 'café\n  Keep spacing\t']) {
    const element = { ...text, originalText };
    assert.deepEqual(decodeDraft(encodeDraft([element])).elements, [element]);
  }
  assert.throws(() => encodeDraft([{ ...text, text: 'a'.repeat(2001) }]), /text/i);
  assert.throws(() => encodeDraft([{ ...text, originalText: '🎬'.repeat(1001) }]), /text/i);
});
test('older drafts without originalText remain compatible and are not rewritten', () => {
  const legacy = { ...text }; delete legacy.originalText;
  assert.deepEqual(decodeDraft(encodeDraft([legacy])).elements, [legacy]);
});
test('JSON and file import reject oversized source before graph/renderer use', () => {
  assert.throws(() => importProject(portable(excessive)), /text/i);
  assert.throws(() => decodeProjectFile(new TextEncoder().encode(portable(excessive))), /text/i);
  assert.throws(() => validateProjectGraph([excessive]), /text/i);
});
test('saved shelf rejects malformed source atomically while valid projects roundtrip', () => {
  const bad = JSON.stringify({ version: 1, projects: [{ name: 'Good', scene: raw(text) }, { name: 'Bad', scene: raw(excessive) }] });
  assert.throws(() => readProjects(bad), /text/i);
  assert.deepEqual(importProject(exportProject('Good', [text], {}, {})).elements, [text]);
});
test('recovery remains an opaque byte-preserving escape hatch for a rejected draft', () => {
  const draft = raw(excessive), before = JSON.stringify(excessive);
  assert.equal(checkedRecoveryFile(recoveryFile(draft)).draft, draft);
  assert.equal(JSON.stringify(excessive), before);
});
test('deleted text does not consume active text budget; unrelated artwork remains valid', () => {
  const shape = { id: 'shape', type: 'rectangle', x: 10, y: 10, width: 100, height: 50 };
  assert.deepEqual(decodeDraft(encodeDraft([{ ...excessive, isDeleted: true }, shape])).elements, [shape]);
});
