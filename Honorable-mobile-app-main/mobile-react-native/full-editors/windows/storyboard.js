(() => {
  const host = document.createElement('div');
  host.innerHTML = `
    <button id="story-open" type="button">Photo storyboard</button>
    <dialog id="storyboard" class="studio-dialog" aria-labelledby="storyboard-title">
      <header class="studio-dialog-heading"><h2 id="storyboard-title">Photo storyboard</h2><button data-close type="button" aria-label="Close storyboard">×</button></header>
      <div class="studio-dialog-body">
        <label class="cinematic-file"><input type="file" accept="image/png,image/jpeg,image/webp" multiple aria-label="Choose photos">
          <span class="source-icon" aria-hidden="true">▤</span><span><strong>Photos</strong><small data-selection>Choose 2–12 photos</small></span><span class="file-action">Browse</span>
        </label>
        <p class="story-instructions">Arrange your shots and set how long each stays on screen.</p>
        <ol aria-label="Storyboard shots" role="list"></ol>
        <p role="status"></p>
      </div>
      <footer class="studio-dialog-footer"><output data-story-length aria-label="Total duration"></output><button data-build type="button" disabled>Build timeline</button></footer>
    </dialog>`;
  document.body.append(host);
  const dialog = host.querySelector('dialog');
  const picker = dialog.querySelector('input');
  const list = dialog.querySelector('ol');
  const status = dialog.querySelector('[role=status]');
  const build = dialog.querySelector('[data-build]');
  const close = dialog.querySelector('[data-close]');
  const selection = dialog.querySelector('[data-selection]');
  const durationOutput = dialog.querySelector('[data-story-length]');
  let photos = [];
  let busy = false;

  function updateTimes() {
    let frames = 0;
    for (const [index, row] of [...list.children].entries()) {
      const seconds = Math.floor(frames / 24);
      row.querySelector('output').textContent = Math.floor(seconds / 60) + ':' + String(seconds % 60).padStart(2, '0');
      frames += photos[index].frames || 0;
    }
    build.disabled = busy || photos.length < 2 || photos.some(photo => !photo.frames);
    const seconds = frames/24;
    durationOutput.textContent = photos.length ? photos.length+' shots · '+seconds+' s' : '';
  }

  function render() {
    list.replaceChildren(...photos.map((photo, index) => {
      const row = document.createElement('li');
      const thumbnail = document.createElement('img');
      thumbnail.alt = '';
      thumbnail.src = photo.url;
      const time = document.createElement('output');
      const name = document.createElement('strong');
      name.textContent = photo.file.name;
      const caption = document.createElement('div');
      caption.className = 'story-shot';
      caption.append(name, time);
      const duration = document.createElement('input');
      duration.type = 'number';
      duration.min = '1';
      duration.max = '30';
      duration.step = '1';
      duration.value = photo.frames ? photo.frames / 24 : '';
      duration.setAttribute('aria-label', 'Seconds for ' + photo.file.name);
      duration.oninput = () => {
        photo.frames = duration.value && duration.validity.valid ? duration.valueAsNumber * 24 : 0;
        updateTimes();
      };
      time.setAttribute('aria-label', 'Start time for ' + photo.file.name);
      const hold = document.createElement('label');
      hold.className = 'story-hold';
      hold.append(duration, ' s');
      const order = document.createElement('div');
      order.className = 'story-order';
      row.append(thumbnail, caption, hold, order);
      for (const [offset, label] of [[-1, 'Move up'], [1, 'Move down']]) {
        const button = document.createElement('button');
        button.type = 'button';
        button.textContent = offset < 0 ? '↑' : '↓';
        button.dataset.offset = offset;
        button.setAttribute('aria-label', label + ' ' + photo.file.name);
        button.title = label;
        const destination = index + offset;
        button.disabled = destination < 0 || destination >= photos.length;
        button.onclick = () => {
          [photos[index], photos[destination]] = [photos[destination], photos[index]];
          render();
          const moved = list.children[destination];
          const repeat = moved.querySelector('[data-offset="' + offset + '"]:enabled');
          (repeat ?? moved.querySelector('button:enabled')).focus();
        };
        order.append(button);
      }
      return row;
    }));
    updateTimes();
  }

  picker.onchange = () => {
    for (const photo of photos) URL.revokeObjectURL(photo.url);
    photos = [];
    const selected = Array.from(picker.files);
    if (selected.length >= 2 && selected.length <= 12 && selected.every(file => /^image\//.test(file.type))) {
      photos = selected.map(file => ({file, frames: 72, url: URL.createObjectURL(file)}));
      status.textContent = '';
    } else {
      status.textContent = 'Choose 2–12 supported photos.';
    }
    selection.textContent = photos.length ? photos.length+' photos selected' : 'Choose 2–12 photos';
    render();
  };

  host.querySelector('#story-open').onclick = () => dialog.showModal();
  close.onclick = () => dialog.close();
  dialog.addEventListener('cancel', event => { if (busy) event.preventDefault(); });
  for (const type of ['keydown', 'keyup']) dialog.addEventListener(type, event => event.stopPropagation());

  build.onclick = async () => {
    if (build.disabled) return;
    busy = true;
    picker.disabled = close.disabled = true;
    list.inert = true;
    updateTimes();
    status.textContent = 'Building your timeline…';
    try {
      const editor = window.filmcraft;
      if (!editor?.info?.().startupMs) throw Error('Wait for Video Studio to finish opening.');
      const items = [];
      for (const photo of photos) {
        const imported = await editor.importFiles([photo.file]);
        if (imported.items?.length !== 1) throw Error('Could not import ' + photo.file.name + '.');
        items.push(imported.items[0]);
      }
      await editor.execute('file.newSequence', {name:'Photo story', width:1080, height:1920, fps:24, video:1, audio:0});
      let start = 0;
      const clips = [];
      for (const [index, photo] of photos.entries()) {
        const placed = await editor.execute('timeline.place', {item:items[index], track:'V1', seconds:start / 24, duration:{seconds:photo.frames / 24}, audio:false});
        clips.push(...placed.clips);
        start += photo.frames;
      }
      await editor.execute('clip.fitToFrame', {clips});
      void editor.request('ui.set', {timeline:{fit:true}}).catch(() => {});
      status.textContent = '';
      dialog.close();
    } catch (error) {
      status.textContent = error.message;
    } finally {
      busy = false;
      picker.disabled = close.disabled = false;
      list.inert = false;
      updateTimes();
    }
  };
})();
