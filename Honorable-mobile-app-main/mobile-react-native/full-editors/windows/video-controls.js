const reviewPath = '/opfs/noble-mcp/review-frame.jpg';
const projectPath = '/opfs/noble-mcp/editable-project.fcproj';
const within = (value,min,max) => Number.isFinite(value) && value >= min && value <= max;
const wait = ms => new Promise(resolve=>setTimeout(resolve,ms));

async function reviewImage(bytes) {
  const bitmap = await createImageBitmap(new Blob([bytes],{type:'image/jpeg'}));
  try {
    const scale = Math.min(1,640/Math.max(bitmap.width,bitmap.height));
    const canvas = document.createElement('canvas');
    canvas.width = Math.round(bitmap.width*scale);
    canvas.height = Math.round(bitmap.height*scale);
    canvas.getContext('2d').drawImage(bitmap,0,0,canvas.width,canvas.height);
    return {type:'image',mimeType:'image/jpeg',data:canvas.toDataURL('image/jpeg',0.85).split(',')[1]};
  } finally {
    bitmap.close();
  }
}

export function createVideoControls(editor,{ticks,currentRevision,describe,showTimeline,canShare}) {
  const run = (command,args) => editor.execute(command,args);

  async function selectSequence({sequence,revision}) {
    const {activeSequence} = await currentRevision(revision);
    const timeline = await run('sequence.inspect',{item:sequence});
    await editor.request('ui.playback',{action:'stop'});
    if (activeSequence !== sequence) await run('sequence.open',{item:sequence});
    return timeline;
  }

  function findClip(timeline,id) {
    for (const kind of ['video','audio']) {
      for (const track of timeline[kind]) {
        const clip = track.items.find(item=>item.clip === id);
        if (!clip) continue;
        if (track.locked) throw Error('Unlock the clip’s track in Noble before editing.');
        return {clip,track,kind};
      }
    }
    throw Error('Choose a clip ID from the requested sequence.');
  }

  function checkRange(start,length,timeline) {
    if ((start+length)*ticks > timeline.duration) throw Error('Keep the requested range inside the sequence.');
  }

  async function editResult(sequence,change) {
    showTimeline();
    return {data:{...await describe(sequence),change}};
  }

  async function transaction(callback) {
    let applied = 0;
    try {
      return await callback(async (id,args)=>{
        const result = await run(id,args);
        applied++;
        return result;
      });
    } catch (error) {
      try {
        while (applied > 0) {
          await run('edit.undo');
          applied--;
        }
      } catch {
        throw Error(`Noble reported "${error.message}". Automatic rollback was incomplete. Check the timeline before continuing.`);
      }
      throw error;
    }
  }

  return {
    async duplicate_video_edit(args) {
      const original = await selectSequence(args);
      await run('project.select',{items:[args.sequence]});
      const sequence = await transaction(async apply=>{
        const {items:[copy]} = await apply('edit.duplicate',{});
        await apply('clip.rename',{item:copy,name:args.name?.trim() || original.name+' copy'});
        return copy;
      });
      await run('sequence.open',{item:sequence});
      return editResult(sequence,{original:args.sequence});
    },

    async edit_video_clip(args) {
      const timeline = await selectSequence(args);
      const {clip,kind} = findClip(timeline,args.clip);
      const op = args.operation;
      const clips = [clip.clip];
      let outcome;
      switch (op.type) {
        case 'trim':
          outcome = await run('timeline.trim',{clip:clip.clip,edge:op.edge,delta:op.delta*ticks,mode:op.ripple ? 'ripple' : 'regular'});
          break;
        case 'split':
          if (op.at*ticks <= clip.start || op.at*ticks >= clip.end) throw Error('Split at an interior frame of the clip.');
          outcome = await run('timeline.razor',{clip:clip.clip,time:op.at*ticks});
          break;
        case 'move': {
          const destination = timeline[kind].find(candidate=>candidate.id === op.track);
          if (!destination || destination.locked) throw Error('Choose an unlocked destination track of the same type.');
          const time = op.at*ticks;
          if (!op.insert && destination.items.some(other=>other.clip !== clip.clip && other.start < time+clip.duration && other.end > time)) {
            throw Error('The destination overlaps another clip. Choose an empty range or explicitly request a ripple insert.');
          }
          outcome = await run('timeline.move',{moves:[{clip:clip.clip,track:destination.id,time}],insert:op.insert === true,linked:false});
          break;
        }
        case 'retime':
          outcome = await run('clip.speedDuration',{clips,speed:op.percent,reverse:op.reverse ?? clip.reverse,
            ripple:op.ripple === true,maintainPitch:true,interpolation:'frameSampling'});
          break;
        case 'remove':
          outcome = await run('timeline.delete',{clips,ripple:op.ripple === true});
          break;
      }
      return editResult(args.sequence,{type:op.type,clip:clip.clip,outcome});
    },

    async transform_video_clip(args) {
      const {clip,kind} = findClip(await selectSequence(args),args.clip);
      const {property,value,at} = args;
      if (kind !== 'video') throw Error('Transform a video clip.');
      const valid = {scale:()=>within(value,1,1000),rotation:()=>within(value,-360,360),
        opacity:()=>within(value,0,100),position:()=>Array.isArray(value)};
      if (!valid[property]()) throw Error('Use scale 1–1000%, rotation ±360°, opacity 0–100%, or an [x,y] pixel position.');
      if (at !== undefined && (at*ticks < clip.start || at*ticks >= clip.end)) throw Error('Place the keyframe inside the selected clip.');
      await run('effects.setParam',{clip:args.clip,effect:property === 'opacity' ? 'opacity' : 'motion',param:property,value,
        ...(at === undefined ? {} : {keyframe:true,time:at*ticks})});
      return editResult(args.sequence,{clip:args.clip,property,value,at});
    },

    async grade_video_clip(args) {
      const {clip,kind} = findClip(await selectSequence(args),args.clip);
      const limits = {exposure:[-5,5],contrast:[-100,100],saturation:[0,200]};
      if (kind !== 'video' || !within(args.value,...limits[args.property])) {
        throw Error('Grade a video clip with exposure ±5 stops, contrast ±100, or saturation 0–200%.');
      }
      await transaction(async apply=>{
        if (!clip.effects.some(effect=>effect.effect === 'lumetri')) await apply('effects.apply',{clips:[args.clip],effect:'lumetri'});
        return apply('effects.setParam',{clip:args.clip,effect:'lumetri',param:args.property,value:args.value});
      });
      return editResult(args.sequence,{clip:args.clip,property:args.property,value:args.value});
    },

    async add_video_transition(args) {
      const timeline = await selectSequence(args);
      const {kind} = findClip(timeline,args.clip);
      const audio = args.kind === 'constant_power';
      if (audio !== (kind === 'audio')) throw Error('Choose a transition for the clip’s track type.');
      const {num,den} = timeline.settings.frame_rate;
      const result = await run(audio ? 'sequence.applyAudioTransition' : 'sequence.applyVideoTransition',{
        clip:args.clip,effect:args.kind,edge:args.edge,align:args.alignment,frames:Math.max(1,Math.round(args.frames*num/(24*den))),
      });
      return editResult(args.sequence,result);
    },

    async mix_video_audio(args) {
      const timeline = await selectSequence(args);
      const {track,property,value,at} = args;
      const strip = track === 'master' ? 'Mix' : track;
      if (strip !== 'Mix' && !timeline.audio.some(candidate=>candidate.id === strip && !candidate.locked)) {
        throw Error('Choose an unlocked audio track from the requested sequence.');
      }
      const volume = property === 'volumeDb';
      if (!within(value,-(volume ? 60 : 100),volume ? 12 : 100) || (!volume && strip === 'Mix')) {
        throw Error('Use −60 to +12 dB for gain or −100 to +100 for stereo track pan. The master supports gain.');
      }
      if (at === undefined) await run('mixer.setStrip',{strip,[property]:value});
      else {
        checkRange(at,1,timeline);
        await run('mixer.setKeyframe',{strip,lane:volume ? 'volume' : 'pan',time:at*ticks,value});
      }
      return editResult(args.sequence,{track,property,value,at,mixer:await run('mixer.inspect')});
    },

    async add_video_title(args) {
      const timeline = await selectSequence(args);
      const {width,height} = timeline.settings;
      checkRange(args.at,args.frames,timeline);
      const title = await run('graphics.newText',{text:args.text.trim(),position:args.position ?? [width*0.07,height*0.68],
        size:args.size ?? 52,seconds:args.frames/24,time:args.at*ticks,newClip:true});
      return editResult(args.sequence,title);
    },

    async add_video_captions(args) {
      const timeline = await selectSequence(args);
      const cues = [...args.cues].sort((a,b)=>a.at-b.at);
      let end = 0;
      for (const cue of cues) {
        checkRange(cue.at,cue.frames,timeline);
        if (cue.at < end) throw Error('Caption cues must not overlap.');
        end = cue.at+cue.frames;
      }
      const result = await transaction(async apply=>{
        const {track} = await apply('captions.newTrack',{format:'Subtitle',name:args.name?.trim() || 'Captions'});
        const captions = [];
        for (const cue of cues) {
          const {caption} = await apply('captions.add',{track,text:cue.text.trim(),time:cue.at*ticks,durationSeconds:cue.frames/24});
          captions.push(caption);
        }
        return {track,captions};
      });
      return editResult(args.sequence,{...result,...await run('captions.list',{track:result.track})});
    },

    async render_video_frame({sequence,at}) {
      if (!canShare()) throw Error('Enable frame sharing under Video Studio > Tools to send images to connected AI.');
      const timeline = await run('sequence.inspect',{item:sequence});
      checkRange(at,1,timeline);
      for (let attempt = 0; ; attempt++) {
        try {
          await run('file.exportFrame',{target:'program',sequence,time:at*ticks,format:'jpeg',path:reviewPath});
          break;
        } catch (error) {
          if (attempt === 19 || !/frame is still loading/i.test(error.message)) throw error;
          await wait(150);
        }
      }
      return {data:{sequence,at,imageIndex:0},images:[await reviewImage(await editor.readFile(reviewPath))]};
    },

    async save_video_project(args) {
      const timeline = await selectSequence(args);
      await run('file.saveCopy',{path:projectPath});
      const path = await window.nobleNavigation.saveVideoProject(args.name?.trim() || timeline.name,await editor.readFile(projectPath));
      return {data:{sequence:args.sequence,path}};
    },
  };
}
