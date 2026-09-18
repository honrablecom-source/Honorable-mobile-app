/* Shared web presentation. Product state and requests remain in their adapters. */
const ai = (name) =>
  `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${
    honorableIcons[name] || honorableIcons.image
  }</svg>`;
const tag = (text) => `<span class=a-tag>${esc(text)}</span>`;
const circle = (name, cls = "") =>
  `<span class="a-circle ${cls}">${ai(name)}</span>`;
const nativeTabs = [
  ["home", "Home"],
  ["memories", "Memories"],
  ["studio", "Studio"],
  ["pass", "Pass"],
  ["usage", "Usage"],
];
let memoryFocus = false,
  nativeMore = true;
const testUsage = honorableActivity;
honorableDock = () =>
  `<nav class="honorable-dock a-dock" aria-label="Main navigation">${nativeTabs
    .map(
      ([id, label]) =>
        `<button aria-current="${
          state.honorableTab === id ? "page" : "false"
        }" data-htab="${id}" class="${
          state.honorableTab === id ? "active" : ""
        }">${ai(id)}<small>${label}</small></button>`
    )
    .join("")}</nav>`;
function androidPrompt(image, label, text, query, featured = false) {
  return `<button class="a-prompt ${
    featured ? "featured" : ""
  }" data-prompt="${esc(
    query
  )}"><img src="/${image}" alt=""><div><small>Example scene</small><strong>${text}</strong></div></button>`;
}
const filteredNativeResults = () =>
  state.results?.results?.filter(
    (x) =>
      state.resultFilter === "All" ||
      (state.resultFilter === "Screenshots" && x.isScreenshot === true) ||
      x.type ===
        (state.resultFilter === "Photos"
          ? "IMAGE"
          : state.resultFilter === "Videos"
          ? "VIDEO"
          : "")
  ) || [];
memories = () =>
  state.results?.loading
    ? `<div class="a-memory a-searching"><button class=text-action id=cancel-product-search>${ai(
        "back"
      )} Cancel search</button><div class=search-progress role=status>${ai(
        "search"
      )}<h1>Searching your memories…</h1><p>${esc(
        state.query
      )}</p><small>Your media stays on this device.</small></div></div>`
    : androidResults();
function androidResults() {
  const items = filteredNativeResults(),
    best = items[0];
  return `<div class=a-results><header><button class=text-action id=editsearch>${ai(
    "back"
  )} Change search</button><h1>${esc(state.query)}</h1><small>${items.length} ${
    items.length === 1 ? "match" : "matches"
  }${items.length ? " · Best first" : ""}</small></header>${
    best
      ? `<button class=a-best data-a-result=0 aria-label="Open leading result"><div>${visual(
          best
        )}${
          best.timestamp != null
            ? `<span class=media-time>${fmt(best.timestamp)}</span>`
            : ""
        }</div></button><div class=leading-caption><span>${esc(
          best.name || "Memory"
        )}</span><small>${
          state.results.confident ? "Best match" : "Closest result"
        }</small></div>${
          !state.results.confident
            ? "<p class=a-confidence>No clear match yet. Try another detail.</p>"
            : ""
        }`
      : "<h2>No clear match yet.</h2><p>Try a place, color or something visible in the scene.</p>"
  }<div class=a-filters>${["All", "Photos", "Videos", "Screenshots"]
    .map(
      (f) =>
        `<button data-filter="${f}" aria-pressed="${
          state.resultFilter === f
        }" class="${state.resultFilter === f ? "active" : ""}">${f}</button>`
    )
    .join("")}</div>${
    items.length > 1
      ? `<div class=a-film>${items
          .slice(1)
          .map(
            (x, i) =>
              `<button data-a-result=${i + 1} aria-label="Open ${esc(
                x.name
              )}">${visual(x)}${
                x.timestamp != null ? `<span>${fmt(x.timestamp)}</span>` : ""
              }</button>`
          )
          .join("")}</div>`
      : ""
  }</div>`;
}
function androidPrivacy() {
  return `<div class=a-privacy><button class=text-action data-htab=settings>${ai(
    "back"
  )} Account</button><h1>Privacy & Data</h1><p>Your library is yours.</p><div class=setting-row><span>On-device search<small>Photos and videos are processed locally.</small></span></div><div class=setting-row><span>Account information<small>Sign-in and credit records use your configured account service.</small></span></div><div class=setting-row><span>Feedback attachments<small>Only files you select and consent to share are sent.</small></span></div></div>`;
}
honorable = () => {
  const bodies = {
    home: honorableHome,
    memories,
    settings: honorableSettings,
    models: honorableModels,
    pass: honorablePass,
    usage: testUsage,
    privacy: androidPrivacy,
  };
  return `<div class="page honorable a-native">${(
    bodies[state.honorableTab] || honorableHome
  )()}</div>${honorableDock()}`;
};
let viewerChrome = true;
viewer = () => {
  const item = state.preview || state.media[state.opened];
  if (!item) return gallery();
  return `<div class="page a-viewer ${
    viewerChrome ? "" : "chrome-hidden"
  }">${visual(
    item,
    "viewer-media"
  )}<button class=viewer-toggle id=viewer-toggle aria-label="${
    viewerChrome ? "Hide" : "Show"
  } controls"></button><header><button class=a-icon-button data-back aria-label=Back>${ai(
    "back"
  )}</button><span>${esc(
    item.name || "Memory"
  )}</span><a class=a-icon-button href="${src(
    item
  )}" target=_blank rel=noopener aria-label="Open original">${ai(
    "arrow"
  )}</a></header><section><small>${item.type === "VIDEO" ? "Video" : "Photo"}${
    item.timestamp != null ? " · " + fmt(item.timestamp) : ""
  }</small><p>${esc(item.name || "")}</p></section></div>`;
};
const previousBind = window.bindWebTest;
window.bindWebTest = () => {
  previousBind();
  screen.querySelector("#editsearch")?.addEventListener("click", () => {
    state.results = null;
    state.honorableTab = "home";
    render();
    screen.querySelector("#q")?.focus();
  });
  screen.querySelector("#viewer-toggle")?.addEventListener("click", () => {
    viewerChrome = !viewerChrome;
    screen
      .querySelector(".a-viewer")
      ?.classList.toggle("chrome-hidden", !viewerChrome);
    screen
      .querySelector("#viewer-toggle")
      ?.setAttribute(
        "aria-label",
        viewerChrome ? "Hide controls" : "Show controls"
      );
  });
  screen.querySelectorAll("[data-a-result]").forEach(
    (el) =>
      (el.onclick = () => {
        state.preview = filteredNativeResults()[Number(el.dataset.aResult)];
        state.opened = state.media.findIndex(
          (x) => x.uri === state.preview.uri
        );
        viewerChrome = true;
        go("viewer");
      })
  );
};
document.querySelectorAll("[data-test-screen]").forEach(
  (button) =>
    (button.onclick = () => {
      state.app = "honorable";
      state.honorableTab = button.dataset.testScreen;
      render();
    })
);
render();
