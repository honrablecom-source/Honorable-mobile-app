import {mountVideoMcp} from './video-mcp.js';

const bar = document.createElement('header');
bar.id = 'noble-workspace-bar';
const canvas = document.querySelector('#filmcraft_canvas');
bar.innerHTML = `
  <a class="workspace-home" href="/desktop/home.html"><img src="/desktop/noble.png" width="24" height="24" alt=""> Home</a>
  <strong>${canvas ? 'Video Studio' : 'Image Studio'}</strong>
`;
document.body.prepend(bar);
document.body.classList.add(canvas ? 'noble-video-workspace' : 'noble-image-workspace');
if (window.nobleNavigation) {
  const home = bar.querySelector('.workspace-home');
  home.addEventListener('click', event => {
    event.preventDefault();
    window.nobleNavigation.openWorkspace(home.href);
  });
}
if (canvas) void setupVideo();

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
    <button type="button" data-action="import">Import media</button>
    <button type="button" data-action="open">Open</button>
    <button type="button" data-action="save">Save</button>
    <button type="button" popovertarget="noble-cut-tools">Tools</button>
    <button type="button" popovertarget="noble-layouts">Layout</button>
    <button type="button" popovertarget="noble-commands">Commands</button>
    <button type="button" class="workspace-primary" data-action="export" aria-pressed="false">Export</button>
  `;
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
  const agentButton = document.querySelector('#cinematic-open');
  if (agentButton) {
    agentButton.innerHTML = '<span aria-hidden="true">✦</span> AI agent';
    agentButton.classList.add('workspace-ai');
    actions.insertBefore(agentButton, actions.querySelector('[data-action="export"]'));
  }

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
  const action = name => actions.querySelector('[data-action="' + name + '"]');
  let statusTimer;
  const setStatus = message => {
    clearTimeout(statusTimer);
    status.textContent = message;
    if (message) statusTimer = setTimeout(() => {status.textContent = '';}, 8500);
  };

  async function refreshTimeline() {
    try {
      const project = await editor.execute('project.inspect');
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
  const started = Date.now();
  while (!window.filmcraft?.info?.().startupMs && Date.now() - started < 30000) {
    await new Promise(resolve => setTimeout(resolve, 80));
  }
  const editor = window.filmcraft;
  if (!editor?.info?.().startupMs) return;
  const invoke = id => editor.request('ui.menu.invoke', {id});
  const layoutButtons = [...layouts.querySelectorAll('[data-layout]')];
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
    for (const button of layoutButtons) button.setAttribute('aria-pressed', String(button.dataset.layout === layout));
    localStorage.setItem('noble-video-layout', layout);
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

  const start = document.createElement('section');
  start.className = 'workspace-start';
  start.hidden = true;
  start.setAttribute('aria-labelledby','workspace-start-title');
  start.innerHTML = `
    <div class="start-inner">
      <div class="start-heading">
        <h1 id="workspace-start-title">Start your edit</h1>
        <p>Drop video clips or photos here to put them on an editable timeline.</p>
      </div>
      <div class="start-primary">
        <button type="button" data-start="import">Choose media</button>
        <button type="button" data-start="open">Open project</button>
      </div>
      <div class="start-tools">
        <button type="button" data-start="agent"><strong>Draft with AI</strong><span>Build a first cut from clips and music.</span></button>
        <button type="button" data-start="story"><strong>Photo storyboard</strong><span>Set the order and timing of your photos.</span></button>
      </div>
      <button class="start-blank" type="button" data-start="blank">Open empty timeline</button>
      <p class="start-feedback" role="status"></p>
    </div>
  `;
  document.body.append(start);
  const startFeedback = start.querySelector('.start-feedback');
  const startActions = Object.fromEntries(
    [...start.querySelectorAll('[data-start]')].map(button => [button.dataset.start, button]));
  let starting = false;

  async function showTimelineIfReady() {
    const project = await refreshTimeline();
    if (project?.activeSequence) start.hidden = true;
  }
  async function beginWithMedia(files) {
    if (starting) return;
    const visual = files.filter(file => /^(video|image)\//.test(file.type));
    if (!visual.length) {
      startFeedback.textContent = 'Choose a video or photo to start the timeline. Add audio with Import media.';
      return;
    }
    starting = true;
    for (const button of Object.values(startActions)) button.disabled = true;
    startFeedback.textContent = 'Opening media…';
    try {
      const imported = await editor.importFiles(visual);
      if (!imported.items?.length) throw Error('Studio could not open those files. Try MP4, PNG, or JPEG.');
      await editor.execute('file.newSequence',{fromItems:imported.items,video:2,audio:2});
      void fitTimeline().catch(() => {});
      start.hidden = true;
      await refreshTimeline();
      if (imported.errors?.length) setStatus('Some files could not be opened. The supported files are on the timeline.');
    } catch (error) {
      startFeedback.textContent = error.message;
    } finally {
      starting = false;
      for (const button of Object.values(startActions)) button.disabled = false;
    }
  }

  const firstMedia = picker('video/*,image/*', true, beginWithMedia);
  start.ondragover = event => {
    if (!event.dataTransfer.types.includes('Files')) return;
    event.preventDefault();
    start.classList.add('is-drop-target');
  };
  start.ondragleave = event => {
    if (!start.contains(event.relatedTarget)) start.classList.remove('is-drop-target');
  };
  start.ondrop = event => {
    event.preventDefault();
    start.classList.remove('is-drop-target');
    if (event.dataTransfer.files.length) void beginWithMedia(Array.from(event.dataTransfer.files));
  };
  const imports = picker('video/*,audio/*,image/*,.srt,.vtt', true, files => run(async () => {
    const imported = await editor.importFiles(files);
    if (imported.items?.length) start.hidden = true;
    if (imported.errors?.length) throw Error(imported.items?.length
      ? 'Some files could not be imported. Supported files were added to the project.'
      : 'These files could not be imported. Check the format and try again.');
    setStatus(imported.items.length + (imported.items.length === 1 ? ' file imported.' : ' files imported.'));
  }));
  const openProject = file => run(async () => {
    const current = await editor.execute('project.inspect');
    if (current.dirty && !window.confirm('Replace the current project? Save your edits first if you want to keep them.')) return;
    const opened = await editor.openProject(file);
    start.hidden = true;
    await refreshTimeline();
    void fitTimeline().catch(() => {});
    setStatus(opened.missingMedia
      ? 'Project opened with missing media. Reimport the source files or relink them in the project panel.'
      : 'Project opened.');
  });
  const projects = picker('.fcproj', false, ([file]) => openProject(file));
  startActions.import.onclick = () => firstMedia.click();
  startActions.open.onclick = () => projects.click();
  startActions.blank.onclick = () => { start.hidden = true; canvas.focus(); };
  startActions.agent.onclick = () => document.querySelector('#cinematic-open')?.click();
  startActions.story.onclick = () => document.querySelector('#story-open')?.click();
  window.addEventListener('noble-cinematic-edit', () => void showTimelineIfReady());
  for (const id of ['#cinematic','#storyboard','#noble-agent']) {
    document.querySelector(id)?.addEventListener('close', () => void showTimelineIfReady());
  }
  const openEntry = entry => {
    if (entry === 'agent') startActions.agent.click();
    if (entry === 'story') startActions.story.click();
    if (entry === 'open') {
      if (!start.hidden) startActions.open.focus();
      else action('open').focus();
    }
  };
  action('import').onclick = () => imports.click();
  action('open').onclick = () => projects.click();
  action('save').onclick = () => { void run(() => invoke('file.save')); };
  const exportButton = action('export');
  let returnToStarter = false;
  exportButton.onclick = () => void run(async () => {
    const desiredMode = !exporting;
    await editor.request('ui.set', {mode: desiredMode ? 'export' : 'edit'});
    exporting = desiredMode;
    if (exporting) {
      returnToStarter = !start.hidden;
      start.hidden = true;
    } else if (returnToStarter) {
      start.hidden = false;
      await showTimelineIfReady();
      returnToStarter = false;
    }
    exportButton.textContent = exporting ? 'Back to edit' : 'Export';
    exportButton.setAttribute('aria-pressed', String(exporting));
    await refreshTimeline();
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
        void run(async () => {await invoke(item.id); await refreshTimeline();});
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
      menu = (await editor.request('ui.menu.list')).filter(item => item.path.length && !/^help\.(discord|website|github|appPage)$/.test(item.id));
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
  actions.inert = false;
  const initialProject = await refreshTimeline();
  start.hidden = !initialProject || Boolean(initialProject.activeSequence) || Boolean(initialProject.root?.children?.length);
  window.nobleProjectLauncher = {
    openEntry,
    async openLocalProject(name, base64) {
      const bytes = Uint8Array.from(atob(base64), value => value.charCodeAt(0));
      await openProject(new File([bytes], name, {type:'application/json'}));
    },
  };
  mountVideoMcp(editor,{settings:createPopover,notify:setStatus,showTimeline:()=>void showTimelineIfReady()});
  const entry = new URLSearchParams(location.search).get('entry');
  if (entry) openEntry(entry);
}
