import {receiveBrief} from './home-composer.js';
import {inspectVideo, inspectMusic} from './cinematic-media.js';
import {inspectChange, mountVideoChanges, recordVideoChange} from './video-changes.js';

const host = document.createElement('div');
host.innerHTML = `
  <button id="cinematic-open" type="button">AI editor</button>
  <dialog id="cinematic" aria-labelledby="cinematic-title">
    <header class="cinematic-heading">
      <h2 id="cinematic-title">AI editor</h2>
      <button data-close type="button" aria-label="Close AI editor">×</button>
    </header>
    <div class="cinematic-tabs" role="tablist" aria-label="AI editor">
      <button data-tab="agent" type="button" id="agent-tab" role="tab" aria-controls="agent-edit" aria-selected="true">Compose</button>
      <button data-tab="changes" type="button" id="changes-tab" role="tab" aria-controls="agent-changes" aria-selected="false" tabindex="-1">Changes</button>
    </div>
    <section id="agent-edit" role="tabpanel" aria-labelledby="agent-tab">
      <div class="cinematic-content">
        <div class="cinematic-format"><strong>Vertical edit</strong><span>9:16 · 20 seconds</span></div>
        <fieldset class="cinematic-sources">
          <legend>Media</legend>
          <label class="cinematic-file"><input data-video type="file" accept="video/*" multiple aria-label="Choose video clips">
            <span class="source-icon" aria-hidden="true">▸</span><span><strong>Video clips</strong><small data-video-name>Choose 3–12 clips</small></span><span class="file-action">Browse</span>
          </label>
          <label class="cinematic-file"><input data-music type="file" accept="audio/*" aria-label="Choose music">
            <span class="source-icon" aria-hidden="true">♪</span><span><strong>Music</strong><small data-music-name>Choose a soundtrack</small></span><span class="file-action">Browse</span>
          </label>
        </fieldset>
        <label class="cinematic-brief">Describe the edit
          <textarea data-brief rows="4" maxlength="1000" placeholder="Open quietly. Pick up the pace on the beat and end on a close-up."></textarea>
        </label>
      </div>
      <footer class="cinematic-footer">
        <p data-status role="status"></p>
        <button data-draft type="button">Build first cut</button>
        <div class="cinematic-secondary"><button data-revise type="button" hidden>Revise middle</button>
        <button data-undo type="button" hidden>Restore previous cut</button></div>
      </footer>
    </section>
    <section id="agent-changes" role="tabpanel" aria-labelledby="changes-tab" hidden>
      <div class="changes-empty" data-changes-empty>
        <h3>No AI edits yet</h3>
        <p>Edits made here or through MCP will appear as you work in this project.</p>
        <button data-to-compose type="button">Compose an edit</button>
      </div>
      <div class="changes-details" data-changes-details hidden>
        <label>AI edit <select></select></label>
        <strong data-changes-summary></strong>
        <div class="changes-range" data-changes-range aria-hidden="true"></div>
        <p class="changes-hint">Select a timecode to jump to that edit.</p>
        <ol aria-label="Timeline changes"></ol>
        <p data-changes-status role="status"></p>
      </div>
    </section>
  </dialog>`;
document.body.append(host);
const dialog = host.querySelector('dialog');
const get = name => dialog.querySelector('[data-' + name + ']');
const toggle = host.querySelector('#cinematic-open');
const changes = document.createElement('button');
changes.id = 'cinematic-changes';
changes.type = 'button';
changes.textContent = 'Changes';
changes.hidden = true;
host.prepend(changes);
toggle.setAttribute('aria-controls', dialog.id);
toggle.setAttribute('aria-expanded', 'false');
changes.setAttribute('aria-controls', dialog.id);
changes.setAttribute('aria-expanded', 'false');
const video = get('video'), music = get('music'), brief = get('brief'), status = get('status');
const tabs = [...dialog.querySelectorAll('[role=tab]')];
let state;
let busy = false;
let opener = toggle;

function selectTab(name,{focus = false} = {}) {
  for (const tab of tabs) {
    const selected = tab.dataset.tab === name;
    tab.setAttribute('aria-selected',String(selected));
    tab.tabIndex = selected ? 0 : -1;
    document.getElementById(tab.getAttribute('aria-controls')).hidden = !selected;
    if (selected && focus) tab.focus();
  }
  toggle.setAttribute('aria-expanded',String(dialog.open && name === 'agent'));
  changes.setAttribute('aria-expanded',String(dialog.open && name === 'changes'));
  if (dialog.open && name === 'agent' && !state && !busy && !status.textContent) void refreshModel();
}
for (const tab of tabs) {
  tab.onclick = () => selectTab(tab.dataset.tab);
  tab.onkeydown = event => {
    if (!['ArrowLeft','ArrowRight','Home','End'].includes(event.key)) return;
    event.preventDefault();
    const other = event.key === 'Home' ? tabs[0] : event.key === 'End' ? tabs.at(-1) : tabs.find(item=>item !== tab);
    selectTab(other.dataset.tab,{focus:true});
  };
}
get('to-compose').onclick = () => selectTab('agent',{focus:true});

mountVideoChanges(document.getElementById('agent-changes'),{async seek(sequence,time) {
  const editor = window.filmcraft;
  const timeline = await editor.execute('sequence.inspect',{item:sequence});
  await editor.request('ui.playback',{action:'stop'});
  await editor.execute('sequence.open',{item:sequence});
  await editor.execute('playhead.set',{time:Math.min(time,Math.max(0,timeline.duration-1))});
  window.dispatchEvent(new Event('noble-cinematic-edit'));
}});

function buttons() {
  get('draft').disabled = video.disabled = music.disabled = brief.disabled = get('close').disabled = busy;
  get('draft').textContent = state ? 'Build another cut' : 'Build first cut';
  get('revise').hidden = !state || Boolean(state.prior);
  get('undo').hidden = !state?.prior;
  get('revise').disabled = busy || !state || Boolean(state.prior);
  get('undo').disabled = busy || !state?.prior;
}

for (const [input,name,empty] of [[video,'video-name','Choose 3–12 clips'],[music,'music-name','Choose a soundtrack']]) {
  input.onchange = () => {
    const label = get(name);
    label.textContent = input.files.length > 1 ? input.files.length+' clips selected' : input.files[0]?.name ?? empty;
    label.title = label.textContent;
    state = undefined;
    buttons();
  };
}
window.addEventListener('noble-project-replaced',()=>{state = undefined; status.textContent = ''; buttons();});
function openPanel(button,name) {
  if (dialog.open && button.getAttribute('aria-expanded') === 'true') {
    if (!busy) dialog.close();
    return;
  }
  opener = button;
  const opened = !dialog.open;
  if (opened) dialog.show();
  if (opened && name === 'agent' && !state && !busy) status.textContent = '';
  selectTab(name,{focus:true});
}
async function refreshModel() {
  status.textContent = 'Checking the local model…';
  try {
    const setup = await (await fetch('/mcp/status')).json();
    if (busy || state) return;
    status.textContent = setup.ready ? 'Local model ready · '+setup.model :
      (!setup.running ? 'Start the Ollama desktop app or run ollama serve. ' : '') +
      'Install the local model with ollama pull ' + setup.model + '.';
  } catch {
    if (!busy) status.textContent = 'Could not check the local editing service. Reopen Noble Studio and try again.';
  }
}
toggle.onclick = () => void openPanel(toggle,'agent');
changes.onclick = () => void openPanel(changes,'changes');
get('close').onclick = () => dialog.close();
dialog.addEventListener('cancel', event => {if (busy) event.preventDefault();});
dialog.addEventListener('close', () => {
  toggle.setAttribute('aria-expanded', 'false');
  changes.setAttribute('aria-expanded', 'false');
  opener.focus();
});
dialog.addEventListener('keydown', event => {
  if (event.key === 'Escape' && !busy) dialog.close();
});
for (const type of ['keydown','keyup']) dialog.addEventListener(type,event => event.stopPropagation());

async function createSequence(editor, cuts, items, soundtrack, name) {
  const {sequence} = await editor.execute('file.newSequence', {name,width:1080,height:1920,fps:24,video:1,audio:1});
  const clips = [];
  for (const cut of cuts) {
    const placed = await editor.execute('timeline.place', {
      item:items[cut.clip], track:'V1', frame:cut.at,
      sourceIn:{seconds:cut.from/24}, duration:{seconds:cut.frames/24}, audio:false,
    });
    clips.push(...placed.clips);
  }
  await editor.execute('clip.fitToFrame',{clips});
  const intro = cuts[0].frames;
  const quiet = await editor.execute('timeline.place',{
    item:soundtrack,track:'A1',frame:0,duration:{seconds:intro/24},video:false,
  });
  await editor.execute('clip.audioGain',{clips:quiet.clips,mode:'set',db:-12});
  await editor.execute('timeline.place',{
    item:soundtrack,track:'A1',frame:intro,sourceIn:{seconds:intro/24},duration:{seconds:(480-intro)/24},video:false,
  });
  void editor.request('ui.set', {timeline:{fit:true}}).catch(() => {});
  return sequence;
}

async function run(revision) {
  if (busy || document.body.inert) return;
  const editor = window.filmcraft;
  const files = Array.from(video.files);
  const track = music.files[0];
  if (!editor?.info?.().startupMs || files.length < 3 || files.length > 12 || !track || !brief.value.trim()) {
    status.textContent = 'Wait for Studio to load, choose 3–12 videos and one music track, and enter an edit request.';
    return;
  }
  busy = true;
  document.body.dataset.nobleAgentBusy = '';
  buttons();
  const prior = revision ? state : undefined;
  try {
    const before = await inspectChange(editor,prior?.sequence).catch(()=>null);
    status.textContent = 'Measuring footage motion and detecting beats locally…';
    const media = prior?.media ?? {
      moments:(await Promise.all(files.map((file,index)=>inspectVideo(file,index)))).flat(),
      beats:await inspectMusic(track),
    };
    status.textContent = 'Waiting for the local model to inspect the media through MCP…';
    const response = await fetch('/mcp',{
      method:'POST',headers:{'Content-Type':'application/json','MCP-Protocol-Version':'2026-07-28'},
      body:JSON.stringify({jsonrpc:'2.0',id:1,method:'tools/call',params:{name:'draft_cinematic_edit',
        arguments:{brief:brief.value,media,previous:prior?.cuts}}}),
    });
    const reply = await response.json();
    const result = reply.result?.structuredContent;
    if (!result) throw Error(reply.result?.content?.[0]?.text ?? 'Could not create the cinematic draft.');
    const items = prior?.items ?? (await editor.importFiles(files)).items;
    const soundtrack = prior?.soundtrack ?? (await editor.importFiles([track])).items[0];
    if (items.length !== files.length || !soundtrack) throw Error('Studio could not import every selected file.');
    const sequence = await createSequence(editor,result.cuts,items,soundtrack,revision ? 'Cinematic edit · middle revision' : 'Cinematic edit');
    state = {sequence,cuts:result.cuts,beats:result.beats,media,items,soundtrack,prior};
    status.textContent = (revision ? 'Middle revised. ' : 'First cut ready. ') + result.cuts.length + ' editable shots. Press Play to review.';
    const after = await inspectChange(editor,sequence).catch(()=>null);
    if (after) recordVideoChange({before,after,title:revision ? 'Revised the middle' : 'Built a first cut',source:'Noble AI',beats:result.beats});
    if (after && dialog.open) selectTab('changes',{focus:true});
    window.dispatchEvent(new Event('noble-cinematic-edit'));
  } catch (error) {
    if (prior) await editor.execute('sequence.open',{item:prior.sequence}).catch(()=>{});
    status.textContent = error.message;
  } finally {
    busy = false;
    delete document.body.dataset.nobleAgentBusy;
    buttons();
  }
}

get('draft').onclick = () => run(false);
get('revise').onclick = () => run(true);
get('undo').onclick = async () => {
  if (busy || !state?.prior) return;
  busy = true;
  buttons();
  try {
    const editor = window.filmcraft;
    const before = await inspectChange(editor,state.sequence).catch(()=>null);
    await editor.execute('sequence.open',{item:state.prior.sequence});
    state = state.prior;
    const after = await inspectChange(editor,state.sequence).catch(()=>null);
    if (after) recordVideoChange({before,after,title:'Restored previous cut',source:'Noble AI'});
    void window.filmcraft.request('ui.set', {timeline:{fit:true}}).catch(() => {});
    status.textContent = 'Restored the previous edit. The revision is still available as a separate sequence.';
    window.dispatchEvent(new Event('noble-cinematic-edit'));
  } catch (error) {
    status.textContent = error.message;
  } finally {
    busy = false;
    buttons();
  }
};

receiveBrief('video', value => {
  brief.value = value;
  if (dialog.open) selectTab('agent');
  else openPanel(toggle, 'agent');
});