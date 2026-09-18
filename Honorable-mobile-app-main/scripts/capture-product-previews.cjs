// Captures product UI with isolated test accounts and a clearly labelled search fixture.
const http = require("node:http"),
  fs = require("node:fs"),
  os = require("node:os"),
  path = require("node:path"),
  assert = require("node:assert/strict");
const { chromium } = require("../ui-previews/node_modules/playwright");
const { createServer } = require("../dev-server/src/server");
const root = path.resolve(__dirname, "../android-app/test-lab/web-test-shell");
(async () => {
  let accounts;
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "honorable-product-ui-"));
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
    if (req.url.split("?")[0].endsWith("/preview-video.mp4")) {
      const file = path.resolve(
          __dirname,
          "../test-media/video-expanded/video-01.mp4"
        ),
        size = fs.statSync(file).size;
      const range = /bytes=(\d+)-(\d*)/.exec(req.headers.range || "");
      const start = range ? Number(range[1]) : 0,
        end =
          range && range[2] ? Math.min(size - 1, Number(range[2])) : size - 1;
      res.setHeader("Content-Type", "video/mp4");
      res.setHeader("Accept-Ranges", "bytes");
      res.setHeader("Content-Length", end - start + 1);
      if (range) {
        res.statusCode = 206;
        res.setHeader("Content-Range", `bytes ${start}-${end}/${size}`);
      }
      return fs.createReadStream(file, { start, end }).pipe(res);
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
  });
  try {
    const output = path.resolve(__dirname, "../ui-rebuild/previews");
    fs.mkdirSync(output, { recursive: true });
    const page = await browser.newPage({
      viewport: { width: 430, height: 932 },
    });
    page.setDefaultTimeout(15000);
    const errors = [];
    page.on("pageerror", (e) => errors.push(e.message));
    const shots = [];
    async function capture(id, label) {
      await page.evaluate(() => document.fonts.ready);
      await page.evaluate(async () => {
        await Promise.all(
          [...document.images].map((i) => i.decode().catch(() => {}))
        );
      });
      await page.waitForTimeout(100);
      await page.screenshot({ path: path.join(output, id + ".png") });
      shots.push({ id, label });
    }

    await page.goto(origin);
    await page.waitForSelector(".auth-welcome");
    await page.evaluate(() => {
      document.querySelector(".dev-label").textContent =
        "DESIGN PREVIEW · FIXTURE MEDIA";
      document.querySelector(".test-tools").style.display = "none";
    });
    await capture("01-welcome", "Welcome");
    await page.locator("#test-account-login").click();
    await page.waitForSelector(".a-home");
    await page.evaluate(() => {
      state.media = [
        { uri: "prompt_beach.png", name: "By the water", type: "IMAGE" },
        {
          uri: "prompt_birthday.png",
          name: "Birthday afternoon",
          type: "IMAGE",
        },
        { uri: "prompt_red_car.png", name: "Winter drive", type: "IMAGE" },
        {
          uri: "preview-video.mp4",
          name: "Park walk",
          type: "VIDEO",
          duration: 12000,
        },
      ];
      render();
    });
    await capture("02-home", "Home · fixture library");
    await page.locator("#q").fill("the beach with tall grass");
    await page.locator("#q").focus();
    await capture("03-home-search-active", "Home · search active");
    await page.evaluate(() => {
      state.query = "the beach with tall grass";
      state.honorableTab = "memories";
      state.results = { loading: true };
      render();
    });
    await capture("04-searching", "Searching · preview state");
    await page.evaluate(() => {
      state.results = { confident: true, results: state.media.slice(0, 3) };
      render();
    });
    await capture("05-results", "Results · fixture, not search evidence");
    await page.locator('[data-a-result="0"]').click();
    await capture("07-memory-viewer", "Memory viewer");
    await page.locator("#edit-result").click();
    await page.waitForFunction(() => !!editImage);
    await capture("13-photo-editor", "Photo editor");
    await page.locator("[data-workspace=Crop]").click();
    await capture("14-crop", "Crop");
    await page.locator("[data-workspace=Edit]").click();
    await capture("15-adjust", "Adjust");
    await page.locator("#filter-photo").click();
    await page.locator("#edit-before").first().click();
    await capture("16-before-after", "Before / After · showing original");
    await page.locator("#edit-before").first().click();
    await page.locator("[data-workspace=Layers]").click();
    await capture("17-layers", "Layers");
    await page.evaluate(() => {
      editWorkspace = "Edit";
      state.app = "honorable";
      state.results = null;
      state.honorableTab = "memories";
      render();
    });
    await capture("06-memories", "Memories · fixture library");
    for (const [tab, id, label] of [
      ["pass", "08-pass", "Memory Pass · first 20"],
      ["usage", "10-usage", "Usage · test account"],
      ["studio", "11-studio-free", "Studio · free"],
    ]) {
      await page.evaluate((t) => {
        state.honorableTab = t;
        render();
      }, tab);
      if (tab === "pass") {
        await page.waitForFunction(
          () => document.querySelectorAll("[data-purchase]").length === 20
        );
        await capture(id, label);
        await page.locator("#toggle-passes").click();
        assert.equal(await page.locator("[data-purchase]").count(), 31);
        await capture("09-pass-all", "Memory Pass · all 31");
        await page.locator("#toggle-passes").click();
      } else await capture(id, label);
    }
    await page.locator("details").last().locator("summary").click();
    await page.locator("#studio-test").click();
    await page.waitForFunction(
      () => accountState?.subscription?.status === "ACTIVE"
    );
    await capture("12-studio-member", "Studio · test membership");
    await page.evaluate(() => {
      state.honorableTab = "settings";
      render();
    });
    await capture("24-account-settings", "Account & Settings");
    await page.evaluate(() => {
      state.app = "viewer";
      editWorkspace = "__editing";
      editProject.files["scripts/main.py"].text =
        "# Preview script — execution is unavailable\n# Keep the original; describe an edit as code.\n\nproject.active_layer.opacity = 0.85\n";
      editProject.files["scripts/automation.java"].text =
        "// Preview script — execution is unavailable\npublic class MemoryEdit {\n    public void describe() {\n        // Project state remains local.\n    }\n}\n";
      activeWorkspace = "Project";
      render();
    });
    await capture("20-project-explorer", "Project explorer");
    await page.evaluate(() => {
      activeWorkspace = "Code";
      render();
    });
    await capture("21-python-editor", "Python · execution unavailable");
    await page.locator('[data-script="scripts/automation.java"]').click();
    await capture("22-java-editor", "Java · execution unavailable");
    await page.locator("#view-code").click();
    await capture("23-project-as-code", "Project as Code");
    await page.setViewportSize({ width: 1440, height: 1000 });
    await page.evaluate(() => {
      activeWorkspace = "Edit";
      render();
    });
    await capture("25-desktop-photo-editor", "Desktop photo editor");
    await page.evaluate(() => {
      activeWorkspace = "Code";
      render();
    });
    await capture("27-desktop-code-workspace", "Desktop code workspace");
    await page.setViewportSize({ width: 430, height: 932 });
    await page.evaluate(async () => {
      await openEditor({
        uri: "preview-video.mp4",
        name: "Park walk · fixture",
        type: "VIDEO",
      });
      activeWorkspace = "Video";
      render();
    });
    await page.waitForFunction(() =>
      Number.isFinite(document.querySelector("video")?.duration)
    );
    await page.evaluate(() => {
      document.querySelector("video").currentTime = 1;
    });
    await page.waitForFunction(
      () => document.querySelector("video")?.readyState >= 2
    );
    await page.locator("#timeline-add").click();
    await page.waitForFunction(
      () => document.querySelector("video")?.readyState >= 2
    );
    await page.locator("#playhead").fill("1");
    await page.locator("#playhead").dispatchEvent("input");
    await page.waitForFunction(() => {
      const v = document.querySelector("video");
      return v?.readyState >= 2 && !v.seeking && v.currentTime >= 0.9;
    });
    await page.evaluate(async () => {
      const video = document.querySelector("video");
      await video.play();
    });
    await page.waitForTimeout(350);
    await page.evaluate(() => {
      const video = document.querySelector("video");
      video.pause();
      const slider = document.querySelector("#playhead");
      slider.value = String(video.currentTime);
      slider.dispatchEvent(new Event("input", { bubbles: true }));
    });
    await page.waitForTimeout(700);
    await capture("18-video-editor", "Video editor · foundation");
    await page.setViewportSize({ width: 1440, height: 1000 });
    await capture(
      "26-desktop-video-editor",
      "Desktop video editor · foundation"
    );
    await page.setViewportSize({ width: 430, height: 932 });
    await page.evaluate(() => {
      activeWorkspace = "Nodes";
      editNotice = "";
      editProject.nodes.nodes = [
        {
          id: "source",
          name: "Source · preview",
          type: "source",
          x: 20,
          y: 140,
        },
        {
          id: "output",
          name: "Output · preview",
          type: "output",
          x: 235,
          y: 280,
        },
      ];
      editProject.nodes.connections = [
        { from: { nodeId: "source" }, to: { nodeId: "output" } },
      ];
      render();
    });
    await capture("19-nodes", "Nodes · foundation");
    shots.sort((a, b) => a.id.localeCompare(b.id));
    fs.writeFileSync(
      path.join(output, "screens.json"),
      JSON.stringify(shots, null, 2)
    );
    fs.writeFileSync(
      path.join(output, "index.html"),
      '<!doctype html><meta charset="utf-8"><title>Honorable design review</title><style>body{background:#181818;color:#eee;font:16px Arial;margin:24px}main{display:grid;grid-template-columns:repeat(4,1fr);gap:24px}figure{margin:0}img{width:100%;height:auto}figcaption{padding:12px 0}a{color:inherit}</style><h1>Honorable · design review</h1><p>Preview data only. Screens rendered from the working web client. Native screens require device review.</p><main>' +
        shots
          .map(
            (s) =>
              '<figure><a href="' +
              s.id +
              '.png"><img src="' +
              s.id +
              '.png"></a><figcaption>' +
              s.id +
              " · " +
              s.label +
              "</figcaption></figure>"
          )
          .join("") +
        "</main>"
    );
    assert.deepEqual(errors, []);
    console.log("Captured " + shots.length + " UI previews in " + output);
  } finally {
    await browser.close();
    await new Promise((r) => server.close(r));
  }
})().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
