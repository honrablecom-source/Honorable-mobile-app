import {inspectMusic, inspectVideo} from './cinematic-media.js';
import {createVideoControls} from './video-controls.js';
import {changeTitles, inspectChange, recordVideoChange} from './video-changes.js';

const ticks = 10584000000; // FilmCraft's ticks per 24 fps frame.
const snapshotPath = '/opfs/noble-mcp/current-project.fcproj';
const analysisLimit = 160 * 1024 * 1024;
const asFrames = value => Math.round(value / ticks);
const clipTiming = clip => ({
  id:clip.clip,item:clip.item,name:clip.name,at:asFrames(clip.start),frames:asFrames(clip.duration),
  sourceFrame:asFrames(clip.sourceIn),gainDb:clip.gainDb,speed:clip.speed,reverse:clip.reverse,
  effects:clip.effects,
});
const trackTiming = track => ({
  id:track.id,name:track.name,locked:track.locked,clips:track.items.map(clipTiming),
  transitions:track.transitions.map(transition=>({
    id:transition.id,effect:transition.effect,at:asFrames(transition.start),frames:asFrames(transition.duration),
    from:transition.from,to:transition.to,alignment:transition.align,
  })),
});
const timed = sequence => ({
  sequence:sequence.id,name:sequence.name,frames:asFrames(sequence.duration),
  fps:sequence.settings.frame_rate.num/sequence.settings.frame_rate.den,
  width:sequence.settings.width,height:sequence.settings.height,
  video:sequence.video.map(trackTiming),
  audio:sequence.audio.map(trackTiming),
});
const fingerprint = sequence => JSON.stringify([sequence.settings,sequence.video,sequence.audio,sequence.markers]);
const countFrames = segments => segments.reduce((sum,clip)=>sum+clip.frames,0);
const nonEmpty = name => name.trim();

export function mountVideoMcp(editor,{settings,notify,showTimeline}) {
  const label = document.createElement('label');
  label.className = 'workspace-frame-approval';
  const share = document.createElement('input');
  share.type = 'checkbox';
  label.append(share,' Allow connected AI to see imported video frames and edited previews');
  settings.append(label);
  const run = (command,params) => editor.execute(command,params);
  const drafts = new Map();
  const jobs = new Map();
  let exportingJob;
  let nextJob = 0;

  async function sources() {
    await run('file.saveCopy',{path:snapshotPath});
    const snapshot = JSON.parse(new TextDecoder().decode(await editor.readFile(snapshotPath))).project;
    const files = new Map(editor.files().map(file=>[file.path,file.size]));
    return new Map(Object.values(snapshot.items).flatMap(entry=>{
      const media = entry.kind?.Media;
      if (!media || !['Movie','Still','AudioOnly'].includes(media.info?.kind)) return [];
      const {video,audio_streams,duration,kind} = media.info;
      const path = media.media?.File?.path;
      return [[entry.id,{
        item:entry.id,name:entry.name,kind,available:Boolean(path && files.has(path)),
        width:video?.width,height:video?.height,hasAudio:Boolean(audio_streams?.length),
        frames:Math.floor(duration/ticks),path,size:files.get(path),
      }]];
    }));
  }

  async function currentRevision(expected) {
    const {revision,activeSequence} = await run('project.inspect');
    if (expected !== undefined && revision !== expected) throw Error('The project changed. List media or inspect the timeline again before editing.');
    return {revision,activeSequence};
  }

  function validateEdit(edit,media) {
    for (const cut of edit.segments) {
      const clip = media.get(cut.item);
      if (!clip?.available || !clip.width) throw Error('Import or relink video item '+cut.item+' in Noble first.');
      if (clip.kind === 'Still' ? cut.sourceFrame !== 0 : cut.sourceFrame + cut.frames > clip.frames) {
        throw Error('A trim falls outside '+clip.name+'.');
      }
    }
    if (!edit.music) return;
    const audio = media.get(edit.music.item);
    if (!audio?.available || !audio.hasAudio) throw Error('Import or relink the soundtrack in Noble first.');
    if ((edit.music.sourceFrame ?? 0)+countFrames(edit.segments) > audio.frames) throw Error('The soundtrack is shorter than this edit.');
  }

  async function assemble(edit,previous) {
    const {activeSequence} = await currentRevision(edit.revision);
    await editor.request('ui.playback',{action:'stop'});
    let sequence;
    try {
      const landscape = edit.aspect === 'landscape';
      ({sequence} = await run('file.newSequence',{
        name:nonEmpty(edit.name),width:landscape ? 1280 : 720,height:landscape ? 720 : 1280,
        fps:24,video:edit.title ? 2 : 1,audio:1,
      }));
      let at = 0;
      for (const segment of edit.segments) {
        const {clips} = await run('timeline.place',{
          item:segment.item,track:'V1',frame:at,sourceIn:segment.sourceFrame*ticks,
          duration:segment.frames*ticks,audio:false,
        });
        await run(segment.framing === 'fill' ? 'clip.fillFrame' : 'clip.fitToFrame',{clips});
        if (segment.zoom > 100) {
          const native = await run('sequence.inspect',{item:sequence});
          const clip = native.video[0].items.find(entry=>entry.clip === clips[0]);
          const fitted = Number(/Float\(([^)]+)/.exec(clip.effects.find(e=>e.effect === 'motion')?.params?.scale?.value)?.[1]);
          if (!Number.isFinite(fitted)) throw Error('Could not measure the fitted clip scale.');
          await run('effects.setParam',{clip:clips[0],effect:'motion',param:'scale',value:fitted*segment.zoom/100});
        }
        at += segment.frames;
      }
      if (edit.music) {
        const {clips} = await run('timeline.place',{
          item:edit.music.item,track:'A1',frame:0,duration:at*ticks,
          sourceIn:(edit.music.sourceFrame ?? 0)*ticks,video:false,
        });
        if (edit.music.gainDb !== undefined) await run('clip.audioGain',{clips,mode:'set',db:edit.music.gainDb});
      }
      if (edit.title) {
        const headline = await run('graphics.newText',{
          text:nonEmpty(edit.title),position:[38,landscape ? 650 : 1090],size:edit.title.length > 24 ? 29 : 47,
          seconds:Math.min(at,36)/24,time:0,newClip:true,
        });
        await run('graphics.set',{clip:headline.clip,layer:headline.layer,props:{shadow:true,shadow_distance:4,shadow_blur:16}});
      }
      const native = await run('sequence.inspect',{item:sequence});
      let next = 0;
      if (asFrames(native.duration) !== at || native.video[0].items.length !== edit.segments.length ||
          !native.video[0].items.every((clip,index)=>{
            const expected = edit.segments[index];
            const match = clip.item === expected.item && asFrames(clip.start) === next &&
              asFrames(clip.duration) === expected.frames && asFrames(clip.sourceIn) === expected.sourceFrame;
            next += expected.frames;
            return match;
          })) throw Error('Noble could not place every shot at the requested timing.');
      const {revision} = await currentRevision();
      drafts.set(sequence,{edit,signature:fingerprint(native)});
      if (drafts.size > 30) drafts.delete(drafts.keys().next().value);
      await editor.request('ui.set',{timeline:{fit:true}}).catch(()=>{});
      showTimeline();
      notify('Connected AI created an editable timeline. Press Play to review.');
      return {...timed(native),revision,previous};
    } catch (error) {
      if (sequence) {
        await run('project.select',{items:[sequence]}).catch(()=>{});
        await run('project.delete',{items:[sequence]}).catch(()=>{});
      }
      if (activeSequence) await run('sequence.open',{item:activeSequence}).catch(()=>{});
      showTimeline();
      throw error;
    }
  }

  async function finishExport(job,id,virtualPath,name) {
    try {
      while (true) {
        const info = (await run('jobs.list')).find(entry=>entry.id === id);
        if (!info) throw Error('The Noble export job is unavailable.');
        job.progress = info.progress;
        if (info.finished) {
          if (info.error || info.result?.error || !info.result?.bytes) throw Error(info.result?.error || info.error || 'Noble could not export this video.');
          const bytes = await editor.readFile(virtualPath);
          const saved = window.nobleNavigation ? await window.nobleNavigation.saveVideoExport(name,bytes) : downloadVideo(name,bytes);
          Object.assign(job,{status:'finished',progress:1,path:saved,bytes:bytes.byteLength});
          notify(window.nobleNavigation ? 'Saved the MP4 to Videos / Noble Exports.' : 'The MP4 is ready in your browser downloads.');
          return;
        }
        await new Promise(resolve=>setTimeout(resolve,750));
      }
    } catch (error) {
      Object.assign(job,{status:'failed',error:error.message});
      notify(error.message);
    } finally {
      exportingJob = undefined;
    }
  }

  function downloadVideo(name,bytes) {
    const link = document.createElement('a');
    const url = URL.createObjectURL(new Blob([bytes],{type:'video/mp4'}));
    link.href = url;
    link.download = (name || 'Noble video')+'.mp4';
    document.body.append(link);
    link.click();
    link.remove();
    setTimeout(()=>URL.revokeObjectURL(url),30000);
    return link.download;
  }

  async function startExport(sequence,quality,{matchRate = false} = {}) {
    if (exportingJob) throw Error('Wait for the current Noble video export to finish.');
    const job = {job:++nextJob,status:'exporting',progress:0};
    exportingJob = job.job;
    try {
      const native = await run('sequence.inspect',{item:sequence});
      const {width,height,frames,fps} = timed(native);
      if (!frames || frames > 2880 || width*height > 1920*1080) {
        throw Error('Quick export supports up to two minutes at HD resolution. Open Advanced export for other settings.');
      }
      await run('sequence.open',{item:sequence});
      const virtualPath = '/opfs/noble-mcp/export-'+job.job+'.mp4';
      const started = await run('file.exportMedia',{
        path:virtualPath,format:'h264',width,height,fps:matchRate ? fps : 24,
        bitrateKbps:quality === 'high' ? 6200 : 3500,audio:true,
        ...(matchRate ? {burnCaptions:true} : {}),
      });
      jobs.set(job.job,job);
      if (jobs.size > 20) jobs.delete(jobs.keys().next().value);
      void finishExport(job,started.job,virtualPath,native.name);
      return {...job};
    } catch (error) {
      exportingJob = undefined;
      throw error;
    }
  }

  function exportStatus(job) {
    if (!jobs.has(job)) throw Error('This video export is not available in the current Noble session.');
    return {...jobs.get(job)};
  }

  const describe = async sequence=>({...timed(await run('sequence.inspect',{item:sequence})),...await currentRevision()});
  const handlers = {
    ...createVideoControls(editor,{ticks,currentRevision,describe,showTimeline,canShare:()=>share.checked}),
    async list_video_media() {
      const project = await run('project.inspect');
      const all = [...(await sources()).values()];
      const sequences = [];
      const visit = bin=>{
        for (const entry of bin.children) {
          if (entry.children) visit(entry);
          else if (entry.type === 'Sequence') sequences.push({sequence:entry.item,name:entry.name});
        }
      };
      visit(project.root);
      return {data:{revision:project.revision,activeSequence:project.activeSequence,totalMedia:all.length,
        media:all.slice(0,256).map(({path,size,...visible})=>visible),sequences:sequences.slice(0,100)}};
    },
    async inspect_video_media({items,frames}) {
      if (frames && !share.checked) throw Error('Enable frame sharing under Video Studio > Tools to send images to connected AI.');
      const media = await sources(), data = [], images = [];
      for (const id of new Set(items)) {
        const file = media.get(id);
        if (!file?.available || file.size > analysisLimit) throw Error('Import or relink a working copy under 160 MB before inspecting item '+id+'.');
        const input = new File([await editor.readFile(file.path)],file.name);
        if (file.kind === 'Movie') {
          const measured = await inspectVideo(input,id,{details:true,minFrames:2,preview:frames});
          data.push({item:id,name:file.name,moments:measured.candidates,
            ...(measured.image ? {previewFrame:measured.previewFrame,imageIndex:images.length} : {})});
          if (measured.image) images.push(measured.image);
        } else if (file.kind === 'AudioOnly') {
          data.push({item:id,name:file.name,beatFrames:await inspectMusic(input,{minSeconds:1,maxSeconds:120})});
        } else {
          data.push({item:id,name:file.name,kind:file.kind,width:file.width,height:file.height});
        }
      }
      return {data:{items:data},images};
    },
    async build_video_edit(args) {
      const media = await sources();
      validateEdit(args,media);
      return {data:await assemble(args)};
    },
    async inspect_video_edit({sequence}) {
      return {data:await describe(sequence)};
    },
    async revise_video_edit(args) {
      if (!drafts.has(args.sequence)) throw Error('Choose a timeline built through this connection during the current session.');
      const native = await run('sequence.inspect',{item:args.sequence});
      const {edit,signature} = drafts.get(args.sequence);
      if (signature !== fingerprint(native)) throw Error('This timeline was edited by hand. Inspect it and build a fresh copy to preserve those changes.');
      const after = args.startIndex + args.replaceCount;
      const removed = edit.segments.slice(args.startIndex,after);
      if (removed.length !== args.replaceCount || countFrames(removed) !== countFrames(args.segments)) {
        throw Error('Replace whole shots and match their total frame count to keep the rest of the timeline in place.');
      }
      const next = {...edit,name:args.name,revision:args.revision,segments:[
        ...edit.segments.slice(0,args.startIndex),...args.segments,...edit.segments.slice(after),
      ]};
      if (next.segments.length > 96) throw Error('Use at most 96 shots in one timeline.');
      validateEdit(next,await sources());
      return {data:await assemble(next,args.sequence)};
    },
    async preview_video_edit({sequence,frame = 0,play = false}) {
      const native = await run('sequence.inspect',{item:sequence});
      if (frame >= asFrames(native.duration)) throw Error('Choose a frame inside this timeline.');
      await editor.request('ui.playback',{action:'stop'});
      await run('sequence.open',{item:sequence});
      await run('playhead.set',{time:frame*ticks});
      showTimeline();
      const playback = play ? await editor.request('ui.playback',{action:'play'}) : {playing:false};
      return {data:{sequence,frame,playing:playback.playing === true}};
    },
    async export_video_edit({sequence,quality}) {
      return {data:await startExport(sequence,quality)};
    },
    async video_export_status({job}) {
      return {data:exportStatus(job)};
    },
  };

  const readOnly = new Set(['list_video_media','inspect_video_media','inspect_video_edit','video_export_status']);
  window.nobleVideoMcp = async ({tool,args}) => {
    if (!Object.hasOwn(handlers,tool)) return {error:'Unknown Noble video tool.'};
    const blocked = !readOnly.has(tool);
    if (blocked && (document.body.inert || 'nobleAgentBusy' in document.body.dataset)) return {error:'Noble is busy.'};
    if (blocked) document.body.inert = true;
    try {
      const title = tool === 'edit_video_clip' ? {
        trim:'Trimmed a clip',split:'Split a clip',move:'Moved a clip',retime:'Retimed a clip',remove:'Removed a clip',
      }[args.operation.type] : changeTitles[tool];
      const before = title ? await inspectChange(editor,args.sequence).catch(()=>null) : null;
      const result = await handlers[tool](args);
      if (title && result.data.revision !== args.revision) {
        const after = await inspectChange(editor,result.data.sequence).catch(()=>null);
        if (after) recordVideoChange({before,after,title,source:'MCP',tool,args,change:result.data.change});
      }
      return {result};
    } catch (error) {
      return {error:error.message || String(error)};
    } finally {
      if (blocked) document.body.inert = false;
    }
  };
  return {startExport,exportStatus};
}
