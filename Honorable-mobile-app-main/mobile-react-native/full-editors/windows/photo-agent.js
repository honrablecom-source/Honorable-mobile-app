import {describePhotoEdit,photoAdjustmentLayers,photoMeasurements,validatePhotoRecipe} from './photo-recipe.js';

const version = '2026-07-28';
let requestId = 0;

function isSamePhoto(a,b) {
  return Boolean(a && b && a.documentId === b.documentId && a.revision === b.revision);
}

async function readPhoto(command, document) {
  const across = Math.min(8,document.width);
  const down = Math.min(8,document.height);
  const steps = [];
  for (let row = 0; row < down; row++) {
    for (let column = 0; column < across; column++) {
      steps.push(['document.pixel',{
        x:Math.floor((column + 0.5) * document.width / across),
        y:Math.floor((row + 0.5) * document.height / down),
      }]);
    }
  }
  const {ok,results} = await command('file.scripts.browse',{steps});
  if (!ok || results.length !== steps.length) throw Error('Image Studio could not sample the current photo.');
  return photoMeasurements(document,results.map(entry => entry.result));
}

async function draft(brief, measurements, finish, signal) {
  const id = ++requestId;
  const response = await fetch('/mcp',{
    method:'POST',signal,headers:{'Content-Type':'application/json','MCP-Protocol-Version':version},
    body:JSON.stringify({jsonrpc:'2.0',id,method:'tools/call',params:{name:'draft_photo_edit',arguments:{brief,measurements,finish}}}),
  });
  if (!response.ok) throw Error('Could not reach the local photo agent.');
  const result = await response.json();
  if (result.id !== id) throw Error('The local photo agent returned an invalid reply.');
  if (result.error || result.result?.isError) {
    throw Error(result.error?.message || result.result?.content?.[0]?.text || 'The local photo agent could not prepare a plan.');
  }
  const plan = result.result?.structuredContent;
  if (plan?.tool !== 'inspect_photo') throw Error('The plan is missing photo inspection.');
  return validatePhotoRecipe(plan);
}

function mountProof(panel) {
  const proof = document.createElement('section');
  proof.className = 'photo-proof';
  proof.setAttribute('aria-label','Photo adjustment proof');
  proof.hidden = true;
  proof.dataset.mode = 'split';
  proof.innerHTML = `
    <label class="photo-proof-mode">Comparison
      <select aria-label="Comparison mode">
        <option value="original">Original</option><option value="split" selected>Split view</option><option value="preview">Preview</option>
      </select>
    </label>
    <div class="photo-proof-stage">
      <div class="photo-proof-frame">
        <img alt="Original photo" data-proof-original>
        <img alt="Photo with the selected adjustments" data-proof-adjusted>
        <div class="photo-proof-divider" aria-hidden="true"></div>
        <input type="range" min="0" max="100" value="50" aria-label="Original and preview divider" title="Drag to compare the original and preview">
      </div>
    </div>`;
  panel.querySelector('[data-agent-review]').append(proof);
  const original = proof.querySelector('[data-proof-original]');
  const adjusted = proof.querySelector('[data-proof-adjusted]');
  const divider = proof.querySelector('input');
  const mode = proof.querySelector('select');
  mode.onchange = () => {
    proof.dataset.mode = mode.value;
    divider.hidden = proof.dataset.mode !== 'split';
  };
  divider.oninput = () => proof.style.setProperty('--proof-split',divider.value+'%');
  return {
    async show(result) {
      proof.style.setProperty('--proof-ratio',result.width / result.height);
      if (result.original) original.src = 'data:image/png;base64,' + result.original;
      adjusted.src = 'data:image/png;base64,' + result.adjusted;
      await Promise.all([original.decode(),adjusted.decode()]);
      proof.hidden = false;
    },
    clear() {
      proof.hidden = true;
      original.removeAttribute('src');
      adjusted.removeAttribute('src');
    },
  };
}

export function mountPhotoAgent(panel,{request,command,run,canvas,connection}) {
  panel.innerHTML = `
    <button type="button" data-agent-retry hidden>Check connection</button>
    <details data-agent-request open><summary hidden>Edit request <span data-agent-brief-summary></span></summary><form data-agent-form>
      <label for="photo-agent-brief">Describe your edit</label>
      <textarea id="photo-agent-brief" rows="2" maxlength="1000" required placeholder="Brighten the photo and warm the colors."></textarea>
      <label class="photo-finish" for="photo-agent-finish">Finish
        <select id="photo-agent-finish"><option value="natural">Natural</option><option value="creative">Creative</option></select>
      </label>
      <div class="photo-agent-actions">
        <button type="submit" data-agent-plan>Plan edit</button>
        <button type="button" data-agent-cancel hidden>Cancel</button>
      </div>
    </form></details>
    <p role="status" data-agent-status></p>
    <section data-agent-review hidden aria-label="Proposed adjustments">
      <div class="photo-layer-review"><h3>Adjustment layers</h3>
      <ol data-agent-edits></ol>
      </div>
      <div class="photo-review-settings"><label class="photo-proof-strength">Strength
        <span class="photo-percent"><input type="number" min="0" max="100" step="1" required value="100" aria-label="Adjustment strength" data-agent-strength><span aria-hidden="true">%</span></span>
      </label>
      <p role="status" data-agent-quality hidden></p>
      <button type="button" data-agent-apply>Apply selected</button></div>
    </section>
    <button type="button" data-agent-undo hidden>Undo agent edit</button>`;
  const $ = name => panel.querySelector('[data-agent-' + name + ']');
  const text = panel.querySelector('textarea');
  const finish = panel.querySelector('select');
  const proof = mountProof(panel);
  const status = $('status');
  let connected = false;
  let checking;
  let rendering = false;
  let activePhoto;
  let pending;
  let proposed;
  let undo;
  let proofSource;
  let proofReady;
  let proofTimer;
  let proofWanted;
  let proofBusy = false;

  function controls() {
    const current = isSamePhoto(activePhoto,proposed?.source);
    $('plan').disabled = !connected || !activePhoto || Boolean(pending) || rendering || proofBusy;
    $('cancel').hidden = !pending;
    $('apply').disabled = !current || !proofReady || Number($('strength').value) === 0 || rendering || Boolean(pending);
    $('undo').hidden = !isSamePhoto(activePhoto,undo);
    $('undo').disabled = rendering;
    finish.disabled = rendering || Boolean(pending);
  }

  function checkConnection() {
    if (checking) return checking;
    connection.textContent = 'Checking Ollama…';
    $('retry').hidden = true;
    checking = (async () => {
      try {
        const response = await fetch('/mcp/status',{signal:AbortSignal.timeout(6000)});
        if (!response.ok) throw Error();
        const setup = await response.json();
        connected = setup.ready === true;
        connection.textContent = connected ? 'MCP connected · ' + setup.model
          : (setup.running ? '' : 'Start Ollama. ') + 'Install the local model with ollama pull ' + setup.model + '.';
      } catch {
        connected = false;
        connection.textContent = 'The local editing service is unavailable.';
      } finally {
        checking = undefined;
        $('retry').hidden = connected;
        controls();
      }
      return connected;
    })();
    return checking;
  }

  function clearPlan() {
    $('request').open = true;
    $('request').querySelector('summary').hidden = true;
    proposed = undefined;
    clearTimeout(proofTimer);
    proofWanted = proofReady = proofSource = undefined;
    proof.clear();
    $('review').hidden = true;
    $('edits').replaceChildren();
  }

  $('retry').onclick = () => void checkConnection();
  $('cancel').onclick = () => pending?.abort();
  function queueProof(immediate = false) {
    clearTimeout(proofTimer);
    proofReady = undefined;
    if (!$('strength').validity.valid) {
      proofWanted = undefined;
      status.textContent = 'Enter a whole percentage from 0 to 100.';
      controls();
      return;
    }
    const edits = [...$('edits').querySelectorAll('input:checked')].map(box => proposed?.edits[Number(box.value)]).filter(Boolean);
    $('quality').hidden = true;
    proofWanted = proposed && edits.length ? {
      source:proposed.source,layers:photoAdjustmentLayers(edits,Number($('strength').value)),
    } : undefined;
    if (proofWanted) {
      status.textContent = 'Rendering preview…';
      if (!immediate) proofTimer = setTimeout(renderProof,160);
    } else {
      proofSource = undefined;
      proof.clear();
      status.textContent = 'Select an adjustment to see its preview.';
    }
    controls();
    if (proofWanted && immediate) return renderProof();
  }

  async function renderProof() {
    if (proofBusy || !proofWanted) return;
    const desired = proofWanted;
    const source = desired.source;
    proofBusy = true;
    try {
      const result = await command('noble.photo.preview',{
        documentId:source.documentId,revision:source.revision,layers:desired.layers,includeOriginal:!isSamePhoto(source,proofSource),
      });
      if (proofWanted !== desired || !isSamePhoto(source,activePhoto)) return;
      await proof.show(result);
      if (proofWanted !== desired || !isSamePhoto(source,activePhoto)) {
        proof.clear();
        proofSource = undefined;
        return;
      }
      proofSource = source;
      proofReady = desired;
      status.textContent = '';
      const warnings = [];
      if (result.highlightClipping >= .005) warnings.push('Clipped highlights ' + (result.highlightClipping*100).toFixed(1) + '%');
      if (result.shadowClipping >= .005) warnings.push('Clipped shadows ' + (result.shadowClipping*100).toFixed(1) + '%');
      $('quality').hidden = !warnings.length;
      $('quality').textContent = warnings.join(' · ');
      $('quality').title = 'Lower strength to preserve more detail.';
    } catch (error) {
      if (proofWanted === desired) {
        proof.clear();
        proofSource = undefined;
        status.textContent = error.message || String(error);
      }
    } finally {
      proofBusy = false;
      if (proofWanted === desired) proofWanted = undefined;
      if (proofWanted) void renderProof();
      controls();
    }
  }

  $('edits').onchange = () => queueProof();
  $('strength').oninput = () => queueProof();
  finish.onchange = () => {
    if (!proposed) return;
    clearPlan();
    status.textContent = 'Plan again with the new finish.';
    controls();
  };
  async function planEdit(brief,style) {
    if (pending || rendering || proofBusy) return {error:'A photo preview is already being prepared in Noble.'};
    if (!brief.trim()) return {error:'Describe the photo adjustment first.'};
    const controller = new AbortController();
    pending = controller;
    clearPlan();
    controls();
    status.textContent = 'Measuring the open photo…';
    try {
      const {document:source} = await request('ui.inspect');
      if (!source || !Number.isSafeInteger(source.documentId)) throw Error('Open a photo in the current Windows build.');
      const measurements = await readPhoto(command,source);
      controller.signal.throwIfAborted();
      if (!isSamePhoto(source,(await request('ui.inspect')).document)) throw Error('The photo changed. Create a fresh plan.');
      status.textContent = 'Planning with the local model…';
      const edits = await draft(brief,measurements,style,controller.signal);
      controller.signal.throwIfAborted();
      activePhoto = (await request('ui.inspect')).document;
      if (!isSamePhoto(source,activePhoto)) throw Error('The photo changed. Create a fresh plan.');
      if (!edits.length) {
        status.textContent = 'Try asking for brightness, contrast, saturation, warmth, or black and white.';
        return {error:status.textContent};
      }
      proposed = {source,edits};
      $('strength').value = '100';
      for (const [index,edit] of edits.entries()) {
        const item = document.createElement('li');
        const label = document.createElement('label');
        const checkbox = document.createElement('input');
        checkbox.type = 'checkbox';
        checkbox.checked = true;
        checkbox.value = index;
        label.append(checkbox,describePhotoEdit(edit));
        item.append(label);
        $('edits').append(item);
      }
      $('review').hidden = false;
      $('request').querySelector('summary').hidden = false;
      $('request').open = false;
      $('brief-summary').textContent = brief;
      await queueProof(true);
      controller.signal.throwIfAborted();
      if (!proofReady) return {error:status.textContent || 'Noble could not render the preview.'};
      return {result:edits};
    } catch (error) {
      clearPlan();
      status.textContent = controller.signal.aborted ? 'Plan cancelled.' : error.message || String(error);
      return {error:status.textContent};
    } finally {
      pending = undefined;
      controls();
    }
  }
  $('form').onsubmit = async event => {
    event.preventDefault();
    if ($('plan').disabled) return;
    const reply = await planEdit(text.value,finish.value);
    if (reply.error) status.textContent = reply.error;
  };

  async function mutate(action) {
    if (rendering) return;
    rendering = true;
    controls();
    try {
      await run(async () => {
        try {
          canvas.inert = true;
          const snapshot = (await request('ui.inspect')).document;
          await action(snapshot);
          activePhoto = (await request('ui.inspect')).document;
        } catch (error) {
          status.textContent = error.message || String(error);
        } finally {
          canvas.inert = false;
        }
      });
    } finally {
      rendering = false;
      controls();
    }
  }

  $('apply').onclick = () => {
    const plan = proofReady;
    if (!plan || $('apply').disabled) return;
    void mutate(async snapshot => {
      if (!isSamePhoto(snapshot,plan.source)) throw Error('The photo changed. Create a fresh plan.');
      const {layers} = await command('noble.photo.apply',{
        documentId:snapshot.documentId,revision:snapshot.revision,layers:plan.layers,
      });
      undo = (await request('ui.inspect')).document;
      clearPlan();
      status.textContent = 'Added ' + layers.length + (layers.length === 1 ? ' editable layer.' : ' editable layers.');
    });
  };
  $('undo').onclick = () => {
    const last = undo;
    if ($('undo').disabled || !last) return;
    void mutate(async snapshot => {
      if (!isSamePhoto(snapshot,last)) throw Error('The photo changed. Use the document history for earlier edits.');
      if (await command('edit.undo') !== true) throw Error('The previous photo state is unavailable.');
      undo = undefined;
      status.textContent = 'Agent edit undone.';
    });
  };

  controls();
  return {
    open:checkConnection,
    async setStrength(percent) {
      if (!proposed || !isSamePhoto(activePhoto,proposed.source) || pending || rendering || proofBusy) throw Error('Prepare a photo edit preview first and wait for it to finish.');
      $('strength').value=String(percent);
      await queueProof(true);
      if (!proofReady) throw Error(status.textContent || 'Could not render the selected adjustments.');
      return {percent,canApply:!$('apply').disabled,status:'awaiting_approval'};
    },
    async planFromMcp({brief,finish:style}) {
      if (pending || rendering || proofBusy) return {error:'A photo preview is already being prepared in Noble.'};
      if (!activePhoto) return {error:'Open a photo in Noble Image Studio first.'};
      if (!await checkConnection()) return {error:connection.textContent};
      const reply = await planEdit(brief,style);
      if (reply.result) {
        text.value = brief;
        finish.value = style;
      }
      return reply;
    },
    update(source) {
      activePhoto = source;
      if (proposed && !isSamePhoto(source,proposed.source)) {
        clearPlan();
        status.textContent = 'The photo changed. Create a fresh plan.';
      }
      controls();
    },
  };
}
