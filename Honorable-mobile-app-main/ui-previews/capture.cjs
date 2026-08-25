const fs = require('fs');
const os = require('os');
const path = require('path');
const { spawnSync } = require('child_process');

const allIds = [
  'ios-01-home',
  '01-home',
  '02-memories',
  '03-search-focus',
  '04-ai-searching',
  '05-search-results',
  '06-video-results',
  '07-media-viewer',
  '08-terms-ai',
  '09-terms-result',
  '10-activity',
  '11-settings',
  '12-privacy',
  '13-honorable-plus',
  '14-indexing',
  '15-empty-search',
  '16-dark-home',
  '17-dark-memories',
  '18-dark-terms-ai',
  '19-error-state',
];
const verifyOnly = process.argv.includes('--verify');
const requestedIds = process.argv.slice(2).filter((id) => id !== '--verify');
const unknownIds = requestedIds.filter((id) => !allIds.includes(id));
if (unknownIds.length) throw new Error(`Unknown preview id: ${unknownIds.join(', ')}`);
const ids = requestedIds.length ? requestedIds : allIds;

const root = __dirname;

function validateConnections() {
  const previewData = fs.readFileSync(path.join(root, 'preview-data.js'), 'utf8');
  const renderer = fs.readFileSync(path.join(root, 'render.js'), 'utf8');
  const gallery = fs.readFileSync(path.join(root, 'index.html'), 'utf8');
  const renderPage = fs.readFileSync(path.join(root, 'render.html'), 'utf8');
  const manifestIds = [...previewData.matchAll(/\['([^']+)','[^']+','[^']+','(?:IMPLEMENTED|PARTIAL)'\]/g)].map((match) => match[1]);

  if (JSON.stringify(manifestIds) !== JSON.stringify(allIds)) {
    throw new Error(`Preview manifest is disconnected. Capture: ${allIds.join(', ')}; manifest: ${manifestIds.join(', ')}`);
  }
  for (const id of allIds) {
    if (!renderer.includes(`'${id}':`)) throw new Error(`Renderer is missing ${id}`);
  }
  if (!gallery.includes('src="preview-data.js"')) throw new Error('Gallery is not connected to preview-data.js');
  if (!renderPage.includes('src="render.js"')) throw new Error('Render page is not connected to render.js');
  if (!renderPage.includes('href="render.css"') || !renderPage.includes('href="render-overrides.css"')) {
    throw new Error('Render page styles are disconnected');
  }
  const rootPngIds = fs.readdirSync(root)
    .filter((name) => name.endsWith('.png'))
    .map((name) => name.slice(0, -4))
    .sort();
  const expectedPngIds = [...allIds].sort();
  if (JSON.stringify(rootPngIds) !== JSON.stringify(expectedPngIds)) {
    throw new Error(`Preview folder contains stale or missing PNGs: ${rootPngIds.join(', ')}`);
  }
  for (const id of allIds) {
    const header = fs.readFileSync(path.join(root, `${id}.png`)).subarray(0, 24);
    if (header.readUInt32BE(16) !== 432 || header.readUInt32BE(20) !== 936) {
      throw new Error(`${id}.png is not a 432x936 device canvas`);
    }
  }
  console.log(`CONNECTIONS: PASS (${allIds.length} previews)`);
}

async function captureWithPlaywright(chromium) {
  const browser = await chromium.launch({ headless: true });
  try {
    const page = await browser.newPage({ viewport: { width: 432, height: 936 }, deviceScaleFactor: 1 });
    for (const id of ids) {
      await page.goto(`file://${path.join(root, 'render.html')}?screen=${id}`);
      await page.waitForTimeout(250);
      await page.screenshot({ path: path.join(root, `${id}.png`) });
      console.log(`CAPTURED: ${id}`);
    }
  } finally {
    await browser.close();
  }
}

function findCachedChromium() {
  const explicit = process.env.HONORABLE_CHROMIUM;
  if (explicit && fs.existsSync(explicit)) return explicit;

  const cache = path.join(os.homedir(), '.cache', 'ms-playwright');
  if (fs.existsSync(cache)) {
    const candidates = fs.readdirSync(cache)
      .filter((name) => name.startsWith('chromium-'))
      .sort()
      .reverse()
      .map((name) => path.join(cache, name, 'chrome-linux', 'chrome'));
    const cached = candidates.find((candidate) => fs.existsSync(candidate));
    if (cached) return cached;
  }

  for (const command of ['chromium', 'chromium-browser', 'google-chrome']) {
    const found = spawnSync('which', [command], { encoding: 'utf8' });
    if (found.status === 0 && found.stdout.trim()) return found.stdout.trim();
  }
  return null;
}

function captureWithChromium(executable) {
  const profiles = fs.mkdtempSync(path.join(os.tmpdir(), 'honorable-previews-'));
  try {
    for (const id of ids) {
      const output = path.join(root, `${id}.png`);
      const rawOutput = path.join(profiles, `${id}-raw.png`);
      const result = spawnSync(executable, [
        '--headless=new',
        '--no-sandbox',
        '--disable-dev-shm-usage',
        '--disable-background-networking',
        '--disable-component-update',
        '--disable-default-apps',
        '--disable-sync',
        '--metrics-recording-only',
        '--no-first-run',
        '--no-service-autorun',
        '--password-store=basic',
        '--use-mock-keychain',
        '--disable-breakpad',
        '--disable-features=OptimizationHints,MediaRouter,AutofillServerCommunication,CertificateTransparencyComponentUpdater,Translate,Crashpad,PaintHolding',
        '--hide-scrollbars',
        '--disable-gpu',
        '--disable-logging',
        '--log-level=3',
        `--user-data-dir=${path.join(profiles, `${id}-profile`)}`,
        '--window-size=432,1023',
        `--screenshot=${rawOutput}`,
        `file://${path.join(root, 'render.html')}?screen=${id}`,
      ], { encoding: 'utf8', timeout: 30000 });

      if (result.status !== 0 || !fs.existsSync(rawOutput)) {
        const detail = result.error?.message || result.stderr.trim() || `exit ${result.status}`;
        throw new Error(`Capture failed for ${id}: ${detail}`);
      }

      const crop = spawnSync('ffmpeg', [
        '-hide_banner', '-loglevel', 'error', '-y',
        '-i', rawOutput,
        '-vf', 'crop=432:936:0:0',
        '-frames:v', '1', output,
      ], { encoding: 'utf8', timeout: 30000 });
      if (crop.status !== 0 || !fs.existsSync(output)) {
        const detail = crop.error?.message || crop.stderr.trim() || `exit ${crop.status}`;
        throw new Error(`Crop failed for ${id}: ${detail}. Install ffmpeg or Playwright for exact-size captures.`);
      }
      console.log(`CAPTURED: ${id}`);
    }
  } finally {
    fs.rmSync(profiles, { recursive: true, force: true });
  }
}

try {
  validateConnections();
  if (!verifyOnly) {
    (async () => {
      try {
        const { chromium } = require('playwright');
        await captureWithPlaywright(chromium);
      } catch (error) {
        if (error.code !== 'MODULE_NOT_FOUND') throw error;
        const executable = findCachedChromium();
        if (!executable) {
          throw new Error('Playwright is unavailable and no Chromium executable was found. Set HONORABLE_CHROMIUM to a browser path.');
        }
        captureWithChromium(executable);
      }
    })().catch((error) => {
      console.error(error.message);
      process.exitCode = 1;
    });
  }
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}
