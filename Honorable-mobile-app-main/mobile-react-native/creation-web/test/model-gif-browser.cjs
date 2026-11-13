// Actual Three/Web Worker/GIF integration, using the public native-produced depth fixture only.
const { chromium } = require('../../../ui-previews/node_modules/playwright');
const { GifReader } = require('omggif');
const { createServer } = require('node:http'),
  fs = require('node:fs/promises'),
  path = require('node:path'),
  assert = require('node:assert/strict');
const root = path.resolve(__dirname, '../../../..'),
  output =
    process.env.NOBLE_GIF_OUTPUT ||
    path.join(root, '.context/noble-fast-gif/drawing'),
  evidence = path.join(root, '.context/noble-fast-gif');
(async () => {
  const mime = {
    '.html': 'text/html',
    '.js': 'text/javascript',
    '.css': 'text/css',
    '.woff2': 'font/woff2',
    '.json': 'application/json',
  };
  const server = createServer(async (req, res) => {
    try {
      let name = new URL(req.url, 'http://localhost').pathname.replace(
        /^\/drawing\//,
        '/',
      );
      if (name === '/') name = '/index.html';
      const file = path.resolve(output, '.' + name);
      if (!file.startsWith(output + path.sep)) {
        res.writeHead(403).end();
        return;
      }
      const data =
        name === '/config.js'
          ? 'window.EXCALIDRAW_ASSET_PATH="/drawing/";'
          : await fs.readFile(file);
      res.writeHead(200, {
        'Content-Type': mime[path.extname(file)] || 'application/octet-stream',
        'Content-Security-Policy':
          "default-src 'self'; script-src 'self' 'wasm-unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self' blob:; worker-src 'self' blob:; frame-src 'none'; object-src 'none'; base-uri 'none'",
      });
      res.end(data);
    } catch {
      res.writeHead(404).end();
    }
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const url = `http://127.0.0.1:${server.address().port}`;
  let browser;
  try {
    const incoming = (
      await fs.readFile(
        path.join(root, '.context/noble-main-depth/real-model-astronaut.glb'),
      )
    ).toString('base64');
    browser = await chromium.launch({
      headless: true,
      args: ['--no-sandbox', '--enable-unsafe-swiftshader'],
    });
    const page = await browser.newPage({
        viewport: { width: 390, height: 844 },
      }),
      errors = [],
      external = [];
    let downloads = 0;
    page.on('pageerror', error => errors.push(error.message));
    page.on('download', () => downloads++);
    await page.route('**/*', route => {
      const target = route.request().url();
      if (
        !target.startsWith(url) &&
        !target.startsWith('data:') &&
        !target.startsWith('blob:')
      ) {
        external.push(target);
        return route.abort();
      }
      return route.continue();
    });
    await page.addInitScript(
      ({ incoming }) => {
        window.HonorableDrawing = {
          getDepthModelLength: () => incoming.length,
          readDepthModelChunk: offset => incoming.slice(offset, offset + 32768),
          releaseDepthModel: () => {},
        };
      },
      { incoming },
    );
    await page.goto(url);
    await page
      .getByRole('button', { name: 'Open AI depth relief', exact: true })
      .click();
    const duration = page.getByLabel('Turntable seconds', { exact: true });
    await duration.focus();
    await duration.press('Home');
    assert.equal(await duration.inputValue(), '1');
    const share = page.getByRole('button', {
      name: 'Share GIF animation',
      exact: true,
    });
    await page.evaluate(() => {
      delete window.HonorableDrawing;
    });
    const canvas = page.locator(
      'canvas[aria-label="Interactive 3D model preview"]',
    );
    await share.waitFor();
    await canvas.screenshot();
    const baseline = await canvas.screenshot(),
      saved = await page.evaluate(() =>
        localStorage.getItem('noble-model-studio-v1'),
      );
    const downloadWait = page.waitForEvent('download'),
      start = Date.now();
    await share.click();
    const download = await downloadWait,
      bytes = await fs.readFile(await download.path()),
      reader = new GifReader(bytes);
    const firstExportElapsedMs=Date.now()-start;
    assert.equal(reader.numFrames(), 10);
    assert.equal(reader.loopCount(), 0);
    assert(reader.width <= 384 && reader.height <= 384);
    for (let i = 0; i < 10; i++) assert.equal(reader.frameInfo(i).delay, 10);
    const first = new Uint8Array(reader.width * reader.height * 4),
      moved = new Uint8Array(first.length);
    reader.decodeAndBlitFrameRGBA(0, first);
    reader.decodeAndBlitFrameRGBA(2, moved);
    assert(
      !Buffer.from(first).equals(Buffer.from(moved)),
      'Actual parallax frames must move',
    );
    await page.getByText('GIF download requested.', { exact: false }).waitFor();
    assert(
      (await canvas.screenshot()).equals(baseline),
      'GIF capture must restore preview pose',
    );
    assert.equal(
      await page.evaluate(() => localStorage.getItem('noble-model-studio-v1')),
      saved,
    );
    await fs.writeFile(
      path.join(evidence, 'public-depth-animation.gif'),
      bytes,
    );
    // Stop after two real frames, holding a subsequent worker message deterministically.
    await page.evaluate(() => {
      const Original = window.Worker;
      window.__gifTerminated = 0;
      window.Worker = function (...args) {
        const worker = new Original(...args),
          send = worker.postMessage.bind(worker),
          terminate = worker.terminate.bind(worker);
        worker.postMessage = (message, transfer) => {
          if (
            window.__holdGif &&
            message.type === 'frame' &&
            message.number >= 2
          ) {
            window.__gifHeld = true;
            return;
          }
          send(message, transfer);
        };
        worker.terminate = () => {
          window.__gifTerminated++;
          return terminate();
        };
        return worker;
      };
      window.__holdGif = true;
    });
    await duration.focus();
    await duration.press('End');
    await duration.press('ArrowLeft');
    await duration.press('ArrowLeft');
    await duration.press('ArrowLeft');
    await duration.press('ArrowLeft');
    await duration.press('ArrowLeft');
    await duration.press('ArrowLeft');
    assert.equal(await duration.inputValue(), '6');
    const stoppedBaseline = await canvas.screenshot(),
      priorDownloads = downloads;
    await share.click();
    await page.waitForFunction(() => window.__gifHeld === true);
    await page
      .getByRole('button', { name: 'Stop GIF export', exact: true })
      .click();
    await page.getByText('GIF export stopped.', { exact: false }).waitFor();
    assert(await page.evaluate(() => window.__gifTerminated > 0));
    assert.equal(downloads, priorDownloads);
    assert(
      (await canvas.screenshot()).equals(stoppedBaseline),
      'Stopped GIF must restore preview',
    );
    // Native transfer uses actual GIF bytes and only an explicit share callback clears its lease.
    await duration.focus();
    await duration.press('Home');
    await page.evaluate(() => {
      window.__holdGif = false;
      window.__gifChunks = [];
      window.HonorableDrawing = {
        beginGifShare: (id, size) => {
          window.__gifId = id;
          window.__gifSize = size;
          return true;
        },
        appendGifShareChunk: (_, value) => {
          window.__gifChunks.push(value);
          return true;
        },
        finishGifShare: id =>
          window.honorableGifShareResult({ requestId: id, opened: true }),
        releaseGifShare: () => {
          window.__gifReleased = true;
        },
      };
    });
    await share.click();
    await page
      .getByText('Android share sheet opened.', { exact: false })
      .waitFor();
    const native = await page.evaluate(() => ({
        size: window.__gifSize,
        chunks: window.__gifChunks,
      })),
      nativeBytes = Buffer.concat(
        native.chunks.map(c => Buffer.from(c, 'base64')),
      );
    assert.equal(nativeBytes.length, native.size);
    assert.equal(new GifReader(nativeBytes).numFrames(), 10);
    assert.deepEqual(errors, []);
    assert.deepEqual(external, []);
    const receipt = {
      fixture: 'Public NASA native DepthAnything output',
      browserOnly: true,
      width: reader.width,
      height: reader.height,
      frames: reader.numFrames(),
      bytes: bytes.length,
      firstExportElapsedMs,
      checks: [
        'actual worker encoding',
        'independent GIF decode/motion/loop/timing',
        'pose restored',
        'saved model unchanged',
        'Stop terminates worker with no download',
        'actual GIF native chunk protocol',
      ],
      pageErrors: errors,
      externalRequests: external,
      deviceTested: false,
    };
    await fs.writeFile(
      path.join(evidence, 'browser-receipt.json'),
      JSON.stringify(receipt, null, 2),
    );
    console.log('PASS GIF browser workflow, Stop, and native chunk protocol');
  } finally {
    if (browser) await browser.close();
    await new Promise(resolve => server.close(resolve));
  }
})().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
