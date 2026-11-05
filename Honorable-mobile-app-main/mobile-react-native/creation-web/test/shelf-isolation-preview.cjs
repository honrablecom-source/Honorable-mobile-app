const { chromium } = require('../../../ui-previews/node_modules/playwright');
const { withProjectView } = require('./project-navigation.cjs');
const assert = require('node:assert/strict'), fs = require('node:fs'), path = require('node:path');
const output = path.resolve(__dirname, '../../../../.context/shelf-isolation');
fs.mkdirSync(output, { recursive: true });
const url = process.env.NOBLE_PREVIEW_URL || 'http://127.0.0.1:4194/after/drawing/';
(async () => {
  const { saveProject, writeProjects } = await import('../projects.mjs');
  const element = { id: 'kept', type: 'rectangle', x: 40, y: 50, width: 120, height: 80, angle: 0, opacity: 100, strokeColor: '#111111', backgroundColor: '#dddddd', fillStyle: 'solid', strokeWidth: 1, roughness: 0, groupIds: [], boundElements: null, seed: 1, version: 1, versionNonce: 1 };
  const healthy = writeProjects(saveProject([], 'Healthy work', [element], {}));
  const keys = ['noble-drawing-projects-v1', 'noble-drawing-components-v1'];
  const corrupt = '{unreadable shelf 🙂';
  const browser = await chromium.launch({ args: ['--no-sandbox'] });
  const reviews = [];
  try {
    for (const bad of [0, 1]) {
      const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
      const errors = [], external = [];
      page.on('pageerror', error => errors.push(error.message));
      await page.route('**/*', route => {
        const value = route.request().url();
        if (!value.startsWith(new URL(url).origin) && !value.startsWith('data:') && !value.startsWith('blob:')) { external.push(value); return route.abort(); }
        return route.continue();
      });
      await page.addInitScript(({ keys, bad, healthy, corrupt }) => {
        localStorage.setItem(keys[bad], corrupt); localStorage.setItem(keys[1 - bad], healthy);
      }, { keys, bad, healthy, corrupt });
      try {
        await page.goto(url);
        await page.getByRole('button', { name: 'Save draft', exact: true }).click();
        await page.getByRole('button', { name: 'Projects', exact: true }).click();
        const action = bad === 0 ? 'Insert Healthy work' : 'Open Healthy work';
        await withProjectView(page, bad === 0 ? 'parts' : 'saved', () => page.getByRole('button', { name: action, exact: true }).click());
        await page.waitForFunction(() => !window.honorableDrawingBusy);
        await page.getByRole('button', { name: 'Save draft', exact: true }).click();
        const result = await page.evaluate(keys => ({ shelves: keys.map(key => localStorage.getItem(key)), draft: JSON.parse(localStorage.getItem('honorable-drawing-v1')) }), keys);
        assert.equal(result.shelves[bad], corrupt); assert.equal(result.shelves[1 - bad], healthy);
        assert.equal(result.draft.elements.length, 1);
        // A shelf that becomes unreadable after being displayed must not leave stale actions.
        const projectsButton = page.getByRole('button', { name: 'Projects', exact: true });
        if (await projectsButton.getAttribute('aria-expanded') === 'true') await projectsButton.click();
        await page.evaluate(({ key, raw }) => localStorage.setItem(key, raw), { key: keys[1 - bad], raw: 'newly damaged shelf' });
        await page.getByRole('button', { name: 'Projects', exact: true }).click();
        await page.getByRole('status').getByText(/Saved designs and Reusable parts could not be read/).waitFor();
        await withProjectView(page, bad === 0 ? 'parts' : 'saved', async () => assert.equal(await page.getByRole('button', { name: action, exact: true }).count(), 0));
        assert.deepEqual(await page.evaluate(keys => keys.map(key => localStorage.getItem(key)), keys), keys.map((_, index) => index === bad ? corrupt : 'newly damaged shelf'));
        await page.getByRole('button', { name: 'Projects', exact: true }).click();
        await page.evaluate(({ key, healthy }) => localStorage.setItem(key, healthy), { key: keys[1 - bad], healthy });
        await page.getByRole('button', { name: 'Projects', exact: true }).click();
        await withProjectView(page, bad === 0 ? 'parts' : 'saved', () => page.getByRole('button', { name: action, exact: true }).waitFor());
        assert.deepEqual(errors, []); assert.deepEqual(external, []);
        await page.screenshot({ path: path.join(output, `healthy-${1 - bad}.png`) });
        reviews.push({ failedShelf: keys[bad], healthyAction: action, damagedTextPreserved: true, healthyShelfUnchanged: true, staleActionsCleared: true, bothFailuresPreserved: true, repairedShelfAvailableOnReopen: true, openedObjects: result.draft.elements.length, pageErrors: errors, externalRequests: external });
      } finally { await page.close(); }
    }
    fs.writeFileSync(path.join(output, 'review.json'), JSON.stringify(reviews, null, 2));
    console.log('PASS: damaged named-design and component shelves are isolated; healthy Open/Insert works; both original shelf texts preserved; no page/external errors. Browser only.');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
