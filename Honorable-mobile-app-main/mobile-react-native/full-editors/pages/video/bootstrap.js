const url = new URL(location.href);
const compatibility = url.searchParams.has('nowebcodecs');
url.searchParams.set('empty', '');
url.searchParams.set('webgl', '');
history.replaceState(null, '', url.href);

const phase = document.getElementById('phase');
const retry = document.getElementById('retry');
retry.textContent = compatibility ? 'Retry standard startup' : 'Retry with compatibility settings';
retry.onclick = () => {
  if (compatibility) {
    url.searchParams.delete('nowebcodecs');
    url.searchParams.delete('cpu');
    url.searchParams.set('webgl', '');
  } else {
    // Keep the same WebGL renderer across retries. Automatic backend selection crashed the Pixel 8 GPU process.
    url.searchParams.set('cpu', '');
    url.searchParams.set('nowebcodecs', '');
  }
  location.replace(url.href);
};

async function openStudio() {
  let stage = 'loading the local video engine';
  const waiting = setTimeout(() => {
    phase.textContent = 'Still ' + stage + '. You can wait or retry. Your saved projects are kept.';
    retry.hidden = false;
  }, 20000);
  try {
    const {default: init, start} = await import('./editor.js');
    await init();
    stage = 'starting the video workspace';
    phase.textContent = 'Starting the video workspace…';
    await start('filmcraft_canvas');
    document.getElementById('status').remove();
  } catch (error) {
    console.warn('Video Studio startup failed', stage, error instanceof Error ? error.name : typeof error);
    phase.textContent = 'The studio stopped while ' + stage + '. Retry or update Android System WebView. Your saved projects are kept.';
    retry.hidden = false;
  } finally {
    clearTimeout(waiting);
  }
}
openStudio();
