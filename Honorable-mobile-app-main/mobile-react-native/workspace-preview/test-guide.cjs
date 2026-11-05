const {chromium} = require('../../ui-previews/node_modules/playwright');
const assert = require('node:assert/strict');
(async () => {
  const browser = await chromium.launch({args:['--no-sandbox']});
  try {
    const page = await browser.newPage({viewport:{width:390,height:844},reducedMotion:'reduce'});
    const errors = [], external = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.route('**/*', route => {
      const url = route.request().url();
      if (!url.startsWith('http://127.0.0.1:4177') && !url.startsWith('blob:') && !url.startsWith('data:')) {
        external.push(url); return route.abort();
      }
      return route.continue();
    });
    await page.goto('http://127.0.0.1:4177');
    await page.getByRole('button',{name:'Edit without an account',exact:true}).click();
    const toggle = page.getByRole('button',{name:'Help me get started',exact:true});
    await toggle.click();
    for (const [choice, action] of [
      ['Polish a photo','Choose my photo'],
      ['Turn photos into a video','Choose my story photos'],
      ['Make an original design','Open my drawing canvas'],
    ]) {
      await page.getByRole('radio',{name:choice,exact:true}).click();
      assert.equal(await page.getByRole('radio',{name:choice,exact:true}).getAttribute('aria-checked'),'true');
      await page.getByRole('button',{name:action,exact:true}).waitFor();
    }
    for (const width of [320,390,844]) {
      await page.setViewportSize({width,height:844});
      assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
    }
    await toggle.click(); await toggle.click();
    await page.getByRole('button',{name:'Open my drawing canvas',exact:true}).click();
    await page.getByRole('button',{name:'New drawing',exact:true}).waitFor();
    assert.deepEqual(errors,[]); assert.deepEqual(external,[]);
    console.log('PASS: guide goals, selected states, retained choice, actual drawing launch, reduced-motion browser, 320/390/844px, no page errors or external requests. Android acceptance pending.');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
