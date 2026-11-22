import {mountPhotoAgent} from './photo-agent.js';
import {mountPhotoSettings} from './photo-settings.js';

const bar = document.querySelector('#noble-workspace-bar');
const canvas = document.querySelector('#photocraft_canvas');
const shell = document.createElement('main');
shell.id = 'photo-workbench';
shell.setAttribute('aria-label', 'Photo workspace');
shell.innerHTML = `
  <section id="photo-viewer" aria-label="Photo preview">
    <div class="photo-empty" hidden>
      <div class="photo-starter">
        <span class="photo-open-icon" aria-hidden="true">&#xEB9F;</span><h1>Open a photo</h1>
        <p>Drop photos or layered projects here.</p>
        <button type="button" data-empty-open disabled>Browse files</button>
        <button type="button" data-retry hidden>Retry Image Studio</button>
        <output data-empty-status role="status"></output>
      </div>
    </div>
  </section>
  <aside id="photo-assistant" class="photo-inspector" aria-label="Noble AI" hidden>
    <header class="photo-dock-bar">
      <div class="photo-tabs" role="tablist" aria-label="Photo AI panels">
        <button id="photo-agent-tab" type="button" role="tab" aria-controls="photo-agent">Editing agent</button>
      </div>
      <span class="photo-connection" role="status"></span>
      <button type="button" data-close-dock aria-label="Close AI panel" title="Close AI panel">×</button>
    </header>
    <section id="photo-agent" class="photo-pane photo-agent" role="tabpanel" aria-labelledby="photo-agent-tab" hidden></section>
  </aside>
  <footer class="photo-footer">
    <output role="status" data-photo-status></output>
  </footer>`;
document.body.append(shell);
document.body.classList.add('photo-shell');
const $ = selector => shell.querySelector(selector);
const viewer = $('#photo-viewer');
for (const node of [canvas, document.querySelector('#photocraft_loading'), document.querySelector('#photocraft_stopped')]) {
  if (node) viewer.prepend(node);
}
canvas.setAttribute('aria-label', 'Photo editing canvas');

const actions = document.createElement('div');
actions.className = 'workspace-actions photo-actions';
actions.innerHTML = `
  <input type="file" accept=".pcraft,.psd,.psb,.ora,.png,.jpg,.jpeg,.webp,.tif,.tiff,.bmp,.gif,.heic,.heif" multiple hidden>
  <div role="group" class="photo-files" aria-label="Photo files">
    <button type="button" data-open title="Open photo or project (Ctrl+O)" disabled>Open</button>
    <button type="button" data-save title="Save layered project (Ctrl+S)" disabled>Save project</button>
  </div>
  <div role="group" class="photo-history" aria-label="Edit history">
    <button type="button" data-history="edit.undo" disabled>Undo</button>
    <button type="button" data-history="edit.redo" disabled>Redo</button>
  </div>
  <div class="photo-layout-actions" role="group" aria-label="Workspace layout">
    <button type="button" popovertarget="photo-settings">Workspace</button>
    <button type="button" data-assistant aria-controls="photo-assistant" aria-expanded="false" disabled>Noble AI</button>
  </div>
  <button type="button" popovertarget="photo-export" class="workspace-primary" disabled>Export photo</button>`;
bar.append(actions);
const exportMenu = document.createElement('div');
exportMenu.id = 'photo-export';
exportMenu.className = 'workspace-popover';
exportMenu.setAttribute('popover', 'auto');
exportMenu.innerHTML = '<h2>Export a copy</h2><button type="button" data-format="png">PNG <small>Preserves transparency</small></button><button type="button" data-format="jpg">JPEG <small>Smaller file for sharing</small></button>';
bar.append(exportMenu);

const open = actions.querySelector('[data-open]');
const filePicker = actions.querySelector('input');
const assistantButton = actions.querySelector('[data-assistant]');
const status = $('[data-photo-status]');
const emptyStatus = $('[data-empty-status]');
const emptyOpen = $('[data-empty-open]');
const retry = $('[data-retry]');
const photoEmpty = $('.photo-empty');
const photoControl = [actions.querySelector('[data-save]'), actions.querySelector('[popovertarget=photo-export]')];
let state;
let canvasOnly = false;
let engineTheme = 'pro';
let panelUpdate = Promise.resolve();
let savedPanels;
let busy = false;
let ready = false;
let polling = false;
const photoQueue = [];
const wait = ms => new Promise(resolve => setTimeout(resolve, ms));

async function request(method, params = {}) {
  let timer;
  try {
    const pending = window.nobleImage.request(method, params);
    const reply = method === 'ui.inspect' ? await Promise.race([pending,new Promise((_,reject)=>{
      timer = setTimeout(()=>reject(Error('Image Studio is taking too long to respond.')),8000);
    })]) : await pending;
    if (!reply.ok) throw Error(reply.error || 'Image Studio could not complete the action.');
    return reply.result;
  } finally {
    clearTimeout(timer);
  }
}
const command = (id, params = {}) => request('engine.execute', {command:id, params});
const setEngineTheme = theme => request('ui.menu.invoke', {id:'window.theme.' + theme});

async function run(action) {
  if (!ready || busy) return;
  busy = true;
  setStatus('');
  setControls();
  try {
    await action();
  } catch (error) {
    setStatus(error.message || String(error));
  } finally {
    await refresh();
    busy = false;
    setControls();
    void flushPhotoQueue();
  }
}

function setStatus(text) {
  status.textContent = emptyStatus.textContent = text;
}

function setControls() {
  const doc = state?.document;
  const reviewing = ai && !ai.panel.hidden && !ai.review.hidden;
  open.disabled = emptyOpen.disabled = !ready || busy;
  assistantButton.disabled = !ready;
  if (ai) ai.tab.disabled = !doc || busy;
  for (const button of photoControl) button.disabled = !ready || busy || !doc || reviewing;
  for (const button of actions.querySelectorAll('[data-history]')) {
    button.disabled = busy || reviewing || !doc?.[button.dataset.history.endsWith('undo') ? 'canUndo' : 'canRedo'];
  }
}

function render() {
  const doc = state.document;
  photoEmpty.hidden = Boolean(doc) || Boolean(state.dialogs?.length);
  ai?.update(doc);
  agent.update(doc);
  setControls();
}

async function refresh() {
  try {
    state = await request('ui.inspect');
    render();
  } catch (error) {
    if (!status.textContent) setStatus(error.message || String(error));
  }
}

const inspector = $('.photo-inspector');
const tabs = $('.photo-tabs');
const ai = configureAi();
const agent = mountPhotoAgent($('#photo-agent'),{request,command,run,canvas,connection:$('.photo-connection')});
const settings = mountPhotoSettings(bar,shell,preferences => {
  const theme = preferences.theme === 'warm' ? 'studio' : 'pro';
  if (theme !== engineTheme) {
    engineTheme = theme;
    if (ready) panelUpdate = panelUpdate.then(() => setEngineTheme(theme)).catch(error => setStatus(error.message));
  }
  if (canvasOnly === preferences.canvasOnly) return;
  canvasOnly = preferences.canvasOnly;
  if (canvasOnly) closeAssistant();
  if (ready) syncCanvasPanels();
});

function syncCanvasPanels() {
  const hide = canvasOnly;
  panelUpdate = panelUpdate.then(async () => {
    if (hide && !savedPanels) {
      const {panels} = await request('ui.inspect');
      const keys = ['menu_bar','toolbar','options_bar','dock','properties','status_bar','context_task_bar'];
      savedPanels = Object.fromEntries(keys.map(key => [key,panels[key]]));
      await request('ui.set',{panels:Object.fromEntries(keys.map(key => [key,false]))});
    } else if (!hide && savedPanels) {
      await request('ui.set',{panels:savedPanels});
      savedPanels = undefined;
    }
  }).catch(error => setStatus(error.message));
  return panelUpdate;
}

function closeAssistant() {
  if (ai && !ai.panel.hidden) ai.close.click();
  inspector.hidden = true;
  $('#photo-agent').hidden = true;
  assistantButton.setAttribute('aria-expanded','false');
}
$('[data-close-dock]').onclick = () => {
  closeAssistant();
  assistantButton.focus();
};

async function openAssistant() {
  if (canvasOnly) {
    settings.showPanels();
    await panelUpdate;
  }
  inspector.hidden = false;
  assistantButton.setAttribute('aria-expanded','true');
  showTab($('#photo-agent-tab'));
}
assistantButton.onclick = async () => {
  if (!inspector.hidden) return closeAssistant();
  await openAssistant();
  $('#photo-agent-tab').focus();
};

function selectTab(tab) {
  $('.photo-connection').hidden = tab.id !== 'photo-agent-tab';
  for (const button of tabs.querySelectorAll('[role=tab]')) {
    const active = button === tab;
    button.setAttribute('aria-selected', String(active));
    button.tabIndex = active ? 0 : -1;
    const pane = document.getElementById(button.getAttribute('aria-controls'));
    if (button !== ai?.tab) pane.hidden = !active;
  }
}

function configureAi() {
  const tab = document.querySelector('#noble-ai-bar button');
  const panel = document.querySelector('#noble-ai-dialog');
  if (!tab || !panel) return null;
  const part = name => panel.querySelector('[data-' + name + ']');
  panel.querySelector('#noble-ai-title').textContent = 'Enhance photo';
  tab.id = 'photo-ai-tab';
  tab.textContent = 'AI tools';
  tab.setAttribute('role', 'tab');
  tab.setAttribute('aria-controls', panel.id);
  tabs.append(tab);
  inspector.append(panel);
  panel.setAttribute('role', 'tabpanel');
  panel.setAttribute('aria-labelledby', tab.id);
  panel.removeAttribute('aria-modal');
  panel.onkeydown = event => {
    event.stopPropagation();
    if (event.key === 'Escape') {
      event.preventDefault();
      close.click();
      $('#photo-agent-tab').focus();
    }
  };
  const close = part('close');
  panel.querySelector('header').hidden = true;
  const preview = document.createElement('div');
  preview.className = 'photo-ai-preview';
  preview.append(part('compare'), part('original-view'), part('result-view'));
  const review = part('review');
  review.prepend(preview);

  const setup = document.createElement('div');
  setup.className = 'photo-ai-setup';
  const runOnPhoto = document.createElement('button');
  runOnPhoto.type = 'button';
  runOnPhoto.className = 'photo-ai-run';
  runOnPhoto.textContent = 'Preview on current photo';
  const current = part('current');
  const runModel = part('run');
  const stop = part('stop');
  part('strength').value = '45';
  part('strength-label').textContent = '45%';
  const another = document.createElement('button');
  another.type = 'button';
  another.textContent = 'Choose another tool';
  another.onclick = () => {
    stop.onclick();
    part('tool').focus();
  };
  const corrections = document.createElement('div');
  corrections.className = 'photo-ai-corrections';
  const delivery = document.createElement('div');
  delivery.className = 'photo-ai-delivery';
  const copyNote = review.lastElementChild;
  corrections.append(part('color'),part('refine'),another);
  delivery.append(part('approve').closest('label'),part('apply').parentElement,copyNote);
  review.append(corrections,delivery);
  let source;
  runOnPhoto.onclick = async () => {
    runOnPhoto.disabled = true;
    try {
      source = (await request('ui.inspect')).document;
      await current.onclick();
      if (!panel.hidden && !runModel.disabled) runModel.click();
    } catch (error) {
      part('status').textContent = error.message;
    } finally {
      sync();
    }
  };
  const choices = document.createElement('div');
  choices.className = 'photo-ai-commands';
  choices.append(runOnPhoto,stop);
  setup.append(part('tool').closest('label'), choices, part('note'));
  panel.prepend(setup);
  panel.prepend(panel.querySelector('header'));
  current.parentElement.hidden = true;
  runModel.parentElement.hidden = true;
  const fileLabel = part('file').closest('label');
  fileLabel.hidden = true;
  part('source').hidden = true;
  if (part('source').nextElementSibling?.tagName === 'P') part('source').nextElementSibling.hidden = true;
  const apply = part('apply');
  apply.textContent = 'Open as new image';
  const openCopy = apply.onclick;
  apply.onclick = () => void run(async () => {
    const before = await request('ui.inspect');
    if (update(before.document)) return;
    source = undefined;
    await openCopy();
    if (panel.hidden) await finishImport(before);
  });
  const sync = () => {
    runOnPhoto.disabled = current.disabled;
    setControls();
  };
  const observer = new MutationObserver(sync);
  observer.observe(current, {attributeFilter:['disabled']});
  observer.observe(review, {attributeFilter:['hidden']});
  new MutationObserver(() => {
    sync();
    if (!panel.hidden) {
      selectTab(tab);
      part('status').textContent = '';
    } else if (!inspector.hidden && tab.getAttribute('aria-selected') === 'true') {
      selectTab($('#photo-agent-tab'));
      if (document.activeElement === tab) $('#photo-agent-tab').focus();
    }
  }).observe(panel, {attributeFilter:['hidden']});
  function update(doc) {
    if (!source || panel.hidden || (doc?.documentId === source.documentId && doc?.revision === source.revision)) return false;
    source = undefined;
    close.click();
    setStatus('The document changed. Run the AI tool again to preview the current edit.');
    return true;
  }
  return {tab, panel, close, review,update};
}

function showTab(tab) {
  if (tab === ai?.tab) {
    if (ai.panel.hidden) ai.tab.click();
  } else {
    if (ai && !ai.panel.hidden) ai.close.click();
    selectTab(tab);
    if (document.activeElement === ai?.tab) tab.focus();
    if (tab.id === 'photo-agent-tab') void agent.open();
  }
}
for (const tab of tabs.querySelectorAll('button:not(#photo-ai-tab)')) tab.onclick = () => showTab(tab);
tabs.addEventListener('keydown', event => {
  const allTabs = Array.from(tabs.querySelectorAll('button')).filter(tab => !tab.disabled);
  const index = allTabs.indexOf(event.target);
  if (index === -1 || !['ArrowLeft','ArrowRight','Home','End'].includes(event.key)) return;
  event.preventDefault();
  const offset = event.key === 'ArrowLeft' ? -1 : 1;
  const next = event.key === 'Home' ? allTabs[0] : event.key === 'End' ? allTabs.at(-1) : allTabs[(index + offset + allTabs.length) % allTabs.length];
  showTab(next);
  next.focus();
});
selectTab($('#photo-agent-tab'));

window.noblePhotoMcp = async input => {
  if (!ready || busy) return {error:'Image Studio is busy. Try again in a moment.'};
  await refresh();
  if (!state?.document) return {error:'Open a photo in Noble Image Studio first.'};
  await openAssistant();
  return agent.planFromMcp(input);
};

async function importPhoto(file) {
  if (!file.size || file.size > 64 * 1024 * 1024) throw Error('Choose a nonempty image or project up to 64 MB.');
  if (ai && !ai.panel.hidden) ai.close.click();
  const before = await request('ui.inspect');
  let name = file.name;
  let bytes = new Uint8Array(await file.arrayBuffer());
  if (/\.hei[cf]$/i.test(name)) {
    const decode = window.nobleNavigation?.decodePhonePhoto;
    if (!decode) throw Error('Open this HEIC or HEIF photo in Noble’s full photo workspace.');
    bytes = new Uint8Array(await decode(name,bytes));
    name = name.replace(/\.hei[cf]$/i,'.png');
  }
  await window.nobleImage.importFile(name,bytes);
  await finishImport(before);
}

async function finishImport(before) {
  let deadline = Date.now() + 25000;
  const previousDialogs = new Set((before.dialogs ?? []).map(dialog=>dialog.id));
  const preserved = new Set();
  try {
    while (Date.now() < deadline) {
      const result = await request('ui.inspect');
      const newDocument = result.session.documents.find(doc=>!before.session.documents.some(previous=>previous.id === doc.id && previous.index === doc.index));
      if (newDocument || result.session.documents.length > before.session.documents.length) {
        const opened = newDocument ?? result.session.documents.at(-1);
        if (result.session.active !== opened.index) await command('document.activate', {document:opened.index});
        return;
      }
      if (result.status !== before.status && (result.statusError || /^cancel(?:l?ed|ing) opening/i.test(result.status))) {
        throw Error(result.status);
      }
      const dialog = result.dialogs?.at(-1);
      if (dialog && !previousDialogs.has(dialog.id)) {
        // Honor PhotoCraft's default Preserve choice for a profile already embedded in the photo.
        // Show the actual editor dialog whenever opening still needs a decision.
        const fields = dialog.fields;
        if (result.dialogs.length === 1 && fields?.__heldOpen === true && fields.missing === false &&
            fields.action === 'preserve' && fields.remember === false && !preserved.has(dialog.id)) {
          preserved.add(dialog.id);
          await request('ui.key',{key:'Enter'});
        } else {
          shell.dataset.openPrompt = before.document ? 'document' : 'empty';
          photoEmpty.hidden = true;
          setStatus('Image Studio is waiting for your choice in the open dialog.');
          deadline = Date.now() + 25000;
        }
      }
      await wait(150);
    }
    throw Error('Image Studio could not finish opening this file.');
  } finally {
    delete shell.dataset.openPrompt;
  }
}
open.onclick = () => {
  if (window.nobleNavigation?.pickPhotos) window.nobleNavigation.pickPhotos().catch(error => setStatus(error.message));
  else filePicker.click();
};
emptyOpen.onclick = open.onclick;

function queuePhotos(files) {
  if (files.length > 12) return setStatus('Choose up to 12 photos per import.');
  if (!files.length) return;
  photoQueue.push(...files.map(file => ({file,name:file.name})));
  void flushPhotoQueue();
}

async function flushPhotoQueue() {
  if (!ready || busy || !photoQueue.length) return;
  const batch = photoQueue.splice(0);
  await run(async()=>{
    const failures = [];
    let opened = 0;
    for (const item of batch) {
      if (item.entry === 'new') {
        await command('file.new',{width:1920,height:1080,background:'white'});
        continue;
      }
      const {file,name,url} = item;
      setStatus('Opening '+name+'…');
      let success = false;
      try {
        if (item.error) throw Error(item.error);
        await importPhoto(file);
        success = true;
        opened++;
      } catch (error) {
        failures.push(name+' · '+error.message);
      } finally {
        if (url) window.nobleNavigation.photoImportResult(url,success);
      }
    }
    setStatus([opened ? 'Opened '+opened+(opened === 1 ? ' file.' : ' files.') : '',...failures].filter(Boolean).join(' '));
  });
}

window.nobleNavigation?.onPhotoImport?.(entry=>{
  photoQueue.push({name:entry.name,url:entry.url,error:entry.error,file:entry.error ? null : new File([entry.bytes],entry.name)});
  void flushPhotoQueue();
});
window.nobleNavigation?.onPhotoEntry?.(entry=>{
  if (entry !== 'new') return;
  photoQueue.push({entry});
  void flushPhotoQueue();
});
filePicker.onchange = () => {
  const files = Array.from(filePicker.files);
  filePicker.value = '';
  queuePhotos(files);
};
viewer.ondragover = event => {
  if ((state?.document && event.target === canvas) || !event.dataTransfer.types.includes('Files')) return;
  event.preventDefault();
  photoEmpty.classList.add('drop-target');
};
viewer.ondragleave = () => photoEmpty.classList.remove('drop-target');
viewer.ondrop = event => {
  photoEmpty.classList.remove('drop-target');
  if ((state?.document && event.target === canvas) || !event.dataTransfer.files.length) return;
  event.preventDefault();
  event.stopPropagation();
  queuePhotos(Array.from(event.dataTransfer.files));
};
const activateDocument = index => void run(async () => {
  if (ai && !ai.panel.hidden) ai.close.click();
  await command('document.activate', {document:Number(index)});
});
for (const button of actions.querySelectorAll('[data-history]')) {
  button.onclick = () => void run(() => command(button.dataset.history));
}

async function save(extension) {
  const clean = state.document.name.replace(/\.[^.]+$/, '').replace(/[<>:"/\\|?*\u0000-\u001f]/g, '_').slice(0, 100);
  status.textContent = 'Preparing file…';
  await request('app.save', {path:(clean || 'Noble image') + '.' + extension, wait:true});
  if (!window.nobleNavigation?.onPhotoDelivery) status.textContent = 'Check your downloads for the file.';
}
actions.querySelector('[data-save]').onclick = () => void run(() => save('pcraft'));
for (const button of exportMenu.querySelectorAll('[data-format]')) {
  button.onclick = () => {
    exportMenu.hidePopover();
    void run(() => save(button.dataset.format));
  };
}
window.nobleNavigation?.onPhotoDelivery?.(({file,state:delivery}) => {
  status.textContent = delivery === 'completed' ? 'Saved ' + file : delivery === 'cancelled' ? 'Save cancelled.' : 'Could not save the file.';
});
document.addEventListener('keydown', event => {
  if (!ready || !event.ctrlKey || event.altKey || event.shiftKey) return;
  const key = event.key.toLowerCase();
  if ((key === 'pageup' || key === 'pagedown') && state?.session.documents.length > 1) {
    event.preventDefault();
    event.stopPropagation();
    if (!busy && !event.repeat) {
      const {active, documents} = state.session;
      const current = documents.findIndex(doc => doc.index === active);
      const next = (current + documents.length + (key === 'pageup' ? -1 : 1)) % documents.length;
      activateDocument(documents[next].index);
    }
    return;
  }
  const button = key === 'o' ? open : key === 's' ? actions.querySelector('[data-save]') : null;
  if (!button) return;
  event.preventDefault();
  event.stopPropagation();
  if (!button.disabled && !event.repeat) button.click();
}, true);

async function initialize() {
  retry.hidden = true;
  try {
    const deadline = Date.now() + 16000;
    let initial;
    while (!initial) {
      try { initial = await request('ui.inspect'); }
      catch (error) {
        if (Date.now() >= deadline) throw error;
        await wait(150);
      }
    }
    try {
      await setEngineTheme(engineTheme);
      await request('ui.set',{panels:{menu_bar:true,toolbar:true,options_bar:true,dock:true,status_bar:true}});
    } catch (error) {
      setStatus('Could not restore the workspace layout. ' + error.message);
    }
    ready = true;
    if (canvasOnly) await syncCanvasPanels();
    await refresh();
    void flushPhotoQueue();
    setInterval(async () => {
      if (busy || polling || document.hidden) return;
      polling = true;
      try { await refresh(); } finally { polling = false; }
    }, 750);
  } catch (error) {
    ready = false;
    setControls();
    photoEmpty.hidden = false;
    retry.hidden = false;
    setStatus(error.message || String(error));
  }
}
retry.onclick = initialize;
setControls();
if (window.nobleImage) void initialize();
else window.addEventListener('noble-image-ready', initialize, {once:true});
