const { withProjectView } = require('./project-navigation.cjs');
const { chromium } = require('../../../ui-previews/node_modules/playwright');
const assert = require('node:assert/strict'), fs = require('node:fs'), path = require('node:path');
const output = path.resolve(__dirname, '../../../../.context/starter-designs/browser');
fs.mkdirSync(output, { recursive: true });
const url = process.env.NOBLE_PREVIEW_URL || 'http://127.0.0.1:4177/drawing/', origin = new URL(url).origin;
const original = { id: 'original', type: 'rectangle', x: -200, y: -200, width: 30, height: 30, angle: 0, opacity: 100, strokeColor: '#111111', backgroundColor: '#dddddd', fillStyle: 'solid', strokeWidth: 1, roughness: 0, seed: 1, version: 1, versionNonce: 1, groupIds: [], boundElements: null, locked: false };
(async () => {
 const browser = await chromium.launch({ args: ['--no-sandbox'] }); let page;
 try {
  for (const style of ['quote', 'announcement', 'flyer']) {
   page = await browser.newPage({ viewport: { width: 390, height: 844 } });
   const errors = [], external = [];
   page.on('pageerror', e => errors.push(e.message));
   page.on('console', m => { if (m.type() === 'error' && m.text().includes('Content Security Policy')) errors.push(m.text()); });
   await page.route('**/*', route => { const u = route.request().url(); if (!u.startsWith(origin) && !u.startsWith('data:') && !u.startsWith('blob:')) { external.push(u); return route.abort(); } return route.continue(); });
   await page.addInitScript(original => {
    if (!localStorage.getItem('honorable-drawing-v1')) localStorage.setItem('honorable-drawing-v1', JSON.stringify({ version: 1, elements: [original], appState: { viewBackgroundColor: '#ffffff', currentItemFontFamily: 2 } }));
    window.HonorableDrawing = { usePng: png => { window.__png = png; window.honorableExportResult('Synthetic starter ready'); } };
   }, original);
   await page.goto(url);
   const save = async () => { await page.getByRole('button', { name: 'Save draft', exact: true }).click(); return page.evaluate(() => JSON.parse(localStorage.getItem('honorable-drawing-v1'))); };
   const open = async () => { await page.getByRole('button', { name: 'Layouts', exact: true }).click(); await page.getByRole('button', { name: 'Starter design', exact: true }).click(); };
   await open(); await page.getByLabel('Starter design style', { exact: true }).selectOption(style);
   console.log('Configured style', style);
   await page.getByLabel('Design headline', { exact: true }).fill('');
   await page.getByRole('button', { name: 'Insert editable starter design', exact: true }).click();
   await page.getByRole('status').getByText(/Add a headline/).waitFor(); assert.equal((await save()).elements.length, 1);
   await page.getByLabel('Design headline', { exact: true }).fill('A bright idea');
   await page.getByLabel('Design details', { exact: true }).fill('Make something you are proud of.\nR&D <b>stays plain text</b>');
   await page.getByLabel('Design sign-off', { exact: true }).fill('Made in Noble');
   await page.getByRole('button', { name: 'Table', exact: true }).click();
   await page.getByRole('button', { name: 'Starter design', exact: true }).click();
   assert.equal(await page.getByLabel('Design headline', { exact: true }).inputValue(), 'A bright idea');
   if (style === 'quote') for (const width of [320, 390, 844]) {
    await page.setViewportSize({ width, height: 844 }); assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
    await page.screenshot({ path: path.join(output, `form-${width}.png`) });
   }
   await page.setViewportSize({ width: 390, height: 844 });
   await page.getByRole('button', { name: 'Insert editable starter design', exact: true }).click();
   assert.equal(await page.getByRole('button', { name: 'Close panel', exact: true }).count(), 0);
   console.log('Inserted style', style);
   const inserted = await save(), artwork = inserted.elements.filter(e => e.id !== 'original');
   assert.equal(artwork.length, 11); assert.equal(new Set(artwork.map(e => e.groupIds[0])).size, 1); assert(artwork.every(e => !e.locked && e.groupIds.length === 1));
   assert.equal(inserted.elements.find(e => e.id === 'original').width, 30);
   const ids = new Map(artwork.map(e => [e.id, e]));
   for (const text of artwork.filter(e => e.type === 'text')) { const parent = ids.get(text.containerId); assert(parent.boundElements.some(b => b.id === text.id)); assert.equal(text.strokeColor, '#111111'); }
   await page.waitForTimeout(300);
   await page.screenshot({ path: path.join(output, `${style}-390.png`) });
   await page.getByTestId('button-undo').click(); assert.equal((await save()).elements.length, 1);
   await page.getByTestId('button-redo').click(); assert.equal((await save()).elements.length, 12);
   await page.getByRole('button', { name: 'Projects', exact: true }).click();
   await withProjectView(page, 'saved', () => page.getByLabel('Project name', { exact: true }).fill(`${style} starter`));
   await withProjectView(page, 'saved', () => page.getByRole('button', { name: 'Save named composition', exact: true }).click());
   await withProjectView(page, 'advanced', () => page.getByRole('button', { name: 'Export project JSON', exact: true }).click());
   const portable = JSON.parse(await withProjectView(page, 'advanced', () => page.getByLabel('Project JSON', { exact: true }).inputValue())); assert.equal(JSON.parse(portable.scene).elements.length, 12);
   await withProjectView(page, 'advanced', () => page.getByRole('button', { name: 'Prepare vector SVG', exact: true }).click()); await withProjectView(page, 'advanced', () => page.getByLabel('Vector SVG', { exact: true }).waitFor());
   const svg = await withProjectView(page, 'advanced', () => page.getByLabel('Vector SVG', { exact: true }).inputValue()); assert(svg.includes('R&amp;D')); assert(svg.includes('&lt;b&gt;')); fs.writeFileSync(path.join(output, `${style}.svg`), svg);
   await page.getByRole('button', { name: 'Close panel', exact: true }).click();
   await page.getByRole('button', { name: 'Use image', exact: true }).click(); await page.waitForFunction(() => window.__png?.startsWith('data:image/png;base64,'));
   fs.writeFileSync(path.join(output, `${style}.png`), Buffer.from((await page.evaluate(() => window.__png)).split(',')[1], 'base64'));
   const pixels = await page.evaluate(async () => {
    const image = new Image(); await new Promise((resolve, reject) => { image.onload = resolve; image.onerror = reject; image.src = window.__png; });
    const canvas = document.createElement('canvas'); canvas.width = image.naturalWidth; canvas.height = image.naturalHeight;
    const ctx = canvas.getContext('2d'); ctx.drawImage(image, 0, 0); const data = ctx.getImageData(0, 0, canvas.width, canvas.height).data;
    let ink = 0, white = 0, gray = 0;
    for (let i = 0; i < data.length; i += 4) { if (data[i] === 17 && data[i + 1] === 17 && data[i + 2] === 17) ink++; if (data[i] === 255 && data[i + 1] === 255 && data[i + 2] === 255) white++; if (data[i] === 238 && data[i + 1] === 238 && data[i + 2] === 238) gray++; }
    return { width: canvas.width, height: canvas.height, ink, white, gray };
   });
   assert(pixels.width > 600 && pixels.height > 900 && pixels.ink > 100 && pixels.white > 10000);
   if (style === 'flyer') assert(pixels.gray > 10000);
   fs.writeFileSync(path.join(output, `${style}-pixels.json`), JSON.stringify(pixels, null, 2));
   await page.reload(); assert.equal((await save()).elements.length, 12);
   // Stress actual renderer wrapping with maximal wide Unicode fields.
   await open(); await page.getByLabel('Starter design style', { exact: true }).selectOption(style);
   await page.getByLabel('Design headline', { exact: true }).fill('作品🙂'.repeat(20));
   await page.getByLabel('Design details', { exact: true }).fill('界'.repeat(180));
   await page.getByLabel('Design sign-off', { exact: true }).fill('界'.repeat(60));
   await page.getByRole('button', { name: 'Insert editable starter design', exact: true }).click();
   const combined = await save(), fresh = combined.elements.filter(e => !inserted.elements.some(old => old.id === e.id));
   assert.equal(fresh.length, 11); assert.notEqual(fresh[0].groupIds[0], artwork[0].groupIds[0]);
   const background = fresh.find(e => e.width === 640 && e.height === 960);
   assert(background, 'starter outer bounds stay fixed');
   for (const e of fresh) { assert(e.x >= background.x - 1 && e.y >= background.y - 1); assert(e.x + e.width <= background.x + 641 && e.y + e.height <= background.y + 961, `${style}: ${e.type} overflows card`); }
   const freshIds = new Map(fresh.map(e => [e.id, e]));
   assert.deepEqual(fresh.filter(e => e.boundElements?.some(b => b.type === 'text')).map(e => e.height), [48, 220, 360, 96]);
   for (const text of fresh.filter(e => e.type === 'text')) {
    const parent = freshIds.get(text.containerId);
    assert(text.x >= parent.x && text.y >= parent.y && text.x + text.width <= parent.x + parent.width + 1 && text.y + text.height <= parent.y + parent.height + 1, 'text fits its own block');
   }
   fs.writeFileSync(path.join(output, `${style}-elements.json`), JSON.stringify(fresh, null, 2));
   assert.deepEqual(errors, []); assert.deepEqual(external, []);
   await page.close(); page = null;
  }
  console.log('PASS:all three starter styles;explicit input/error preserves drawing;form retention/phone widths;editable grouped text and reciprocal bindings;one-step Undo/Redo;named save/portable/SVG literal escaping/PNG/reload;maximal Unicode fits outer card;independent repeat IDs;zero page/CSP/external errors. Browser only; Android pending.');
 } catch (error) { if (page) await page.screenshot({ path: path.join(output, 'failure.png') }); throw error; }
 finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
