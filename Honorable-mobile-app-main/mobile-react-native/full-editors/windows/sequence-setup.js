// FilmCraft captures drops at window level. Register before its bootstrap so a
// drop on the visible import page reaches the selection tray exactly once.
const importTargets = new WeakMap();
window.addEventListener('drop', event => {
  const target = event.target.closest?.('.workspace-start:not([hidden])');
  const add = importTargets.get(target);
  if (!add || !event.dataTransfer?.files.length) return;
  event.preventDefault();
  event.stopImmediatePropagation();
  target.classList.remove('is-drop-target');
  add(event.dataTransfer.files);
},true);

const presets = [
  {id:'source', title:'Match source', caption:'Use the first clip'},
  {id:'landscape', title:'Landscape HD', width:1920, height:1080},
  {id:'portrait', title:'Vertical HD', width:1080, height:1920},
  {id:'square', title:'Square', width:1080, height:1080},
];

export function mountSequenceSetup({editor, onCreated, openProject, openEditor, openAgent, openStoryboard}) {
  const element = document.createElement('section');
  element.className = 'workspace-start';
  element.hidden = true;
  element.setAttribute('aria-labelledby','workspace-start-title');
  element.innerHTML = `
    <div class="sequence-sheet">
      <header class="sequence-title"><h1 id="workspace-start-title">New video project</h1><button type="button" data-start="open">Open existing project…</button></header>
      <div class="sequence-columns">
        <section class="sequence-browser" aria-label="Project setup">
          <div class="sequence-tabs" role="tablist" aria-label="Project setup">
            <button type="button" role="tab" id="preset-tab" aria-controls="preset-panel" aria-selected="true">Sequence presets</button>
            <button type="button" role="tab" id="media-tab" aria-controls="media-panel" aria-selected="false" tabindex="-1">Import media <span data-file-count></span></button>
          </div>
          <div id="preset-panel" role="tabpanel" aria-labelledby="preset-tab">
            <fieldset class="preset-grid"><legend>Choose a starting format</legend></fieldset>
          </div>
          <div id="media-panel" role="tabpanel" aria-labelledby="media-tab" hidden>
            <div class="source-heading"><h2>Source media</h2><button type="button" data-start="import">Add files…</button></div>
            <input type="file" accept="video/*,image/*" multiple hidden>
            <div class="source-drop"><strong>Drop clips or photos here</strong><span>Files are added to the timeline in selection order.</span></div>
            <ol class="source-list" aria-label="Selected media"></ol>
          </div>
          <div class="setup-workflows"><h2>Other workflows</h2><div><button type="button" data-start="agent">AI editing agent <span aria-hidden="true">↗</span></button><button type="button" data-start="story">Photo storyboard <span aria-hidden="true">↗</span></button></div></div>
        </section>
        <form class="sequence-details">
          <h2>Sequence details</h2>
          <label>Sequence name<input name="sequenceName" type="text" value="Sequence 01" required maxlength="120" autocomplete="off"></label>
          <dl><div><dt>Frame size</dt><dd data-frame-size></dd></div></dl>
          <label>Frame rate<select name="frameRate"><option value="auto">Match source</option><option value="24">24 fps</option><option value="25">25 fps</option><option value="30">30 fps</option><option value="60">60 fps</option></select></label>
          <div class="sequence-summary"><span>Source media</span><button type="button" data-review></button></div>
          <p class="start-feedback" role="status"></p>
          <footer><button type="button" data-start="blank">Skip setup</button><button type="submit" data-create>Create sequence</button></footer>
        </form>
      </div>
    </div>`;
  document.body.append(element);
  const find = selector => element.querySelector(selector);
  const form = find('form');
  const rate = form.elements.frameRate;
  const name = form.elements.sequenceName;
  const picker = find('input[type=file]');
  const grid = find('.preset-grid');
  const tabs = [...element.querySelectorAll('[role=tab]')];
  const list = find('.source-list');
  const feedback = find('.start-feedback');
  const media = [];
  let busy = false;

  function showTab(selected, focus = false) {
    for (const tab of tabs) {
      const active = tab === selected;
      tab.setAttribute('aria-selected',String(active));
      tab.tabIndex = active ? 0 : -1;
      find('#' + tab.getAttribute('aria-controls')).hidden = !active;
    }
    if (focus) selected.focus();
  }
  for (const [index,tab] of tabs.entries()) {
    tab.onclick = () => showTab(tab);
    tab.onkeydown = event => {
      if (!['ArrowLeft','ArrowRight','Home','End'].includes(event.key)) return;
      event.preventDefault();
      showTab(tabs[event.key === 'Home' ? 0 : event.key === 'End' ? tabs.length-1 : 1-index],true);
    };
  }

  for (const preset of presets) {
    const label = document.createElement('label');
    label.className = 'sequence-preset';
    label.innerHTML = `<input type="radio" name="sequencePreset" value="${preset.id}"><span class="preset-preview" aria-hidden="true"><i></i></span><strong>${preset.title}</strong><small>${preset.caption || preset.width+' × '+preset.height}</small>`;
    grid.append(label);
  }
  function currentPreset() {
    return presets.find(preset => preset.id === grid.querySelector(':checked').value);
  }
  function updateDetails() {
    const preset = currentPreset();
    const follow = preset.id === 'source' && media.length > 0;
    find('[data-frame-size]').textContent = follow ? 'Match first file' : (preset.width || 1920) + ' × ' + (preset.height || 1080) + ' px';
    rate.options[0].textContent = follow ? 'Match first file' : 'Default (24 fps)';
    find('[data-file-count]').textContent = media.length ? String(media.length) : '';
    find('[data-review]').textContent = media.length ? media.length + (media.length === 1 ? ' file selected' : ' files selected') : 'Add media…';
  }
  grid.onchange = updateDetails;

  function renderMedia() {
    list.replaceChildren(...media.map((entry,index) => {
      const row = document.createElement('li');
      const order = document.createElement('span');
      order.className = 'source-order';
      order.textContent = String(index + 1);
      const thumb = document.createElement(entry.url ? 'img' : 'span');
      if (entry.url) { thumb.src = entry.url; thumb.alt = ''; }
      else { thumb.className = 'video-file-icon'; thumb.setAttribute('aria-hidden','true'); }
      const filename = document.createElement('span');
      filename.className = 'source-name';
      filename.title = filename.textContent = entry.file.name;
      const remove = document.createElement('button');
      remove.type = 'button';
      remove.textContent = '×';
      remove.setAttribute('aria-label','Remove ' + entry.file.name);
      remove.onclick = () => {
        media.splice(index,1);
        if (entry.url) URL.revokeObjectURL(entry.url);
        renderMedia();
        (list.children[Math.min(index, media.length-1)]?.querySelector('button') || find('[data-start=import]')).focus();
      };
      row.append(order,thumb,filename,remove);
      return row;
    }));
    find('.source-drop').hidden = Boolean(media.length);
    updateDetails();
  }
  function addFiles(files) {
    if (busy) return;
    let skipped = 0;
    for (const file of files) {
      if (!/^(image|video)\//.test(file.type) && !(file.type === '' && /\.(mp4|m4v|mov|webm|mkv|avi|png|jpe?g|webp|gif|bmp|tiff?)$/i.test(file.name))) { skipped++; continue; }
      media.push({file, url:file.type.startsWith('image/') ? URL.createObjectURL(file) : null});
    }
    showTab(tabs[1]);
    renderMedia();
    feedback.textContent = skipped ? 'Only clips and photos can be added here. Import audio in the editor.' : '';
  }
  picker.onchange = () => { addFiles(picker.files); picker.value = ''; };
  find('[data-start=import]').onclick = () => picker.click();
  find('[data-review]').onclick = () => {
    showTab(tabs[1],true);
    if (!media.length) picker.click();
  };
  element.ondragover = event => {
    if (!event.dataTransfer.types.includes('Files')) return;
    event.preventDefault();
    element.classList.add('is-drop-target');
  };
  element.ondragleave = event => {
    if (!element.contains(event.relatedTarget)) element.classList.remove('is-drop-target');
  };
  importTargets.set(element,addFiles);
  for (const eventType of ['keydown','keyup']) {
    element.addEventListener(eventType,event => event.stopPropagation());
  }

  function reset() {
    for (const entry of media) if (entry.url) URL.revokeObjectURL(entry.url);
    media.length = 0;
    grid.querySelector('input').checked = true;
    form.reset();
    feedback.textContent = '';
    showTab(tabs[0]);
    renderMedia();
  }
  find('[data-start=open]').onclick = openProject;
  find('[data-start=agent]').onclick = openAgent;
  find('[data-start=story]').onclick = openStoryboard;
  find('[data-start=blank]').onclick = () => { reset(); openEditor(); };
  form.onsubmit = async event => {
    event.preventDefault();
    if (busy) return;
    if (!name.value.trim()) { name.focus(); feedback.textContent = 'Enter a sequence name.'; return; }
    busy = element.inert = true;
    feedback.textContent = media.length ? 'Importing media…' : 'Creating sequence…';
    try {
      const preset = currentPreset();
      const options = {name:name.value.trim(), video:2, audio:2};
      const imported = media.length ? await editor.importFiles(media.map(entry => entry.file)) : null;
      if (media.length && !imported?.items?.length) throw Error('No media could be imported. Remove unsupported files and try again.');
      if (imported) options.fromItems = imported.items;
      if (preset.width || !imported) {
        options.width = preset.width || 1920;
        options.height = preset.height || 1080;
      }
      if (rate.value !== 'auto' || preset.width || !imported) options.fps = rate.value === 'auto' ? 24 : Number(rate.value);
      await editor.execute('file.newSequence',options);
      reset();
      element.hidden = true;
      await onCreated(Boolean(imported?.errors?.length));
    } catch (error) {
      feedback.textContent = error.message || 'The sequence could not be created.';
    } finally { busy = element.inert = false; }
  };
  reset();
  return {element, reset, get busy() { return busy; }};
}
