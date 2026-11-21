import {inspectVideo, inspectMusic} from './cinematic-media.js';

const host = document.createElement('div');
host.innerHTML = `
  <style>
    #cinematic-open { position:fixed; right:18px; bottom:122px; z-index:10; background:#111; color:white; border:0; border-radius:12px; padding:12px 16px; }
    #cinematic { width:min(650px,90vw); max-height:82vh; padding:22px; color:#171717; background:white; border:1px solid #aaa; border-radius:14px; font:15px/1.5 system-ui; }
    #cinematic::backdrop { background:#0008; }
    #cinematic label { display:block; margin:12px 0; }
    #cinematic textarea { display:block; box-sizing:border-box; width:100%; font:inherit; }
    #cinematic button { margin:4px 5px 0 0; padding:6px 10px; }
  </style>
  <button id="cinematic-open" type="button">Cinematic editing agent</button>
  <dialog id="cinematic" aria-labelledby="cinematic-title">
    <header class="cinematic-heading">
      <h2 id="cinematic-title">AI editing agent</h2>
      <button data-close type="button">Close</button>
    </header>
    <p class="dialog-intro">Build a 20-second vertical cut from your clips and soundtrack.</p>
    <div class="cinematic-sources"><label>Video clips <input data-video type="file" accept="video/*" multiple></label>
    <label>Music <input data-music type="file" accept="audio/*"></label></div>
    <label>Edit request <textarea data-brief rows="4" maxlength="1000" placeholder="Start quiet, cut faster on the beat, and finish on a close-up."></textarea></label>
    <p data-status role="status"></p>
    <div class="cinematic-footer"><button data-draft type="button">Build first cut</button>
    <button data-revise type="button" disabled>Revise middle</button>
    <button data-undo type="button" disabled>Undo agent revision</button></div>
  </dialog>`;
document.body.append(host);
const dialog = host.querySelector('dialog');
const get = name => dialog.querySelector('[data-' + name + ']');
const toggle = host.querySelector('#cinematic-open');
toggle.setAttribute('aria-controls', dialog.id);
toggle.setAttribute('aria-expanded', 'false');
const video = get('video'), music = get('music'), brief = get('brief'), status = get('status');
let state;
let busy = false;

function buttons() {
  get('draft').disabled = video.disabled = music.disabled = brief.disabled = get('close').disabled = busy;
  get('revise').disabled = busy || !state || Boolean(state.prior);
  get('undo').disabled = busy || !state?.prior;
}

for (const input of [video,music]) input.onchange = () => {state = undefined; buttons();};
toggle.onclick = async () => {
  if (dialog.open) {
    if (!busy) dialog.close();
    return;
  }
  dialog.show();
  toggle.setAttribute('aria-expanded', 'true');
  brief.focus();
  if (state) return;
  status.textContent = 'Checking the local model…';
  try {
    const setup = await (await fetch('/mcp/status')).json();
    if (busy || state) return;
    status.textContent = setup.ready ? setup.model + ' is ready.' :
      (!setup.running ? 'Start the Ollama desktop app or run ollama serve. ' : '') +
      'Install the local model with ollama pull ' + setup.model + '.';
  } catch {
    if (!busy) status.textContent = 'Could not check the local editing service. Reopen Noble Studio and try again.';
  }
};
get('close').onclick = () => dialog.close();
dialog.addEventListener('cancel', event => {if (busy) event.preventDefault();});
dialog.addEventListener('close', () => {
  toggle.setAttribute('aria-expanded', 'false');
  toggle.focus();
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
  if (busy) return;
  const editor = window.filmcraft;
  const files = Array.from(video.files);
  const track = music.files[0];
  if (!editor?.info?.().startupMs || files.length < 3 || files.length > 12 || !track || !brief.value.trim()) {
    status.textContent = 'Wait for Studio to load, choose 3–12 videos and one music track, and enter an edit request.';
    return;
  }
  busy = true;
  buttons();
  const prior = revision ? state : undefined;
  try {
    status.textContent = 'Measuring footage motion and detecting beats locally…';
    const media = prior?.media ?? {
      moments:(await Promise.all(files.map(inspectVideo))).flat(),
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
    window.dispatchEvent(new Event('noble-cinematic-edit'));
  } catch (error) {
    if (prior) await editor.execute('sequence.open',{item:prior.sequence}).catch(()=>{});
    status.textContent = error.message;
  } finally {
    busy = false;
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
    await window.filmcraft.execute('sequence.open',{item:state.prior.sequence});
    state = state.prior;
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
