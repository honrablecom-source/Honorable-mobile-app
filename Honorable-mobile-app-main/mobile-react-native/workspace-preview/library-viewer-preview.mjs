// Actual Library, grid and viewer with synthetic media/native boundaries. Never shipped.
import fs from 'node:fs/promises';
import path from 'node:path';
import http from 'node:http';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '../../..');
const output =
  process.env.NOBLE_LIBRARY_PREVIEW_OUTPUT ||
  path.join(root, '.context/library-viewer-preview/browser');
await fs.mkdir(output, { recursive: true });
const require = createRequire(path.join(here, 'package.json'));
const { build } = require('esbuild');
const { chromium } = createRequire(
  path.resolve(here, '../../ui-previews/package.json'),
)('playwright');
const fixture = path.join(output, 'fixture.jsx');
const safe = path.join(output, 'safe.jsx');
const text = path.join(output, 'text.jsx');
await fs.writeFile(
  safe,
  "export {View as SafeAreaView} from 'react-native';export const useSafeAreaInsets=()=>({top:0,bottom:0,left:0,right:0});",
);
// NativeWind isn't executed in this harness; viewer uses actual explicit source styles.
await fs.writeFile(text, "export {Text} from 'react-native';");
await fs.writeFile(
  fixture,
  `
import React,{createContext,useContext,useState} from 'react';
import {createRoot} from 'react-dom/client';
import {MemoriesScreen} from ${JSON.stringify(
    path.resolve(here, '../src/screens/MemoriesScreen.tsx'),
  )};
const Context=createContext(null);export const useLibrary=()=>useContext(Context);
const image='data:image/svg+xml,'+encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="400" height="400"><rect width="400" height="400" fill="#eee"/><circle cx="200" cy="200" r="100" fill="#555"/></svg>');
const item=(id,type,name)=>({mediaId:id,mediaUri:'content://synthetic/original/'+id,thumbnailUri:image,mediaType:type,displayName:name,capturedAt:Date.UTC(2026,10,6),durationMs:12000});
const initial=[item(1,'IMAGE','Photo1.jpg'),item(2,'VIDEO','Clip.mp4'),item(3,'IMAGE','Photo3.jpg')];
window.__routes=[];window.__pages=0;window.__reloads=0;
const route=async(kind,value)=>{window.__routes.push({kind,value});if(window.__hold){window.__hold=false;await new Promise(resolve=>{window.__finish=resolve;});}return {opened:true};};
export const honorableNative={openMedia:value=>route('open',value),shareMedia:value=>route('share',value),editPhoto:value=>route('photo',value),editVideo:value=>route('video',value)};
function App(){const[items,setItems]=useState(initial);return <Context.Provider value={{items,total:4,loading:false,indexing:false,error:'',reload:async()=>{window.__reloads++;},loadMore:async()=>{window.__pages++;setItems([...initial,item(4,'IMAGE','Photo4.jpg')]);}}}><MemoriesScreen/></Context.Provider>;}
createRoot(document.getElementById('root')).render(<App/>);
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
      name: 'synthetic-library-boundaries',
      setup(builder) {
        builder.onResolve(
          { filter: /(?:^|\/)LibraryContext$|(?:^|\/)HonorableNative$/ },
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
        builder.onResolve({ filter: /^@\/components\/ui\/text$/ }, () => ({
          path: text,
        }));
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
  for (const viewport of [
    { width: 320, height: 768 },
    { width: 390, height: 844 },
    { width: 844, height: 390 },
  ]) {
    const page = await browser.newPage({ viewport, reducedMotion: 'reduce' });
    const errors = [],
      external = [];
    page.on('pageerror', error => errors.push(error.message));
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
    page.setDefaultTimeout(5000);
    const button = name => page.getByRole('button', { name, exact: true });
    await page.addInitScript(() => {
      window.__alerts = [];
      window.addEventListener('noble-preview-error', event =>
        window.__alerts.push(event.detail),
      );
    });
    await page.goto(url);
    await page.getByLabel('image Photo1.jpg', { exact: true }).click();
    await button('Next library item').waitFor();
    assert(await button('Previous library item').isDisabled());
    await page.evaluate(() => {
      window.__hold = true;
    });
    await button('Open media').click();
    assert(await button('Next library item').isDisabled());
    await page.evaluate(() => window.__finish());
    await button('Next library item').click();
    await button('Play video').waitFor();
    await button('Play video').click();
    await button('Edit media').click();
    await button('Share media').click();
    await button('Next library item').click();
    assert(await button('Next library item').isDisabled());
    await button('Previous library item').click();
    assert(await page.getByText('2 of 3 loaded', { exact: true }).isVisible());
    assert(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    );
    for (const label of [
      'Close viewer',
      'Previous library item',
      'Next library item',
      'Open media',
      'Edit media',
      'Share media',
    ]) {
      const box = await button(label).boundingBox();
      assert(
        box &&
          box.x >= 0 &&
          box.y >= 0 &&
          box.x + box.width <= viewport.width + 1 &&
          box.y + box.height <= viewport.height + 1,
        `${viewport.width}: ${label} outside viewport ${JSON.stringify(box)}`,
      );
    }
    await page.screenshot({
      path: path.join(output, `viewer-${viewport.width}.png`),
    });
    assert.deepEqual(await page.evaluate(() => window.__routes), [
      {
        kind: 'open',
        value: {
          mediaUri: 'content://synthetic/original/1',
          mediaType: 'IMAGE',
          bestTimestampMs: undefined,
        },
      },
      {
        kind: 'open',
        value: {
          mediaUri: 'content://synthetic/original/2',
          mediaType: 'VIDEO',
          bestTimestampMs: undefined,
        },
      },
      { kind: 'video', value: 'content://synthetic/original/2' },
      {
        kind: 'share',
        value: {
          mediaUri: 'content://synthetic/original/2',
          mediaType: 'VIDEO',
        },
      },
    ]);
    await button('Close viewer').click();
    await page
      .getByRole('textbox', {
        name: 'Search filenames in your library',
        exact: true,
      })
      .fill('Photo3');
    await page.getByLabel('image Photo3.jpg', { exact: true }).click();
    assert(await page.getByText('1 of 1 loaded', { exact: true }).isVisible());
    assert(await button('Previous library item').isDisabled());
    assert(await button('Next library item').isDisabled());
    await button('Close viewer').click();
    await page
      .getByRole('textbox', {
        name: 'Search filenames in your library',
        exact: true,
      })
      .fill('Photo');
    await button('Load more memories').click();
    await page.getByLabel('image Photo4.jpg', { exact: true }).click();
    assert(await page.getByText('3 of 3 loaded', { exact: true }).isVisible());
    assert.equal(await page.evaluate(() => window.__pages), 1);
    assert.equal(await page.evaluate(() => window.__reloads), 0);
    assert.deepEqual(await page.evaluate(() => window.__alerts), []);
    assert.deepEqual(errors, []);
    assert.deepEqual(external, []);
    results.push({ viewport, errors, external, passed: true });
    await page.close();
  }
  await fs.writeFile(
    path.join(output, 'results.json'),
    JSON.stringify(results, null, 2) + '\n',
  );
  console.log(JSON.stringify({ output, results }));
} finally {
  if (browser) await browser.close();
  await new Promise(resolve => server.close(resolve));
}
