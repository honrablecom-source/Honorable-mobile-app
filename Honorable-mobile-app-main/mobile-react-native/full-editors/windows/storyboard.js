(() => {
  const host = document.createElement('div');
  host.innerHTML = `
    <style>
      #story-open { position: fixed; bottom: 70px; right: 18px; z-index: 10; background: #fff; color: #111; border: 1px solid #333; border-radius: 12px; padding: 10px 16px; }
      #storyboard { width: min(620px, calc(100vw - 30px)); max-height: 80vh; color: #111; background: #fff; font: 15px/1.5 system-ui; border: 1px solid #888; padding: 20px; border-radius: 12px; }
      #storyboard::backdrop { background: #0008; }
      #storyboard ol { padding-left: 24px; }
      #storyboard li { margin-bottom: 8px; }
      #storyboard img { width: 60px; height: 44px; object-fit: cover; vertical-align: middle; margin-right: 8px; }
      #storyboard input[type=number] { width: 58px; }
      #storyboard button { margin: 3px; }
    </style>
    <button id="story-open" type="button">Photo storyboard</button>
    <dialog id="storyboard" aria-labelledby="storyboard-title">
      <h2 id="storyboard-title">Photo storyboard</h2>
      <label>Choose 2–12 photos <input type="file" accept="image/png,image/jpeg,image/webp" multiple></label>
      <ol aria-label="Storyboard shots" role="list"></ol>
      <p role="status"></p>
      <button data-build type="button" disabled>Build timeline</button>
      <button data-close type="button">Close</button>
    </dialog>`;
  document.body.append(host);
  const dialog = host.querySelector('dialog');
  const picker = dialog.querySelector('input');
  const list = dialog.querySelector('ol');
  const status = dialog.querySelector('[role=status]');
  const build = dialog.querySelector('[data-build]');
  const close = dialog.querySelector('[data-close]');
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
        button.textContent = label;
        button.dataset.offset = offset;
        button.setAttribute('aria-label', label + ' ' + photo.file.name);
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
