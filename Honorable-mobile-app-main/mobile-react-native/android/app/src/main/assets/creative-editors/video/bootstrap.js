import init,{start} from './editor.js';
// Keep recovery, start without a demo, and use the mobile-tested WebGL2 runner.
const url=new URL(location.href);url.searchParams.set('empty','');url.searchParams.set('webgl','');history.replaceState(null,'',url.href);
try{
  await init();
  await start('filmcraft_canvas');
  document.querySelector('#status')?.remove();
}catch{
  const status=document.querySelector('#status');
  if(status)status.textContent='The video studio could not start. Close and reopen it with an updated Android System WebView. Your existing files are kept.';
}
