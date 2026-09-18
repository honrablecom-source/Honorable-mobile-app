const $ = (s) => document.querySelector(s),
  screen = $("#screen"),
  state = {
    app: "honorable",
    history: [],
    media: [],
    tab: "all",
    honorableTab: "home",
    resultFilter: "All",
    opened: null,
    query: "",
    results: null,
    scroll: { gallery: 0, files: 0 },
    recent: [],
    model: "SERAN_V2",
  };
let status = {};
let viewer = () => "",
  honorable = () => "",
  honorableHome = () => "",
  memories = () => "",
  honorableActivity = () => "",
  honorableModels = () => "",
  honorablePass = () => "",
  honorableSettings = () => "",
  honorableDock = () => "";
const esc = (s) =>
  String(s ?? "").replace(
    /[&<>"']/g,
    (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[
        c
      ])
  );
const src = (x) =>
  "/media/" + x.uri.split("/").map(encodeURIComponent).join("/");
const fmt = (ms) =>
  ms == null
    ? ""
    : `${Math.floor(ms / 60000)}:${String(Math.floor(ms / 1000) % 60).padStart(
        2,
        "0"
      )}`;
const visual = (x, cls = "") =>
  x.type === "VIDEO"
    ? `<video class="${cls}" src="${src(x)}${
        x.timestamp != null ? "#t=" + x.timestamp / 1000 : ""
      }" ${cls === "viewer-media" ? "controls autoplay" : ""} muted></video>`
    : `<img class="${cls}" src="${src(x)}" alt="">`;
function remember(app) {
  if (!["home", "recents"].includes(app))
    state.recent = [app, ...state.recent.filter((x) => x !== app)].slice(0, 3);
}
function go(app, push = true) {
  if (push && state.app !== app) state.history.push(state.app);
  state.app = app;
  remember(app);
  render();
}
function title(n) {
  return `<div class="top"><div><div class="eyebrow">HONORABLE DEVICE</div><h2>${n}</h2></div></div>`;
}
function render() {
  if (window.renderAuthGate && window.renderAuthGate()) return;
  if (!window.authBootstrapped) {
    screen.innerHTML =
      "<div class=auth-launch><b>honorable</b><span>Opening your memories…</span></div>";
    return;
  }
  let h = "";
  if (state.app === "home")
    h = `<div class="page home">${title("Good afternoon")}<div class="apps">${[
      ["honorable", "Home", "Honorable"],
      ["gallery", "Photos", "Gallery"],
      ["files", "Files", "Files"],
    ]
      .map(
        (a) =>
          `<button class="app" data-go="${a[0]}"><span class="icon">${a[1]}</span><small>${a[2]}</small></button>`
      )
      .join("")}</div></div>`;
  if (state.app === "gallery") h = gallery();
  if (state.app === "files") h = files();
  if (state.app === "honorable") h = honorable();
  if (state.app === "viewer") h = viewer();
  if (state.app === "recents")
    h = `<div class="page recents">${title("Recent apps")}${
      state.recent
        .map(
          (a) =>
            `<button class="recent" data-go="${a}"><b>${
              a[0].toUpperCase() + a.slice(1)
            }</b><p>${
              a === "honorable"
                ? state.query || "Natural-language media search"
                : a === "gallery"
                ? `${state.media.length} real media items`
                : "Web test storage"
            }</p></button>`
        )
        .join("") || "<p class=empty>No recent apps</p>"
    }</div>`;
  screen.innerHTML = h;
  bind();
  if (window.bindWebTest) bindWebTest();
  requestAnimationFrame(() => {
    if (state.app === "gallery")
      screen.firstChild.scrollTop = state.scroll.gallery;
    if (state.app === "files") screen.firstChild.scrollTop = state.scroll.files;
  });
}
function gallery() {
  let m = state.media.filter(
    (x) =>
      state.tab === "all" ||
      (state.tab === "photos" && x.type === "IMAGE") ||
      (state.tab === "videos" && x.type === "VIDEO")
  );
  return `<div class="page gallery">${title("Gallery")}<div class=tabs>${[
    ["all", "Photos"],
    ["videos", "Videos"],
    ["albums", "Albums"],
  ]
    .map(
      ([v, n]) =>
        `<button data-tab="${v}" class="${
          state.tab === v ? "active" : ""
        }">${n}</button>`
    )
    .join("")}</div>${
    state.tab === "albums"
      ? `<div class=hero><div class=info><h3>Camera</h3><p>${
          state.media.filter((x) => x.type === "IMAGE").length
        } photos · ${
          state.media.filter((x) => x.type === "VIDEO").length
        } videos</p></div></div>`
      : `<div class=grid>${m
          .map(
            (x, i) =>
              `<button class=tile data-open="${state.media.indexOf(
                x
              )}">${visual(x)}${
                x.duration
                  ? `<span class=duration>${fmt(x.duration)}</span>`
                  : ""
              }</button>`
          )
          .join("")}</div>`
  }</div>`;
}
function files() {
  return `<div class="page files">${title("Storage")}<p class=eyebrow>${
    state.media.length
  } ACTUAL FILES</p>${state.media
    .map(
      (x, i) =>
        `<button class=row data-open="${i}">${visual(x)}<span><b>${esc(
          x.name
        )}</b><br><small>${x.type.toLowerCase()}${
          x.duration ? " · " + fmt(x.duration) : ""
        }</small></span></button>`
    )
    .join("")}</div>`;
}
function bind() {
  screen
    .querySelectorAll("[data-go]")
    .forEach((b) => (b.onclick = () => go(b.dataset.go)));
  screen.querySelectorAll("[data-htab]").forEach(
    (b) =>
      (b.onclick = () => {
        state.honorableTab = b.dataset.htab;
        render();
      })
  );
  screen.querySelectorAll("[data-model]").forEach(
    (b) =>
      (b.onclick = () => {
        state.model = b.dataset.model;
        render();
      })
  );
  screen.querySelectorAll("[data-filter]").forEach(
    (b) =>
      (b.onclick = () => {
        state.resultFilter = b.dataset.filter;
        render();
      })
  );
  screen.querySelectorAll("[data-prompt]").forEach(
    (b) =>
      (b.onclick = () => {
        state.query = b.dataset.prompt;
        runSearch();
      })
  );
  screen.querySelectorAll("[data-tab]").forEach(
    (b) =>
      (b.onclick = () => {
        state.scroll.gallery = 0;
        state.tab = b.dataset.tab === "albums" ? "albums" : b.dataset.tab;
        render();
      })
  );
  screen.querySelectorAll("[data-open]").forEach(
    (b) =>
      (b.onclick = () => {
        state.preview = null;
        state.opened = +b.dataset.open;
        go("viewer");
      })
  );
  screen.querySelector("[data-back]")?.addEventListener("click", back);
  screen.querySelectorAll("[data-step]").forEach(
    (b) =>
      (b.onclick = () => {
        state.preview = null;
        state.opened =
          (state.opened + +b.dataset.step + state.media.length) %
          state.media.length;
        render();
      })
  );
  let p = screen.firstChild;
  if (p)
    p.onscroll = () => {
      if (state.app === "gallery") state.scroll.gallery = p.scrollTop;
      if (state.app === "files") state.scroll.files = p.scrollTop;
    };
  let f = screen.querySelector("form.search");
  if (f) f.onsubmit = search;
  screen.querySelector("#editsearch")?.addEventListener("click", () => {
    state.results = null;
    render();
  });
  screen.querySelector("#showmore")?.addEventListener("click", (e) => {
    e.target.hidden = true;
    $("#candidates").hidden = false;
  });
  screen.querySelectorAll("[data-result]").forEach(
    (el) =>
      (el.onclick = (e) => {
        let x = state.results.results.filter(
            (x) =>
              state.resultFilter === "All" ||
              x.type === (state.resultFilter === "Photos" ? "IMAGE" : "VIDEO")
          )[+el.dataset.result],
          i = state.media.findIndex((m) => m.uri === x.uri);
        if (i >= 0) {
          state.preview = x;
          state.opened = i;
          go("viewer");
        }
      })
  );
}
async function search(e) {
  e.preventDefault();
  state.query = $("#q").value.trim();
  if (state.query) runSearch();
}
async function runSearch() {
  state.results = { loading: true };
  render();
  try {
    state.results = await (
      await fetch(
        "/api/search?q=" + encodeURIComponent(state.query) + "&top=12"
      )
    ).json();
    $(
      "#debug"
    ).textContent = `SEARCH ENGINE: SHARED ANDROID CORE\nINDEXED MEDIA: ${
      status.indexed
    }\nTINYCLIP: ${status.tinyclip ? "ACTIVE" : "UNAVAILABLE"}\nVLM: ${
      status.vlm
    }\nQUERY LATENCY: ${state.results.latencyMs} ms\nTOP RESULT SCORE: ${
      state.results.results[0]?.score ?? "-"
    }\nTOP1 MARGIN: ${state.results.margin}`;
  } catch (e) {
    state.results = { confident: false, decision: e.message, results: [] };
  }
  render();
}
function back() {
  if (state.app === "viewer" && state.history.length)
    go(state.history.pop(), false);
  else if (state.history.length) go(state.history.pop(), false);
  else {
    state.honorableTab = "home";
    go("honorable", false);
  }
}
document
  .querySelectorAll("nav button")
  .forEach(
    (b) =>
      (b.onclick = () =>
        b.dataset.nav === "back" ? back() : go(b.dataset.nav, false))
  );
function clean() {
  Object.assign(state, {
    app: "honorable",
    history: [],
    opened: null,
    query: "",
    results: null,
    tab: "all",
    honorableTab: "home",
    scroll: { gallery: 0, files: 0 },
    recent: [],
  });
  render();
}
$("#clean").onclick = clean;
$("#reset").onclick = clean;
$("#record").onclick = () => document.body.classList.toggle("recording");
$("#preset").onchange = (e) => ($("#phone").dataset.preset = e.target.value);
$("#refresh").onclick = async () => {
  state.media = (
    await (await fetch("/api/refresh", { method: "POST" })).json()
  ).items;
  render();
};
setInterval(
  () =>
    ($("#clock").textContent = new Date().toLocaleTimeString([], {
      hour: "2-digit",
      minute: "2-digit",
    })),
  1000
);
$("#clock").textContent = new Date().toLocaleTimeString([], {
  hour: "2-digit",
  minute: "2-digit",
});
function fitDevice() {
  $("#phone").style.zoom = "1";
} // Responsive CSS owns sizing; legacy device zoom clipped toolbars.
addEventListener("resize", fitDevice, { passive: true });
fitDevice();
function loadLibrary() {
  return Promise.all([
    fetch("/api/media").then((r) => r.json()),
    fetch("/api/status").then((r) => r.json()),
  ])
    .then(([m, s]) => {
      if (!Array.isArray(m.items))
        throw Error(m.error || "Search adapter starting");
      state.media = m.items;
      status = s;
      document.body.classList.toggle(
        "debug",
        s.debug || new URLSearchParams(location.search).get("debug") === "true"
      );
      $("#debug").textContent = `SEARCH ENGINE: REAL\nINDEXED MEDIA: ${
        s.indexed
      }\nTINYCLIP: ${s.tinyclip ? "ACTIVE" : "UNAVAILABLE"}\nVLM: ${s.vlm}`;
      render();
    })
    .catch((e) => {
      $("#debug").textContent = e.message;
      setTimeout(loadLibrary, 2000);
    });
}
render();
loadLibrary();
