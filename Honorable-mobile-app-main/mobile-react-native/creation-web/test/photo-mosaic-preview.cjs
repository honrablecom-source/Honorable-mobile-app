const { chromium } = require('../../../ui-previews/node_modules/playwright');
const { withProjectView } = require('./project-navigation.cjs');
const assert = require('node:assert/strict'), fs = require('node:fs'), path = require('node:path');
const output = path.resolve(__dirname, '../../../../.context/photo-mosaic/browser');
const previewRoot = process.env.NOBLE_MOSAIC_PREVIEW_OUTPUT || path.resolve(output, '../preview');
const url = process.env.NOBLE_PREVIEW_URL || 'http://127.0.0.1:4191/drawing/', origin = new URL(url).origin;
fs.mkdirSync(output, { recursive: true });
require('esbuild').buildSync({ stdin: { contents: "import{mosaicPhoto,mosaicPixels}from './photo-mosaic.mjs';window.__mosaicPhoto=mosaicPhoto;window.__mosaicPixels=mosaicPixels;", resolveDir: path.resolve(__dirname, '..'), sourcefile: 'mosaic-service-test.mjs' }, bundle: true, format: 'iife', platform: 'browser', target: 'chrome90', outfile: path.join(previewRoot, 'mosaic-service-test.js') });

(async () => {
  const browser = await chromium.launch({ args: ['--no-sandbox'] }); let page;
  try {
    page = await browser.newPage({ viewport: { width: 390, height: 844 } });
    const sourceURL = await page.evaluate(() => {
      const canvas = document.createElement('canvas'); canvas.width = 96; canvas.height = 64;
      const c = canvas.getContext('2d'), data = c.createImageData(96, 64);
      for (let y = 0; y < 64; y++) for (let x = 0; x < 96; x++) data.data.set(x < 32 ? [220, 54, 28, 255] : x < 64 ? [32, 91, 210, 128] : [32, 160, 80, 0], (y * 96 + x) * 4);
      c.putImageData(data, 0, 0); return canvas.toDataURL('image/png');
    });
    const { photoFile } = await import('../image-assets.mjs'); const file = photoFile(sourceURL, 0);
    const source = { id: 'source', type: 'image', fileId: file.id, status: 'saved', scale: [-1, 1], x: 20, y: 20, width: 140, height: 160, angle: .2, opacity: 70, locked: false, crop: { x: 0, y: 0, width: 64, height: 64, naturalWidth: 96, naturalHeight: 64 }, groupIds: [], boundElements: null, frameId: null, version: 1, versionNonce: 1, seed: 1 };
    const protectedShape = { id: 'protected', type: 'rectangle', x: 500, y: 100, width: 80, height: 80, angle: 0, opacity: 50, strokeColor: '#111111', backgroundColor: '#fefefe', fillStyle: 'solid', locked: true, groupIds: [], boundElements: null, version: 1, versionNonce: 1, seed: 2 };
    const initial = { version: 2, elements: [source, protectedShape], files: { [file.id]: file }, appState: { viewBackgroundColor: '#ffffff', currentItemFontFamily: 2 } };
    const errors = [], external = [];
    page.on('pageerror', error => errors.push(error.message));
    page.on('console', message => { if (message.type() === 'error' && message.text().includes('Content Security Policy')) errors.push(message.text()); });
    await page.route('**/*', route => { const target = route.request().url(); if (!target.startsWith(origin) && !target.startsWith('data:') && !target.startsWith('blob:')) { external.push(target); return route.abort(); } return route.continue(); });
    await page.addInitScript(initial => {
      if (!localStorage.getItem('honorable-drawing-v1')) localStorage.setItem('honorable-drawing-v1', JSON.stringify(initial));
      window.HonorableDrawing = { usePng: value => { window.__png = value; window.honorableExportResult('Synthetic mosaic image ready'); } };
    }, initial);
    await page.goto(url);
    const save = async () => { await page.getByRole('button', { name: 'Save draft', exact: true }).click(); return JSON.parse(await page.evaluate(() => localStorage.getItem('honorable-drawing-v1'))); };
    const before = await save();
    const open = async () => { const objects = page.getByRole('button', { name: 'Objects', exact: true }); if (await objects.getAttribute('aria-expanded') !== 'true') await objects.click(); await page.getByRole('button', { name: 'Make mosaic from photo 1', exact: true }).click(); };
    const preview = async () => { await page.getByRole('button', { name: 'Preview photo mosaic', exact: true }).click(); await page.getByRole('img', { name: 'Editable photo mosaic preview', exact: true }).waitFor(); };
    await open(); await preview();
    const previewShapes = await page.getByRole('img', { name: 'Editable photo mosaic preview', exact: true }).locator('polygon').evaluateAll(nodes => nodes.map(node => ({ points: node.getAttribute('points'), fill: node.getAttribute('fill'), opacity: Number(node.getAttribute('opacity')) })));
    assert(previewShapes.length > 8 && previewShapes.length < 48);
    assert(previewShapes.some(shape => shape.opacity > 0 && shape.opacity < 1));
    assert(previewShapes.some(shape => shape.fill === '#dc361c'));
    assert(previewShapes.every(shape => shape.fill !== '#20a050'));
    assert.match(await page.getByText(/editable triangles\./).textContent(), /fully transparent facets omitted/);
    for (const width of [320, 390, 844]) { await page.setViewportSize({ width, height: 844 }); assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)); }
    await page.setViewportSize({ width: 390, height: 844 });
    await page.getByRole('img', { name: 'Editable photo mosaic preview', exact: true }).screenshot({ path: path.join(output, 'mosaic-preview.png') });
    await page.getByLabel('Triangle detail', { exact: true }).selectOption('3');
    assert.equal(await page.getByRole('button', { name: 'Insert editable mosaic', exact: true }).count(), 0);
    await preview();
    const coarse = await page.getByRole('img', { name: 'Editable photo mosaic preview', exact: true }).locator('polygon').count();
    assert(coarse < previewShapes.length);
    await page.getByRole('button', { name: 'Keep original only', exact: true }).click();
    assert.deepEqual((await save()).elements, before.elements);
    await open(); await preview();
    await page.getByRole('button', { name: 'Insert editable mosaic', exact: true }).click();
    const inserted = await save(), added = inserted.elements.filter(element => !before.elements.some(old => old.id === element.id));
    assert.equal(added.length, previewShapes.length);
    assert.equal(new Set(added.map(element => element.groupIds[0])).size, 1);
    assert(added.every(element => element.type === 'line' && element.fillStyle === 'solid' && element.strokeColor === 'transparent' && element.angle === 0 && element.groupIds.length === 1 && element.points.length === 4));
    assert(added.every(element => element.points[0][0] === element.points[3][0] && element.points[0][1] === element.points[3][1]));
    for (const old of before.elements) {
      const current = inserted.elements.find(element => element.id === old.id);
      for (const key of ['x', 'y', 'width', 'height', 'angle', 'opacity', 'scale', 'crop', 'fileId', 'locked', 'backgroundColor']) assert.deepEqual(current[key], old[key]);
    }
    assert.deepEqual(inserted.files, before.files);
    assert.deepEqual(added.map(element => [element.backgroundColor, element.opacity]), previewShapes.map(shape => [shape.fill, Math.round(shape.opacity * 100)]));
    await page.getByTestId('button-undo').click(); assert.equal((await save()).elements.length, 2);
    await page.getByTestId('button-redo').click(); assert.equal((await save()).elements.length, added.length + 2);
    await page.getByRole('button', { name: 'Projects', exact: true }).click();
    await withProjectView(page, 'images', async () => { await page.getByLabel('Use transparent image background', { exact: true }).check(); await page.getByRole('button', { name: 'Use selected artwork', exact: true }).click(); });
    await page.waitForFunction(() => window.__png?.startsWith('data:image/png;base64,'));
    const png = await page.evaluate(() => window.__png);
    fs.writeFileSync(path.join(output, 'selected-mosaic.png'), Buffer.from(png.split(',')[1], 'base64'));
    const pngReview = await page.evaluate(async value => {
      const image = new Image(); await new Promise((resolve, reject) => { image.onload = resolve; image.onerror = reject; image.src = value; });
      const c = document.createElement('canvas'); c.width = image.naturalWidth; c.height = image.naturalHeight; const ctx = c.getContext('2d'); ctx.drawImage(image, 0, 0); const data = ctx.getImageData(0, 0, c.width, c.height).data;
      let visible = 0, partial = 0, transparent = 0, red = 0, blue = 0, green = 0;
      for (let i = 0; i < data.length; i += 4) { if (!data[i + 3]) { transparent++; continue; } visible++; if (data[i + 3] < 250) partial++; if (data[i] > 180 && data[i + 1] < 80 && data[i + 2] < 80) red++; if (data[i + 2] > 150 && data[i] < 80) blue++; if (data[i + 1] > 140 && data[i] < 80 && data[i + 2] < 100) green++; }
      return { width: c.width, height: c.height, visible, partial, transparent, red, blue, green };
    }, png);
    assert(pngReview.visible > 100); assert(pngReview.partial > 100); assert(pngReview.transparent > 100); assert(pngReview.red > 100); assert(pngReview.blue > 100); assert.equal(pngReview.green, 0);
    await page.reload();
    const reopened = await save(); assert.equal(reopened.elements.length, inserted.elements.length);
    assert.equal(reopened.files[file.id].dataURL, sourceURL);
    for (const shape of added) { const restored = reopened.elements.find(element => element.id === shape.id); assert.deepEqual(restored.points, shape.points); for (const key of ['x', 'y', 'width', 'height']) assert.equal(restored[key], shape[key]); assert.equal(restored.backgroundColor, shape.backgroundColor); assert.equal(restored.opacity, shape.opacity); }
    await page.getByRole('button', { name: 'Projects', exact: true }).click();
    await withProjectView(page, 'advanced', () => page.getByRole('button', { name: 'Export project JSON', exact: true }).click());
    const portable = JSON.parse(await withProjectView(page, 'advanced', () => page.getByLabel('Project JSON', { exact: true }).inputValue()));
    assert.equal(JSON.parse(portable.scene).elements.filter(element => element.type === 'line').length, added.length);
    await withProjectView(page, 'advanced', () => page.getByRole('button', { name: 'Prepare vector SVG', exact: true }).click());
    const svg = await withProjectView(page, 'advanced', () => page.getByLabel('Vector SVG', { exact: true }).inputValue());
    const svgReview = await page.evaluate(value => { const dom = new DOMParser().parseFromString(value, 'image/svg+xml'); return { images: dom.querySelectorAll('image').length, redFacets: [...dom.querySelectorAll('path')].filter(node => node.getAttribute('fill') === '#dc361c').length }; }, svg);
    assert(svg.includes(sourceURL)); assert(svgReview.redFacets > 0);
    fs.writeFileSync(path.join(output, 'mosaic-composition.svg'), svg);
    await page.addScriptTag({ url: new URL('mosaic-service-test.js', url).href });
    const fences = await page.evaluate(async source => {
      const result = [];
      for (const stop of [1, 2, 3, 4, 5, 17, 80]) { let calls = 0; try { await window.__mosaicPhoto(source, 12, () => { if (++calls === stop) throw Error('Synthetic stale owner'); }); result.push({ stop, calls, error: 'unexpected success' }); } catch (error) { result.push({ stop, calls, error: error.message }); } }
      return result;
    }, sourceURL);
    for (const fence of fences) { assert.equal(fence.calls, fence.stop); assert.equal(fence.error, 'Synthetic stale owner'); }
    // Refuse the whole insertion when it cannot fit; do not drop triangles to meet the budget.
    const full = await browser.newPage({ viewport: { width: 390, height: 844 } });
    const packed = { ...initial, elements: [...initial.elements, ...Array.from({ length: 497 }, (_, index) => ({ ...protectedShape, id: `packed-${index}`, width: 2, height: 2, opacity: 0 }))] };
    await full.addInitScript(value => { if (!localStorage.getItem('honorable-drawing-v1')) localStorage.setItem('honorable-drawing-v1', JSON.stringify(value)); }, packed);
    await full.goto(url); await full.getByRole('button', { name: 'Objects', exact: true }).click();
    await full.getByRole('button', { name: 'Make mosaic from photo 1', exact: true }).click();
    await full.getByRole('button', { name: 'Preview photo mosaic', exact: true }).click();
    await full.getByRole('button', { name: 'Insert editable mosaic', exact: true }).click();
    await full.getByText('Use up to 500 drawing objects.', { exact: true }).waitFor();
    await full.getByRole('button', { name: 'Save draft', exact: true }).click();
    const unchanged = await full.evaluate(() => JSON.parse(localStorage.getItem('honorable-drawing-v1')));
    assert.equal(unchanged.elements.length, 499); assert.equal(unchanged.files[file.id].dataURL, sourceURL); await full.close();
    for (const [name, text] of [['DELAUNATOR-LICENSE.txt', 'Mapbox'], ['ROBUST-PREDICATES-LICENSE.txt', 'public domain']]) {
      const response = await page.request.get(new URL(`THIRD_PARTY/${name}`, url).href); assert.equal(response.status(), 200); assert((await response.text()).includes(text));
    }
    assert.deepEqual(errors, []); assert.deepEqual(external, []);
    fs.writeFileSync(path.join(output, 'review.json'), JSON.stringify({ previewTriangles: previewShapes.length, coarseTriangles: coarse, addedTriangles: added.length, sourceAndProtectedPreserved: true, pngReview, svgReview, fences, objectLimit: { before: 499, after: 499 }, pageErrors: errors, externalRequests: external }, null, 2));
    console.log('PASS: actual photo mosaic preview/detail invalidation/Cancel/explicit insertion; unchanged cropped, rotated, flipped, translucent source and protected artwork; grouped editable colored triangle paths; Undo/Redo/reload/JSON/vector SVG and selected transparent PNG; seven async stale guards; whole insertion refused at500object limit; phone widths and notices; zero page/CSP/external errors. Browser only; noAndroid/APK/device.');
  } catch (error) { if (page) await page.screenshot({ path: path.join(output, 'failure.png') }); throw error; }
  finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
