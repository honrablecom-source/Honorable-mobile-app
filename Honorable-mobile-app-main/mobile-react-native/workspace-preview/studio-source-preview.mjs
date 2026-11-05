// Actual StudioScreen rendered through RN Web with synthetic boundaries. Never shipped in the app.
import fs from 'node:fs/promises';
import path from 'node:path';
import http from 'node:http';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '../../..');
const output =
  process.env.NOBLE_STUDIO_PREVIEW_OUTPUT ||
  path.join(root, '.context/studio-source-browser/browser');
await fs.mkdir(output, { recursive: true });
const setupMode = process.env.NOBLE_STUDIO_GATE_PREVIEW === '1';
const require = createRequire(path.join(here, 'package.json'));
const { build } = require('esbuild');
const { chromium } = createRequire(
  path.resolve(here, '../../ui-previews/package.json'),
)('playwright');
const fixture = path.join(output, 'fixture.jsx');
await fs.writeFile(
  fixture,
  `
import React,{createContext,useContext,useState} from 'react';
import {createRoot} from 'react-dom/client';
import {StudioScreen} from ${JSON.stringify(
    path.resolve(here, '../src/screens/StudioScreen.tsx'),
  )};
import {LibraryGate} from ${JSON.stringify(
    path.resolve(here, '../src/library/LibraryGate.tsx'),
  )};
const setupMode=${JSON.stringify(setupMode)};
const Context=createContext(null);
const image='data:image/svg+xml,'+encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="200" height="150"><rect width="200" height="150" fill="#eee"/><circle cx="100" cy="75" r="30" fill="#555"/></svg>');
const item=(id,type,name)=>({mediaId:id,mediaUri:'content://synthetic/'+id,thumbnailUri:image,mediaType:type,displayName:name,capturedAt:0,durationMs:1000});
const initial=[...Array.from({length:25},(_,i)=>item(i+1,'IMAGE','Source '+i+'.jpg')),item(30,'VIDEO','First clip.mp4'),item(31,'VIDEO','Second clip.mp4')];
window.__routes=[];window.__pageCalls=0;
export const useLibrary=()=>useContext(Context);
const route=(kind,value)=>{window.__routes.push({kind,value});return Promise.resolve({opened:true});};
export const editMemory=value=>route('edit',value);
export const useOpenWorkspace=()=>({busy:false,open:kind=>route('picker',kind),create:value=>route('canvas',value),draw:()=>route('draw'),photoVideo:()=>route('sequence'),projects:()=>route('projects'),videoProjects:()=>route('videoProjects')});
function App(){const[items,setItems]=useState(initial);const loadMore=async()=>{window.__pageCalls++;await new Promise(r=>setTimeout(r,200));if(window.__failNext){window.__failNext=false;throw Error('Synthetic page failure');}setItems([...initial,item(50,'IMAGE','Later photo.jpg'),item(51,'VIDEO','Later video.mp4')]);};return <Context.Provider value={{items:setupMode?[]:items,total:setupMode?0:29,loading:false,indexing:false,status:{engine:'REAL',permissionGranted:!setupMode,indexedCount:setupMode?0:29,status:'READY'},requestAccess:()=>route('permission'),reindex:()=>route('index'),error:'',loadMore}}>{setupMode?<LibraryGate><div data-testid='search-shell'>Search shell fixture</div></LibraryGate>:<StudioScreen/>}</Context.Provider>;}
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
  define: { 'process.env.NODE_ENV': '"production"' },
  plugins: [
    {
      name: 'synthetic-studio-boundaries',
      setup(builder) {
        builder.onResolve(
          {
            filter:
            /(?:^|\/)LibraryContext$|\/native\/openWorkspace$|\/native\/editMemory$/,
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
          () => ({ path: path.join(here, 'safe-area.jsx') }),
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
      '<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><style>html,body,#root{margin:0;height:100%;width:100%;}#root{display:flex}</style></head><body><div id="root"></div><script src="/app.js"></script></body></html>',
    );
  } else if (req.url === '/app.js') {
    res.writeHead(200, { 'Content-Type': 'text/javascript' });
    res.end(await fs.readFile(path.join(output, 'app.js')));
  } else res.writeHead(404).end();
});
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
const url = `http://127.0.0.1:${server.address().port}/`;
let browser, page;
try {
  browser = await chromium.launch({ args: ['--no-sandbox'] });
  page = await browser.newPage({ viewport: { width: 390, height: 844 } });
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
  if (setupMode) {
    for (const width of [320, 390, 844]) {
      await page.setViewportSize({ width, height: 844 });
      const entry = page.getByRole('button', {
        name: 'Open Studio without library setup',
        exact: true,
      });
      await entry.click();
      const back = page.getByRole('button', {
        name: 'Back to library setup',
        exact: true,
      });
      await back.waitFor();
      assert.equal(await page.getByTestId('search-shell').count(), 0);
      assert(
        await page.evaluate(
          () => document.documentElement.scrollWidth <= innerWidth,
        ),
      );
      await page.screenshot({ path: path.join(output, `studio-${width}.png`) });
      if (width === 390) {
        for (const label of [
          'Design with photos & text',
          'Create a blank square image',
          'Turn photos into a video',
          'Saved image projects',
          'Saved photo videos',
        ])
          await page.getByRole('button', { name: label, exact: true }).click();
        assert.deepEqual(
          await page.evaluate(() => window.__routes.map(route => route.kind)),
          ['draw', 'canvas', 'sequence', 'projects', 'videoProjects'],
        );
      }
      await back.click();
      await entry.waitFor();
      assert.equal(await page.getByTestId('search-shell').count(), 0);
      assert(
        await page.evaluate(
          () => document.documentElement.scrollWidth <= innerWidth,
        ),
      );
      await page.screenshot({ path: path.join(output, `setup-${width}.png`) });
    }
    assert.deepEqual(errors, []);
    assert.deepEqual(external, []);
    await fs.writeFile(
      path.join(output, 'review.json'),
      JSON.stringify(
        {
          actualComponents: ['LibraryGate', 'StudioScreen'],
          setupToStudioAndBack: true,
          searchRemainsGated: true,
          existingCreationCallbacks: [
            'draw',
            'canvas',
            'sequence',
            'projects',
            'videoProjects',
          ],
          permissionOrIndexRequested: false,
          viewportWidths: [320, 390, 844],
          pageErrors: errors,
          externalRequests: external,
          scope:
            'Actual RNWeb components with synthetic boundaries;not Android/device behavior',
        },
        null,
        2,
      ),
    );
    console.log(
      'PASS: actual Gate opens existing Studio creators and saved projects without permission/index requests, returns to gated setup;320/390/844 widths;no page/external errors. RNWeb only.',
    );
  } else {
    const edit = name =>
      page.getByRole('button', { name: `Edit ${name}`, exact: true });
    const filter = page.getByRole('textbox', {
      name: 'Filter loaded source filenames',
      exact: true,
    });
    await filter.waitFor();
    assert.equal(await edit('Source 24.jpg').count(), 0);
    await page
      .getByRole('button', { name: 'Next loaded sources', exact: true })
      .click();
    assert.equal(await edit('Source 0.jpg').count(), 0);
    assert.equal(
      await page.getByRole('button', { name: /^Edit Source / }).count(),
      12,
    );
    await page
      .getByRole('button', { name: 'Next loaded sources', exact: true })
      .click();
    await edit('Source 24.jpg').click();
    assert.deepEqual(await page.evaluate(() => window.__routes.at(-1)), {
      kind: 'edit',
      value: { id: '25', mediaUri: 'content://synthetic/25', type: 'IMAGE' },
    });
    for (const width of [320, 390, 844]) {
      await page.setViewportSize({ width, height: 844 });
      await page
        .getByRole('button', { name: 'Previous loaded sources', exact: true })
        .scrollIntoViewIfNeeded();
      assert(
        await page.evaluate(
          () => document.documentElement.scrollWidth <= innerWidth,
        ),
      );
      await page.screenshot({ path: path.join(output, `paging-${width}.png`) });
    }
    await page.setViewportSize({ width: 390, height: 844 });
    await page
      .getByRole('button', { name: 'Previous loaded sources', exact: true })
      .click();
    await page
      .getByRole('button', { name: 'Previous loaded sources', exact: true })
      .click();
    await edit('Source 0.jpg').waitFor();
    assert.equal(await edit('Source 24.jpg').count(), 0);
    await filter.fill('  SOURCE 24  ');
    await edit('Source 24.jpg').waitFor();
    assert.equal(await edit('Source 0.jpg').count(), 0);
    await page
      .getByRole('button', { name: 'Clear source filter', exact: true })
      .click();
    assert.equal(await edit('Source 24.jpg').count(), 0);
    await page
      .getByRole('tab', { name: 'Video workspace', exact: true })
      .click();
    await filter.fill('Later');
    assert.equal(await edit('First clip.mp4').count(), 0);
    await page.evaluate(() => {
      window.__failNext = true;
    });
    await page
      .getByRole('button', { name: 'Load more library sources', exact: true })
      .click();
    await page
      .getByRole('alert')
      .filter({ hasText: 'Synthetic page failure' })
      .waitFor();
    await page
      .getByRole('button', { name: 'Load more library sources', exact: true })
      .click();
    await edit('Later video.mp4').waitFor();
    assert.equal(await page.evaluate(() => window.__pageCalls), 2);
    assert.equal(await filter.inputValue(), 'Later');
    assert.equal(await edit('Later photo.jpg').count(), 0);
    await edit('Later video.mp4').click();
    assert.deepEqual(await page.evaluate(() => window.__routes.at(-1)), {
      kind: 'edit',
      value: { id: '51', mediaUri: 'content://synthetic/51', type: 'VIDEO' },
    });
    await filter.fill('Not present');
    await page
      .getByRole('button', { name: 'Import another source', exact: true })
      .click();
    assert.deepEqual(await page.evaluate(() => window.__routes.at(-1)), {
      kind: 'picker',
      value: 'VIDEO',
    });
    for (const width of [320, 390, 844]) {
      await page.setViewportSize({ width, height: 844 });
      await filter.scrollIntoViewIfNeeded();
      assert(
        await page.evaluate(
          () => document.documentElement.scrollWidth <= innerWidth,
        ),
      );
      await page.screenshot({
        path: path.join(output, `sources-${width}.png`),
      });
    }
    assert.deepEqual(errors, []);
    assert.deepEqual(external, []);
    await fs.writeFile(
      path.join(output, 'review.json'),
      JSON.stringify(
        {
          actualComponent: 'src/screens/StudioScreen.tsx',
          boundedLoadedBrowsing: true,
          localFilenameFilter: true,
          clearResetsPage: true,
          pageFailureRetry: true,
          modeAndQueryPreserved: true,
          exactSelectedUris: true,
          directPickerWithoutMatch: true,
          viewportWidths: [320, 390, 844],
          pageErrors: errors,
          externalRequests: external,
          scope:
            'React Native Web with synthetic native/library mocks;not Android/device acceptance',
        },
        null,
        2,
      ),
    );
    console.log(
      'PASS: actual StudioScreen loaded browsing, local filename filter, clear, page failure/retry, mode/query preservation, exact source URI, no-match picker and phone widths; no page/external errors. RN Web only.',
    );
  }
} catch (error) {
  if (page) await page.screenshot({ path: path.join(output, 'failure.png') });
  throw error;
} finally {
  if (browser) await browser.close();
  await new Promise(resolve => server.close(resolve));
}
