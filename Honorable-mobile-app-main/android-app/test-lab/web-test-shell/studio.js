/* Product surfaces use account API values. Script text is never evaluated. */
let showAllPasses = false,
  usageLimit = 20,
  editProject = null,
  editUndo = [],
  editRedo = [],
  editWorkspace = "Edit",
  editFile = "scripts/main.py",
  editBefore = false,
  editImage = null,
  editNotice = "",
  projectSearch = "";
const HP = HonorableProject;
state.model = "SERAN_V1";
nativeTabs.splice(
  0,
  nativeTabs.length,
  ["home", "Home"],
  ["memories", "Memories"],
  ["studio", "Studio"],
  ["pass", "Pass"],
  ["usage", "Usage"]
);
const balanceView = () =>
  `<div class=balances><article><small>FREE THIS MONTH</small><b>${
    accountState?.freeMonthlyRemaining ?? "—"
  } / ${
    accountState?.freeMonthlyTotal ?? catalog.freeMonthlyCredits ?? "—"
  }</b></article><article><small>YOUR MEMORY CREDITS</small><b>${
    accountState?.balance ?? "—"
  }</b><small>Never expires</small></article></div>`;
honorablePass = () =>
  `<div class=product><small class=section-label>MEMORY PASS</small><h1>Memory Credits</h1><p>Search at your own pace.<br>Purchased credits never expire.</p>${balanceView()}<h2>${
    showAllPasses ? "All 31 passes" : "Choose a pass"
  }</h2><p class=note>Reference USD prices · ${
    webConfig.developmentPurchases
      ? "test purchases, no payment collected"
      : "purchases unavailable"
  }</p><div class=pass-grid>${(catalog.passes
    ? Object.entries(catalog.passes)
    : []
  )
    .slice(0, showAllPasses ? 31 : 20)
    .map(([id, credits], i) => {
      const p = productPasses.find((p) => p.id === id);
      return `<button class="${
        i < 2 ? "recommended" : "compact-pass"
      }" data-purchase="${id}" ${
        !webConfig.developmentPurchases || authState !== "online"
          ? "disabled"
          : ""
      }>${
        i < 2 ? `<small>${["Starter", "Everyday"][i]}</small>` : ""
      }<b>${credits.toLocaleString()}</b><span>credits</span><strong>$${
        p?.referenceUsd ?? "—"
      }</strong></button>`;
    })
    .join("")}</div><button id=toggle-passes>${
    showAllPasses ? "Show recommended" : "Show all 31 passes"
  }</button><button id=restore>Restore purchases</button></div>`;
let productPasses = [];
// Server catalog keeps the legacy map shape for older clients and exposes detail separately.
const fetchProductCatalog = async () => {
  const c = await webRequest("/account/v1/catalog");
  catalog = c;
  productPasses = c.passDetails || [];
  render();
};
const usagePage = () => {
  const remaining = accountState?.freeMonthlyRemaining ?? 0,
    total = accountState?.freeMonthlyTotal ?? 15,
    models = catalog.models || [],
    max = Math.max(
      1,
      ...models.map((m) => accountState?.usage?.byModel?.[m.model] ?? 0)
    );
  return `<div class=product><small class=section-label>YOUR USAGE</small><h1 class=credit-total>${
    accountState ? remaining : "—"
  }</h1><p>Free credits remaining</p><progress max="${total}" value="${remaining}" aria-label="Free credits remaining"></progress><small>Resets ${
    accountState?.nextResetAt
      ? new Date(accountState.nextResetAt).toLocaleDateString()
      : "—"
  } · ${total} monthly credits</small><div class=purchased-balance><span>Purchased Memory Credits<small>Never expire</small></span><b>${
    accountState?.balance ?? "—"
  }</b></div><h2>THIS MONTH</h2><p>${
    accountState?.usage?.month?.searchCount ?? 0
  } searches · ${
    accountState?.usage?.month?.freeCreditsUsed ?? 0
  } free credits used · ${
    accountState?.usage?.month?.purchasedCreditsUsed ?? 0
  } purchased used</p>${models
    .map(
      (m) =>
        `<div class=usage-model><span>${esc(
          m.name
        )}</span><div class=usage-track><i style="width:${
          (100 * (accountState?.usage?.byModel?.[m.model] ?? 0)) / max
        }%"></i></div><b>${
          accountState?.usage?.byModel?.[m.model] ?? 0
        }</b></div>`
    )
    .join("")}<h2>RECENT ACTIVITY</h2>${
    (accountState?.transactions || [])
      .slice(0, usageLimit)
      .map(
        (t) =>
          `<div class=ledger-row><span>${esc(t.description)}<small>${new Date(
            t.createdAt
          ).toLocaleDateString()}${
            t.type === "DEBIT"
              ? ` · ${t.freeSpent || 0} free / ${
                  t.purchasedSpent ?? -t.credits
                } purchased`
              : ""
          }</small></span><b>${t.credits > 0 ? "+" : ""}${t.credits}</b></div>`
      )
      .join("") || "<p>Your next discovery starts here.</p>"
  }${
    (accountState?.transactions?.length || 0) > usageLimit
      ? "<button id=usage-more>Load more</button>"
      : ""
  }<details><summary>All time & library</summary><p>${
    accountState?.usage?.allTime?.searchesCompleted ?? 0
  } searches completed · ${
    accountState?.usage?.allTime?.passesPurchased ?? 0
  } passes purchased</p><input id=media-input type=file accept="image/*,video/*" multiple><button id=reindex>Refresh index</button><button data-go=gallery>Browse media</button></details></div>`;
};
let studioCategory = null,
  modelSheet = false;
const studioCategories = {
  Photo: [
    "basicEdit",
    "generativeFill",
    "expansion",
    "subjectSelection",
    "patch",
    "smartFilters",
    "raw",
    "relight",
    "smartLayers",
  ],
  Video: ["videoSequence", "cameraTracking", "aiVideo"],
  Animate: ["basicPoseRig", "rigAnimation"],
  "3D": ["geometry", "video3d"],
  Code: ["code"],
  Nodes: ["nodes"],
};
const studioPage = () => {
  const member = accountState?.subscription?.status === "ACTIVE";
  return `<div class="product studio-hub"><header><small class=section-label>HONORABLE STUDIO</small><span class=membership>${
    member ? "MEMBER" : "EXPLORE"
  }</span></header>${
    member
      ? `<h1>Studio</h1><h2>CONTINUE EDITING</h2>${
          editProject
            ? `<button class=recent-project id=continue-edit><img src="${esc(
                editProject.media[0].kind === "VIDEO"
                  ? "prompt_beach.png"
                  : editProject.media[0].uri
              )}" alt=""><span>${esc(
                editProject.name
              )}<small>Open local project</small></span></button>`
            : "<p>Choose a memory to begin your first project.</p>"
        }`
      : "<div class=studio-intro><h1>Make something<br>of a memory.</h1><p>Start with a photo. Keep the original.</p><button data-go=gallery>Choose a memory</button></div>"
  }<h2>${member ? "CREATE" : "CREATE"}</h2><div class=creative-grid>${[
    "Photo",
    "Video",
    "Code",
  ]
    .map(
      (name, i) =>
        `<button data-category="${name}" class="creative-tile tile-${i}">${
          i === 0 && state.media.some((m) => m.type === "IMAGE")
            ? `<img src="${src(
                state.media.find((m) => m.type === "IMAGE")
              )}" alt="From your library"><span class=creative-art>${ai(
                "image"
              )}</span>`
            : `<span class=creative-art>${ai(i === 1 ? "film" : "code")}</span>`
        }<span>${name}<small>${
          i === 0 ? "Free essentials" : member ? "Workspace" : "Studio · locked"
        }</small></span></button>`
    )
    .join("")}</div>${
    member
      ? "<h2>ADVANCED</h2><div class=tools><button data-category=Nodes>Nodes</button><button data-category=Code>Code</button></div>"
      : ""
  }${
    studioCategory
      ? `<section class=category-detail><button id=category-close aria-label="Close category">${ai(
          "close"
        )}</button><h2>${esc(studioCategory)}</h2>${
          studioCategory === "Photo"
            ? "<p>Adjust, crop, rotate and save a new copy.</p><button data-go=gallery>Choose a memory</button>"
            : studioCategory === "Nodes" || studioCategory === "Code"
            ? `<p>${
                studioCategory === "Nodes"
                  ? "Node graph foundation. Processing is unavailable."
                  : "Python and Java editors. Execution is unavailable."
              }</p>${
                editProject
                  ? `<button data-open-advanced=${studioCategory}>Open workspace</button>`
                  : "<button data-go=gallery>Choose a project source</button>"
              }`
            : "<p>Explore a workspace from a memory. Advanced processing remains unavailable.</p><button data-go=gallery>Choose media</button>"
        }${Object.entries(HP.capabilities)
          .filter(
            ([k]) =>
              (studioCategories[studioCategory] || []).includes(k) &&
              k !== "basicEdit"
          )
          .map(
            ([key, c]) =>
              `<div class=ledger-row><span>${esc(
                key.replace(/([A-Z])/g, " $1")
              )}</span><button data-capability=${key}>${c.status.replace(
                "_",
                " "
              )}</button></div>`
          )
          .join("")}</section>`
      : ""
  }<button id=studio-unlock>${
    member ? "Studio active" : "Explore Studio membership"
  }</button>${
    webConfig.developmentPurchases
      ? `<details><summary>Development account</summary><button id=studio-test>${
          member ? "Remove" : "Enable"
        } test entitlement</button><small>No charge</small></details>`
      : ""
  }</div>`;
};
honorableHome = () =>
  `<div class=a-home><header class=a-brand><strong>honorable</strong><button class=a-icon-button data-htab=settings aria-label="Account">${ai(
    "account"
  )}</button></header><h1>What do you<br>remember?</h1><form class=search><input id=q aria-label="Describe a memory" placeholder="the video where we were at the beach…" value="${esc(
    state.query
  )}"><button aria-label=Search>${ai(
    "arrow"
  )}</button></form><div class=search-meta>${modelSelector()}<small>${
    accountState?.freeMonthlyRemaining ?? "—"
  } free · ${
    accountState?.balance ?? "—"
  } purchased</small></div><div class=a-section-title><h2>Recent memories</h2><button class=text-action data-htab=memories>View library ${ai(
    "chevron"
  )}</button></div>${
    state.media.length
      ? `<div class=recent-memories>${state.media
          .slice(0, 3)
          .map(
            (m, i) =>
              `<button class="a-prompt ${
                i === 0 ? "featured" : ""
              }" data-library=${i} aria-label="Open ${esc(m.name)}">${visual(
                m
              )}<div><strong>${esc(m.name)}</strong></div></button>`
          )
          .join("")}</div>`
      : "<div class=empty-library><p>No memories indexed yet.</p><small>Choose the photos and videos you want Honorable to search.</small><button data-go=gallery>Choose media</button></div>"
  }</div>`;
honorableSettings = () =>
  `<div class="product account-page"><button class=text-action data-htab=home>${ai(
    "back"
  )} Home</button><h1>Account</h1><section class=account-identity><h2>${esc(
    accountState?.profile?.name || "Your account"
  )}</h2><p>${esc(accountState?.profile?.email || "")}</p><small>${
    authState === "offline" ? "Offline · previously signed in" : "Signed in"
  }</small><p class=account-credit-line>${
    accountState?.freeMonthlyRemaining ?? "—"
  } free · ${
    accountState?.balance ?? "—"
  } purchased credits</p></section><h2>Your library</h2>${[
    ["privacy", "Privacy & Data", "Your choices and participation"],
    ["usage", "Storage & indexing", "Manage your local media"],
    ["models", "Search models", "Choose how to search"],
  ]
    .map(
      ([t, l, d]) =>
        `<button class=settings-link data-htab=${t}><span>${l}<small>${d}</small></span>${ai(
          "chevron"
        )}</button>`
    )
    .join("")}<h2>Membership</h2>${[
    ["pass", "Memory Passes"],
    ["usage", "Usage"],
    ["studio", "Studio membership"],
  ]
    .map(
      ([t, l]) =>
        `<button class=settings-link data-htab=${t}>${l}${ai(
          "chevron"
        )}</button>`
    )
    .join("")}<button class=signout id=account-signout>Sign out</button></div>`;
const modelChoices = () =>
  `<div class=model-choices>${(catalog.models || [])
    .map(
      (m) =>
        `<button data-product-model=${m.model} aria-pressed="${
          (state.model || "SERAN_V1") === m.model
        }" ${!m.available ? "disabled" : ""}><strong>${esc(
          m.name
        )}</strong><span>${m.credits} credit${
          m.credits === 1 ? "" : "s"
        }</span><small>${esc(m.description)}${
          !m.available ? " · Coming Soon" : ""
        }</small></button>`
    )
    .join("")}</div>`;
const modelSelector = () => {
  const m = (catalog.models || []).find((m) => m.model === state.model);
  return `<div class=model-row><button id=model-open>${esc(
    m?.name || "FAST"
  )} ▾ <span>· ${m?.credits ?? 1} credit${
    m?.credits === 1 ? "" : "s"
  }</span></button></div>${
    modelSheet
      ? `<div class=sheet-backdrop><section class=model-sheet role=dialog aria-modal=true aria-label="Search model"><header><h2>Choose your model</h2><button id=model-close aria-label=Close>${ai(
          "close"
        )}</button></header>${modelChoices()}</section></div>`
      : ""
  }`;
};
honorableModels = () =>
  `<div class=product><button data-htab=home>Back</button><h1>Choose your model</h1><p>The right pace for your next discovery.</p>${modelChoices()}</div>`;
const baseProductMemories = memories;
memories = () =>
  state.results
    ? baseProductMemories()
    : `<div class=a-memory><h1>Memories</h1><form class=search><input id=q aria-label="Search memories" placeholder="Search memories" value="${esc(
        state.query
      )}"><button aria-label=Search>${ai(
        "search"
      )}</button></form>${modelSelector()}<div class=a-filters>${[
        "Photos",
        "Videos",
        "Recent",
      ]
        .map(
          (f) =>
            `<button data-library-filter=${f} class="${
              (state.libraryFilter || "Recent") === f ? "active" : ""
            }">${f}</button>`
        )
        .join("")}</div>${
        state.media.length
          ? `<div class=library-grid>${state.media
              .map((m, i) => ({ m, i }))
              .filter(
                ({ m }) =>
                  !state.libraryFilter ||
                  state.libraryFilter === "Recent" ||
                  m.type ===
                    (state.libraryFilter === "Videos" ? "VIDEO" : "IMAGE")
              )
              .map(
                ({ m, i }) =>
                  `<button data-library=${i} aria-label="${esc(
                    m.name
                  )}">${visual(m)}</button>`
              )
              .join("")}</div>`
          : `<div class=empty-library><p>No memories indexed yet.</p><small>Choose the photos and videos you want to search.</small><button data-go=gallery>Choose media</button></div>`
      }</div>`;
const priorProductHonorable = honorable;
honorable = () =>
  ["studio", "usage"].includes(state.honorableTab)
    ? `<div class="page honorable dark a-native">${
        state.honorableTab === "studio" ? studioPage() : usagePage()
      }</div>${honorableDock()}`
    : priorProductHonorable();
const baseProductViewer = viewer;
viewer = () =>
  editWorkspace === "__editing"
    ? editorPage()
    : baseProductViewer().replace(
        "</header>",
        "<button id=edit-result>Edit</button><button id=share-result>Share</button></header>"
      );
let activeSearch = null,
  activeSearchController = null;
runSearch = async () => {
  if (activeSearch) return;
  const model = state.model || "SERAN_V1",
    id = crypto.randomUUID();
  activeSearch = id;
  activeSearchController = new AbortController();
  state.results = { loading: true };
  render();
  try {
    let result;
    if (webConfig.searchTransport === "VERIFIED_LOCAL_ENGINE") {
      result = await webRequest(
        "/web/search",
        { query: state.query, model, requestId: id },
        activeSearchController.signal
      );
    } else if (webConfig.searchTransport === "TEST_FIXTURE") {
      await webRequest("/account/v1/search/start", { model, requestId: id });
      const response = await fetch(
        "/api/search?q=" +
          encodeURIComponent(state.query) +
          "&top=12&model=" +
          encodeURIComponent(model)
      );
      if (!response.ok) throw Error("Search failed");
      result = await response.json();
      if (activeSearch !== id) return;
      await webRequest("/account/v1/search/complete", {
        requestId: id,
        outcome: result.results?.length ? "SUCCESS" : "FAILED",
      });
    } else throw Error("Real search transport is not configured.");
    if (activeSearch !== id) return;
    state.results = result;
    state.honorableTab = "memories";
    await loadAccount();
  } catch (error) {
    if (activeSearch !== id) return;
    state.results = { results: [], confident: false, decision: error.message };
    webNotice = error.message;
  } finally {
    if (activeSearch === id) activeSearch = null;
    render();
  }
};
function commitEdit(next) {
  editUndo.push(HP.clone(editProject));
  if (editUndo.length > 50) editUndo.shift();
  editRedo = [];
  editProject = next;
  persistProject();
}
function persistProject() {
  try {
    localStorage.setItem("honorable-local-project", HP.serialize(editProject));
  } catch {
    editNotice =
      "Local project storage is full. Export your project to keep changes.";
  }
}
async function openEditor(item) {
  editProject = HP.create(item);
  editUndo = [];
  editRedo = [];
  editImage = null;
  editBefore = false;
  editWorkspace = "__editing";
  activeWorkspace = "Canvas";
  editNotice = "";
  if (item.type !== "VIDEO") {
    const im = new Image();
    im.onload = () => {
      editImage = im;
      render();
    };
    im.onerror = () => {
      editNotice = "Unable to load original image.";
      render();
    };
    im.src = src(item);
  }
  state.app = "viewer";
  render();
}
let activeAdjustment = "brightness";
let activeWorkspace = "Canvas",
  selectedClip = null,
  codeTab = "EDITOR",
  nodeZoom = 1,
  nodePan = { x: 0, y: 0 };
function syntax(text) {
  return text
    .split("\n")
    .map(
      (line, i) =>
        `<span class=code-line><i>${i + 1}</i> ${line
          .split(
            /(\b(?:project|public|class|void|import|from|def|return|if|else|for|new|float|int)\b|#[^\n]*)/g
          )
          .map((t) =>
            /^(project|public|class|void|import|from|def|return|if|else|for|new|float|int)$/.test(
              t
            )
              ? `<b>${esc(t)}</b>`
              : t.startsWith("#")
              ? `<em>${esc(t)}</em>`
              : esc(t)
          )
          .join("")}</span>`
    )
    .join("");
}
function projectTree() {
  const paths = [
    "project.honorable",
    "media/original",
    "layers/layers.json",
    "timeline/sequence.json",
    "nodes/composition.json",
    ...Object.keys(editProject.files),
  ].filter((f) => f.includes(projectSearch));
  const tree = {};
  for (const path of paths) {
    let node = tree;
    path.split("/").forEach((name, i, parts) => {
      node[name] ??= {};
      if (i === parts.length - 1) node[name].$path = path;
      node = node[name];
    });
  }
  function branch(node) {
    return Object.entries(node)
      .filter(([k]) => k !== "$path")
      .map(([name, child]) => {
        const folder =
          Object.keys(child).some((k) => k !== "$path") ||
          editProject.files[child.$path]?.kind === "folder";
        return folder
          ? `<details open class=tree-folder><summary>${ai("memories")}${esc(
              name
            )}</summary>${branch(child)}</details>`
          : `<button class=file data-file="${esc(child.$path)}">${ai(
              "terms"
            )}${esc(name)}</button>`;
      })
      .join("");
  }
  return `<div class=project-tree><strong>${esc(
    editProject.name
  )}</strong>${branch(tree)}</div>`;
}
function nodeGraph() {
  return `<div class=node-viewport><div class=node-controls><span>COMPOSITING · FOUNDATION</span><button id=node-out aria-label="Zoom out">−</button><small>${Math.round(
    nodeZoom * 100
  )}%</small><button id=node-in aria-label="Zoom in">+</button><button id=node-reset>Reset view</button></div><div class=node-plane style="transform:translate(${
    nodePan.x
  }px,${
    nodePan.y
  }px) scale(${nodeZoom})"><svg class=node-connections viewBox="0 0 1000 600">${editProject.nodes.connections
    .map((c) => {
      const a = editProject.nodes.nodes.find((n) => n.id === c.from?.nodeId),
        b = editProject.nodes.nodes.find((n) => n.id === c.to?.nodeId);
      return a && b
        ? `<path d="M ${(a.x || 0) + 180} ${(a.y || 0) + 50} C ${
            (a.x || 0) + 260
          } ${(a.y || 0) + 50}, ${(b.x || 0) - 80} ${(b.y || 0) + 50}, ${
            b.x || 0
          } ${(b.y || 0) + 50}"/>`
        : "";
    })
    .join("")}</svg>${
    editProject.nodes.nodes.length
      ? editProject.nodes.nodes
          .map(
            (n) =>
              `<article class=node-card style="left:${Number(n.x) || 0}px;top:${
                Number(n.y) || 0
              }px"><header>${esc(
                n.name || n.type || n.id
              )}</header><p><i class=node-port></i> Input <span>Output <i class=node-port></i></span></p></article>`
          )
          .join("")
      : '<article class="node-card node-placeholder"><header>No nodes in this project</header><p><i class=node-port></i> Input <span>Output <i class=node-port></i></span></p><small>Graph processing is unavailable.</small></article>'
  }</div></div>`;
}
function timelineView() {
  const t = editProject.timeline;
  const duration = Math.max(
      10,
      ...t.tracks.flatMap((t) =>
        t.clips.map((c) => c.start + (c.out - c.in) / c.speed)
      )
    ),
    width = Math.max(600, duration * 24);
  return `<section class=sequence><header><strong>Sequence 01</strong><small>FOUNDATION · preview source only</small><span id=timeline-time>${t.playhead.toFixed(
    2
  )} s</span></header><div class=timeline-scroll><div class=timeline-content style="width:${width}px"><div class=time-ruler>${Array.from(
    { length: 11 },
    (_, i) => `<span>${((duration * i) / 10).toFixed(1)}s</span>`
  ).join(
    ""
  )}</div><input id=playhead class=timeline-seek type=range min=0 max=${duration} step=.1 value=${
    t.playhead
  } aria-label="Sequence playhead"><div class=playhead-line style="left:${Math.min(
    100,
    (t.playhead / duration) * 100
  )}%"></div>${
    t.tracks.length
      ? t.tracks
          .map(
            (t) =>
              `<div class=sequence-track><small>${esc(
                t.kind
              )} 01</small>${t.clips
                .map(
                  (c) =>
                    `<button class="sequence-clip ${
                      selectedClip === c.id ? "selected" : ""
                    }" data-clip="${esc(c.id)}" style="left:${
                      (c.start / duration) * 100
                    }%;width:${Math.max(
                      5,
                      ((c.out - c.in) / c.speed / duration) * 100
                    )}%"><i></i>${esc(editProject.name)} · ${(
                      c.out - c.in
                    ).toFixed(1)}s<i></i></button>`
                )
                .join("")}</div>`
          )
          .join("")
      : "<div class=sequence-track><small>V1</small><span class=empty-track>Add source clip to begin a sequence</span></div>"
  }${t.markers
    .map(
      (m) =>
        `<span class=sequence-marker style="left:${
          (m.time / duration) * 100
        }%" title="${esc(m.label)}">◆</span>`
    )
    .join("")}</div></div></section>`;
}
function editorPage() {
  const video = editProject.media[0].kind === "VIDEO",
    premium = accountState?.subscription?.status === "ACTIVE",
    dedicated = ["Code", "Representation", "Nodes", "Project"].includes(
      activeWorkspace
    ),
    tools = video
      ? [
          ["Video", "Sequence"],
          ["Layers", "Layers"],
          ["More", "More"],
        ]
      : [
          ["Edit", "Adjust"],
          ["Crop", "Crop"],
          ["Layers", "Layers"],
          ["More", "More"],
        ];
  return `<div class="studio-editor ${dedicated ? "dedicated" : ""} ${
    video ? "is-video" : ""
  }" data-active="${activeWorkspace}"><header><button id=editor-close aria-label=Back>${ai(
    "back"
  )}</button><strong>${esc(
    editProject.name
  )}<small>Honorable Studio</small></strong><button id=edit-undo ${
    !editUndo.length ? "disabled" : ""
  } aria-label=Undo>Undo</button><button id=edit-redo ${
    !editRedo.length ? "disabled" : ""
  } aria-label=Redo>Redo</button><button id=edit-before aria-pressed="${editBefore}">${
    editBefore ? "Show edit" : "Compare"
  }</button><button id=edit-export ${
    video ? "disabled" : ""
  }>Save copy</button></header><aside class=studio-project><h3>Project</h3>${projectTree()}<h3>Workspaces</h3><button data-workspace=Canvas>Canvas</button><button data-workspace=Code>Code</button><button data-workspace=Project>Project explorer</button></aside><div class=studio-canvas>${
    activeWorkspace === "Nodes" && premium
      ? nodeGraph()
      : video
      ? `<video controls src="${esc(
          src({
            uri: editProject.media[0].uri,
            type: "VIDEO",
            timestamp: editProject.timeline.playhead * 1000,
          })
        )}"></video>`
      : '<canvas id=edit-canvas aria-label="Photo edit preview"></canvas>'
  }<span class=canvas-caption>${
    video ? "SOURCE PREVIEW" : editBefore ? "ORIGINAL" : "EDITED COPY"
  } · ${esc(editProject.name)}</span></div><aside class="studio-inspector ${
    activeWorkspace === "Canvas" ? "resting" : ""
  }"><div class=inspector-top><small>${
    activeWorkspace === "Canvas" ? "INSPECTOR" : activeWorkspace.toUpperCase()
  }</small><button data-workspace=Canvas aria-label="Close panel">${ai(
    "close"
  )}</button></div>${inspector(video, premium)}<p class=note role=status>${esc(
    editNotice
  )}</p></aside>${
    video && premium && !dedicated ? timelineView() : ""
  }<nav class=studio-workspaces>${tools
    .map(
      ([w, l]) =>
        `<button data-workspace=${w} aria-pressed="${
          activeWorkspace === w
        }">${ai(
          w === "Layers"
            ? "layers"
            : w === "Edit"
            ? "settings"
            : w === "More"
            ? "more"
            : w === "Crop"
            ? "crop"
            : "film"
        )}<span>${l}</span></button>`
    )
    .join("")}</nav></div>`;
}
function inspector(video, premium) {
  if (["Video", "Nodes", "Code"].includes(activeWorkspace) && !premium)
    return "<h3>Honorable Studio</h3><p>This workspace requires an active Studio membership.</p>";
  if (activeWorkspace === "Canvas")
    return "<h3>Make it yours.</h3><p class=note>Select a tool to begin. Your original stays untouched.</p>";
  if (activeWorkspace === "More")
    return "<h3>Workspace</h3><div class=tools><button data-workspace=Code>Code</button><button data-workspace=Project>Project</button><button id=edit-before>Before / After</button><button id=reset-photo>Reset photo</button><button data-workspace=Nodes>Nodes</button></div>";
  if (activeWorkspace === "Crop")
    return `<h3>Crop & rotate</h3><label>Center crop<output>${Math.round(
      editProject.adjustments.crop * 100
    )}%</output><input data-adjust=crop type=range min=.2 max=1 step=.05 value=${
      editProject.adjustments.crop
    }></label><button id=rotate-photo>Rotate 90°</button>`;
  if (activeWorkspace === "Pose")
    return "<h3>Pose · Partial</h3><button data-capability=basicPoseRig>View availability</button>";
  if (activeWorkspace === "AI")
    return "<h3>AI tools</h3><button data-capability=generativeFill>Generative Fill · Coming Soon</button><button data-capability=expansion>Expansion · Coming Soon</button>";
  if (activeWorkspace === "Edit")
    return video
      ? "<h3>Video preview</h3><p>Moment context preserved. Sequence editing is a foundation; video export is Coming Soon.</p>"
      : `<h3>Adjust</h3><div class=adjust-tabs>${[
          "brightness",
          "contrast",
          "saturation",
          "exposure",
        ]
          .map(
            (k) =>
              `<button data-adjust-tab=${k} aria-pressed="${
                activeAdjustment === k
              }">${
                k === "saturation" ? "Color" : k[0].toUpperCase() + k.slice(1)
              }</button>`
          )
          .join("")}</div>${[
          ["brightness", 0, 2, 0.05],
          ["contrast", 0, 2, 0.05],
          ["saturation", 0, 2, 0.05],
          ["exposure", -2, 2, 0.1],
        ]
          .filter(([k]) => k === activeAdjustment)
          .map(
            ([k, min, max, step]) =>
              `<label>${k}<output>${Math.round(
                (editProject.adjustments[k] - (k === "exposure" ? 0 : 1)) * 100
              )}</output><input data-adjust=${k} type=range min=${min} max=${max} step=${step} value=${
                editProject.adjustments[k]
              } aria-label=${k}></label>`
          )
          .join(
            ""
          )}<div class=tools><button id=rotate-photo>Rotate 90°</button><button id=filter-photo>Monochrome</button><button id=reset-photo>Reset</button></div>`;
  if (activeWorkspace === "Layers")
    return `<h3>Layers</h3>${editProject.layers
      .map(
        (l, i) =>
          `<div class=layer-row><img src="${esc(
            editProject.media[0].kind === "VIDEO"
              ? "prompt_beach.png"
              : editProject.media[0].uri
          )}" alt=""><span>${esc(l.name)}<small>${esc(l.type)} · ${Math.round(
            l.opacity * 100
          )}%</small></span><label title=Visibility><input aria-label="${esc(
            l.name
          )} visibility" data-layer-visible=${i} type=checkbox ${
            l.visible ? "checked" : ""
          }></label><button data-layer-lock=${i} aria-label="${
            l.locked ? "Unlock" : "Lock"
          } ${esc(l.name)}">${ai(
            l.locked ? "lock" : "settings"
          )}</button><label class=layer-opacity>Opacity<input data-layer-opacity=${i} type=range min=0 max=1 step=.05 value=${
            l.opacity
          } aria-label="Layer opacity" ${
            l.locked ? "disabled" : ""
          }></label></div>`
      )
      .join(
        ""
      )}<p class=note>Smart layers and blend engines are Coming Soon.</p>`;
  if (activeWorkspace === "Video")
    return `<h3>Sequence tools</h3><div class=tools><button id=timeline-add>Add source clip</button><button id=timeline-split ${
      !selectedClip ? "disabled" : ""
    }>Split at playhead</button><button id=timeline-trim ${
      !selectedClip ? "disabled" : ""
    }>Trim start to playhead</button><button id=timeline-marker>Add marker</button></div><p class=note>Clip and marker edits save to the project. Multi-track rendering and video export are unavailable.</p>`;
  if (activeWorkspace === "Nodes")
    return "<h3>Node graph</h3><p class=note>FOUNDATION · Pan the canvas and zoom to inspect project nodes. Graph execution is unavailable.</p><button id=view-code>View Project as Code</button>";
  if (activeWorkspace === "Project")
    return `<h3>Project explorer</h3><input id=project-search placeholder="Search project" value="${esc(
      projectSearch
    )}">${projectTree()}<button id=view-code>View Project as Code</button><div class=tools>${[
      "New script",
      "New folder",
      "Rename / move",
      "Duplicate",
      "Delete",
    ]
      .map((t, i) => `<button data-file-op=${i}>${t}</button>`)
      .join("")}</div>`;
  if (activeWorkspace === "Representation")
    return `<h3>View Project as Code</h3><pre>${esc(
      HP.serialize(editProject)
    )}</pre><button id=project-download>Export project</button>`;
  const file = editProject.files[editFile];
  return `<nav class=code-tabs>${["FILES", "EDITOR", "OUTPUT", "PREVIEW"]
    .map(
      (t) =>
        `<button data-code-tab=${t} aria-pressed="${
          codeTab === t
        }">${t}</button>`
    )
    .join("")}</nav>${
    codeTab === "FILES"
      ? projectTree()
      : codeTab === "OUTPUT"
      ? "<h3>Output</h3><p>Execution is unavailable. No script has been run.</p>"
      : codeTab === "PREVIEW"
      ? `<h3>Project preview</h3><pre>${esc(HP.serialize(editProject))}</pre>`
      : `<div class=tools><button aria-pressed="${
          editFile === "scripts/main.py"
        }" data-script="scripts/main.py">main.py</button><button aria-pressed="${
          editFile === "scripts/automation.java"
        }" data-script="scripts/automation.java">automation.java</button></div><div class=code-file-title>${esc(
          editFile
        )}<small>${
          file?.language === "java" ? "Java" : "Python"
        }</small></div><div class=code-editor><pre id=syntax-preview aria-hidden=true>${syntax(
          file?.text || ""
        )}</pre><textarea id=script-editor aria-label="Script editor" spellcheck=false autocapitalize=off wrap=off>${esc(
          file?.text || ""
        )}</textarea></div><div class=tools><button id=script-save>Save</button><button id=script-run disabled title="Execution is unavailable">Run</button><button id=script-stop disabled>Stop</button><button id=view-code>View Project as Code</button></div><p class=note>Execution is unavailable. Scripts are saved in this project.</p>`
  }`;
}
function renderPixels(canvas, full = false) {
  if (!editImage || !canvas) return;
  const a = editBefore ? HP.defaults() : editProject.adjustments;
  const crop = a.crop,
    sw = editImage.naturalWidth * crop,
    sh = editImage.naturalHeight * crop,
    rot = a.rotation % 180 !== 0;
  const scale = full ? 1 : Math.min(1, 1280 / Math.max(sw, sh));
  canvas.width = Math.max(1, Math.round((rot ? sh : sw) * scale));
  canvas.height = Math.max(1, Math.round((rot ? sw : sh) * scale));
  const ctx = canvas.getContext("2d");
  ctx.translate(canvas.width / 2, canvas.height / 2);
  ctx.rotate((a.rotation * Math.PI) / 180);
  ctx.filter = `brightness(${
    a.brightness * Math.pow(2, a.exposure)
  }) contrast(${a.contrast}) saturate(${a.saturation}) ${
    a.filter === "mono" ? "grayscale(1)" : ""
  }`;
  const layer = editProject.layers[0];
  ctx.globalAlpha = editBefore ? 1 : layer.visible ? layer.opacity : 0;
  ctx.drawImage(
    editImage,
    (editImage.naturalWidth - sw) / 2,
    (editImage.naturalHeight - sh) / 2,
    sw,
    sh,
    (-sw * scale) / 2,
    (-sh * scale) / 2,
    sw * scale,
    sh * scale
  );
}
function downloadBlob(blob, name) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 10000);
}
const priorStudioBind = window.bindWebTest;
window.bindWebTest = () => {
  priorStudioBind();
  const on = (id, fn) =>
    screen.querySelector("#" + id)?.addEventListener("click", fn);
  on("cancel-product-search", async () => {
    const id = activeSearch;
    if (!id) return;
    activeSearchController?.abort();
    activeSearch = null;
    state.results = null;
    render();
    try {
      await webRequest("/account/v1/search/complete", {
        requestId: id,
        outcome: "CANCELLED",
      });
    } catch (e) {
      webNotice = e.message;
      render();
    }
  });
  on("model-open", () => {
    modelSheet = true;
    render();
  });
  on("model-close", () => {
    modelSheet = false;
    render();
  });
  on("category-close", () => {
    studioCategory = null;
    render();
  });
  screen.querySelectorAll("[data-category]").forEach(
    (b) =>
      (b.onclick = () => {
        studioCategory = b.dataset.category;
        render();
        screen
          .querySelector(".category-detail")
          ?.scrollIntoView({ block: "nearest" });
      })
  );
  screen.querySelectorAll("[data-open-advanced]").forEach(
    (b) =>
      (b.onclick = () => {
        if (accountState?.subscription?.status !== "ACTIVE") {
          webNotice = "This workspace requires Studio.";
          render();
          return;
        }
        activeWorkspace = b.dataset.openAdvanced;
        editWorkspace = "__editing";
        state.app = "viewer";
        render();
      })
  );
  screen.querySelectorAll("[data-library-filter]").forEach(
    (b) =>
      (b.onclick = () => {
        state.libraryFilter = b.dataset.libraryFilter;
        render();
      })
  );
  screen.querySelectorAll("[data-library]").forEach(
    (b) =>
      (b.onclick = () => {
        state.opened = +b.dataset.library;
        state.preview = state.media[state.opened];
        go("viewer");
      })
  );
  on("toggle-passes", () => {
    showAllPasses = !showAllPasses;
    render();
  });
  on("usage-more", () => {
    usageLimit += 20;
    render();
  });
  on("studio-unlock", () => {
    webNotice =
      "Studio is a monthly membership for advanced creative tools. Store subscriptions are not enabled yet.";
    render();
  });
  on("studio-test", () =>
    webAction(async () => {
      await webRequest("/account/dev/studio", {
        active: accountState?.subscription?.status !== "ACTIVE",
      });
      await loadAccount();
    })
  );
  screen.querySelectorAll("[data-product-model]").forEach(
    (b) =>
      (b.onclick = () => {
        state.model = b.dataset.productModel;
        modelSheet = false;
        render();
      })
  );
  screen.querySelectorAll("[data-capability]").forEach(
    (b) =>
      (b.onclick = () => {
        const result = HP.route(b.dataset.capability, {
          studio: accountState?.subscription?.status === "ACTIVE",
        });
        const message =
          result.reason === "STUDIO_REQUIRED"
            ? "This advanced tool requires Honorable Studio."
            : result.blocker || "This capability is Coming Soon.";
        webNotice = message;
        editNotice = message;
        render();
      })
  );
  screen.querySelectorAll("[data-code-tab]").forEach(
    (b) =>
      (b.onclick = () => {
        const editor = screen.querySelector("#script-editor");
        if (editor)
          commitEdit(
            HP.fileOperation(editProject, "save", editFile, null, editor.value)
          );
        codeTab = b.dataset.codeTab;
        render();
      })
  );
  on("node-in", () => {
    nodeZoom = Math.min(2, nodeZoom + 0.1);
    render();
  });
  on("node-out", () => {
    nodeZoom = Math.max(0.4, nodeZoom - 0.1);
    render();
  });
  on("node-reset", () => {
    nodeZoom = 1;
    nodePan = { x: 0, y: 0 };
    render();
  });
  const viewport = screen.querySelector(".node-viewport");
  if (viewport) {
    viewport.onpointerdown = (e) => {
      if (e.target.closest("button")) return;
      viewport.setPointerCapture(e.pointerId);
      let last = { x: e.clientX, y: e.clientY };
      viewport.onpointermove = (e) => {
        nodePan.x += e.clientX - last.x;
        nodePan.y += e.clientY - last.y;
        last = { x: e.clientX, y: e.clientY };
        viewport.querySelector(
          ".node-plane"
        ).style.transform = `translate(${nodePan.x}px,${nodePan.y}px) scale(${nodeZoom})`;
      };
      viewport.onpointerup = () => {
        viewport.onpointermove = null;
      };
    };
  }
  screen.querySelectorAll("[data-layer-lock]").forEach(
    (b) =>
      (b.onclick = () => {
        const p = HP.clone(editProject);
        p.layers[+b.dataset.layerLock].locked =
          !p.layers[+b.dataset.layerLock].locked;
        commitEdit(p);
        render();
      })
  );
  on("edit-result", () =>
    openEditor(state.preview || state.media[state.opened])
  );
  on("share-result", async () => {
    const item = state.preview || state.media[state.opened];
    if (navigator.share) {
      try {
        await navigator.share({
          title: item.name,
          url: new URL(src(item), location.href).href,
        });
      } catch {}
    } else {
      webNotice =
        "Sharing is unavailable in this browser. Use Open to access the media.";
      render();
    }
  });
  on("continue-edit", () => {
    editWorkspace = "__editing";
    state.app = "viewer";
    render();
  });
  on("editor-close", () => {
    editWorkspace = "Edit";
    render();
  });
  on("edit-undo", () => {
    editRedo.push(HP.clone(editProject));
    editProject = editUndo.pop();
    persistProject();
    render();
  });
  on("edit-redo", () => {
    editUndo.push(HP.clone(editProject));
    editProject = editRedo.pop();
    persistProject();
    render();
  });
  screen.querySelectorAll("#edit-before").forEach(
    (b) =>
      (b.onclick = () => {
        editBefore = !editBefore;
        render();
      })
  );
  screen.querySelectorAll("[data-adjust-tab]").forEach(
    (button) =>
      (button.onclick = () => {
        activeAdjustment = button.dataset.adjustTab;
        render();
      })
  );
  screen.querySelectorAll("[data-adjust]").forEach((input) => {
    input.oninput = () => {
      const output = input.parentElement.querySelector("output");
      if (output)
        output.textContent =
          input.dataset.adjust === "crop"
            ? Math.round(+input.value * 100) + "%"
            : Math.round(
                (+input.value - (input.dataset.adjust === "exposure" ? 0 : 1)) *
                  100
              );
      const preview = HP.clone(editProject);
      preview.adjustments[input.dataset.adjust] = +input.value;
      const old = editProject;
      editProject = preview;
      renderPixels(screen.querySelector("#edit-canvas"));
      editProject = old;
    };
    input.onchange = () => {
      const p = HP.clone(editProject);
      p.adjustments[input.dataset.adjust] = +input.value;
      commitEdit(p);
      render();
    };
  });
  for (const [id, change] of [
    ["rotate-photo", (a) => (a.rotation = (a.rotation + 90) % 360)],
    ["filter-photo", (a) => (a.filter = a.filter === "mono" ? "none" : "mono")],
    ["reset-photo", (a) => Object.assign(a, HP.defaults())],
  ])
    on(id, () => {
      const p = HP.clone(editProject);
      change(p.adjustments);
      commitEdit(p);
      render();
    });
  on("edit-export", () => {
    const canvas = document.createElement("canvas"),
      before = editBefore;
    editBefore = false;
    renderPixels(canvas, true);
    editBefore = before;
    canvas.toBlob((blob) => {
      if (!blob) {
        editNotice = "Export failed";
        render();
        return;
      }
      downloadBlob(blob, "Honorable-copy.png");
      editProject.exports.push({
        name: "Honorable-copy.png",
        createdAt: new Date().toISOString(),
      });
      persistProject();
      editNotice = "Saved as a new PNG copy. Original preserved.";
      render();
    }, "image/png");
  });
  screen.querySelectorAll("[data-workspace]").forEach(
    (b) =>
      (b.onclick = () => {
        if (
          ["Video", "Nodes", "Code"].includes(b.dataset.workspace) &&
          accountState?.subscription?.status !== "ACTIVE"
        ) {
          editNotice = "This workspace requires Honorable Studio.";
        } else {
          activeWorkspace = b.dataset.workspace;
          editNotice = "";
        }
        render();
      })
  );
  screen.querySelectorAll("[data-layer-visible],[data-layer-opacity]").forEach(
    (input) =>
      (input.onchange = () => {
        const p = HP.clone(editProject);
        if (input.dataset.layerVisible !== undefined)
          p.layers[+input.dataset.layerVisible].visible = input.checked;
        else p.layers[+input.dataset.layerOpacity].opacity = +input.value;
        commitEdit(p);
        render();
      })
  );
  screen.querySelector("#playhead")?.addEventListener("input", (e) => {
    editProject.timeline.playhead = +e.target.value;
    const video = screen.querySelector("video");
    if (video) video.currentTime = +e.target.value;
    const time = screen.querySelector("#timeline-time");
    if (time)
      time.textContent = editProject.timeline.playhead.toFixed(2) + " s";
    const line = screen.querySelector(".playhead-line");
    if (line) line.style.left = (+e.target.value / +e.target.max) * 100 + "%";
    persistProject();
  });
  on("view-code", () => {
    activeWorkspace = "Representation";
    render();
  });
  on("project-download", () =>
    downloadBlob(
      new Blob([HP.serialize(editProject)], { type: "application/json" }),
      "project.honorable"
    )
  );
  screen.querySelectorAll("[data-script]").forEach(
    (b) =>
      (b.onclick = () => {
        const editor = screen.querySelector("#script-editor");
        if (editor)
          commitEdit(
            HP.fileOperation(editProject, "save", editFile, null, editor.value)
          );
        editFile = b.dataset.script;
        codeTab = "EDITOR";
        render();
      })
  );
  on("script-save", () => {
    commitEdit(
      HP.fileOperation(
        editProject,
        "save",
        editFile,
        null,
        screen.querySelector("#script-editor").value
      )
    );
    editNotice = "Script saved locally.";
    render();
  });
  on("script-run", () => {
    editNotice = HP.sandbox.validate(
      editProject.files[editFile].language,
      screen.querySelector("#script-editor").value
    ).message;
    render();
  });
  on("script-stop", () => {
    editNotice = "No script is running.";
    render();
  });
  screen.querySelector("#project-search")?.addEventListener("change", (e) => {
    projectSearch = e.target.value;
    render();
  });
  screen.querySelectorAll("[data-file]").forEach(
    (b) =>
      (b.onclick = () => {
        if (editProject.files[b.dataset.file]?.kind === "script") {
          editFile = b.dataset.file;
          codeTab = "EDITOR";
          if (accountState?.subscription?.status === "ACTIVE")
            activeWorkspace = "Code";
          else editNotice = "The Code workspace requires Studio.";
        } else activeWorkspace = "Representation";
        render();
      })
  );
  screen.querySelectorAll("[data-file-op]").forEach(
    (b) =>
      (b.onclick = () => {
        const i = +b.dataset.fileOp,
          from = prompt(
            i < 2 ? "Virtual project path" : "Existing virtual path",
            i < 2 ? "scripts/new.py" : editFile
          );
        if (!from) return;
        const to = i === 2 || i === 3 ? prompt("Destination path") : null;
        if (
          i === 4 &&
          !confirm("Delete this virtual file/folder and its children?")
        )
          return;
        try {
          commitEdit(
            HP.fileOperation(
              editProject,
              ["create", "folder", "move", "duplicate", "delete"][i],
              from,
              to
            )
          );
          editNotice = "Project updated";
        } catch (e) {
          editNotice = e.message;
        }
        render();
      })
  );
  screen.querySelector("#script-editor")?.addEventListener("scroll", (e) => {
    const pre = screen.querySelector("#syntax-preview");
    pre.scrollTop = e.target.scrollTop;
    pre.scrollLeft = e.target.scrollLeft;
  });
  screen.querySelector("#script-editor")?.addEventListener("input", (e) => {
    screen.querySelector("#syntax-preview").innerHTML = syntax(e.target.value);
  });
  const timelineChange = (op) => {
    try {
      commitEdit(
        HP.timelineOperation(editProject, op, {
          studio: accountState?.subscription?.status === "ACTIVE",
        })
      );
      editNotice =
        "Timeline state updated. Multi-track rendering is Coming Soon.";
    } catch (e) {
      editNotice = e.message;
    }
    render();
  };
  on("timeline-add", () => {
    const duration = screen.querySelector("video")?.duration;
    if (!Number.isFinite(duration) || duration <= 0) {
      editNotice = "Wait for video metadata before adding a clip.";
      render();
      return;
    }
    if (!editProject.timeline.tracks.length)
      commitEdit(
        HP.timelineOperation(
          editProject,
          { type: "addTrack", id: "video-1", kind: "video" },
          { studio: accountState?.subscription?.status === "ACTIVE" }
        )
      );
    timelineChange({
      type: "addClip",
      trackId: "video-1",
      id: crypto.randomUUID(),
      mediaId: "source",
      duration,
    });
  });
  screen.querySelectorAll("[data-clip]").forEach(
    (b) =>
      (b.onclick = () => {
        selectedClip = b.dataset.clip;
        editNotice = "Clip selected";
        render();
      })
  );
  on("timeline-split", () =>
    timelineChange({
      type: "split",
      trackId: "video-1",
      clipId: selectedClip,
      id: crypto.randomUUID(),
      time: editProject.timeline.playhead,
    })
  );
  on("timeline-trim", () => {
    const clip = editProject.timeline.tracks
      .flatMap((t) => t.clips)
      .find((c) => c.id === selectedClip);
    timelineChange({
      type: "trim",
      trackId: "video-1",
      clipId: selectedClip,
      in: editProject.timeline.playhead,
      out: clip?.out,
    });
  });
  on("timeline-marker", () =>
    timelineChange({
      type: "marker",
      time: editProject.timeline.playhead,
      label: "Marker",
    })
  );
  renderPixels(screen.querySelector("#edit-canvas"));
};
fetchProductCatalog().catch(() => {});
render();
