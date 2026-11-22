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
let recent = [];
function renderRecent() {
  const filtered = recent.filter(file=>file.name.toLowerCase().includes(recentFilter.value.trim().toLowerCase()));
  if (sortRecent.value==='name') filtered.sort((a,b)=>a.name.localeCompare(b.name));
  recentStatus.textContent = filtered.length ? '' : recent.length ? 'No files match your filter.' : 'Open a photo or video project to see it here.';
  recentContainer.replaceChildren(...filtered.map(file=>{
    const button = document.createElement('button');
    button.type = 'button';
    const preview = document.createElement(file.thumbnail?'img':'span');
    if (file.thumbnail) {preview.src=file.thumbnail;preview.alt='';}
    else {preview.className='file-type';preview.textContent=file.name.split('.').pop().toUpperCase();preview.setAttribute('aria-hidden','true');}
    const name=document.createElement('strong');
    name.textContent=file.name;
    button.append(preview,name);
    button.onclick=async()=>{
      button.disabled=true;
      try {await window.nobleNavigation.openRecent(file.id);}
      catch (error) {recentStatus.textContent=error.message;}
      finally {button.disabled=false;}
    };
    return button;
  }));
}
async function refreshRecent() {
  try {recent=await window.nobleNavigation?.listRecent() ?? [];renderRecent();}
  catch (error) {recentStatus.textContent=error.message;}
}
recentFilter.oninput=renderRecent;
sortRecent.onchange=renderRecent;
function setLayout(layout) {
  recentContainer.classList.toggle('list',layout==='list');
  for(const button of document.querySelectorAll('.recent-heading button')) button.setAttribute('aria-pressed',String(button.dataset.layout===layout));
}
setLayout(localStorage.getItem('noble.home.recentLayout') || 'grid');
for (const button of document.querySelectorAll('.recent-heading button')) button.onclick=()=>{
  setLayout(button.dataset.layout);
  localStorage.setItem('noble.home.recentLayout',button.dataset.layout);
};
window.addEventListener('noble-recent-updated',refreshRecent);
void refreshRecent();
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
