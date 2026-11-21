(() => {
  const host = document.createElement('div');
  host.innerHTML = `
    <style>
      #noble-agent-open{position:fixed;right:18px;bottom:18px;z-index:10;background:#151515;color:#fff;border:0;border-radius:12px;padding:12px 18px}
      #noble-agent{box-sizing:border-box;width:min(540px,calc(100vw - 32px));max-height:85vh;padding:22px;border:1px solid #ddd;border-radius:16px;color:#111;background:#fff;font:15px/1.5 system-ui}
      #noble-agent::backdrop{background:#0008} #noble-agent h2{margin:0 0 12px}
      #noble-agent label{display:block;margin:10px 0} #noble-agent textarea{box-sizing:border-box;width:100%;font:inherit;padding:8px}
      #noble-agent button{min-height:40px;padding:6px 12px;margin:4px 6px 0 0}
    </style>
    <button id="noble-agent-open" type="button">Editing agent</button>
    <dialog id="noble-agent" aria-labelledby="noble-agent-title">
      <h2 id="noble-agent-title">Draft a vertical edit</h2>
      <label>Choose 2–12 photos or video clips <input data-files type="file" multiple accept="image/png,image/jpeg,image/webp,video/mp4,video/webm"></label>
      <label>Describe each shot in file order <textarea data-labels rows="3"></textarea></label>
      <label>Tell Noble what to make <textarea data-brief rows="3" maxlength="1000" placeholder="Make a fast cinematic vertical edit. Open on the skyline and end on the close-up."></textarea></label>
      <p data-status role="status"></p>
      <ol data-review></ol>
      <button data-draft type="button">Draft with AI</button>
      <button data-revise type="button" disabled>Revise with AI</button>
      <button data-apply type="button" disabled>Build editable timeline</button>
      <button data-close type="button">Close</button>
    </dialog>`;
  document.body.append(host);
  const dialog = host.querySelector('dialog');
  const find = name => dialog.querySelector('[data-' + name + ']');
  const files = find('files'), labels = find('labels'), brief = find('brief');
  const draft = find('draft'), apply = find('apply'), status = find('status'), review = find('review');
  const revise = find('revise');
  let approved, busy = false, cutLabels = [];

  async function maxFrames(file) {
    if (!file.type.startsWith('video/')) return 240;
    const video = document.createElement('video');
    const url = URL.createObjectURL(file);
    try {
      const duration = await new Promise((resolve, reject) => {
        const fail = () => reject(Error('Could not read the duration of ' + file.name + '.'));
        const timeout = setTimeout(fail, 8000);
        video.onloadedmetadata = () => {
          clearTimeout(timeout);
          resolve(video.duration);
        };
        video.onerror = () => {
          clearTimeout(timeout);
          fail();
        };
        video.preload = 'metadata';
        video.src = url;
      });
      if (!Number.isFinite(duration) || duration < 0.5) {
        throw Error(file.name + ' must be at least half a second and have a readable duration.');
      }
      return Math.min(240, Math.floor(duration * 24));
    } finally {
      video.removeAttribute('src');
      video.load();
      URL.revokeObjectURL(url);
    }
  }
  function reset() {
    approved = undefined;
    review.replaceChildren();
    apply.disabled = revise.disabled = true;
    status.textContent = '';
  }
  function renderCuts() {
    review.replaceChildren();
    for (const [index, cut] of approved.cuts.entries()) {
      const row = document.createElement('li');
      const label = cutLabels[cut.shot - 1];
      row.append(label + ' · ' + (cut.frames / 24).toFixed(2) + ' s ');
      for (const [offset, action] of [[-1, 'Move up'], [1, 'Move down'], [0, 'Remove']]) {
        const button = document.createElement('button');
        button.type = 'button';
        button.textContent = action;
        button.setAttribute('aria-label', action + ' ' + label);
        const target = index + offset;
        button.disabled = offset ? target < 0 || target >= approved.cuts.length : approved.cuts.length <= 2;
        button.onclick = () => {
          if (busy) return;
          if (offset) {
            [approved.cuts[index], approved.cuts[target]] = [approved.cuts[target], approved.cuts[index]];
          } else {
            approved.cuts.splice(index, 1);
          }
          renderCuts();
          review.children[Math.min(target, review.children.length - 1)].querySelector('button:enabled')?.focus();
        };
        row.append(button);
      }
      review.append(row);
    }
  }
  function setBusy(value) {
    busy = value;
    files.disabled = labels.disabled = brief.disabled = draft.disabled = value;
    apply.disabled = revise.disabled = value || !approved;
    review.inert = value;
    find('close').disabled = value;
  }
  host.querySelector('#noble-agent-open').onclick = () => dialog.showModal();
  find('close').onclick = () => dialog.close();
  dialog.addEventListener('cancel', event => {if (busy) event.preventDefault();});
  for (const type of ['keydown', 'keyup']) dialog.addEventListener(type, event => event.stopPropagation());
  files.onchange = () => {
    reset();
    labels.value = [...files.files].map(file => file.name.slice(0, 100)).join('\n');
  };
  labels.oninput = reset;
  async function requestDraft(previousDraft) {
    if (busy) return;
    const shots = labels.value.split('\n').map(label => label.trim());
    if (shots.length !== files.files.length || shots.length < 2 || shots.length > 12 || shots.some(label => !label || label.length > 100) || !brief.value.trim()) {
      status.textContent = 'Choose 2–12 photos or video clips, describe each one and enter an edit brief.';
      return;
    }
    setBusy(true);
    status.textContent = 'Asking the local model to draft your edit…';
    try {
      const limits = await Promise.all([...files.files].map(maxFrames));
      const response = await fetch('/mcp', {
        method: 'POST',
        headers: {'Content-Type': 'application/json', 'MCP-Protocol-Version': '2026-07-28', 'Mcp-Method': 'tools/call', 'Mcp-Name':'draft_vertical_edit'},
        body: JSON.stringify({jsonrpc:'2.0', id:1, method:'tools/call', params:{name:'draft_vertical_edit', arguments:{brief:brief.value, shots, limits, previousDraft}, _meta:{'io.modelcontextprotocol/clientInfo':{name:'Noble Studio', version:'0.1.0'}}}})
      });
      if (!response.ok) throw Error('Could not reach the local editing tool.');
      const reply = await response.json();
      if (!reply.result?.structuredContent) throw Error(reply.error?.message ?? reply.result?.content?.[0]?.text ?? 'No draft returned.');
      approved = reply.result.structuredContent;
      cutLabels = shots;
      status.textContent = approved.title;
      renderCuts();
    } catch (error) {
      status.textContent = error.message;
    } finally {
      setBusy(false);
    }
  }
  draft.onclick = () => requestDraft();
  revise.onclick = () => requestDraft(approved);
  apply.onclick = async () => {
    if (!approved || busy) return;
    if (!window.filmcraft) {
      status.textContent = 'Wait for Video Studio to finish opening.';
      return;
    }
    setBusy(true);
    try {
      const plan = approved;
      const selected = [...files.files];
      const items = [];
      for (const {shot} of plan.cuts) {
        const imported = await window.filmcraft.importFiles([selected[shot - 1]]);
        if (imported.items?.length !== 1) throw Error('A source file could not be imported. Check its file format.');
        items.push(imported.items[0]);
      }
      await window.filmcraft.execute('file.newSequence', {name:plan.title,width:1080,height:1920,fps:24,video:1,audio:0});
      let start = 0;
      const clips = [];
      for (const [index, {frames}] of plan.cuts.entries()) {
        const placed = await window.filmcraft.execute('timeline.place', {item:items[index],track:'V1',seconds:start/24,duration:{seconds:frames/24},audio:false});
        clips.push(...placed.clips);
        start += frames;
      }
      await window.filmcraft.execute('clip.fitToFrame', {clips});
      reset();
      dialog.close();
    } catch (error) {
      status.textContent = error.message;
    } finally {
      setBusy(false);
    }
  };
})();
