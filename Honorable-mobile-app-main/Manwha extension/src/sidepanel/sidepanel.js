const app = document.querySelector("#app");
document.querySelector("#refresh").addEventListener("click", load);
chrome.storage.onChanged.addListener((changes) => {
  if (changes.selectedCharacterId) load();
});

const escapeHtml = (value) => String(value ?? "").replace(/[&<>'"]/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;" })[char]);
const tidyText = (value) => String(value ?? "").replace(/\s*\[\s*\d+(?:\s*[,–-]\s*\d+)*\s*\]\s*/g, " ")
  .replace(/[†‡]+/g, " • ").replace(/(?:\s*•\s*){2,}/g, " • ").replace(/\s+/g, " ").trim();
function formatFieldValue(key, value) {
  const tidy = tidyText(value);
  const relationship = /family|relative|partner|spouse|parent|sibling|children|master|disciple|relationship/i.test(key);
  const parts = tidy.split(/\s*•\s*|\s*;\s*/).map((part) => part.trim()).filter(Boolean);
  if ((relationship && parts.length > 1) || parts.length >= 4) {
    return `<ul class="fact-list">${parts.slice(0, 30).map((part) => `<li>${escapeHtml(part)}</li>`).join("")}</ul>`;
  }
  return escapeHtml(tidy);
}

async function load() {
  app.innerHTML = '<section class="loading">Loading character…</section>';
  const result = await chrome.runtime.sendMessage({ type: "GET_SELECTION" }).catch((error) => ({ ok: false, error: error.message }));
  if (result.empty) return result.activeNovel?.wiki ? showWaiting(result.activeNovel, result.unaddedCount) : result.activeNovel ? showWikiSetup(result.activeNovel) : showEmpty();
  if (!result.ok) return showError(result.error);
  render(result);
}

function showEmpty() {
  app.innerHTML = '<section class="empty"><div class="mark">N</div><h1>Select a character</h1><p>Character names in recognized chapters appear in blue.</p></section>';
}

function showWaiting(profile, unaddedCount = 0) {
  app.innerHTML = `<section class="empty"><div class="mark">N</div><h1>${escapeHtml(profile.title)}</h1><p>The character index is ready. Click a blue character name in the chapter.</p>
    ${unaddedCount ? `<div class="queue-status"><strong>${unaddedCount}</strong><span>unadded names being checked</span></div>` : ""}</section>`;
}

function showWikiSetup(profile) {
  app.innerHTML = `<section><div class="mark">N</div><h1>${escapeHtml(profile.title)}</h1>
    <p class="summary">NovelLens recognized this novel, but could not safely identify its character wiki automatically.</p>
    <form id="wiki-form" class="actions">
      <label for="wiki-host">Fandom wiki address</label>
      <input id="wiki-host" required placeholder="example.fandom.com" autocomplete="off">
      <button class="primary">Connect character wiki</button>
    </form><p id="wiki-error" class="error"></p></section>`;
  document.querySelector("#wiki-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const button = event.currentTarget.querySelector("button");
    button.disabled = true; button.textContent = "Importing characters…";
    const result = await chrome.runtime.sendMessage({ type: "LINK_WIKI", profile, hostname: document.querySelector("#wiki-host").value });
    if (!result.ok) {
      document.querySelector("#wiki-error").textContent = result.error;
      button.disabled = false; button.textContent = "Connect character wiki";
    } else {
      app.innerHTML = `<section class="empty"><div class="mark">✓</div><h1>Wiki connected</h1><p>${result.characters.length} characters imported. Reload the chapter once to enable highlights.</p></section>`;
    }
  });
}

function showError(message) {
  app.innerHTML = `<section class="error"><h1>Couldn't load character</h1><p>${escapeHtml(message)}</p></section>`;
}

function render({ details, character, profile, warning, inDatabase }) {
  const entries = Object.entries(details.fields || {}).filter(([, value]) => String(value).trim());
  const groupRules = [
    ["Identity", /gender|sex|race|species|age|birth|origin|nationality|height|hair|eyes/i],
    ["Story timeline", /debut|first appearance|introduction|joined|join date|chapter|volume/i],
    ["Affiliation", /affiliation|organization|occupation|profession|guild|clan|sect|team|residence/i],
    ["Power & abilities", /rank|level|class|cultivation|sequence|pathway|ability|abilities|power|weapon|skill|magic/i],
    ["Relationships", /family|relative|partner|spouse|parent|sibling|children|master|disciple/i]
  ];
  const spoilerPattern = /death|deceased|status|fate|current|former|transformation|true identity|future/i;
  const grouped = new Map(groupRules.map(([name]) => [name, []]));
  const other = [];
  for (const entry of entries) {
    const group = groupRules.find(([, pattern]) => pattern.test(entry[0]));
    (group ? grouped.get(group[0]) : other).push(entry);
  }
  if (other.length) grouped.set("More details", other);
  const fieldRows = (items) => items.map(([key, value]) => `<div class="field"><dt>${escapeHtml(key)}</dt><dd>${formatFieldValue(key, value)}</dd></div>`).join("");
  const section = (name, items, hidden = false) => items.length ? `<section class="profile-section${hidden ? " spoiler-section" : ""}"${hidden ? " hidden" : ""}><div class="section-heading"><span>${escapeHtml(name)}</span><span>${items.length}</span></div><dl>${fieldRows(items)}</dl></section>` : "";
  const visibleSections = [], spoilerSections = [];
  for (const [name, items] of grouped) {
    const visible = items.filter(([key]) => !spoilerPattern.test(key));
    const spoilers = items.filter(([key]) => spoilerPattern.test(key));
    if (visible.length) visibleSections.push(section(name, visible));
    if (spoilers.length) spoilerSections.push(section(name, spoilers, true));
  }
  const wikiUrl = `https://${profile.wiki.hostname}/wiki/${encodeURIComponent(details.sourcePageTitle.replace(/ /g, "_"))}`;
  const roleNames = { MC: "Main Character", FMC: "Female Lead", ANT: "Antagonist", SUPPORT: "Supporting", CHARACTER: "Character" };
  const categoryText = (character.categories || []).join(" | ");
  const entryKind = /locations?|cities|countries|districts?/i.test(categoryText) ? "Location"
    : /items?|weapons?|artifacts?/i.test(categoryText) ? "Artifact"
    : /organizations?|factions?|guilds?|clans?/i.test(categoryText) ? "Faction"
    : roleNames[character.roleCode] || "Character";
  const role = entryKind;
  app.innerHTML = `
    <article class="${inDatabase ? "database-known" : ""}">
      <div class="profile-titlebar"><span class="title-glyph">✦</span><div><span>Story profile</span><h1>${escapeHtml(details.displayName)}</h1></div></div>
      <div class="hero">${details.imageUrl ? `<img class="portrait" src="${escapeHtml(details.imageUrl)}" alt="${escapeHtml(details.displayName)}"><div class="portrait-shade"></div><span class="image-source">Fandom image</span>` : '<div class="portrait placeholder">N</div>'}
        <div class="hero-caption"><span class="art-label">Profile art <b>1 / 1</b></span><span class="role role-${escapeHtml(character.roleCode || "CHARACTER")}">${escapeHtml(role)}</span></div></div>
      <div class="identity-strip"><div><strong>${escapeHtml(details.displayName)}</strong><span>${escapeHtml(profile.title)}</span></div>${inDatabase ? '<div class="database-badge"><i></i> Verified</div>' : ""}</div>
      ${details.aliases?.length ? `<p class="aliases">Also known as ${details.aliases.map(escapeHtml).join(" · ")}</p>` : ""}
      <div class="quick-stats"><div><strong>${escapeHtml(entryKind)}</strong><span>Entry type</span></div><div><strong>${escapeHtml(entries.length)}</strong><span>Wiki facts</span></div><div><strong>${details.imageUrl ? "HD" : "—"}</strong><span>Artwork</span></div></div>
      <section class="overview"><h2><span>▣</span> Background & context</h2><p class="summary">${escapeHtml(tidyText(details.summary))}</p></section>
      ${warning ? `<p class="warning">Showing cached information: ${escapeHtml(warning)}</p>` : ""}
      ${visibleSections.join("")}
      ${spoilerSections.join("")}
      ${spoilerSections.length ? '<p class="warning">Additional status and late-story information may contain spoilers.</p>' : ""}
      <div class="actions">
        ${spoilerSections.length ? '<button class="primary" id="show-full">Reveal spoiler details</button>' : ""}
        <a class="secondary" href="${escapeHtml(wikiUrl)}" target="_blank" rel="noopener noreferrer">Read on ${escapeHtml(profile.wiki.hostname)} ↗</a>
      </div>
      <p class="source">Live information provided by the novel’s Fandom community. Page ID ${escapeHtml(character.wikiPageId)}.</p>
    </article>`;
  document.querySelector("#show-full")?.addEventListener("click", (event) => {
    document.querySelectorAll(".spoiler-section").forEach((sectionNode) => { sectionNode.hidden = false; });
    event.currentTarget.remove();
  });
}

load();
