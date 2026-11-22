function nextEvent(media, event, action) {
  return new Promise((resolve, reject) => {
    const finish = error => {
      clearTimeout(timeout);
      media.removeEventListener(event, ready);
      media.removeEventListener('error', failed);
      if (error) reject(error);
      else resolve();
    };
    const ready = () => finish();
    const failed = () => finish(Error('Could not decode the selected video.'));
    const timeout = setTimeout(failed, 8000);
    media.addEventListener(event, ready);
    media.addEventListener('error', failed);
    action();
  });
}

export async function inspectVideo(file, clip, {minFrames = 12, details = false, preview = false} = {}) {
  const video = document.createElement('video');
  const url = URL.createObjectURL(file);
  const canvas = document.createElement('canvas');
  canvas.width = 32;
  canvas.height = 18;
  const context = canvas.getContext('2d', {willReadFrequently:true});
  try {
    video.muted = true;
    video.preload = 'auto';
    await nextEvent(video, 'loadeddata', () => {video.src = url;});
    if (!Number.isFinite(video.duration) || video.duration < (details ? minFrames/24 : 1)) throw Error(file.name + ' is too short.');
    const moments = [];
    for (const time of [0, .25, .5, .7].map(fraction => Math.min(1800, video.duration * fraction))) {
      if (video.duration - time < minFrames/24) continue;
      const snapshots = [];
      for (const timestamp of [time, Math.min(time + .22, video.duration - .02)]) {
        if (Math.abs(video.currentTime - timestamp) > .005) {
          await nextEvent(video, 'seeked', () => {video.currentTime = timestamp;});
        }
        context.drawImage(video, 0, 0, 32, 18);
        const rgba = context.getImageData(0, 0, 32, 18).data;
        snapshots.push(Array.from({length:32*18}, (_, i) => (rgba[i*4]*.2126 + rgba[i*4+1]*.7152 + rgba[i*4+2]*.0722)/255));
      }
      const light = snapshots[0].reduce((sum, value) => sum + value, 0) / (32*18);
      const motion = snapshots[0].reduce((sum, value, i) => sum + Math.abs(value-snapshots[1][i]), 0) / (32*18);
      moments.push({clip, label:file.name.slice(0,100), start:Math.ceil(time*24),
        available:Math.min(14400, Math.floor(video.duration*24)-Math.ceil(time*24)),
        light:Number(light.toFixed(3)), motion:Number(motion.toFixed(3))});
    }
    const candidates = moments.filter(moment => moment.available >= minFrames);
    if (!details) return candidates;
    let image, previewFrame;
    if (preview && candidates.length) {
      const strongest = candidates.reduce((a,b)=>a.motion >= b.motion ? a : b);
      previewFrame = strongest.start;
      if (Math.abs(video.currentTime-strongest.start/24) > .005) {
        await nextEvent(video,'seeked',()=>{video.currentTime = strongest.start/24;});
      }
      const ratio = Math.min(1,384/Math.max(video.videoWidth,video.videoHeight));
      canvas.width = Math.max(1,Math.round(video.videoWidth*ratio));
      canvas.height = Math.max(1,Math.round(video.videoHeight*ratio));
      context.drawImage(video,0,0,canvas.width,canvas.height);
      image = {type:'image',mimeType:'image/jpeg',data:canvas.toDataURL('image/jpeg',.76).split(',')[1]};
    }
    return {candidates,image,previewFrame};
  } finally {
    video.removeAttribute('src');
    video.load();
    URL.revokeObjectURL(url);
  }
}

export async function inspectMusic(file,{minSeconds = 20,maxSeconds = 20} = {}) {
  if (file.size > 60 * 1024 * 1024) throw Error('Choose a soundtrack smaller than 60 MB.');
  const context = new AudioContext();
  try {
    const audio = await context.decodeAudioData(await file.arrayBuffer());
    if (audio.duration < minSeconds) throw Error('Choose a soundtrack at least ' + minSeconds + ' seconds long.');
    const samples = audio.getChannelData(0);
    const hop = Math.floor(audio.sampleRate / 100);
    const energy = [];
    for (let start = 0; start + hop <= Math.min(samples.length, audio.sampleRate * maxSeconds); start += hop) {
      let squared = 0;
      for (let i = start; i < start+hop; i++) squared += samples[i]*samples[i];
      energy.push(Math.sqrt(squared/hop));
    }
    const flux = energy.map((value,i) => Math.max(0, value - (energy[i-2] ?? value)));
    const average = flux.reduce((sum, value) => sum + value, 0)/flux.length;
    const beats = [];
    let last = -30;
    for (let i = 2; i < flux.length-2; i++) {
      if (i-last < 23 || flux[i] < Math.max(.009,average*3.5) ||
          flux[i] < flux[i-1] || flux[i] < flux[i+1]) continue;
      const frame = Math.round(i * hop * 24 / audio.sampleRate);
      if (frame > 0 && frame < maxSeconds*24) beats.push(frame);
      last = i;
    }
    return beats;
  } finally {
    await context.close();
  }
}
