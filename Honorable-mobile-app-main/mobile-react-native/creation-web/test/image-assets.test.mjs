import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { imageInfo, photoFile, photoBytes, filesForElements, mergePhotoFiles, MAX_PHOTOS, MAX_PHOTO_BYTES } from '../image-assets.mjs';
import { encodeDraft, decodeDraft, validateDrawingElements } from '../scene.mjs';
import { saveProject, writeProjects, readProjects, exportProject, importProject, duplicateProject } from '../projects.mjs';
import { captureComponent, insertComponent, repeatGrid } from '../composition.mjs';
import { artworkPalette } from '../palette.mjs';

const png = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLbtAAAAABJRU5ErkJggg==';
const file = photoFile(png, 0);
const files = { [file.id]: file };
const image = { id: 'photo-object', type: 'image', x: 0, y: 0, width: 80, height: 80, fileId: file.id, scale: [1, 1], crop: null, status: 'saved', opacity: 100 };
const box = { id: 'box', type: 'rectangle', x: 100, y: 0, width: 30, height: 20, strokeColor: '#111111' };

test('photo content ID matches independent SHA-256 and drops source metadata', () => {
  assert.equal(file.id, createHash('sha256').update(Buffer.from(png.split(',')[1], 'base64')).digest('hex'));
  assert.deepEqual(photoBytes(png).width, 1);
  const sanitized = filesForElements([image], { [file.id]: { ...file, sourceUri: 'content://private', name: 'private-name', account: 'excluded' } });
  assert.deepEqual(sanitized, files);
  assert(!JSON.stringify(sanitized).includes('private'));
});

test('vector documents stay version1; photo documents round-trip version2 with only used copies', () => {
  assert.equal(decodeDraft(encodeDraft([box], {}, files)).version, 1);
  const saved = decodeDraft(encodeDraft([image, box], { collaborators: 'excluded' }, files));
  assert.equal(saved.version, 2); assert.deepEqual(saved.files, files); assert.deepEqual(saved.elements, [image, box]);
  assert.equal(saved.appState.collaborators, undefined);
  assert.equal(decodeDraft(encodeDraft([{ ...image, isDeleted: true }, box], {}, files)).files, undefined);
  assert.deepEqual(decodeDraft(encodeDraft([{ ...image, opacity: 0, locked: true }], {}, files)).files, files);
});

test('missing, corrupt, remote, wrong-type and future photo data fail closed', () => {
  assert.throws(() => encodeDraft([image]));
  assert.throws(() => decodeDraft(JSON.stringify({ version: 1, elements: [image], files })));
  assert.throws(() => decodeDraft(JSON.stringify({ version: 3, elements: [image], files })));
  for (const dataURL of ['https://example.com/photo.png', 'file:///photo.png', 'data:image/svg+xml;base64,PHN2Zy8+', png.replace('image/png', 'image/jpeg'), png + ' ', png.replace('iVBOR', 'aVBOR')]) assert.throws(() => photoFile(dataURL));
  assert.throws(() => filesForElements([image], { [file.id]: { ...file, id: 'a'.repeat(64) } }));
  assert.throws(() => filesForElements([image], { [file.id]: { ...file, created: Infinity } }));
  const damaged = Buffer.from(png.split(',')[1], 'base64'); damaged[50] ^= 1;
  assert.throws(() => filesForElements([image], { [file.id]: { ...file, dataURL: 'data:image/png;base64,' + damaged.toString('base64') } }));
});

test('byte, pixel, animation and reference limits apply before renderer use', () => {
  assert.throws(() => photoFile('data:image/png;base64,' + 'A'.repeat(Math.ceil(MAX_PHOTO_BYTES / 3) * 4 + 4)));
  const huge = Buffer.from(png.split(',')[1], 'base64'); huge.writeUInt32BE(1025, 16);
  assert.throws(() => imageInfo(huge, true), /dimensions/);
  const animated = Buffer.from(png.split(',')[1], 'base64'); animated.write('acTL', 37, 'ascii');
  assert.throws(() => imageInfo(animated, true), /still/);
  assert.throws(() => mergePhotoFiles(Object.fromEntries(Array.from({ length: MAX_PHOTOS + 1 }, (_, i) => [String(i), file]))));
  assert.throws(() => filesForElements([image], Object.create(files)), /missing/);
});

test('photo placements reject invalid flips, geometry and crops', () => {
  for (const scale of [null, [1], [0, 1], [1, 2], [1, 1, 1]]) assert.throws(() => validateDrawingElements([{ ...image, scale }]));
  for (const crop of [{}, { x: 0, y: 0, width: 2, height: 1, naturalWidth: 1, naturalHeight: 1 }, { x: -1, y: 0, width: 1, height: 1, naturalWidth: 1, naturalHeight: 1 }]) assert.throws(() => encodeDraft([{ ...image, crop }], {}, files));
  const crop = { x: 0, y: 0, width: 1, height: 1, naturalWidth: 1, naturalHeight: 1 };
  assert.deepEqual(decodeDraft(encodeDraft([{ ...image, crop, scale: [-1, 1] }], {}, files)).elements[0].crop, crop);
  assert.throws(() => encodeDraft([{ ...image, crop: { ...crop, naturalWidth: 2 } }], {}, files), /crop/);
});

test('named shelves, independent copies and portable projects retain image copies', () => {
  const shelf = saveProject([], 'Photo layout', [image, box], {}, files);
  const loaded = readProjects(writeProjects(duplicateProject(shelf, 'Photo layout', 'Copy')));
  assert.equal(loaded.length, 2);
  assert.deepEqual(decodeDraft(loaded[1].scene).files, files);
  const portable = exportProject('Photo layout', [image, box], {}, files);
  assert.deepEqual(importProject(portable).files, files);
  assert(!portable.includes('content://'));
});

test('components and repeats retain photo references without changing source objects or bytes', () => {
  const before = JSON.stringify([image, box]), beforeFiles = JSON.stringify(files);
  const component = captureComponent([image, box], { [image.id]: true });
  const shelf = saveProject([], 'Photo component', component, {}, files);
  assert.deepEqual(decodeDraft(shelf[0].scene).files, files);
  let next = 0;
  const inserted = insertComponent([box], component, 200, 200, () => `insert-${next++}`);
  assert.equal(decodeDraft(encodeDraft(inserted, {}, files)).elements[1].fileId, file.id);
  const repeated = repeatGrid([image], { [image.id]: true }, 2, 1, 10, () => `copy-${next++}`);
  assert.equal(repeated[1].fileId, file.id); assert.notEqual(repeated[1].id, image.id);
  assert.equal(JSON.stringify([image, box]), before); assert.equal(JSON.stringify(files), beforeFiles);
  assert.deepEqual(mergePhotoFiles(files, { [file.id]: { ...file, created: 100 } }), files);
  assert.deepEqual(artworkPalette([image, box]), [{ color: '#111111', count: 1 }]);
});
