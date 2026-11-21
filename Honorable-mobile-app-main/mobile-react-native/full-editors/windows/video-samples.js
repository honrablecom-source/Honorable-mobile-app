export async function sampleVideo(file) {
  const video = document.createElement('video');
  const url = URL.createObjectURL(file);
  const waitFor = (event, start) => new Promise((resolve, reject) => {
    const finish = (callback, value) => {
      clearTimeout(timer);
      video.removeEventListener(event, ready);
      video.removeEventListener('error', failed);
      callback(value);
    };
    const ready = () => finish(resolve);
    const failed = () => finish(reject, Error('Could not read ' + file.name));
    const timer = setTimeout(failed, 5000);
    video.addEventListener(event, ready);
    video.addEventListener('error', failed);
    start();
  });
  try {
    video.preload = 'auto';
    video.muted = true;
    await waitFor('loadeddata', () => { video.src = url; });
    if (!Number.isFinite(video.duration) || video.duration <= 0 || !video.videoWidth || !video.videoHeight) return [];
    const canvas = document.createElement('canvas');
    canvas.width = canvas.height = 224;
    const context = canvas.getContext('2d');
    const crop = Math.min(video.videoWidth, video.videoHeight);
    const frames = [];
    for (const fraction of [.15, .5, .85]) {
      const time = video.duration * fraction;
      if (Math.abs(video.currentTime - time) > .001) {
        await waitFor('seeked', () => { video.currentTime = time; });
      }
      context.drawImage(video, (video.videoWidth - crop) / 2, (video.videoHeight - crop) / 2, crop, crop, 0, 0, 224, 224);
      const frame = await new Promise(resolve => canvas.toBlob(resolve));
      if (frame) frames.push(frame);
    }
    return frames;
  } catch {
    return [];
  } finally {
    video.removeAttribute('src');
    video.load();
    URL.revokeObjectURL(url);
  }
}
