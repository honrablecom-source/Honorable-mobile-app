import {mountPhotoAgent} from './photo-agent.js';
import {geometryNote,mountPhotoActivity} from './photo-activity.js';
import photoCatalog from './photo-catalog.js';
import {checkCommand,photoContext,resultSummary} from './native-skill.js';
import {mountPhotoSettings} from './photo-settings.js';

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
        <button type="button" data-open disabled>Browse files</button>
        <button type="button" data-retry hidden>Retry Image Studio</button>
        <output data-empty-status role="status"></output>
      </div>
    </div>
  </section>
  <aside id="photo-assistant" class="photo-inspector" aria-label="Noble AI" hidden>
    <header class="photo-dock-bar">
      <div class="photo-tabs" role="tablist" aria-label="Photo AI panels">
        <button id="photo-agent-tab" type="button" role="tab" aria-controls="photo-agent">Editing agent</button>
        <button id="photo-activity-tab" type="button" role="tab" aria-controls="photo-activity">Activity</button>
      </div>
      <span class="photo-connection" role="status"></span>
      <button type="button" data-close-dock aria-label="Close AI panel" title="Close AI panel">×</button>
    </header>
    <section id="photo-agent" class="photo-pane photo-agent" role="tabpanel" aria-labelledby="photo-agent-tab" hidden></section>
    <section id="photo-activity" class="photo-pane" role="tabpanel" aria-labelledby="photo-activity-tab" hidden></section>
  </aside>
  <footer class="photo-footer">
    <a class="photo-projects" href="/desktop/home.html" aria-label="Back to Projects">‹ Projects</a>
    <output role="status" data-photo-status></output>
    <button type="button" popovertarget="photo-settings">Workspace</button>
    <button type="button" data-assistant aria-controls="photo-assistant" aria-expanded="false" disabled>Noble AI</button>
    <button type="button" data-activity aria-controls="photo-activity" aria-expanded="false">Activity</button>
    <input type="file" accept=".pcraft,.psd,.psb,.ora,.png,.jpg,.jpeg,.webp,.tif,.tiff,.bmp,.gif,.heic,.heif" multiple hidden>
  </footer>`;
document.body.append(shell);
document.body.classList.add('noble-image-workspace','photo-shell');
const $ = selector => shell.querySelector(selector);
const viewer = $('#photo-viewer');
for (const node of [canvas, document.querySelector('#photocraft_loading'), document.querySelector('#photocraft_stopped')]) {
  if (node) viewer.prepend(node);
}
canvas.setAttribute('aria-label', 'Photo editing canvas');

const open = $('[data-open]');
const filePicker = $('.photo-footer input[type=file]');
const assistantButton = $('[data-assistant]');
const status = $('[data-photo-status]');
const emptyStatus = $('[data-empty-status]');
const retry = $('[data-retry]');
const photoEmpty = $('.photo-empty');
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
  open.disabled = !ready || busy;
  assistantButton.disabled = !ready;
  if (ai) ai.tab.disabled = !doc || busy;
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
const activityTab = $('#photo-activity-tab');
const activityButton = $('[data-activity]');
const activity = mountPhotoActivity($('#photo-activity'),activityButton,photoCatalog,()=>state?.document?.name);
const agentTrack = (id,label,args,operation,preview=false) => activity.track({id,name:id,label,args,source:'Noble AI',preview},operation);
const ai = configureAi();
const agent = mountPhotoAgent($('#photo-agent'),{request,command,run,canvas,track:agentTrack,connection:$('.photo-connection')});
const settings = mountPhotoSettings(shell,preferences => {
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
  activityButton.setAttribute('aria-expanded','false');
}
$('[data-close-dock]').onclick = () => {
  const opener = activityButton.getAttribute('aria-expanded') === 'true' ? activityButton : assistantButton;
  closeAssistant();
  opener.focus();
};

async function openAssistant(tab = $('#photo-agent-tab')) {
  if (canvasOnly) {
    settings.showPanels();
    await panelUpdate;
  }
  inspector.hidden = false;
  assistantButton.setAttribute('aria-expanded','true');
  showTab(tab);
}
activityButton.onclick = async () => {
  if (!inspector.hidden && activityTab.getAttribute('aria-selected') === 'true') return closeAssistant();
  await openAssistant(activityTab);
  activityTab.focus();
};
assistantButton.onclick = async () => {
  if (!inspector.hidden && activityTab.getAttribute('aria-selected') !== 'true') return closeAssistant();
  await openAssistant();
  $('#photo-agent-tab').focus();
};

function selectTab(tab) {
  inspector.dataset.tab = tab.id;
  activityButton.setAttribute('aria-expanded',String(!inspector.hidden && tab === activityTab));
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
  tabs.insertBefore(tab,activityTab);
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
  async function runCurrentModel() {
    runOnPhoto.disabled = true;
    try {
      source = (await request('ui.inspect')).document;
      await current.onclick();
      if (!panel.hidden && !runModel.disabled) await runModel.onclick();
    } catch (error) {
      part('status').textContent = error.message;
      throw error;
    } finally {
      sync();
    }
  }
  runOnPhoto.onclick = () => agentTrack(part('tool').value,'Preview '+part('tool').selectedOptions[0].textContent,{},async () => {
    await runCurrentModel();
    if (review.hidden) return {error:part('status').textContent || 'Preview unavailable.'};
  },true).catch(error => setStatus(error.message));
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
    await agentTrack('open_copy','Open AI result as a new image',{},async () => {
      await openCopy();
      if (!panel.hidden) throw Error(part('status').textContent || 'Image was not opened.');
      await finishImport(before);
    });
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
      if (inspector.dataset.tab !== activityTab.id) selectTab(tab);
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
  async function previewModel(model,expected) {
    if (runOnPhoto.disabled || part('tool').disabled) throw Error('A local photo tool is already running.');
    const currentDoc=(await request('ui.inspect')).document;
    if (currentDoc?.documentId!==expected.documentId || currentDoc.revision!==expected.revision) throw Error('The photo changed before preview.');
    part('tool').value=model;
    if (part('tool').value!==model) throw Error('This model is unavailable in the installed editor.');
    part('tool').dispatchEvent(new Event('change'));
    await runCurrentModel();
    if (panel.hidden || review.hidden || update((await request('ui.inspect')).document)) throw Error(part('status').textContent || 'The preview was cancelled.');
    return {status:'awaiting_approval',model,width:part('after').width,height:part('after').height};
  }
  return {tab, panel, close, review,update,preview:previewModel};
}

function showTab(tab) {
  if (tab === ai?.tab) {
    selectTab(tab);
    if (ai.panel.hidden) ai.tab.click();
  } else {
    if (tab !== activityTab && ai && !ai.panel.hidden) ai.close.click();
    selectTab(tab);
    if (document.activeElement === ai?.tab) tab.focus();
    if (tab.id === 'photo-agent-tab') void agent.open();
  }
}
for (const tab of tabs.querySelectorAll('button:not(#photo-ai-tab)')) tab.onclick = () => showTab(tab);
ai?.tab.addEventListener('click',() => selectTab(ai.tab));
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

window.noblePhotoMcp = input => activity.external(input,() => handlePhotoMcp(input));
async function handlePhotoMcp(input) {
  if (!ready || busy) return {error:'Image Studio is busy. Try again in a moment.'};
  if (input.tool==='editor_skill') {
    const {route,id,input:args}=input.args;
    const read=async()=>photoContext((await request('ui.inspect')).document);
    const before=await read();
    if (route==='context') return {result:before};
    if (route==='open') { open.click(); return {result:{status:'picker_requested'}}; }
    if (before.documentId!==args.documentId || before.revision!==args.revision) throw Error('The photo changed. Call get_photo_context again.');
    if (route==='model' || route==='strength') {
      if (!before.documentId) throw Error('Open a photo first.');
      await refresh();
      const watching = !inspector.hidden && inspector.dataset.tab === activityTab.id;
      if (!watching) await openAssistant();
      if (route==='strength') return {result:await agent.setStrength(args.percent)};
      if (!ai) throw Error('Local AI tools are unavailable.');
      if (!watching) showTab(ai.tab);
      else if (ai.panel.hidden) { ai.tab.click(); selectTab(activityTab); }
      return {result:await ai.preview(id,before)};
    }
    busy=true;
    setControls();
    try {
      if (route==='save') {
        if (!before.documentId) throw Error('Open a photo first.');
        await save(id,before.name);
        return {result:{status:'save_requested',format:id}};
      }
      if (!['engine','menu','palette','preset'].includes(route)) throw Error('Unknown photo skill.');
      if (route!=='palette') checkCommand(await request(route==='menu'?'ui.menu.list':'engine.commands'),id);
      const now=await read();
      if (now.documentId!==args.documentId || now.revision!==args.revision) throw Error('The photo changed before this action.');
      let value;
      if (route==='preset') {
        const parameters={data:args.data};
        if (id==='brush.presets.importImage' && args.name) parameters.fileName=args.name;
        else if (id!=='pattern.import' && args.name) parameters.group=args.name;
        if (id==='style.presets.importAsl') parameters.replace=false;
        value=await command(id,parameters);
      } else if (route==='palette') value=await request('ui.set',{tool:id.slice('toolbox.'.length)});
      else if (route==='menu') value=await request('ui.menu.invoke',{id});
      else value=await command(id,args.parameters??{});
      const result={command:id,...await read(),value:resultSummary(value)};
      const note=geometryNote(id,args.parameters ?? {},before.width,before.height);
      if (note) result.geometryNote=note;
      return {result};
    } finally {
      busy=false;
      await refresh();
      void flushPhotoQueue();
    }
  }
  await refresh();
  if (!state?.document) return {error:'Open a photo in Noble Image Studio first.'};
  if (inspector.hidden || inspector.dataset.tab !== activityTab.id) await openAssistant();
  return agent.planFromMcp(input);
}

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

async function save(extension,name=state.document.name) {
  const clean = name.replace(/\.[^.]+$/, '').replace(/[<>:"/\\|?*\u0000-\u001f]/g, '_').slice(0, 100);
  status.textContent = 'Preparing file…';
  await request('app.save', {path:(clean || 'Noble image') + '.' + extension, wait:true});
  if (!window.nobleNavigation?.onPhotoDelivery) status.textContent = 'Check your downloads for the file.';
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
  if (key !== 'o' && key !== 's') return;
  event.preventDefault();
  event.stopPropagation();
  if (busy || event.repeat) return;
  if (key === 'o') open.click();
  else if (state?.document && (!ai || ai.panel.hidden || ai.review.hidden)) void run(() => save('pcraft'));
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
    state = await request('ui.inspect');
    render();
    delete document.documentElement.dataset.editorStarting;
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
    delete document.documentElement.dataset.editorStarting;
  }
}
retry.onclick = initialize;
setControls();
if (window.nobleImage) void initialize();
else window.addEventListener('noble-image-ready', initialize, {once:true});
