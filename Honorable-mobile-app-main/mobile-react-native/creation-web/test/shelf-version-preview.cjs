const { projectView, withProjectView } = require('./project-navigation.cjs');
const { chromium } = require('../../../ui-previews/node_modules/playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const output = path.resolve(__dirname, '../../../../.context/shelf-version');
fs.mkdirSync(output, { recursive: true });
const url = process.env.NOBLE_PREVIEW_URL || 'http://127.0.0.1:4177/drawing/';
const origin = new URL(url).origin;
const keys = { project: 'noble-drawing-projects-v1', component: 'noble-drawing-components-v1' };
const box = width => ({ id: 'a', type: 'rectangle', x: 0, y: 0, width, height: 20, angle: 0, opacity: 100, strokeColor: '#111111', backgroundColor: '#cccccc', fillStyle: 'solid', strokeWidth: 1, roughness: 0, locked: false, groupIds: [], boundElements: null, version: 1, versionNonce: 1, seed: 1 });
const scene = width => JSON.stringify({ version: 1, elements: [box(width)], appState: { viewBackgroundColor: '#ffffff' } });
const shelf = (width, extra = false) => JSON.stringify({ version: 1, projects: [{ name: 'Saved', scene: scene(width) }, ...(extra ? [{ name: 'Other', scene: scene(60) }] : [])] });
(async () => {
  const browser = await chromium.launch({ args: ['--no-sandbox'] });
  try {
    for (const operation of ['delete', 'rename', 'duplicate', 'component', 'unrelated']) {
      const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
      const errors = [], external = [];
      page.on('pageerror', error => errors.push(error.message));
      await page.route('**/*', route => {
        const target = route.request().url();
        if (!target.startsWith(origin) && !target.startsWith('data:') && !target.startsWith('blob:')) { external.push(target); return route.abort(); }
        return route.continue();
      });
      await page.addInitScript(({ keys, initial, draft }) => {
        for (const key of Object.values(keys)) localStorage.setItem(key, initial);
        localStorage.setItem('honorable-drawing-v1', draft);
      }, { keys, initial: shelf(20), draft: scene(40) });
      await page.goto(url);
      await page.getByRole('button', { name: 'Projects', exact: true }).click();
      if (operation === 'rename' || operation === 'duplicate') {
        await withProjectView(page, 'saved', () => page.getByRole('button', { name: 'Manage Saved', exact: true }).click());
        await withProjectView(page, 'saved', () => page.getByLabel('New saved project name', { exact: true }).fill('Renamed'));
      }
      const key = operation === 'component' ? keys.component : keys.project;
      const changed = operation === 'unrelated' ? shelf(20, true) : shelf(80);
      await page.evaluate(({ key, changed }) => localStorage.setItem(key, changed), { key, changed });
      const draftBefore = await page.evaluate(() => localStorage.getItem('honorable-drawing-v1'));
      const label = { delete: 'Delete Saved', rename: 'Rename saved project', duplicate: 'Duplicate saved project', component: 'Delete component Saved', unrelated: 'Delete Saved' }[operation];
      await projectView(page, operation === 'component' ? 'parts' : 'saved');
      await page.getByRole('button', { name: label, exact: true }).click();
      const actual = await page.evaluate(key => localStorage.getItem(key), key);
      if (operation === 'unrelated') {
        assert.deepEqual(JSON.parse(actual).projects.map(project => project.name), ['Other']);
        await withProjectView(page, 'saved', () => page.getByRole('button', { name: 'Undo project delete', exact: true }).click());
        assert.equal(JSON.parse(await page.evaluate(key => localStorage.getItem(key), key)).projects.length, 2);
      } else {
        assert.equal(actual, changed, `${operation} must preserve an unseen replacement version`);
        await page.getByRole('status').getByText(/Saved work changed.*Reopen Projects/).waitFor();
        // Refresh the shelf, then the newly visible version may be deleted and restored.
        await page.getByRole('button', { name: 'Close panel', exact: true }).click();
        await page.getByRole('button', { name: 'Projects', exact: true }).click();
        const component = operation === 'component';
        await page.getByRole('button', { name: component ? 'Delete component Saved' : 'Delete Saved', exact: true }).click();
        assert.equal(JSON.parse(await page.evaluate(key => localStorage.getItem(key), key)).projects.length, 0);
        await page.getByRole('button', { name: component ? 'Undo component change' : 'Undo project delete', exact: true }).click();
        assert.equal(JSON.parse(JSON.parse(await page.evaluate(key => localStorage.getItem(key), key)).projects[0].scene).elements[0].width, 80);
      }
      assert.equal(await page.evaluate(() => localStorage.getItem('honorable-drawing-v1')), draftBefore);
      assert.deepEqual(errors, []); assert.deepEqual(external, []);
      await page.close();
    }
    console.log('PASS: stale project delete/rename/duplicate and component delete preserve unseen replacements; refresh permits current deletion+Undo; unrelated additions survive; current draft unchanged; zero page/external errors. Browser only; Android pending.');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
