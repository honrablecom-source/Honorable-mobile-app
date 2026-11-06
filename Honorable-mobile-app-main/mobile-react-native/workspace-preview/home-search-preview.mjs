// Actual Home/search bar; synthetic local media, native methods and credit provider. Never shipped.
import fs from 'node:fs/promises';
import path from 'node:path';
import http from 'node:http';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
const here = path.dirname(fileURLToPath(import.meta.url));
const output =
  process.env.NOBLE_HOME_PREVIEW_OUTPUT ||
  path.resolve(here, '../../../.context/home-results-return/browser');
await fs.mkdir(output, { recursive: true });
const require = createRequire(path.join(here, 'package.json'));
const { build } = require('esbuild');
const { chromium } = createRequire(
  path.resolve(here, '../../ui-previews/package.json'),
)('playwright');
const fixture = path.join(output, 'fixture.jsx'),
  safe = path.join(output, 'safe.jsx'),
  ui = path.join(output, 'ui.jsx');
await fs.writeFile(
  safe,
  "export {View as SafeAreaView} from 'react-native';export const useSafeAreaInsets=()=>({top:0,right:0,bottom:0,left:0});",
);
await fs.writeFile(
  ui,
  "export {Text,Pressable as Button} from 'react-native';",
);
await fs.writeFile(
  fixture,
  `
import React from 'react';import {createRoot} from 'react-dom/client';
import {HomeScreen} from ${JSON.stringify(
    path.resolve(here, '../src/screens/HomeScreen.tsx'),
  )};
window.__calls=[];window.__alerts=[];window.addEventListener('noble-preview-error',event=>window.__alerts.push(event.detail));
const call=(kind,value)=>window.__calls.push({kind,value});
const image='data:image/svg+xml,'+encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="200" height="200"><rect width="200" height="200" fill="#eee"/><circle cx="100" cy="100" r="60" fill="#555"/></svg>');
const item={mediaId:1,mediaUri:'content://synthetic/photo',thumbnailUri:image,mediaType:'IMAGE',displayName:'Synthetic photo.jpg',capturedAt:0};
let history=['Synthetic previous query'];
export const useLibrary=()=>({items:[item],loading:false,error:''});
export const useNavigation=()=>({navigate:()=>{},getParent:()=>({navigate:()=>{}})});
export const useOpenWorkspace=()=>({busy:false,open:()=>{}});
export const useMemoryPass=()=>({connected:true,startSearch:async(model,id)=>call('start',{model,id}),finishSearch:async(id,outcome)=>call('finish',{id,outcome})});
export const honorableNative={getSearchHistory:async()=>({queries:history}),clearSearchHistory:async()=>{history=[];call('clearHistory');},selectSeranModel:async model=>call('model',model),search:async query=>{call('search',query);if(window.__hold){window.__hold=false;await new Promise(resolve=>{window.__finish=resolve;});}history=[query,...history];return {confident:true,decision:'MATCH',semantic:1,margin:1,results:[{...item,rank:1}]};}};
createRoot(document.getElementById('root')).render(<HomeScreen/>);
`,
);
await build({
  entryPoints: [fixture],
  outfile: path.join(output, 'app.js'),
  bundle: true,
  minify: true,
  format: 'iife',
  target: ['chrome100'],
  loader: { '.png': 'dataurl' },
  define: { 'process.env.NODE_ENV': '"production"' },
  plugins: [
    {
      name: 'synthetic-home-boundaries',
      setup(builder) {
        builder.onResolve(
          {
            filter:
              /(?:^|\/)LibraryContext$|(?:^|\/)HonorableNative$|\/passes\/MemoryPassContext$|\/native\/openWorkspace$|^@react-navigation\/native$/,
          },
          () => ({ path: fixture }),
        );
        builder.onResolve({ filter: /^react$/ }, () => ({
          path: require.resolve('react'),
        }));
        builder.onResolve({ filter: /^react-dom\/client$/ }, () => ({
          path: require.resolve('react-dom/client'),
        }));
        builder.onResolve({ filter: /^react-native$/ }, () => ({
          path: path.join(here, 'rn-web.mjs'),
        }));
        builder.onResolve(
          { filter: /^react-native-safe-area-context$/ },
          () => ({ path: safe }),
        );
        builder.onResolve(
          { filter: /^@\/components\/ui\/(?:text|button)$/ },
          () => ({ path: ui }),
        );
        builder.onResolve({ filter: /^lucide-react-native$/ }, () => ({
          path: require.resolve('lucide-react'),
        }));
      },
    },
  ],
  nodePaths: [path.join(here, 'node_modules')],
});
const server = http.createServer(async (req, res) => {
  if (req.url === '/') {
    res.writeHead(200, {
      'Content-Type': 'text/html',
      'Content-Security-Policy':
        "default-src 'self';script-src 'self';style-src 'self' 'unsafe-inline';img-src 'self' data:;font-src 'self' data:;connect-src 'self'",
    });
    res.end(
      '<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><style>html,body,#root{margin:0;height:100%;width:100%}#root{display:flex}</style></head><body><div id="root"></div><script src="/app.js"></script></body></html>',
    );
  } else if (req.url === '/app.js') {
    res.writeHead(200, { 'Content-Type': 'text/javascript' });
    res.end(await fs.readFile(path.join(output, 'app.js')));
  } else res.writeHead(404).end();
});
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
const url = `http://127.0.0.1:${server.address().port}/`;
let browser;
try {
  browser = await chromium.launch({ args: ['--no-sandbox'] });
  const results = [];
  for (const width of [320, 390, 844]) {
    const page = await browser.newPage({
      viewport: { width, height: 844 },
      reducedMotion: 'reduce',
    });
    const errors = [],
      external = [];
    page.on('pageerror', e => errors.push(e.message));
    await page.route('**/*', route => {
      const u = route.request().url();
      if (
        !u.startsWith(url) &&
        !u.startsWith('data:') &&
        !u.startsWith('blob:')
      ) {
        external.push(u);
        return route.abort();
      }
      return route.continue();
    });
    await page.goto(url);
    const button = name => page.getByRole('button', { name, exact: true });
    const field = page.getByRole('textbox', {
      name: 'Describe a photo or video',
      exact: true,
    });
    const query =
      'Synthetic memory query with a deliberately long title for narrow phone result controls';
    await field.fill(query);
    await button('Search memories').click();
    await button('Clear search results').waitFor();
    assert(
      await page.getByText('Results for ' + query, { exact: true }).isVisible(),
    );
    await button('Clear search results').scrollIntoViewIfNeeded();
    assert(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    );
    await page.screenshot({ path: path.join(output, `results-${width}.png`) });
    await field.fill('Different draft');
    assert(
      await page.getByText('Results for ' + query, { exact: true }).isVisible(),
    );
    await button('Clear search').click();
    assert.equal(await field.inputValue(), '');
    assert.equal(await button('Clear search results').count(), 0);
    await button('Open Studio').waitFor();
    await field.fill('Second query');
    await button('Search memories').click();
    await button('Clear search results').waitFor();
    await button('Clear search results').click();
    await button('Open Studio').waitFor();
    await page.evaluate(() => {
      window.__hold = true;
    });
    await field.fill('Held query');
    await button('Search memories').click();
    assert(await button('Clear search').isDisabled());
    assert.equal(await field.isEditable(), false);
    await page.evaluate(() => window.__finish());
    await button('Clear search results').waitFor();
    await button('Clear search results').click();
    await button('Open Studio').waitFor();
    const calls = await page.evaluate(() => window.__calls);
    for (const kind of ['start', 'search', 'finish'])
      assert.equal(calls.filter(c => c.kind === kind).length, 3);
    assert.equal(calls.filter(c => c.kind === 'clearHistory').length, 0);
    assert(
      calls
        .filter(c => c.kind === 'finish')
        .every(c => c.value.outcome === 'SUCCESS'),
    );
    assert.deepEqual(await page.evaluate(() => window.__alerts), []);
    assert.deepEqual(errors, []);
    assert.deepEqual(external, []);
    results.push({
      width,
      searches: 3,
      starts: 3,
      completions: 3,
      errors,
      external,
      passed: true,
    });
    await page.close();
  }
  await fs.writeFile(
    path.join(output, 'results.json'),
    JSON.stringify(results, null, 2) + '\n',
  );
  console.log(
    JSON.stringify({
      results,
      scope:
        'Actual Home/search bar RNWeb;synthetic native/credit/library,plain UI text/button boundary,zero safearea;not native/device/livebilling acceptance',
    }),
  );
} finally {
  if (browser) await browser.close();
  await new Promise(resolve => server.close(resolve));
}
