// UI integration fixture: real account server and real raster editing, deterministic search transport fixture.
const http = require("node:http"),
  fs = require("node:fs"),
  os = require("node:os"),
  path = require("node:path"),
  assert = require("node:assert/strict");
const { chromium, browserOptions } = require("./browser-test-runtime.cjs");
const { createServer } = require("../dev-server/src/server");
const root = path.resolve(__dirname, "../android-app/test-lab/web-test-shell");
(async () => {
  let accounts;
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "honorable-product-ui-"));
  const sourcePath = path.join(root, "prompt_beach.png");
  const sourceBytes = fs.readFileSync(sourcePath);
  const server = http.createServer((req, res) => {
    if (req.url.startsWith("/account/")) {
      req.url = req.url.slice(8);
      return accounts.emit("request", req, res);
    }
    if (req.url === "/web/config") {
      res.setHeader("Content-Type", "application/json");
      return res.end(
        '{"developmentPurchases":true,"searchTransport":"TEST_FIXTURE"}'
      );
    }
    if (req.url.startsWith("/api/")) {
      res.setHeader("Content-Type", "application/json");
      return res.end(
        req.url.startsWith("/api/search")
          ? JSON.stringify({
              confident: true,
              results: [
                {
                  uri: "prompt_beach.png",
                  name: "Beach",
                  type: "IMAGE",
                  score: 1,
                },
              ],
            })
          : req.url === "/api/media"
          ? '{"items":[]}'
          : '{"indexed":0}'
      );
    }
    const name =
      req.url === "/"
        ? "index.html"
        : req.url.split("?")[0].slice(1).replace("media/", "");
    if (
      !/^[a-zA-Z0-9_.-]+$/.test(name) ||
      !fs.existsSync(path.join(root, name))
    ) {
      res.writeHead(404);
      return res.end();
    }
    res.setHeader(
      "Content-Type",
      name.endsWith(".js")
        ? "text/javascript"
        : name.endsWith(".css")
        ? "text/css"
        : name.endsWith(".png")
        ? "image/png"
        : "text/html"
    );
    fs.createReadStream(path.join(root, name)).pipe(res);
  });
  await new Promise((r) => server.listen(0, "127.0.0.1", r));
  const origin = `http://localhost:${server.address().port}`;
  accounts = createServer({
    file: path.join(dir, "ledger.json"),
    mode: "development",
    webOrigins: [origin],
  });
  const browser = await chromium.launch({
    headless: true,
    args: ["--no-sandbox"],
    ...browserOptions,
  });
  try {
    const page = await browser.newPage({
      viewport: { width: 430, height: 932 },
    });
    const errors = [];
    page.on("pageerror", (e) => errors.push(e.message));
    await page.goto(origin);
    await page.locator("#test-account-login").click();
    await page.waitForSelector(".a-home");
    assert.deepEqual(await page.locator(".a-dock small").allTextContents(), [
      "Home",
      "Memories",
      "Studio",
      "Pass",
      "Usage",
    ]);
    await page.locator("[data-htab=pass]").click();
    await page.waitForFunction(
      () => document.querySelectorAll("[data-purchase]").length === 20
    );
    await page.locator("#toggle-passes").click();
    assert.equal(await page.locator("[data-purchase]").count(), 31);
    await page.locator("#toggle-passes").click();
    await page.locator("[data-purchase=memory_pass_040]").click();
    await page.waitForFunction(() => accountState?.balance === 40);
    assert.equal(
      await page.evaluate(() => accountState.freeMonthlyRemaining),
      15
    );
    await page.locator("[data-htab=usage]").click();
    assert.match(
      await page.locator(".product").innerText(),
      /Free credits remaining/
    );
    await page.locator("[data-htab=home]").click();
    await page.locator("#q").fill("beach");
    await page.locator("#model-open").click();
    assert.equal(
      await page.locator("[data-product-model=SERAN_V3]").isDisabled(),
      true
    );
    await page.locator("#model-close").click();
    await page.locator("[data-htab=studio]").click();
    assert.match(
      await page.locator(".studio-hub").innerText(),
      /Studio · locked/
    );
    await page.locator("details").last().locator("summary").click();
    await page.locator("#studio-test").click();
    await page.waitForFunction(
      () => accountState?.subscription?.status === "ACTIVE"
    );
    await page.locator("[data-htab=home]").click();
    await page.locator("#q").fill("beach");
    await page.locator("form.search button").click();
    await page.waitForSelector("[data-a-result]");
    await page.locator('[data-a-result="0"]').click();
    await page.waitForSelector(".a-viewer");
    await page.locator("#viewer-toggle").click();
    assert.equal(
      await page
        .locator(".a-viewer")
        .evaluate((e) => e.classList.contains("chrome-hidden")),
      true
    );
    await page.locator("#viewer-toggle").click();
    await page.locator("#edit-result").click();
    await page.waitForFunction(() => !!editImage);
    const before = await page
      .locator("#edit-canvas")
      .evaluate((c) => c.toDataURL());
    await page.locator("[data-workspace=Edit]").click();
    assert.equal(await page.locator("[data-adjust]").count(), 1);
    await page.locator("[data-adjust-tab=contrast]").click();
    assert.equal(await page.locator("[data-adjust=contrast]").count(), 1);
    await page.locator("#filter-photo").click();
    const after = await page
      .locator("#edit-canvas")
      .evaluate((c) => c.toDataURL());
    assert.notEqual(after, before);
    await page.locator("#edit-undo").click();
    assert.equal(
      await page.locator("#edit-canvas").evaluate((c) => c.toDataURL()),
      before
    );
    await page.locator("#edit-redo").click();
    const download = page.waitForEvent("download");
    await page.locator("#edit-export").click();
    const exported = await download;
    assert.equal(exported.suggestedFilename(), "Honorable-copy.png");
    const copyPath = path.join(dir, "Honorable-copy.png");
    await exported.saveAs(copyPath);
    const copy = fs.readFileSync(copyPath);
    assert.equal(copy.subarray(0, 8).toString("hex"), "89504e470d0a1a0a");
    assert.notDeepEqual(copy, sourceBytes, "Export must contain the edited image");
    assert.deepEqual(fs.readFileSync(sourcePath), sourceBytes, "Original fixture must remain unchanged");
    const raster = await page.evaluate(async bytes => {
      const image = await createImageBitmap(new Blob([new Uint8Array(bytes)], {type: "image/png"}));
      const canvas = document.createElement("canvas");
      canvas.width = image.width; canvas.height = image.height;
      const context = canvas.getContext("2d"); context.drawImage(image, 0, 0);
      const pixels = context.getImageData(0, 0, image.width, image.height).data;
      let visible = 0, colored = 0;
      for (let i = 0; i < pixels.length; i += 4) {
        if (pixels[i + 3] === 0) continue;
        visible++;
        if (Math.max(pixels[i], pixels[i + 1], pixels[i + 2]) - Math.min(pixels[i], pixels[i + 1], pixels[i + 2]) > 1) colored++;
      }
      return {width: image.width, height: image.height, visible, colored, sourceWidth: editImage.naturalWidth, sourceHeight: editImage.naturalHeight};
    }, Array.from(copy));
    assert.equal(raster.width, raster.sourceWidth); assert.equal(raster.height, raster.sourceHeight);
    assert(raster.visible > 100); assert.equal(raster.colored, 0, "Downloaded copy must retain the mono filter after redo");
    await page.locator("[data-workspace=More]").click();
    await page
      .locator("[data-workspace=Code]")
      .filter({ visible: true })
      .click();
    await page
      .locator("#script-editor")
      .fill("project.active_layer.opacity = 0.5");
    await page.locator("#script-save").click();
    assert.equal(await page.locator("#script-run").isDisabled(), true);
    assert.match(
      await page.locator(".studio-inspector").innerText(),
      /Execution is unavailable/
    );
    await page.locator("#view-code").click();
    assert.match(
      await page.locator(".studio-inspector pre").innerText(),
      /schemaVersion/
    );
    assert.equal(
      await page.evaluate(() => accountState.freeMonthlyRemaining),
      14
    );
    const screenshot = process.env.HONORABLE_PRODUCT_SCREENSHOT || path.join(dir, "editor.png");
    fs.mkdirSync(path.dirname(screenshot), {recursive: true});
    await page.screenshot({ path: screenshot });
    await page.setViewportSize({ width: 1440, height: 1000 });
    await page.evaluate(() => {
      activeWorkspace = "Edit";
      render();
    });
    const bounds = await page
      .locator(".studio-editor")
      .evaluate((e) => ({
        width: e.clientWidth,
        columns: getComputedStyle(e).gridTemplateColumns,
        overflow: e.scrollWidth > e.clientWidth,
      }));
    assert(bounds.width > 1200);
    assert.equal(bounds.columns.split(" ").length, 3);
    assert.equal(bounds.overflow, false);
    await page.setViewportSize({ width: 360, height: 800 });
    assert.equal(
      await page
        .locator(".studio-editor")
        .evaluate((e) => e.scrollWidth > e.clientWidth),
      false
    );
    assert.deepEqual(errors, []);
    console.log(
      "PASS: tabs, 20/31 catalog, test purchase, monthly balance, Studio gate, fixture search debit, pixel editing, undo/redo, downloaded full-resolution mono PNG, unchanged original, code editor and project representation."
    );
    console.log("Screenshot: " + screenshot);
  } finally {
    await browser.close();
    await new Promise((r) => server.close(r));
  }
})().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
