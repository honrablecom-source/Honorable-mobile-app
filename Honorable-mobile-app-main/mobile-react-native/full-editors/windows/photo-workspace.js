import {mountPhotoAgent} from './photo-agent.js';
import {mountPhotoSettings} from './photo-settings.js';

const bar = document.querySelector('#noble-workspace-bar');
const canvas = document.querySelector('#photocraft_canvas');
const shell = document.createElement('main');
shell.id = 'photo-workbench';
shell.setAttribute('aria-label', 'Photo workspace');
shell.innerHTML = `
  <aside class="photo-tools" aria-label="Canvas tools">
    <h2>Tools</h2>
    <button data-tool="move" data-photo><span aria-hidden="true">↖</span> Move</button>
    <button data-tool="crop" data-photo><span aria-hidden="true">⌑</span> Crop</button>
    <button data-command="image.imageRotation.90cw" data-photo><span aria-hidden="true">↻</span> Rotate</button>
    <hr>
    <button data-tool="brush" data-photo><span aria-hidden="true">✎</span> Brush</button>
    <button data-tool="eraser" data-photo><span aria-hidden="true">◇</span> Eraser</button>
    <button data-tool="type" data-photo><span aria-hidden="true">T</span> Text</button>
  </aside>
  <section id="photo-viewer" aria-label="Photo preview">
    <div class="photo-empty" hidden>
      <div class="photo-starter">
        <h1>Open a photo</h1>
        <p>Drop a photo or a layered project here.</p>
        <button type="button" data-empty-open>Browse files</button>
      </div>
    </div>
  </section>
  <aside class="photo-inspector" aria-label="Photo adjustments and layers">
    <div class="photo-tabs" role="tablist" aria-label="Editing panels">
      <button id="photo-adjust-tab" type="button" role="tab" aria-controls="photo-adjust">Adjust</button>
      <button id="photo-layers-tab" type="button" role="tab" aria-controls="photo-layers">Layers</button>
      <button id="photo-agent-tab" type="button" role="tab" aria-controls="photo-agent">AI agent</button>
    </div>
    <section id="photo-adjust" class="photo-pane" role="tabpanel" aria-labelledby="photo-adjust-tab">
      <form data-adjust="brightnessContrast" aria-label="Light adjustments">
        <h2>Light</h2>
        <label>Brightness <output>0</output><input data-photo name="brightness" aria-label="Brightness" type="range" min="-150" max="150" value="0"></label>
        <label>Contrast <output>0</output><input data-photo name="contrast" aria-label="Contrast" type="range" min="-50" max="100" value="0"></label>
        <button data-photo type="submit">Add light adjustment</button>
      </form>
      <form data-adjust="hueSaturation" aria-label="Color adjustments">
        <h2>Color</h2>
        <label>Saturation <output>0</output><input data-photo name="saturation" aria-label="Saturation" type="range" min="-100" max="100" value="0"></label>
        <button data-photo type="submit">Add color adjustment</button>
      </form>
      <section class="photo-more" aria-label="More adjustments">
        <button data-photo data-menu="layer.newAdjustmentLayer.levels">Levels…</button>
        <button data-photo data-menu="layer.newAdjustmentLayer.curves">Curves…</button>
        <button data-photo data-menu="layer.newAdjustmentLayer.blackWhite">Black &amp; white…</button>
      </section>
    </section>
    <section id="photo-layers" class="photo-pane" role="tabpanel" aria-labelledby="photo-layers-tab" hidden>
      <header class="layer-heading"><h2>Layers</h2><button data-photo data-command="layer.new.layer">Add layer</button></header>
      <p class="layer-empty">Open a photo to see its layers.</p>
      <ol class="layer-list" aria-label="Layer stack"></ol>
      <label class="layer-opacity" hidden>Opacity <output>100%</output><input data-photo aria-label="Layer opacity" type="range" min="0" max="100" value="100"></label>
      <section class="photo-more" aria-label="Layer actions">
        <button data-edit-adjustment hidden>Edit adjustment</button>
        <button data-layer-action="layer.duplicate">Duplicate</button>
        <button data-layer-action="layer.delete">Delete</button>
      </section>
    </section>
    <section id="photo-agent" class="photo-pane photo-agent" role="tabpanel" aria-labelledby="photo-agent-tab" hidden></section>
  </aside>
  <footer class="photo-footer">
    <span data-photo-info>No photo open</span>
    <output role="status" data-photo-status></output>
    <div aria-label="Canvas zoom">
      <button type="button" data-photo data-menu="view.fitOnScreen">Fit</button>
      <button type="button" data-photo data-menu="view.actualPixels">100%</button>
    </div>
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
  <input type="file" accept=".pcraft,.psd,.psb,.ora,.png,.jpg,.jpeg,.webp,.tif,.tiff,.bmp,.gif" hidden>
  <button type="button" data-open title="Open photo or project (Ctrl+O)" disabled>Open</button>
  <div role="group" class="photo-history" aria-label="Edit history">
    <button type="button" data-history="edit.undo" disabled>Undo</button>
    <button type="button" data-history="edit.redo" disabled>Redo</button>
  </div>
  <button type="button" data-save title="Save layered project (Ctrl+S)" disabled>Save project</button>
  <div class="photo-layout-actions" role="group" aria-label="Workspace layout">
    <button type="button" popovertarget="photo-settings">Workspace</button>
    <button type="button" data-full aria-pressed="false" disabled>Full tools</button>
  </div>
  <button type="button" popovertarget="photo-export" class="workspace-primary" disabled>Export photo</button>`;
bar.append(actions);
const documentPicker = document.createElement('select');
documentPicker.className = 'photo-document-picker';
documentPicker.setAttribute('aria-label', 'Open documents');
documentPicker.hidden = true;
bar.insertBefore(documentPicker, actions);
const exportMenu = document.createElement('div');
exportMenu.id = 'photo-export';
exportMenu.className = 'workspace-popover';
exportMenu.setAttribute('popover', 'auto');
exportMenu.innerHTML = '<h2>Export a copy</h2><button type="button" data-format="png">PNG <small>Preserves transparency</small></button><button type="button" data-format="jpg">JPEG <small>Smaller file for sharing</small></button>';
bar.append(exportMenu);

const open = actions.querySelector('[data-open]');
const filePicker = actions.querySelector('input');
const full = actions.querySelector('[data-full]');
const status = $('[data-photo-status]');
const photoEmpty = $('.photo-empty');
const photoControl = [...shell.querySelectorAll('[data-photo]'), actions.querySelector('[data-save]'), actions.querySelector('[popovertarget=photo-export]')];
const focusedPanels = {menu_bar:false, toolbar:false, status_bar:false, dock:false, properties:false, options_bar:true, context_task_bar:false};
let nativePanels, state, lastDocuments, lastLayers;
let busy = false;
let ready = false;
let advanced = false;
let polling = false;
const adjustForms = [...shell.querySelectorAll('[data-adjust]')];
const wait = ms => new Promise(resolve => setTimeout(resolve, ms));

async function request(method, params = {}) {
  const reply = await window.nobleImage.request(method, params);
  if (!reply.ok) throw Error(reply.error || 'Image Studio could not complete the action.');
  return reply.result;
}
const command = (id, params = {}) => request('engine.execute', {command:id, params});
const menu = id => request('ui.menu.invoke', {id});

async function run(action) {
  if (!ready || busy) return;
  busy = true;
  status.textContent = '';
  setControls();
  try {
    await action();
  } catch (error) {
    status.textContent = error.message || String(error);
  } finally {
    busy = false;
    await refresh();
    setControls();
  }
}

function setControls() {
  const doc = state?.document;
  const reviewing = ai && !ai.panel.hidden && !ai.review.hidden;
  open.disabled = full.disabled = !ready || busy;
  documentPicker.disabled = busy;
  if (ai) ai.tab.disabled = !doc || busy;
  for (const button of photoControl) button.disabled = !ready || busy || !doc || reviewing;
  for (const button of actions.querySelectorAll('[data-history]')) {
    button.disabled = busy || reviewing || !doc?.[button.dataset.history.endsWith('undo') ? 'canUndo' : 'canRedo'];
  }
  for (const button of shell.querySelectorAll('[data-layer-action]')) button.disabled = busy || doc?.activeLayer == null;
}

function drawLayers(layers, depth = 0) {
  const rows = [];
  for (const layer of layers) {
    const row = document.createElement('li');
    row.style.setProperty('--layer-depth', depth);
    row.classList.toggle('selected', layer.selected);
    const visibility = document.createElement('button');
    visibility.type = 'button';
    visibility.className = 'layer-visibility';
    visibility.dataset.layerControl = layer.id + '-visibility';
    visibility.textContent = layer.visible ? '◉' : '○';
    visibility.setAttribute('aria-label', (layer.visible ? 'Hide ' : 'Show ') + layer.name);
    visibility.onclick = () => void run(() => command('layer.setProps', {layer:layer.id, visible:!layer.visible}));
    const select = document.createElement('button');
    select.type = 'button';
    select.className = 'layer-select';
    select.dataset.layerControl = layer.id + '-select';
    select.setAttribute('aria-pressed', String(layer.selected));
    const name = document.createElement('strong');
    name.textContent = layer.name;
    const kind = document.createElement('small');
    kind.textContent = layer.kind;
    select.append(name, kind);
    select.onclick = () => void run(() => command('layer.select', {layer:layer.id}));
    row.append(visibility, select);
    rows.push(row, ...drawLayers(layer.children || [], depth + 1));
  }
  return rows;
}

function render() {
  const {document:doc, session, tool, views} = state;
  photoEmpty.hidden = Boolean(doc) || advanced;
  shell.dataset.canvasTool = String(tool).toLowerCase();
  const zoom = views?.[session.active]?.zoom;
  $('[data-photo-info]').textContent = doc
    ? [doc.width + ' × ' + doc.height, doc.mode, Number.isFinite(zoom) && Math.round(100 * zoom) + '%'].filter(Boolean).join('  ·  ')
    : 'No photo open';
  const documents = JSON.stringify(session.documents.map(({name,dirty}) => [name,dirty]));
  if (documents !== lastDocuments) {
    documentPicker.replaceChildren(...session.documents.map(({index,name,dirty}) => new Option(name + (dirty ? ' •' : ''), index)));
    lastDocuments = documents;
  }
  documentPicker.hidden = !doc;
  if (doc) documentPicker.value = session.active;
  for (const button of shell.querySelectorAll('.photo-tools [data-tool]')) {
    button.setAttribute('aria-pressed', String(String(tool).toLowerCase() === button.dataset.tool));
  }
  const layers = doc?.layers || [];
  const signature = JSON.stringify(layers);
  if (signature !== lastLayers) {
    const focusedLayer = document.activeElement?.dataset.layerControl;
    $('.layer-list').replaceChildren(...drawLayers(layers));
    if (focusedLayer) ($('[data-layer-control="' + focusedLayer + '"]') || $('.layer-heading button')).focus();
    lastLayers = signature;
  }
  $('.layer-empty').hidden = layers.length > 0;
  const opacity = $('.layer-opacity');
  const active = opacityTarget();
  $('[data-edit-adjustment]').hidden = !adjustForms.some(form => form.dataset.adjust === active?.adjustment?.kind);
  for (const form of adjustForms) {
    const adjustment = active?.adjustment?.kind === form.dataset.adjust ? active.adjustment : null;
    const signature = JSON.stringify([session.active, adjustment && active.id, adjustment?.params]);
    if (signature !== form.dataset.current) {
      form.dataset.current = signature;
      for (const input of form.querySelectorAll('input')) {
        if (input.name === 'contrast') input.min = adjustment?.params.legacy ? -100 : -50;
        if (input.name === 'saturation') input.min = adjustment?.params.colorize ? 0 : -100;
        input.value = adjustment?.params?.[input.name] ?? 0;
        input.oninput();
      }
    }
    form.querySelector('button').textContent =
      (adjustment ? 'Update ' : 'Add ') + (form.dataset.adjust === 'brightnessContrast' ? 'light' : 'color') + ' adjustment';
  }
  opacity.hidden = !active;
  if (active && document.activeElement !== opacity.querySelector('input')) {
    opacity.querySelector('input').value = Math.round(100 * active.opacity);
    opacity.querySelector('output').value = opacity.querySelector('input').value + '%';
  }
  agent.update(doc);
  setControls();
}

function opacityTarget(layers = state?.document?.layers || []) {
  for (const layer of layers) {
    if (layer.id === state?.document?.activeLayer) return layer;
    const child = layer.children && opacityTarget(layer.children);
    if (child) return child;
  }
}

async function refresh() {
  try {
    state = await request('ui.inspect');
    render();
  } catch (error) {
    if (!status.textContent) status.textContent = error.message || String(error);
  }
}

const inspector = $('.photo-inspector');
const tabs = $('.photo-tabs');
const ai = configureAi();
const agent = mountPhotoAgent($('#photo-agent'),{request,command,run,canvas,viewer});
mountPhotoSettings(bar,shell,preferences => {
  if (preferences.canvasOnly) {
    agent.dismissProof();
    if (ai && !ai.panel.hidden) ai.close.click();
  }
});
function selectTab(tab) {
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
  const close = part('close');
  close.textContent = 'Back to edit';
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
  part('review').append(another);
  runOnPhoto.onclick = async () => {
    runOnPhoto.disabled = true;
    try {
      await current.onclick();
      if (!panel.hidden && !runModel.disabled) runModel.click();
    } finally {
      sync();
    }
  };
  setup.append(part('tool').closest('label'), part('note'), runOnPhoto, stop);
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
    await openCopy();
    if (panel.hidden) await finishImport(before);
  });
  const sync = () => {
    runOnPhoto.disabled = current.disabled;
    canvas.inert = !panel.hidden && !review.hidden;
    setControls();
  };
  const observer = new MutationObserver(sync);
  observer.observe(current, {attributeFilter:['disabled']});
  observer.observe(review, {attributeFilter:['hidden']});
  new MutationObserver(() => {
    sync();
    if (!panel.hidden) {
      selectTab(tab);
      part('status').textContent = 'Choose a tool to preview on the open photo.';
    } else if (tab.getAttribute('aria-selected') === 'true') {
      selectTab($('#photo-adjust-tab'));
      if (document.activeElement === tab) $('#photo-adjust-tab').focus();
    }
  }).observe(panel, {attributeFilter:['hidden']});
  return {tab, panel, close, review};
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
selectTab($('#photo-adjust-tab'));

window.noblePhotoMcp = async input => {
  if (!ready || busy) return {error:'Image Studio is busy. Try again in a moment.'};
  if (advanced || shell.classList.contains('photo-canvas-only')) {
    return {error:'Restore the photo layout and inspector in Noble to review the edit.'};
  }
  await refresh();
  if (!state?.document) return {error:'Open a photo in Noble Image Studio first.'};
  showTab($('#photo-agent-tab'));
  return agent.planFromMcp(input);
};

async function importPhoto(file) {
  if (!file.size || file.size > 64 * 1024 * 1024) throw Error('Choose a nonempty image or project up to 64 MB.');
  if (ai && !ai.panel.hidden) ai.close.click();
  const before = await request('ui.inspect');
  window.nobleImage.importFile(file.name, new Uint8Array(await file.arrayBuffer()));
  await finishImport(before);
}

async function finishImport(before) {
  const deadline = Date.now() + 16000;
  while (Date.now() < deadline) {
    const result = await request('ui.inspect');
    if (result.session.documents.length > before.session.documents.length) {
      const newDocument = result.session.documents.at(-1);
      if (result.session.active !== newDocument.index) await command('document.activate', {document:newDocument.index});
      return;
    }
    if (result.statusError && result.status !== before.status) throw Error(result.status);
    await wait(150);
  }
  throw Error('Image Studio could not open that file. Try a photo or a Noble project.');
}
open.onclick = () => filePicker.click();
$('[data-empty-open]').onclick = open.onclick;
filePicker.onchange = () => {
  const file = filePicker.files[0];
  filePicker.value = '';
  if (file) void run(() => importPhoto(file));
};
viewer.ondragover = event => {
  if (advanced || !event.dataTransfer.types.includes('Files')) return;
  event.preventDefault();
  photoEmpty.classList.add('drop-target');
};
viewer.ondragleave = () => photoEmpty.classList.remove('drop-target');
viewer.ondrop = event => {
  photoEmpty.classList.remove('drop-target');
  if (advanced || !event.dataTransfer.files.length) return;
  event.preventDefault();
  event.stopPropagation();
  void run(() => importPhoto(event.dataTransfer.files[0]));
};
documentPicker.onchange = () => void run(async () => {
  if (ai && !ai.panel.hidden) ai.close.click();
  await command('document.activate', {document:Number(documentPicker.value)});
});
$('[data-edit-adjustment]').onclick = () => {
  showTab($('#photo-adjust-tab'));
  queueMicrotask(() => {
    adjustForms.find(form => form.dataset.adjust === opacityTarget()?.adjustment?.kind)?.querySelector('input').focus();
  });
};

for (const button of shell.querySelectorAll('.photo-tools [data-tool]')) {
  button.onclick = () => void run(async () => {
    agent.dismissProof();
    await request('ui.set', {tool:button.dataset.tool, panels:{context_task_bar:button.dataset.tool === 'crop'}});
    canvas.focus();
  });
}
for (const button of shell.querySelectorAll('[data-command]')) button.onclick = () => void run(() => command(button.dataset.command));
for (const button of shell.querySelectorAll('[data-menu]')) button.onclick = () => void run(() => menu(button.dataset.menu));
for (const button of actions.querySelectorAll('[data-history]')) button.onclick = () => void run(() => command(button.dataset.history));
for (const button of shell.querySelectorAll('[data-layer-action]')) {
  button.onclick = () => void run(() => command(button.dataset.layerAction, {layer:state.document.activeLayer}));
}
for (const input of shell.querySelectorAll(':is([data-adjust], .layer-opacity) input[type=range]')) {
  input.oninput = () => {input.closest('label').querySelector('output').value = input.value + (input.name ? '' : '%');};
}
$('.layer-opacity input').onchange = event => {
  const layer = opacityTarget();
  if (layer) void run(() => command('layer.setProps', {layer:layer.id, opacity:Number(event.target.value) / 100}));
};
for (const form of adjustForms) {
  form.onsubmit = event => {
    event.preventDefault();
    const values = Object.fromEntries(Array.from(new FormData(form), ([key,value]) => [key,Number(value)]));
    const layer = opacityTarget();
    const updating = layer?.adjustment?.kind === form.dataset.adjust;
    if (!updating && !Object.values(values).some(Boolean)) return;
    void run(async () => {
      if (updating) await command('layer.setAdjustment', {layer:layer.id, ...values});
      else await command('layer.newAdjustmentLayer.' + form.dataset.adjust, values);
      status.textContent = updating ? 'Adjustment updated.' : 'Adjustment added as a layer.';
    });
  };
}
full.onclick = () => void run(async () => {
  agent.dismissProof();
  if (ai && !ai.panel.hidden) ai.close.click();
  const useFull = !advanced;
  shell.classList.toggle('photo-full-tools', useFull);
  try {
    await new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)));
    await request('ui.set', {panels:useFull ? nativePanels : focusedPanels, fit:true});
  } catch (error) {
    shell.classList.toggle('photo-full-tools', advanced);
    throw error;
  }
  advanced = useFull;
  full.textContent = advanced ? 'Photo layout' : 'Full tools';
  full.setAttribute('aria-pressed', String(advanced));
});

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
  const button = key === 'o' ? open : key === 's' && !advanced ? actions.querySelector('[data-save]') : null;
  if (!button) return;
  event.preventDefault();
  event.stopPropagation();
  if (!button.disabled && !event.repeat) button.click();
}, true);

async function initialize() {
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
    nativePanels = Object.fromEntries(Object.keys(focusedPanels).map(key => [key, initial.panels[key]]));
    await request('ui.set', {panels:focusedPanels, fit:true});
    ready = true;
    await refresh();
    setInterval(async () => {
      if (busy || polling || document.hidden) return;
      polling = true;
      try { await refresh(); } finally { polling = false; }
    }, 750);
  } catch (error) {
    // Stock renderers do not expose the focused canvas controls. Leave their full editor available.
    advanced = true;
    ready = true;
    shell.classList.add('photo-full-tools');
    await refresh();
    full.hidden = true;
    status.textContent = error.message || String(error);
  }
}
setControls();
if (window.nobleImage) void initialize();
else window.addEventListener('noble-image-ready', initialize, {once:true});
