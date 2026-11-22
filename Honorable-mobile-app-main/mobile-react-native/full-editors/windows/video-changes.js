const ticksPerSecond = 254016000000;
const entries = [];
const updates = new EventTarget();
const atStart = row => row.start ?? 0;

export const changeTitles = {
  build_video_edit:'Built a first cut',
  revise_video_edit:'Revised selected shots',
  duplicate_video_edit:'Duplicated the sequence',
  transform_video_clip:'Adjusted framing',
  grade_video_clip:'Adjusted color',
  add_video_transition:'Added a transition',
  mix_video_audio:'Adjusted the audio mix',
  add_video_title:'Added a title',
  add_video_captions:'Added subtitles',
};

export async function inspectChange(editor, sequence) {
  if (!sequence) return null;
  const timeline = await editor.execute('sequence.inspect',{item:sequence});
  const project = await editor.execute('project.inspect');
  const names = new Map();
  function read(bin) {
    for (const item of bin.children) {
      if (item.children) read(item);
      else names.set(item.item,item.name);
    }
  }
  read(project.root);
  const clips = ['video','audio'].flatMap(kind=>timeline[kind].flatMap((track,index)=>track.items.map(item=>({
    id:item.clip,item:item.item,kind,track:track.name,index,name:item.name || names.get(item.item) || (kind === 'video' ? 'Video clip' : 'Audio clip'),
    start:item.start,duration:item.duration,source:item.sourceIn,speed:item.speed,gain:item.gainDb,
    reverse:item.reverse,effects:item.effects,
  }))));
  return {sequence,name:timeline.name,duration:timeline.duration,rate:timeline.settings.frame_rate,clips,
    audioTracks:Object.fromEntries(timeline.audio.map(track=>[track.id,track.name]))};
}

const signature = clip => JSON.stringify([
  clip.item,clip.kind,clip.index,clip.start,clip.duration,clip.source,clip.speed,clip.gain,clip.reverse,clip.effects,
]);

function describeChange(before,after) {
  const parts = [];
  if (before.start !== after.start || before.index !== after.index) parts.push('Moved');
  if (before.duration !== after.duration) parts.push('Length changed');
  if (before.source !== after.source) parts.push('Source in changed');
  if (before.speed !== after.speed || before.reverse !== after.reverse) parts.push('Speed changed');
  if (before.gain !== after.gain) parts.push('Clip gain changed');
  if (JSON.stringify(before.effects) !== JSON.stringify(after.effects)) parts.push('Effects changed');
  return parts.join(' · ');
}

export function timelineChangeRows(before,after) {
  const matches = new Map();
  const sameSequence = before?.sequence === after.sequence;
  for (const clip of before?.clips ?? []) {
    const key = sameSequence ? clip.id : signature(clip);
    const group = matches.get(key) ?? [];
    group.push(clip);
    matches.set(key,group);
  }
  const rows = [];
  for (const clip of after.clips) {
    const key = sameSequence ? clip.id : signature(clip);
    const previous = matches.get(key)?.shift();
    const label = previous ? describeChange(previous,clip) : 'Added';
    if (label) rows.push({...clip,label});
  }
  for (const group of matches.values()) {
    for (const clip of group) rows.push({...clip,label:'Removed'});
  }
  return rows.sort((a,b)=>atStart(a)-atStart(b) || a.kind.localeCompare(b.kind));
}

const ticks = 10584000000;
function extraRows(tool,args,change,after) {
  if (tool === 'mix_video_audio') {
    const title = args.property === 'pan' ? 'Pan' : 'Gain';
    return [{name:args.track === 'master' ? 'Master mix' : after.audioTracks[args.track] || 'Audio track',
      label:title+' set to '+args.value+(args.property === 'volumeDb' ? ' dB' : ''),start:(args.at ?? 0)*ticks}];
  }
  if (tool === 'add_video_transition') {
    return [{name:args.kind.replaceAll('_',' '),label:args.edge === 'in' ? 'Transition at clip start' : 'Transition at clip end',clip:args.clip,edge:args.edge}];
  }
  if (tool === 'add_video_captions' && change?.captions?.length) {
    return args.cues.map(cue=>({name:cue.text,label:'Subtitle added',start:cue.at*ticks,duration:cue.frames*ticks}));
  }
  return [];
}

export function recordVideoChange({before,after,title,source,tool,args,change,beats}) {
  const rows = timelineChangeRows(before,after);
  if (tool === 'transform_video_clip' && args.at !== undefined) {
    const clip = after.clips.find(item=>item.id === args.clip);
    const keyframe = rows.find(row=>row.id === args.clip);
    const label = args.property.charAt(0).toUpperCase()+args.property.slice(1)+' keyframe';
    if (keyframe) Object.assign(keyframe,{label,start:args.at*ticks});
    else if (clip) rows.push({...clip,label,start:args.at*ticks});
  }
  for (const extra of extraRows(tool,args,change,after)) {
    const clip = after.clips.find(item=>item.id === extra.clip);
    const start = extra.start ?? (clip ? clip.start+(extra.edge === 'out' ? clip.duration : 0) : 0);
    rows.push({...extra,start});
  }
  if (beats) {
    const beatTicks = new Set(beats.map(frame=>frame*ticks));
    for (const row of rows) {
      if (row.kind === 'video' && row.label === 'Added' && row.start && beatTicks.has(row.start)) row.label += ' · On beat';
    }
  }
  if (!rows.length && tool && tool !== 'duplicate_video_edit') return;
  const {sequence,name,rate,duration} = after;
  const overview = after.clips.filter(clip=>clip.kind === 'video' && clip.index === 0).slice(0,120)
    .map(({id,start,duration:length})=>({id,start,length}));
  const points = rows.sort((a,b)=>atStart(a)-atStart(b)).map(({id,name:clip,track,label,start})=>({id,name:clip,track,label,start}));
  entries.unshift({title,source,sequence,name,rate,duration,overview,rows:points});
  if (entries.length > 50) entries.pop();
  updates.dispatchEvent(new Event('change'));
}

export function clearVideoChanges() {
  entries.length = 0;
  updates.dispatchEvent(new Event('change'));
}

function formatTime(time,rate) {
  const fps = Math.round(rate.num/rate.den);
  const frames = Math.max(0,Math.round(time/ticksPerSecond*rate.num/rate.den));
  return [Math.floor(frames/fps/60),Math.floor(frames/fps)%60,frames%fps]
    .map(value=>String(value).padStart(2,'0')).join(':');
}

export function mountVideoChanges(panel, {seek}) {
  const empty = panel.querySelector('[data-changes-empty]');
  const details = panel.querySelector('[data-changes-details]');
  const actions = panel.querySelector('select');
  const range = panel.querySelector('[data-changes-range]');
  const list = panel.querySelector('ol');
  const summary = panel.querySelector('[data-changes-summary]');
  const status = panel.querySelector('[data-changes-status]');
  function show(index) {
    const entry = entries[index];
    if (!entry) return;
    const {sequence,name:sequenceName,rate,duration,overview,rows} = entry;
    status.textContent = '';
    summary.textContent = sequenceName;
    range.replaceChildren(...overview.map(clip=>{
      const segment = document.createElement('span');
      segment.style.left = clip.start/duration*100+'%';
      segment.style.width = clip.length/duration*100+'%';
      if (rows.some(row=>row.id === clip.id && row.label !== 'Removed')) segment.className = 'changed';
      return segment;
    }));
    list.replaceChildren(...rows.map(row=>{
      const li = document.createElement('li');
      const button = document.createElement('button');
      button.type = 'button';
      const time = document.createElement('time');
      time.textContent = formatTime(row.start,rate);
      const copy = document.createElement('span');
      const name = document.createElement('strong');
      name.textContent = row.name;
      const detail = document.createElement('small');
      detail.textContent = [row.label,row.track].filter(Boolean).join(' · ');
      copy.append(name,detail);
      button.append(time,copy);
      button.onclick = async ()=>{
        try {
          await seek(sequence,row.start);
          list.querySelector('[aria-current]')?.removeAttribute('aria-current');
          button.setAttribute('aria-current','true');
          status.textContent = 'Playhead at '+time.textContent;
        } catch {
          status.textContent = 'This sequence is no longer available in the current project.';
        }
      };
      li.append(button);
      return li;
    }));
    if (!rows.length) {
      const li = document.createElement('li');
      li.textContent = 'The copy has the same clip timing and effects as its source.';
      list.append(li);
    }
  }
  function refresh() {
    empty.hidden = entries.length > 0;
    details.hidden = !entries.length;
    actions.replaceChildren(...entries.map((entry,index)=>new Option(entry.title+' · '+entry.source,String(index))));
    if (entries.length) show(0);
  }
  updates.addEventListener('change',refresh);
  actions.onchange = ()=>show(Number(actions.value));
  refresh();
}
