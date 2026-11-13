// Programmatic Chromium test with synthetic textures only. Not a device test.
const { chromium } = require('../../../ui-previews/node_modules/playwright');
const { createServer } = require('node:http');
const fs = require('node:fs/promises'),
  path = require('node:path'),
  assert = require('node:assert/strict');
const root = path.resolve(__dirname, '../../../..');
const output =
  process.env.NOBLE_MODEL_OUTPUT ||
  path.join(root, '.context/noble-fast-release/drawing');
const evidence =
  process.env.NOBLE_MODEL_EVIDENCE ||
  path.join(root, '.context/noble-fast-release');
async function depthFixture(png) {
  const THREE = await import('../node_modules/three/build/three.module.js');
  const { exportModel } = await import('../model-studio.mjs');
  globalThis.FileReader = class {
    readAsArrayBuffer(blob) {
      blob.arrayBuffer().then(value => {
        this.result = value;
        this.onloadend?.();
      });
    }
  };
  const scene = new THREE.Group(),
    geometry = new THREE.PlaneGeometry(2, 1.5, 64, 64),
    positions = geometry.attributes.position;
  for (let i = 0; i < positions.count; i++)
    positions.setZ(
      i,
      Math.sin(positions.getX(i) * 3) * Math.cos(positions.getY(i) * 3) * 0.1,
    );
  geometry.computeVertexNormals();
  scene.add(
    new THREE.Mesh(
      geometry,
      new THREE.MeshStandardMaterial({ color: '#ffffff' }),
    ),
  );
  const bytes = await exportModel(scene, []),
    length = new DataView(bytes.buffer).getUint32(12, true);
  const json = JSON.parse(
      new TextDecoder().decode(bytes.subarray(20, 20 + length)).trim(),
    ),
    binary = bytes.subarray(28 + length);
  const image = Buffer.from(png, 'base64'),
    imagePadding = (4 - (image.length % 4)) % 4;
  json.bufferViews.push({
    buffer: 0,
    byteOffset: binary.length,
    byteLength: image.length,
  });
  json.buffers[0].byteLength = binary.length + image.length + imagePadding;
  json.images = [
    { bufferView: json.bufferViews.length - 1, mimeType: 'image/png' },
  ];
  json.textures = [{ source: 0 }];
  json.materials[0].pbrMetallicRoughness.baseColorTexture = { index: 0 };
  json.materials[0].doubleSided = true;
  const text = Buffer.from(JSON.stringify(json)),
    padding = (4 - (text.length % 4)) % 4,
    out = Buffer.alloc(28 + text.length + padding + json.buffers[0].byteLength);
  out.writeUInt32LE(0x46546c67, 0);
  out.writeUInt32LE(2, 4);
  out.writeUInt32LE(out.length, 8);
  out.writeUInt32LE(text.length + padding, 12);
  out.writeUInt32LE(0x4e4f534a, 16);
  text.copy(out, 20);
  out.fill(32, 20 + text.length, 20 + text.length + padding);
  const bin = 20 + text.length + padding;
  out.writeUInt32LE(json.buffers[0].byteLength, bin);
  out.writeUInt32LE(0x004e4942, bin + 4);
  Buffer.from(binary).copy(out, bin + 8);
  image.copy(out, bin + 8 + binary.length);
  geometry.dispose();
  return out.toString('base64');
}
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
    browser = await chromium.launch({
      headless: true,
      args: ['--no-sandbox', '--enable-unsafe-swiftshader'],
    });
    const page = await browser.newPage({
      viewport: { width: 390, height: 844 },
      deviceScaleFactor: 1,
    });
    const errors = [],
      external = [];
    page.on('pageerror', error => errors.push(error.message));
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
    await page.goto(url);
    await page.getByRole('button', { name: 'Model', exact: true }).click();
    await page
      .getByRole('button', { name: 'Share GLB', exact: true })
      .waitFor();
    const share = page.getByRole('button', {
      name: 'Share GLB',
      exact: true,
    });
    await require('node:timers/promises').setTimeout(100);
    await page.waitForFunction(
      () =>
        !Array.from(document.querySelectorAll('button')).find(
          b => b.textContent === 'Share GLB',
        ).disabled,
    );
    await page.getByLabel('Model shape', { exact: true }).selectOption('torus');
    await page
      .getByRole('checkbox', {
        name: 'Include turntable animation',
        exact: true,
      })
      .check();
    await page.getByRole('button', { name: 'Save model', exact: true }).click();
    const saved = await page.evaluate(() =>
      JSON.parse(localStorage.getItem('noble-model-studio-v1')),
    );
    assert.equal(saved.shape, 'torus');
    assert.equal(saved.animate, true);
    await page.getByLabel('Model shape', { exact: true }).selectOption('cone');
    await page
      .getByRole('button', { name: 'Undo model edit', exact: true })
      .click();
    assert.equal(
      await page.getByLabel('Model shape', { exact: true }).inputValue(),
      'torus',
    );
    const texture = await page.evaluate(() => {
      const c = document.createElement('canvas');
      c.width = 16;
      c.height = 16;
      const x = c.getContext('2d');
      x.fillStyle = '#ee6655';
      x.fillRect(0, 0, 16, 16);
      x.fillStyle = '#336699';
      x.fillRect(0, 0, 8, 8);
      return c.toDataURL('image/png').split(',')[1];
    });
    await page
      .getByLabel('Model texture photo', { exact: true })
      .setInputFiles({
        name: 'synthetic.png',
        mimeType: 'image/png',
        buffer: Buffer.from(texture, 'base64'),
      });
    await page
      .getByRole('button', { name: 'Remove texture', exact: true })
      .waitFor();
    await page.getByRole('button', { name: 'Save model', exact: true }).click();
    await page.reload();
    await page.getByRole('button', { name: 'Model', exact: true }).click();
    assert.equal(
      await page.getByLabel('Model shape', { exact: true }).inputValue(),
      'torus',
    );
    assert(
      await page
        .getByRole('checkbox', {
          name: 'Include turntable animation',
          exact: true,
        })
        .isChecked(),
    );
    await page
      .getByRole('button', { name: 'Remove texture', exact: true })
      .waitFor();
    const downloadWait = page.waitForEvent('download');
    await share.click();
    const download = await downloadWait;
    const bytes = await fs.readFile(await download.path());
    assert.equal(bytes.readUInt32LE(0), 0x46546c67);
    assert.equal(bytes.readUInt32LE(8), bytes.length);
    const json = JSON.parse(
      bytes
        .subarray(20, 20 + bytes.readUInt32LE(12))
        .toString()
        .trim(),
    );
    assert.equal(json.animations.length, 1);
    assert.equal(json.images.length, 1);
    assert.equal(json.images[0].uri, undefined);
    assert(Number.isInteger(json.images[0].bufferView));
    assert.equal(json.buffers[0].uri, undefined);
    await page
      .getByRole('button', { name: 'Play turntable', exact: true })
      .click();
    await page
      .getByRole('button', { name: 'Pause turntable', exact: true })
      .waitFor();
    await page
      .getByRole('button', { name: 'Pause turntable', exact: true })
      .click();
    assert(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
      '390px horizontal overflow',
    );
    await fs.mkdir(evidence, { recursive: true });
    await page.screenshot({ path: path.join(evidence, 'model-phone.png') });
    await page.setViewportSize({ width: 320, height: 844 });
    assert(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
      '320px horizontal overflow',
    );
    await page.setViewportSize({ width: 390, height: 844 });
    // Exercise the actual JS chunk protocol through a synthetic native bridge.
    await page.evaluate(() => {
      window.__modelChunks = [];
      window.HonorableDrawing = {
        beginModelShare: (id, size) => {
          window.__modelId = id;
          window.__modelSize = size;
          return true;
        },
        appendModelShareChunk: (_, value) => {
          window.__modelChunks.push(value);
          return true;
        },
        finishModelShare: id =>
          window.honorableModelShareResult({ requestId: id, opened: true }),
        releaseModelShare: () => {
          window.__released = true;
        },
      };
    });
    await share.click();
    await page
      .getByText('Android share sheet opened.', { exact: false })
      .waitFor();
    const native = await page.evaluate(() => ({
      size: window.__modelSize,
      chunks: window.__modelChunks,
    }));
    assert.equal(
      Buffer.concat(native.chunks.map(v => Buffer.from(v, 'base64'))).length,
      native.size,
    );
    await page
      .getByRole('button', { name: 'Add preview to drawing', exact: true })
      .click();
    await page.waitForFunction(() =>
      JSON.parse(
        localStorage.getItem('honorable-drawing-v1') || '{}',
      ).elements?.some(e => e.type === 'image'),
    );
    await page.getByRole('button', { name: 'Save draft', exact: true }).click();
    const scene = await page.evaluate(() =>
      JSON.parse(localStorage.getItem('honorable-drawing-v1')),
    );
    assert.equal(
      scene.elements.filter(e => e.type === 'image' && !e.isDeleted).length,
      1,
    );
    await page.getByRole('button', { name: 'Model', exact: true }).click();
    await page
      .getByLabel('Drawing photo texture', { exact: true })
      .selectOption({ label: 'Photo 1' });
    await page
      .getByRole('button', {
        name: 'Use drawing photo as texture',
        exact: true,
      })
      .click();
    await page
      .getByText('Local photo texture added.', { exact: false })
      .waitFor()
      .catch(async error => {
        console.error(await page.locator('[role=status]').allTextContents());
        throw error;
      });
    // A synthetic same-format native depth handoff exercises real GLTFLoader image decoding.
    // This validates integration, not neural inference or depth quality.
    const incoming = process.env.NOBLE_DEPTH_FIXTURE?(await fs.readFile(process.env.NOBLE_DEPTH_FIXTURE)).toString('base64'):await depthFixture(texture),
      depthPage = await browser.newPage({
        viewport: { width: 390, height: 844 },
      });
    depthPage.on('pageerror', error => errors.push(error.message));
    await depthPage.route('**/*', route => {
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
    await depthPage.addInitScript(
      ({ incoming }) => {
        localStorage.setItem(
          'noble-model-studio-v1',
          JSON.stringify({
            version: 1,
            shape: 'cone',
            width: 1.5,
            height: 1.5,
            depth: 1.5,
            color: '#999999',
            roughness: 0.6,
            metalness: 0,
            duration: 4,
            animate: false,
            texture: null,
            asset: null,
          }),
        );
        window.HonorableDrawing = {
          getDepthModelLength: () => incoming.length,
          readDepthModelChunk: offset => incoming.slice(offset, offset + 32768),
          releaseDepthModel: () => {
            window.__depthReleased = true;
          },
        };
      },
      { incoming },
    );
    await depthPage.goto(url);
    await depthPage
      .getByRole('button', { name: 'Open AI depth relief', exact: true })
      .waitFor();
    assert.equal(
      await depthPage.getByLabel('Model shape', { exact: true }).inputValue(),
      'cone',
    );
    assert(await depthPage.evaluate(() => window.__depthReleased));
    await depthPage
      .getByRole('button', { name: 'Open AI depth relief', exact: true })
      .click();
    assert.equal(
      await depthPage.getByLabel('Model shape', { exact: true }).inputValue(),
      'depth',
    );
    const play = depthPage.getByRole('button', {
      name: 'Play parallax',
      exact: true,
    });
    await play.click();
    await depthPage
      .getByRole('button', { name: 'Pause parallax', exact: true })
      .click();
    await depthPage.getByLabel('Model depth', { exact: true }).focus();
    await depthPage.keyboard.press('ArrowRight');
    await depthPage
      .getByRole('button', { name: 'Save model', exact: true })
      .click();
    const savedDepth = await depthPage.evaluate(() =>
      JSON.parse(localStorage.getItem('noble-model-studio-v1')),
    );
    assert.equal(savedDepth.asset, incoming);
    assert.equal(savedDepth.shape, 'depth');
    assert(savedDepth.depth > 1);
    // Use browser download for actual re-export; the preceding bridge has no share capability.
    await depthPage.evaluate(() => {
      delete window.HonorableDrawing;
    });
    const depthDownload = depthPage.waitForEvent('download');
    await depthPage
      .getByRole('button', { name: 'Share GLB', exact: true })
      .click();
    const depthBytes = await fs.readFile(await (await depthDownload).path());
    const depthJson = JSON.parse(
      depthBytes
        .subarray(20, 20 + depthBytes.readUInt32LE(12))
        .toString()
        .trim(),
    );
    assert.equal(depthJson.animations[0].name, 'Depth parallax');
    assert.equal(depthJson.images.length, 1);
    assert.equal(depthJson.images[0].uri, undefined);
    assert(
      depthJson.nodes.some(
        node => node.name === 'DepthSurface' && node.scale[2] > 1,
      ),
    );
    await depthPage
      .locator('canvas[aria-label="Interactive 3D model preview"]')
      .screenshot({ path: path.join(evidence, 'depth-model-canvas.png') });
    await depthPage.screenshot({
      path: path.join(evidence, 'depth-model-phone.png'),
    });
    const depthCanvas=depthPage.locator('canvas[aria-label="Interactive 3D model preview"]');
    await depthCanvas.scrollIntoViewIfNeeded();const box=await depthCanvas.boundingBox(),beforeOrbit=await depthCanvas.screenshot();
    await depthPage.mouse.move(box.x+box.width/2,box.y+box.height/2);await depthPage.mouse.down();await depthPage.mouse.move(box.x+box.width/2+65,box.y+box.height/2+15,{steps:12});await depthPage.mouse.up();
    await depthPage.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
    const afterOrbit=await depthCanvas.screenshot({path:path.join(evidence,'depth-model-orbit.png')});assert(!beforeOrbit.equals(afterOrbit),'Orbit must change the rendered depth model');
    await depthPage.close();
    assert.deepEqual(errors, []);
    assert.deepEqual(external, []);
    await fs.writeFile(
      path.join(evidence, 'model-browser.json'),
      JSON.stringify(
        {
          viewport: '390x844',
          checks: [
            'WebGL renderer',
            'shape/material local draft restore',
            'model undo',
            'synthetic local texture',
            'GLB binary animation/embedded texture',
            'play/pause',
            'no horizontal overflow',
            'native chunk protocol',
            'preview drawing insertion',
            'Android drawing-photo texture path',
          ],
          pageErrors: errors,
          externalRequests: external,
          glbBytes: bytes.length,
          depthHandoff:
            'Embedded-texture depth GLB → reviewed opening → real GLTFLoader → depth edit → saved draft → animated GLB export → actual orbit rendering; inference is performed separately by the native producer',
          depthFixtureSource:process.env.NOBLE_DEPTH_FIXTURE?'Provided GLB fixture':'Synthetic generated grid',
        depthFixtureSha256:require('node:crypto').createHash('sha256').update(Buffer.from(incoming,'base64')).digest('hex'),
        deviceTested: false,
        },
        null,
        2,
      ),
    );
    console.log(
      'PASS model browser acceptance (10 core gates + depth GLB integration; browser only, no device claim)',
    );
  } finally {
    if (browser) await browser.close();
    await new Promise(resolve => server.close(resolve));
  }
})().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
