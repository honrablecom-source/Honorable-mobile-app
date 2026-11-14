import init,* as core from './editor.js';
const url=new URL(location.href);url.searchParams.set('webgl','');history.replaceState(null,'',url.href);
try{
  await init();
  window.nobleImage={request:(method,params={})=>core.noble_request(method,JSON.stringify(params)),importFile:(name,bytes)=>core.noble_import(name,bytes)};
}catch{
  const status=document.querySelector('#photocraft_loading');
  if(status)status.textContent='The image studio could not start. Close and reopen it with an updated Android System WebView. Your existing files are kept.';
}
