import {mountVideoMenus} from './video-menu.js';
import {mountSequenceSetup} from './sequence-setup.js';
import {mountVideoMcp} from './video-mcp.js';
import {clearVideoChanges} from './video-changes.js';
import {mountVideoExport} from './video-export.js';

const bar = document.createElement('header');
bar.id = 'noble-workspace-bar';
const canvas = document.querySelector('#filmcraft_canvas');
bar.innerHTML = `
  <a class="workspace-home" href="/desktop/home.html" aria-label="Back to Projects" title="Back to Projects">N</a>
`;
document.body.prepend(bar);
document.body.classList.add('noble-video-workspace');
if (window.nobleNavigation) {
  const home = bar.querySelector('.workspace-home');
  home.addEventListener('click', event => {
    event.preventDefault();
    window.nobleNavigation.openWorkspace(home.href);
  });
}
if (canvas) void setupVideo().catch(error => {
  const opening = document.querySelector('#studio-opening');
  if (opening) opening.firstElementChild.textContent = 'Video Studio could not open. ' + (error.message || '');
});

async function setupVideo() {
  const status = document.createElement('output');
  status.className = 'workspace-status';
  status.setAttribute('role', 'status');
  bar.append(status);

  const actions = document.createElement('div');
  actions.className = 'workspace-actions';
  actions.setAttribute('role', 'group');
  actions.setAttribute('aria-label', 'Video Studio actions');
  actions.innerHTML = `
    <nav class="video-menus" aria-label="Video editing menus"></nav>
    <strong class="video-document" aria-label="Active document">Untitled</strong>
    <div class="video-utilities">
      <button type="button" popovertarget="noble-commands" class="command-search" aria-label="Find a command" title="Find a command"></button>
      <button type="button" popovertarget="noble-layouts" class="layout-select"><span class="layout-current-label">Focused</span><span aria-hidden="true">⌄</span></button>
    </div>`;
  actions.inert = true;
  bar.append(actions);
  const transport = document.createElement('footer');
  transport.className = 'workspace-transport';
  transport.setAttribute('aria-label', 'Timeline controls');
  transport.hidden = true;
  transport.innerHTML = `
    <div role="group" aria-label="Playback">
      <button type="button" class="transport-play" data-edit="play"><span aria-hidden="true">▶</span> Play / Pause</button>
      <button type="button" data-edit="start">Go to start</button>
    </div>
    <div role="group" aria-label="Clip editing">
      <button type="button" data-edit="split">Split at playhead</button>
      <button type="button" data-edit="undo">Undo</button>
      <button type="button" data-edit="redo">Redo</button>
    </div>
    <button type="button" data-edit="fit">Fit timeline</button>
  `;
  document.body.append(transport);
  let exporting = false;
  const createPopover = document.createElement('section');
  createPopover.id = 'noble-cut-tools';
  createPopover.className = 'workspace-popover';
  createPopover.setAttribute('popover', 'auto');
  createPopover.setAttribute('aria-label', 'Create a video');
  const toolButtons = [
    ['#story-open', 'Photo storyboard'],
    ['#noble-agent-open', 'Quick draft'],
  ];
  for (const [id, label] of toolButtons) {
    const button = document.querySelector(id);
    if (!button) continue;
    button.textContent = label;
    button.addEventListener('click', () => { if (createPopover.matches(':popover-open')) createPopover.hidePopover(); });
    createPopover.append(button);
  }
  bar.append(createPopover);
  const quickDraft = document.querySelector('#noble-agent');
  if (quickDraft) {
    const heading = document.createElement('header');
    heading.className = 'studio-dialog-heading';
    const close = quickDraft.querySelector('[data-close]');
    close.textContent = '×';
    close.setAttribute('aria-label','Close quick draft');
    heading.append(quickDraft.querySelector('h2'),close);
    const files=quickDraft.querySelector('[data-files]');
    const picker=files.closest('label');
    picker.className='cinematic-file';
    const name=document.createElement('small');
    name.textContent='Choose 2–12 photos or clips';
    picker.replaceChildren(files);
    const icon=document.createElement('span');
    icon.className='source-icon';
    icon.textContent='▤';
    icon.setAttribute('aria-hidden','true');
    const text=document.createElement('span');
    const title=document.createElement('strong');
    title.textContent='Source media';
    text.append(title,name);
    const action=document.createElement('span');
    action.className='file-action';
    action.textContent='Browse';
    picker.append(icon,text,action);
    files.setAttribute('aria-label','Choose photos or video clips');
    files.addEventListener('change',()=>{
      name.textContent=files.files.length ? files.files.length+' files selected' : 'Choose 2–12 photos or clips';
    });
    quickDraft.querySelector('[data-labels]').placeholder='One description per line, in file order';
    const footer = document.createElement('footer');
    footer.className = 'quick-draft-actions';
    footer.append(...quickDraft.querySelectorAll('[data-draft],[data-revise],[data-apply]'));
    quickDraft.prepend(heading);
    quickDraft.append(footer);
  }
  const agentButton = document.querySelector('#cinematic-open');
  const changesButton = document.querySelector('#cinematic-changes');
  for (const button of [agentButton,changesButton].filter(Boolean)) {
    button.hidden = false;
    createPopover.prepend(button);
  }
  const toolsTrigger = document.createElement('button');
  toolsTrigger.type = 'button';
  toolsTrigger.textContent = 'Tools';
  toolsTrigger.setAttribute('popovertarget',createPopover.id);
  createPopover.addEventListener('click', event => {
    if (event.target.closest('button')) createPopover.hidePopover();
  });

  const layouts = document.createElement('section');
  layouts.id = 'noble-layouts';
  layouts.className = 'workspace-popover';
  layouts.setAttribute('popover', 'auto');
  layouts.setAttribute('aria-label', 'Workspace layout');
  layouts.innerHTML = `
    <button type="button" data-layout="Focused">Focused editing</button>
    <button type="button" data-layout="Editing">Full editing</button>
    <button type="button" data-layout="Vertical">Vertical video</button>
    <button type="button" data-layout="Color">Color</button>
    <button type="button" data-layout="Audio">Audio</button>
    <button type="button" data-fit>Fit timeline</button>
    <fieldset class="workspace-type">
      <legend>Editor text and controls</legend>
      <label><input type="radio" name="editor-size" value="system"> System</label>
      <label><input type="radio" name="editor-size" value="comfortable"> Comfortable</label>
      <label><input type="radio" name="editor-size" value="large"> Large</label>
    </fieldset>
  `;
  bar.append(layouts);

  const commands = document.createElement('section');
  commands.id = 'noble-commands';
  commands.className = 'workspace-popover';
  commands.setAttribute('popover', 'auto');
  commands.setAttribute('aria-label', 'Editor commands');
  commands.innerHTML = `
    <label>Find a command <input type="search" placeholder="Split, clip speed, captions…" autofocus></label>
    <div class="command-results"></div>
  `;
  bar.append(commands);
  const filter = commands.querySelector('input');
  const results = commands.querySelector('.command-results');
  let statusTimer;
  const setStatus = message => {
    clearTimeout(statusTimer);
    status.textContent = message;
    if (message) statusTimer = setTimeout(() => {status.textContent = '';}, 8500);
  };

  async function refreshTimeline() {
    try {
      const project = await editor.execute('project.inspect');
      const sequence = project.activeSequence && await editor.execute('sequence.inspect',{item:project.activeSequence});
      actions.querySelector('.video-document').textContent = sequence?.name || project.name || 'Untitled';
      transport.hidden = exporting || !project.activeSequence;
      document.body.classList.toggle('noble-active-timeline', !transport.hidden);
      return project;
    } catch {
      return null;
    }
  }

  const run = async task => {
    setStatus('');
    try {
      await task();
    } catch (error) {
      setStatus(error.message || 'This action could not be completed.');
    }
  };
  while (!window.filmcraft?.info?.().startupMs) {
    if (document.documentElement.dataset.videoBootFailed) return;
    await new Promise(resolve => setTimeout(resolve, 80));
  }
  const editor = window.filmcraft;
  const invoke = id => editor.request('ui.menu.invoke', {id});
  const layoutButtons = [...layouts.querySelectorAll('[data-layout]')];
  const layoutTrigger = actions.querySelector('[popovertarget=noble-layouts]');
  const fitTimeline = () => editor.request('ui.set', {timeline:{fit:true}});
  const transportActions = {
    play: async () => {await editor.request('ui.playback', {action:'toggle'});},
    start: async () => {
      await editor.request('ui.playback', {action:'stop'});
      await editor.execute('playhead.set', {frame:0});
    },
    split: async () => {
      const result = await editor.execute('sequence.addEdit');
      if (!result.cuts) setStatus('Move the playhead inside a clip to split it.');
    },
    undo: async () => {await editor.execute('edit.undo'); await refreshTimeline();},
    redo: async () => {await editor.execute('edit.redo'); await refreshTimeline();},
    fit: fitTimeline,
  };
  for (const button of transport.querySelectorAll('[data-edit]')) {
    button.onclick = async () => {
      if (transport.inert) return;
      transport.inert = true;
      try {
        await run(transportActions[button.dataset.edit]);
      } finally {
        transport.inert = false;
      }
    };
  }
  const refreshIfBlank = () => {
    if (transport.hidden && !exporting) void refreshTimeline();
  };
  canvas.addEventListener('pointerup', refreshIfBlank);
  canvas.addEventListener('keyup', refreshIfBlank);
  canvas.addEventListener('drop', refreshIfBlank);
  layouts.querySelector('[data-fit]').onclick = () => {
    layouts.hidePopover();
    void run(fitTimeline);
  };

  async function setLayout(layout) {
    await editor.request('ui.set', {workspace:layout === 'Focused' ? 'Assembly' : layout});
    updateLayoutLabel(layout);
    localStorage.setItem('noble-video-layout', layout);
  }

  const menuLayoutNames = new Map(Object.entries({
    allpanels:'All Panels', assembly:'Focused', audio:'Audio', captionsandgraphics:'Captions and Graphics',
    color:'Color', editing:'Editing', effects:'Effects', learning:'Learning', review:'Review', vertical:'Vertical',
  }).map(([id,name]) => ['window.workspace.'+id,name]));
  function updateLayoutLabel(layout) {
    for (const button of layoutButtons) button.setAttribute('aria-pressed', String(button.dataset.layout === layout));
    layoutTrigger.querySelector('.layout-current-label').textContent = layout;
    layoutTrigger.setAttribute('aria-label','Layout: '+layout);
  }

  for (const button of layoutButtons) {
    button.onclick = () => {
      layouts.hidePopover();
      void run(() => setLayout(button.dataset.layout));
    };
  }

  function picker(accept, multiple, onFiles) {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = accept;
    input.multiple = multiple;
    input.hidden = true;
    input.onchange = () => {
      const files = Array.from(input.files);
      input.value = '';
      if (files.length) void onFiles(files);
    };
    bar.append(input);
    return input;
  }

  const setup = mountSequenceSetup({
    editor,
    openProject: () => projects.click(),
    openEditor: () => { start.hidden = true; canvas.focus(); },
    openAgent: () => agentButton?.click(),
    openStoryboard: () => document.querySelector('#story-open')?.click(),
    onCreated: async partial => {
      await refreshTimeline();
      void fitTimeline().catch(() => {});
      canvas.focus();
      if (partial) setStatus('Some files could not be opened. The supported files are on the timeline.');
    },
  });
  const start = setup.element;
  window.addEventListener('noble-project-replaced',setup.reset);
  async function showTimelineIfReady() {
    const project = await refreshTimeline();
    if (project?.activeSequence && !start.hidden) {
      start.hidden = true;
      setup.reset();
    }
  }
  const imports = picker('video/*,audio/*,image/*,.srt,.vtt', true, files => run(async () => {
    const imported = await editor.importFiles(files);
    if (imported.items?.length) start.hidden = true;
    if (imported.errors?.length) throw Error(imported.items?.length
      ? 'Some files could not be imported. Supported files were added to the project.'
      : 'These files could not be imported. Check the format and try again.');
    setStatus(imported.items.length + (imported.items.length === 1 ? ' file imported.' : ' files imported.'));
  }));
  async function canReplaceProject() {
    if (setup.busy) throw Error('Wait for the sequence to finish importing.');
    if ('nobleAgentBusy' in document.body.dataset || document.body.inert) throw Error('Wait for the AI edit to finish before opening another project.');
    const current = await editor.execute('project.inspect');
    return !current.dirty || window.confirm('Replace the current project? Save your edits first if you want to keep them.');
  }
  async function loadProject(file) {
    const opened = await editor.openProject(file);
    clearVideoChanges();
    window.dispatchEvent(new Event('noble-project-replaced'));
    start.hidden = true;
    await refreshTimeline();
    void fitTimeline().catch(() => {});
    setStatus(opened.missingMedia
      ? 'Project opened with missing media. Reimport the source files or relink them in the project panel.'
      : 'Project opened.');
  }
  const openProject = file => run(async () => {
    if (await canReplaceProject()) await loadProject(file);
  });

  let openingFromHome = false;
  async function openFromHome(demo) {
    if (openingFromHome) return;
    openingFromHome = true;
    try {
      await run(async () => {
        if (!await canReplaceProject()) return;
        if (demo) {
          setStatus('Opening Light Study…');
          const asset = async name => {
            const response = await fetch('/desktop/light-study/'+name);
            if (!response.ok) throw Error('Light Study is missing a packaged file. Reinstall Noble Studio.');
            return response;
          };
          const project = await (await asset('light-study.fcproj')).json();
          const media = Object.values(project.project.items).filter(item=>item.kind.Media);
          const files = await Promise.all(media.map(async item=>new File([await (await asset(item.name)).blob()],item.name)));
          await editor.execute('file.newProject',{name:'Light Study'});
          const imported = await editor.importFiles(files);
          if (imported.errors?.length || imported.paths?.length !== files.length) throw Error('The demo media could not be loaded.');
          media.forEach((item,index)=>{ item.kind.Media.media.File.path = imported.paths[index]; });
          await loadProject(new File([JSON.stringify(project)],'Light Study.fcproj',{type:'application/json'}));
        } else {
          await editor.execute('file.newProject',{name:'Untitled'});
          clearVideoChanges();
          window.dispatchEvent(new Event('noble-project-replaced'));
          await refreshTimeline();
          start.hidden = false;
        }
      });
    } finally { openingFromHome = false; }
  }
  const projects = picker('.fcproj', false, ([file]) => openProject(file));
  window.addEventListener('noble-cinematic-edit', () => void showTimelineIfReady());
  for (const id of ['#cinematic','#storyboard','#noble-agent']) {
    document.querySelector(id)?.addEventListener('close', () => void showTimelineIfReady());
  }
  const openEntry = async entry => {
    if (entry === 'demo' || entry === 'new') await openFromHome(entry === 'demo');
    if (entry === 'agent') agentButton?.click();
    if (entry === 'story') document.querySelector('#story-open')?.click();
    if (entry === 'open') {
      if (!start.hidden) start.querySelector('[data-start=open]').focus();
      else fileMenu.focus();
    }
  };
  let returnToStarter = false;
  const openAdvancedExport = () => run(async () => {
    const desiredMode = !exporting;
    await editor.request('ui.set', {mode: desiredMode ? 'export' : 'edit'});
    exporting = desiredMode;
    if (desiredMode) await resizeExport();
    else await setEditorSize(currentSize);
    if (exporting) {
      returnToStarter = !start.hidden;
      start.hidden = true;
    } else if (returnToStarter) {
      start.hidden = false;
      await showTimelineIfReady();
      returnToStarter = false;
    }
    await refreshTimeline();
  });

  const overrides = {
    'file.open': () => projects.click(),
    'file.newSequence': () => { start.hidden = false; start.querySelector('[name=sequenceName]').focus(); },
    'file.newProject': () => openFromHome(false),
    'file.openDemoProject': () => openFromHome(true),
    'file.import': () => imports.click(),
    'file.exportMedia': () => exporting ? openAdvancedExport() : openExport(),
    'mode.export': openAdvancedExport,
  };
  async function perform(id) {
    if (setup.busy) throw Error('Wait for the media import to finish.');
    if (overrides[id]) await overrides[id]();
    else {
      // Engine commands can open a dialog in the canvas, including on an empty project.
      const fromSetup = !start.hidden;
      start.hidden = true;
      try { await invoke(id); }
      catch (error) { start.hidden = !fromSetup; throw error; }
      if (id.startsWith('window.workspace.')) {
        const nativeLayout = menuLayoutNames.get(id);
        if (nativeLayout) updateLayoutLabel(nativeLayout);
      }
      const project = await refreshTimeline();
      if (project?.activeSequence) start.hidden = true;
      else if (project && !project.root?.children?.length && id.startsWith('file.close')) start.hidden = false;
    }
  }
  const listMenus = async () => (await editor.request('ui.menu.list')).filter(item =>
    item.path.length && !/^help\.(discord|website|github|appPage)$/.test(item.id));
  const fileMenu = mountVideoMenus(actions.querySelector('.video-menus'), {
    listMenus, toolsTrigger, run: id => run(() => perform(id)), currentExport: () => exporting,
    onError: error => setStatus(error.message),
  });
  let menu = [];
  function renderCommands() {
    const query = filter.value.trim().toLowerCase();
    const matching = menu.filter(item =>
      (item.label + ' ' + item.path.join(' ')).toLowerCase().includes(query)).slice(0, 80);
    results.replaceChildren(...matching.map(item => {
      const button = document.createElement('button');
      button.type = 'button';
      button.disabled = !item.enabled;
      button.textContent = item.label;
      const path = document.createElement('small');
      path.textContent = item.path.join(' / ');
      button.append(path);
      button.onclick = () => {
        commands.hidePopover();
        void run(() => perform(item.id));
      };
      return button;
    }));
    if (!matching.length) results.textContent = query ? 'No matching commands.' : 'No commands available.';
  }

  commands.addEventListener('toggle', async event => {
    if (event.newState !== 'open') return;
    filter.value = '';
    results.textContent = 'Loading commands…';
    try {
      menu = await listMenus();
      renderCommands();
    } catch {
      results.textContent = 'Commands are temporarily unavailable.';
    }
  });
  filter.oninput = renderCommands;

  const sizeChoices = [...layouts.querySelectorAll('[name=editor-size]')];
  const scaleOptions = [100, 125, 150, 175, 200, 250, 300];
  let currentSize = 'comfortable';
  let currentScale = devicePixelRatio;
  let resizeTimer;
  function syncHeader() {
    document.body.style.setProperty('--engine-header', Math.ceil(38 * currentScale) + 'px');
    document.body.style.setProperty('--engine-status', Math.floor(20 * currentScale) + 'px');
  }
  const roomyTracks = () => window.innerWidth >= 960 && window.innerHeight >= 860;
  let lastRoomy = roomyTracks();
  const setTrackSize = (roomy, fit = false) => editor.request('ui.set', {timeline:{
    videoTrackHeight:roomy ? 64 : 46, audioTrackHeight:roomy ? 56 : 46, fit,
  }});
  async function resizeExport() {
    const scale = innerWidth < 950 ? 75 : innerWidth < 1220 ? 100 : Math.round(devicePixelRatio*100);
    const size = [...scaleOptions].reverse().find(option=>option <= scale) ?? 75;
    await editor.execute('prefs.set',{key:'appearance.uiScale',value:String(size)});
    currentScale = size/100;
    syncHeader();
  }
  async function setEditorSize(size) {
    const target = devicePixelRatio * (size === 'large' ? 140 : 118);
    const scale = size === 'system' ? 'auto' : String(scaleOptions.find(level => level >= target) ?? 300);
    await editor.execute('prefs.set', {key:'appearance.uiScale', value:scale});
    // The engine applies the preference on the next frame. Updating the crop now keeps
    // the Windows toolbar aligned while the editor changes scale.
    currentScale = size === 'system' ? devicePixelRatio : Number(scale) / 100;
    syncHeader();
    lastRoomy = roomyTracks();
    await setTrackSize(lastRoomy, true);
    currentSize = size;
    localStorage.setItem('noble-editor-size', size);
    for (const choice of sizeChoices) choice.checked = choice.value === size;
  }
  for (const choice of sizeChoices) {
    choice.onchange = () => { if (choice.checked) void run(() => setEditorSize(choice.value)); };
  }
  let lastDpr = devicePixelRatio;
  window.addEventListener('resize', () => {
    if (exporting) {
      clearTimeout(resizeTimer);
      resizeTimer = setTimeout(()=>void run(resizeExport),110);
      return;
    }
    const scaleChanged = lastDpr !== devicePixelRatio;
    const roomy = roomyTracks();
    if (!scaleChanged && roomy === lastRoomy) return;
    lastDpr = devicePixelRatio;
    lastRoomy = roomy;
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(() => {
      if (scaleChanged) void run(() => setEditorSize(currentSize));
      else void run(() => setTrackSize(roomy));
    }, 150);
  });
  syncHeader();
  const saved = localStorage.getItem('noble-video-layout');
  await run(async () => {
    const size = localStorage.getItem('noble-editor-size');
    await editor.execute('prefs.set', {key:'appearance.animations', value:false});
    await setEditorSize(sizeChoices.some(choice => choice.value === size) ? size : 'comfortable');
    await setLayout(layoutButtons.some(button => button.dataset.layout === saved) ? saved : 'Focused');
  });
  const initialProject = await refreshTimeline();
  start.hidden = !initialProject || Boolean(initialProject.activeSequence) || Boolean(initialProject.root?.children?.length);
  window.nobleProjectLauncher = {
    async openEntry(entry) {
      try { await openEntry(entry); }
      finally { delete document.documentElement.dataset.editorStarting; }
    },
    async openLocalProject(name, base64) {
      const bytes = Uint8Array.from(atob(base64), value => value.charCodeAt(0));
      await openProject(new File([bytes], name, {type:'application/json'}));
    },
  };
  const delivery=mountVideoMcp(editor,{settings:createPopover,notify:setStatus,showTimeline:()=>void showTimelineIfReady()});
  const openExport=mountVideoExport({
    editor,button:fileMenu,
    start:(sequence,quality)=>delivery.startExport(sequence,quality,{matchRate:true}),
    lookup:delivery.exportStatus,advanced:openAdvancedExport,failed:error=>setStatus(error.message),
  });
  actions.inert = false;
  const entry = window.nobleNavigation?.takeVideoEntry?.() ?? new URLSearchParams(location.search).get('entry');
  await window.nobleProjectLauncher.openEntry(entry);
}
