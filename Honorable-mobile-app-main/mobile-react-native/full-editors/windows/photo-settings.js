const key = 'noble.photo.workspace.v1';
const defaults = {dock:'right',width:326,density:'comfortable',theme:'warm',canvasOnly:false};

function normalize(value) {
  const options = value ?? {};
  return {
    dock: options.dock === 'left' ? 'left' : defaults.dock,
    width: Number.isInteger(options.width) && options.width >= 240 && options.width <= 440 ? options.width : defaults.width,
    density: ['compact','large'].includes(options.density) ? options.density : defaults.density,
    theme: options.theme === 'neutral' ? 'neutral' : defaults.theme,
    canvasOnly: options.canvasOnly === true,
  };
}

export function mountPhotoSettings(bar, shell, onLayout) {
  let preferences = defaults;
  let modified = false;
  const store = window.nobleNavigation?.photoPreferences;
  try { preferences = normalize(JSON.parse(localStorage.getItem(key))); } catch {}
  const settings = document.createElement('section');
  settings.id = 'photo-settings';
  settings.className = 'workspace-popover';
  settings.setAttribute('popover','auto');
  settings.setAttribute('aria-label','Customize photo workspace');
  settings.innerHTML = `
    <h2>Customize workspace</h2>
    <form>
      <label>Inspector position <select name="dock"><option value="right">Right</option><option value="left">Left</option></select></label>
      <label class="photo-settings-width"><span>Inspector width</span><output></output><input name="width" type="range" min="240" max="440" step="2" aria-label="Inspector width"></label>
      <label>Control size <select name="density"><option value="comfortable">Comfortable</option><option value="compact">Compact</option><option value="large">Large</option></select></label>
      <label>Theme <select name="theme"><option value="warm">Warm dark</option><option value="neutral">Neutral dark</option></select></label>
      <label class="photo-settings-canvas"><input name="canvasOnly" type="checkbox"> Canvas only</label>
      <button type="reset">Reset layout</button>
      <output class="photo-settings-status" role="status"></output>
    </form>`;
  bar.append(settings);

  const form = settings.querySelector('form');
  const fields = form.elements;
  const width = settings.querySelector('output');
  function apply() {
    document.body.dataset.photoDock = preferences.dock;
    document.body.dataset.photoDensity = preferences.density;
    document.body.dataset.photoTheme = preferences.theme;
    document.body.style.setProperty('--photo-inspect-size', preferences.width === defaults.width ? '' : preferences.width + 'px');
    shell.classList.toggle('photo-canvas-only', preferences.canvasOnly);
    width.value = preferences.width + ' px';
    onLayout(preferences);
  }
  function show() {
    for (const field of ['dock','width','density','theme']) fields.namedItem(field).value = preferences[field];
    fields.namedItem('canvasOnly').checked = preferences.canvasOnly;
    apply();
  }
  async function save() {
    try {
      if (store) await store.write(preferences);
      else localStorage.setItem(key,JSON.stringify(preferences));
      settings.querySelector('.photo-settings-status').textContent = '';
    } catch {
      settings.querySelector('.photo-settings-status').textContent = 'Could not save the workspace settings.';
    }
  }
  form.oninput = () => {
    modified = true;
    const entries = new FormData(form);
    preferences = normalize({...Object.fromEntries(entries),width:Number(entries.get('width')),canvasOnly:entries.has('canvasOnly')});
    apply();
  };
  form.onchange = save;
  form.onreset = event => {
    event.preventDefault();
    modified = true;
    preferences = defaults;
    show();
    save();
  };
  show();
  store?.read().then(saved => {
    if (saved && !modified) {
      preferences = normalize(saved);
      show();
    }
  }).catch(() => {
    settings.querySelector('.photo-settings-status').textContent = 'Could not read the saved workspace settings.';
  });
}
