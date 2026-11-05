const { chromium } = require('../../../ui-previews/node_modules/playwright');
const { withProjectView } = require('./project-navigation.cjs');
const fs = require('node:fs'), path = require('node:path'), assert = require('node:assert/strict');
const root = path.resolve(__dirname, '../../../..'), output = path.join(root, '.context/drawing-reset/browser');
fs.mkdirSync(output, { recursive: true });
const before = process.env.NOBLE_RESET_PHASE === 'before';
const url = process.env.NOBLE_PREVIEW_URL || `http://127.0.0.1:4194/${before ? 'before' : 'after'}/drawing/`;
const nativePath = before ? path.join(root, '.context/drawing-reset/native-before.kt') : path.join(root, 'Honorable-mobile-app-main/mobile-react-native/android/app/src/main/java/com/honorablemobile/HonorableDrawingActivity.kt');
const native = fs.readFileSync(nativePath, 'utf8'), match = native.match(/web\.evaluateJavascript\("((?:[^"\\]|\\.)*)"/);
assert(match, 'Native reset script must remain inspectable');
const resetScript = JSON.parse('"' + match[1] + '"');
const key = 'honorable-drawing-v1', projectKey = 'noble-drawing-projects-v1', componentKey = 'noble-drawing-components-v1';
(async () => {
  const { saveProject } = await import('../projects.mjs');
  const elements = [{ id: 'original', type: 'rectangle', x: 50, y: 40, width: 120, height: 90, angle: 0, opacity: 100, strokeColor: '#111111', backgroundColor: '#dddddd', fillStyle: 'solid', strokeWidth: 1, roughness: 0, groupIds: [], boundElements: null, version: 1, versionNonce: 1, seed: 1 }];
  const state = { viewBackgroundColor: '#ffffff', currentItemFontFamily: 2 };
  const initial = { version: 1, elements, appState: state };
  const projects = JSON.stringify(saveProject([], 'Keep named', elements, state));
  const components = JSON.stringify(saveProject([], 'Keep component', elements, state));
  const browser = await chromium.launch({ args: ['--no-sandbox'] }); let page;
  try {
    page = await browser.newPage({ viewport: { width: 390, height: 844 } });
    const errors = [], external = []; page.on('pageerror', e => errors.push(e.message));
    await page.route('**/*', route => { const value = route.request().url(); if (!value.startsWith(new URL(url).origin) && !value.startsWith('data:') && !value.startsWith('blob:')) { external.push(value); return route.abort(); } return route.continue(); });
    await page.addInitScript(({ initial, projects, components, key, projectKey, componentKey }) => {
      if (!sessionStorage.getItem('reset-fixture-seeded')) {
        localStorage.setItem(key, JSON.stringify(initial)); localStorage.setItem(projectKey, projects); localStorage.setItem(componentKey, components); sessionStorage.setItem('reset-fixture-seeded', 'true');
      }
      const set = Storage.prototype.setItem, remove = Storage.prototype.removeItem;
      window.__staleDraftWrites = 0;
      Storage.prototype.setItem = function (name, value) { if (name === key && window.__resetCleared) window.__staleDraftWrites++; return set.call(this, name, value); };
      Storage.prototype.removeItem = function (name) { if (name === key && window.__rejectClear) throw Error('Synthetic reset storage failure'); const result = remove.call(this, name); if (name === key) window.__resetCleared = true; return result; };
      window.HonorableDrawing = { beginProjectFileSave: () => true, appendProjectFileChunk: () => true, finishProjectFileSave: id => { window.__fileRequest = id; }, releaseProjectFile: () => {} };
    }, { initial, projects, components, key, projectKey, componentKey });
    await page.goto(url);
    await page.getByRole('button', { name: 'Save draft', exact: true }).click();
    const raw = await page.evaluate(key => localStorage.getItem(key), key);
    assert.equal(JSON.parse(raw).elements.length, 1);
    if (before) {
      assert.equal(await page.evaluate(resetScript), true);
      const saved = await page.evaluate(() => window.honorableSaveDraft());
      assert.equal(saved, false, 'Old-page lifecycle save must not resurrect a draft cleared by New drawing');
      return;
    }
    // Real JS file-save protocol stays pending, then is cancelled without changing the source.
    await page.getByRole('button', { name: 'Projects', exact: true }).click();
    await withProjectView(page, 'files', () => page.getByRole('button', { name: 'Save project file', exact: true }).click());
    await page.waitForFunction(() => window.__fileRequest && window.honorableDrawingBusy);
    assert.equal(await page.evaluate(resetScript), false);
    assert.equal(await page.evaluate(key => localStorage.getItem(key), key), raw);
    await page.evaluate(() => window.honorableProjectFileResult({ requestId: window.__fileRequest, cancelled: true }));
    await page.waitForFunction(() => !window.honorableDrawingBusy);
    // A failed removal leaves the source and save function usable, without entering reset state.
    await page.evaluate(() => { window.__rejectClear = true; });
    assert.equal(await page.evaluate(resetScript), false);
    assert.equal(await page.evaluate(key => localStorage.getItem(key), key), raw);
    assert.equal(await page.evaluate(() => window.honorableDrawingBusy), false);
    await page.evaluate(() => { window.__rejectClear = false; });
    assert.equal(await page.evaluate(() => window.honorableSaveDraft()), true);
    // Native confirmation uses this exact script; hold the old page to simulate a delayed reload.
    assert.equal(await page.evaluate(resetScript), true);
    assert.equal(await page.evaluate(key => localStorage.getItem(key), key), null);
    assert.equal(await page.evaluate(() => window.honorableSaveDraft()), false);
    await page.evaluate(() => window.honorableExportResult('Synthetic late callback'));
    assert.equal(await page.evaluate(() => window.honorableDrawingBusy), true);
    assert.equal(await page.evaluate(resetScript), false);
    await page.waitForTimeout(950); // Beyond the real editor's700ms pending-autosave deadline.
    const held = await page.evaluate(({ key, projectKey, componentKey }) => ({ draft: localStorage.getItem(key), projects: localStorage.getItem(projectKey), components: localStorage.getItem(componentKey), staleWrites: window.__staleDraftWrites }), { key, projectKey, componentKey });
    assert.equal(held.draft, null); assert.equal(held.staleWrites, 0); assert.equal(held.projects, projects); assert.equal(held.components, components);
    await page.screenshot({ path: path.join(output, 'reset-pending.png') });
    await page.reload(); await page.getByRole('button', { name: 'Save draft', exact: true }).click();
    const fresh = await page.evaluate(key => JSON.parse(localStorage.getItem(key) || '{"elements":[]}'), key);
    assert.equal(fresh.elements.length, 0);
    assert.equal(await page.evaluate(() => window.honorableDrawingBusy), false);
    await page.getByRole('button', { name: 'Projects', exact: true }).click();
    await withProjectView(page, 'saved', () => page.getByText('Keep named', { exact: true }).waitFor());
    assert.equal(await page.evaluate(componentKey => localStorage.getItem(componentKey), componentKey), components);
    assert.deepEqual(errors, []); assert.deepEqual(external, []);
    fs.writeFileSync(path.join(output, 'review.json'), JSON.stringify({ nativeScript: resetScript, busyFileRefused: true, storageFailurePreserved: true, staleLifecycleSaveRefused: true, staleAutosaveWrites: held.staleWrites, lateCallbackStayedFrozen: true, freshDrawingObjects: fresh.elements.length, namedProjectsPreserved: true, componentsPreserved: true, pageErrors: errors, externalRequests: external, scope: 'Actual browser script/save hooks and simulated native callback timing; not Android WebView/Activity lifecycle execution' }, null, 2));
    console.log('PASS: actual native reset script, busy file-save refusal, storage-removal failure preserves source, old-page lifecycle/timer/late-callback refusal, blank reopen and intact named/component shelves;zero page/external errors. Browser only;Android callback timing remains pending.');
  } catch (error) { if (page) await page.screenshot({ path: path.join(output, before ? 'before-failure.png' : 'failure.png') }); throw error; }
  finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
