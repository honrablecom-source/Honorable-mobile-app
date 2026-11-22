import {sampleVideo} from './video-samples.js';

'use strict';

const folder = document.querySelector('#folder');
const query = document.querySelector('#query');
const status = document.querySelector('#status');
const results = document.querySelector('#results');
const preview = document.querySelector('#preview');
const more = document.querySelector('#more');
const pageSize = 50;
const tabs = Array.from(document.querySelectorAll('nav a[href^="#"]'));

const activityKey = 'noble.home.activity';
let activity = [];
try {
  const saved = JSON.parse(localStorage.getItem(activityKey));
  if (Array.isArray(saved)) activity = saved.filter(item => typeof item?.label === 'string' && Number.isFinite(item.at)).slice(0,50);
} catch {}
const compact = document.querySelector('#compact-covers');
const remember = document.querySelector('#remember-history');
compact.checked = localStorage.getItem('noble.home.compact') === 'true';
remember.checked = localStorage.getItem('noble.home.remember') !== 'false';
document.body.classList.toggle('compact-covers',compact.checked);
compact.onchange = () => {
  document.body.classList.toggle('compact-covers',compact.checked);
  localStorage.setItem('noble.home.compact',String(compact.checked));
};
remember.onchange = () => localStorage.setItem('noble.home.remember',String(remember.checked));
document.querySelector('.desktop-layout').addEventListener('click', event => {
  const link = event.target.closest('a');
  if (!link || !link.closest('#studio,.file-menu') || !remember.checked) return;
  activity.unshift({label:link.getAttribute('aria-label') || link.textContent.trim(),at:Date.now()});
  activity = activity.slice(0,50);
  localStorage.setItem(activityKey,JSON.stringify(activity));
});

function showSection() {
  const selected = tabs.find(tab => tab.hash === location.hash)?.hash || '#studio';
  for (const tab of tabs) {
    document.querySelector(tab.hash).hidden = tab.hash !== selected;
    if (tab.hash === selected) tab.setAttribute('aria-current','page');
    else tab.removeAttribute('aria-current');
  }
  document.querySelector('#page-title').textContent = tabs.find(tab => tab.hash === selected).getAttribute('aria-label');
  if (selected === '#history') {
    document.querySelector('#empty-history').hidden = Boolean(activity.length);
    document.querySelector('#activity-list').replaceChildren(...activity.map(item => {
      const row = document.createElement('li');
      const time = document.createElement('time');
      const at = new Date(item.at);
      time.dateTime = at.toISOString();
      time.textContent = at.toLocaleString();
      row.append(item.label,time);
      return row;
    }));
  }
}

window.addEventListener('hashchange', showSection);
showSection();
document.querySelector('#choose-folder').onclick = () => folder.click();
document.querySelector('[data-empty-folder]').onclick = () => folder.click();
const emptyPanel = document.querySelector('[data-library-empty]');
const libraryPanel = document.querySelector('.library-panel');
const folderCaption = document.querySelector('[data-library-folder]');
const previewTitle = document.querySelector('[data-preview-title]');
const previewEmpty = document.querySelector('[data-preview-empty]');
let files = [];
let visibleCount = pageSize;
let previewUrl;
const description = document.querySelector('#description');
const findPhotos = document.querySelector('#find-photos');
let searchWorker;
let rankedPhotos;
let photoIndex;
let busy = false;

function clearPreview() {
  preview.replaceChildren();
  if (previewUrl) URL.revokeObjectURL(previewUrl);
  previewUrl = undefined;
  previewTitle.textContent = 'Preview';
  previewEmpty.hidden = false;
}

function openPreview(file) {
  clearPreview();
  previewTitle.textContent = file.name;
  previewEmpty.hidden = true;
  previewUrl = URL.createObjectURL(file);
  const isVideo = file.type.startsWith('video/');
  const media = document.createElement(isVideo ? 'video' : 'img');
  if (isVideo) {
    media.controls = true;
    media.setAttribute('aria-label', file.name);
  } else {
    media.alt = file.name;
  }
  media.addEventListener('error', () => {
    if (media.parentElement !== preview) return;
    clearPreview();
    preview.textContent = 'This file could not be opened. Its format may be unsupported.';
  }, {once: true});
  media.src = previewUrl;
  preview.append(media);
}

let selectedFile;

function render() {
  const term = query.value.trim().toLowerCase();
  const matches = rankedPhotos ?? files.filter(file => file.name.toLowerCase().includes(term));
  if (selectedFile && !matches.includes(selectedFile)) {
    selectedFile = undefined;
    clearPreview();
  }
  const visible = matches.slice(0, visibleCount);
  results.replaceChildren(...visible.map(file => {
    const row = document.createElement('li');
    const button = document.createElement('button');
    button.type = 'button';
    button.textContent = file.name;
    const path = file.webkitRelativePath;
    if (path && path.split('/').length > 2) button.dataset.subfolder = path.split('/').slice(1,-1).join('/');
    button.title = path || file.name;
    button.dataset.mediaType = file.type.startsWith('video/') ? 'Video' : 'Photo';
    button.setAttribute('aria-pressed', String(file === selectedFile));
    button.addEventListener('click', () => {
      selectedFile = file;
      for (const rowButton of results.querySelectorAll('button')) {
        rowButton.setAttribute('aria-pressed', String(rowButton === button));
      }
      openPreview(file);
    });
    row.append(button);
    return row;
  }));
  more.hidden = visible.length === matches.length;
  status.textContent = matches.length
    ? 'Showing ' + visible.length + ' of ' + matches.length + ' files.'
    : 'No matching photos or videos.';
}

folder.addEventListener('change', () => {
  clearPreview();
  selectedFile = undefined;
  searchWorker?.terminate();
  searchWorker = rankedPhotos = photoIndex = undefined;
  busy = false;
  files = Array.from(folder.files).filter(file => /^(image|video)\//.test(file.type));
  emptyPanel.hidden = true;
  libraryPanel.hidden = false;
  const root = folder.files[0]?.webkitRelativePath.split('/')[0];
  folderCaption.textContent = root || 'Selected folder';
  document.querySelector('#choose-folder').textContent = 'Change folder';
  files.sort((a, b) => a.name.localeCompare(b.name));
  query.disabled = files.length === 0;
  findPhotos.disabled = files.length === 0;
  query.value = '';
  visibleCount = pageSize;
  render();
});

query.addEventListener('input', () => {
  rankedPhotos = undefined;
  visibleCount = pageSize;
  render();
});

more.addEventListener('click', () => {
  visibleCount += pageSize;
  render();
  if (more.hidden) results.lastElementChild?.firstElementChild.focus();
});

document.querySelector('#semantic').addEventListener('submit', async event => {
  event.preventDefault();
  if (!description.value.trim() || busy) return;
  const firstSearch = !searchWorker;
  if (firstSearch) {
    photoIndex = [...files.filter(file => file.type.startsWith('image/')).slice(0, 200),
      ...files.filter(file => file.type.startsWith('video/')).slice(0, 20)];
    searchWorker = new Worker('/desktop/semantic-bundle.js', {type: 'module'});
  }
  const worker = searchWorker;
  busy = true;
  findPhotos.disabled = query.disabled = true;
  status.textContent = firstSearch ? 'Loading the offline photo search model…' : 'Ranking photos…';
  const complete = () => {
    busy = false;
    findPhotos.disabled = query.disabled = false;
  };
  const failed = message => {
    searchWorker.terminate();
    searchWorker = undefined;
    status.textContent = message;
    complete();
  };
  searchWorker.onerror = () => failed('Offline photo search could not start.');
  searchWorker.onmessage = ({data}) => {
    if (data.error) {
      failed(data.error);
      return;
    }
    if (data.progress) {
      status.textContent = 'Reading media ' + data.progress + ' of ' + photoIndex.length + '…';
      return;
    }
    rankedPhotos = data.ranked.map(({index}) => photoIndex[index]);
    visibleCount = pageSize;
    query.value = '';
    render();
    status.textContent = rankedPhotos.length
      ? 'Ranked ' + rankedPhotos.length + ' files. Closest matches first. Search scans up to 200 photos and 20 videos per folder selection.'
      : 'No supported media could be read.';
    complete();
  };
  const term = description.value.trim();
  const sources = firstSearch ? [] : undefined;
  if (firstSearch) {
    for (const file of photoIndex) {
      if (file.type.startsWith('video/')) {
        status.textContent = 'Sampling video ' + file.name + '…';
        sources.push(await sampleVideo(file));
        if (worker !== searchWorker) return;
      } else {
        sources.push([file]);
      }
    }
  }
  worker.postMessage({query: term, files: sources});
});
const starterCards = document.querySelector('#starter-cards');
const toggleStarters = document.querySelector('#toggle-starters');
function showStarters(show) {
  starterCards.hidden = !show;
  toggleStarters.textContent = show ? 'Hide starters' : 'Show starters';
  toggleStarters.setAttribute('aria-expanded',String(show));
}
showStarters(localStorage.getItem('noble.home.hideStarters') !== 'true');
toggleStarters.onclick = () => {
  showStarters(starterCards.hidden);
  localStorage.setItem('noble.home.hideStarters',String(starterCards.hidden));
};

const recentContainer = document.querySelector('#recent-files');
const recentStatus = document.querySelector('#recent-status');
const recentFilter = document.querySelector('#recent-filter');
const sortRecent = document.querySelector('#recent-sort');
const fileToolbar = document.querySelector('#file-toolbar');
const fileTypes = document.querySelector('#file-types');
const resumeFile = document.querySelector('#resume-file');
const layoutButtons = [...document.querySelectorAll('[data-layout]')];
let recent = [];
let selectedType = 'all';

function fileDetails(file) {
  const extension = /\.([^.]+)$/.exec(file.name)?.[1].toUpperCase() || '';
  if (extension === 'FCPROJ') return {category:'video', label:'Video project'};
  return {category:'photo', label:extension === 'PCRAFT' ? 'Photo project' : extension || 'Photo'};
}

async function reopen(file, button) {
  button.disabled = true;
  try {
    await window.nobleNavigation.openRecent(file.id);
  } catch (error) {
    recentStatus.textContent = error.message;
    recentStatus.scrollIntoView({block:'nearest'});
  } finally {
    button.disabled = false;
  }
}

function recentButton(file) {
  const button = document.createElement('button');
  button.type = 'button';
  button.title = file.name;
  button.setAttribute('aria-label',file.name);
  const info = fileDetails(file);
  const thumbnail = document.createElement(file.thumbnail ? 'img' : 'span');
  if (file.thumbnail) {
    thumbnail.src = file.thumbnail;
    thumbnail.alt = '';
  } else {
    thumbnail.className = 'file-type shell-icon';
    thumbnail.textContent = info.category === 'video' ? '\uE8B2' : '\uEB9F';
    thumbnail.setAttribute('aria-hidden','true');
  }
  const name = document.createElement('strong');
  name.textContent = file.name;
  const format = document.createElement('span');
  format.className = 'file-format';
  format.textContent = info.label;
  const opened = document.createElement('time');
  opened.className = 'opened-at';
  const date = new Date(file.opened || NaN);
  if (Number.isFinite(date.getTime())) {
    opened.dateTime = date.toISOString();
    opened.title = date.toLocaleString();
    const today = new Date().toDateString() === date.toDateString();
    opened.textContent = today
      ? 'Today, ' + date.toLocaleTimeString(undefined,{hour:'numeric',minute:'2-digit'})
      : date.toLocaleDateString(undefined,{month:'short',day:'numeric',year:'numeric'});
  } else opened.textContent = '—';
  button.append(thumbnail,name,format,opened);
  button.onclick = () => reopen(file,button);
  return button;
}

function renderRecent() {
  const query = recentFilter.value.trim().toLowerCase();
  const filtered = recent.filter(file => (selectedType === 'all' || fileDetails(file).category === selectedType) && file.name.toLowerCase().includes(query));
  if (sortRecent.value === 'name') filtered.sort((a,b) => a.name.localeCompare(b.name));
  document.querySelector('#recent-empty').hidden = recent.length > 0;
  fileToolbar.hidden = fileTypes.hidden = !recent.length;
  document.querySelector('#recent-count').textContent = recent.length ? String(filtered.length) : '';
  document.querySelector('#recent-columns').hidden = !filtered.length || !recentContainer.classList.contains('list');
  recentStatus.textContent = '';
  if (recent.length && !filtered.length) {
    recentStatus.textContent = query ? 'No files match your filter.' : selectedType === 'video' ? 'No recent video projects.' : 'No recent photos.';
  }
  recentContainer.replaceChildren(...filtered.map(recentButton));
}
async function refreshRecent() {
  try {
    recent = await window.nobleNavigation?.listRecent() ?? [];
    const latest = recent[0];
    resumeFile.hidden = !latest;
    if (latest) {
      resumeFile.querySelector('strong').textContent = latest.name;
      resumeFile.title = 'Reopen ' + latest.name;
      resumeFile.onclick = () => reopen(latest,resumeFile);
    }
    renderRecent();
  } catch (error) { recentStatus.textContent = error.message; }
}
recentFilter.oninput = renderRecent;
sortRecent.onchange = renderRecent;
for (const button of fileTypes.querySelectorAll('button')) {
  button.onclick = () => {
    selectedType = button.dataset.type;
    for (const item of fileTypes.children) item.setAttribute('aria-pressed',String(item === button));
    renderRecent();
  };
}
function setLayout(layout) {
  recentContainer.classList.toggle('list',layout === 'list');
  for (const button of layoutButtons) button.setAttribute('aria-pressed',String(button.dataset.layout === layout));
  renderRecent();
}
setLayout(localStorage.getItem('noble.home.recentLayout') === 'grid' ? 'grid' : 'list');
for (const button of layoutButtons) button.onclick = () => {
  setLayout(button.dataset.layout);
  localStorage.setItem('noble.home.recentLayout',button.dataset.layout);
};
document.addEventListener('keydown',event => {
  if (event.ctrlKey && !event.altKey && !event.shiftKey && event.key.toLowerCase() === 'k'
      && !document.querySelector('#studio').hidden && !document.body.classList.contains('desktop-editing') && !fileToolbar.hidden) {
    event.preventDefault();
    recentFilter.focus();
    recentFilter.select();
  }
});
window.addEventListener('noble-recent-updated',refreshRecent);
void refreshRecent();
for (const trigger of document.querySelectorAll('.file-commands [popovertarget]')) {
  const menu = trigger.popoverTargetElement;
  menu.addEventListener('beforetoggle',event=>{
    if(event.newState==='open') {
      const rect=trigger.getBoundingClientRect();
      menu.style.left=Math.max(6,Math.min(rect.left,innerWidth-194))+'px';
      menu.style.top=(rect.bottom+5)+'px';
    }
  });
}
if (window.nobleNavigation) {
  for (const link of document.querySelectorAll('a[href^="/video/"],a[href^="/image/"],a[href^="/desktop/native/"],a[href="/desktop/copy-export"],[data-desktop-projects]')) {
    link.addEventListener('click', event => {
      event.preventDefault();
      link.closest('[popover]')?.hidePopover();
      window.nobleNavigation.openWorkspace(link.href);
    });
  }
  const editorChrome = document.querySelector('.desktop-editor-chrome');
  const photoStatus = editorChrome.querySelector('[data-desktop-status]');
  window.nobleNavigation.onNativePhotoLoading?.(loading=>{
    photoStatus.textContent = loading ? 'Opening photos…' : '';
  });
  window.nobleNavigation.onDesktopEditor?.(kind=>{
    document.body.classList.toggle('desktop-editing',Boolean(kind));
    editorChrome.hidden = !kind;
    if (!kind) return;
    editorChrome.querySelector('[data-desktop-ai]').href = '/'+kind+'/?empty&webgl';
  });
}
