/* Native transport for explicit exports from the bundled, offline editor page.
 * No media upload, arbitrary navigation, source-file write or saved-success claim.
 */
(() => {
  'use strict';
  const bridge = window.NobleCreative;
  if (!bridge || !['beginSave','appendSave','finishSave','cancelSave'].every(key => typeof bridge[key] === 'function')) return;
  let active = null, nextId = Math.floor(Math.random()*900000000), lastId = 0;
  let notice;
  function status(text) {
    if (!notice) {
      notice = document.createElement('div');
      notice.setAttribute('role','status');notice.setAttribute('aria-live','polite');
      notice.style.cssText = 'position:fixed;left:8px;right:8px;bottom:8px;z-index:2147483647;padding:10px;background:#fff;color:#111;border:1px solid #111;font:14px sans-serif;pointer-events:none';
      document.body.appendChild(notice);
    }
    notice.textContent = text;
  }
  function stop() {
    const task=active;active=null;
    if(task){task.cancelled=true;try{bridge.cancelSave(task.id);}catch{/* Native close also releases the partial file. */}}
  }
  window.nobleCreativeStopTransfers=stop;
  window.nobleCreativeSaveResult=result=>{
    if(result?.id!==lastId)return;
    status(result.error || (result.opened ? 'Choose a share destination to keep the copy. Opening the sheet does not mean it was saved.' : 'Sharing did not open. Your project is kept.'));
  };
  document.addEventListener('visibilitychange',()=>{if(document.hidden)stop();});
  window.addEventListener('pagehide',stop);
  document.addEventListener('click',async event=>{
    const anchor=event.target instanceof Element ? event.target.closest('a[download]') : null;
    if(!anchor)return;
    let url;
    try{url=new URL(anchor.href,location.href);}catch{return;}
    if(url.protocol!=='blob:'||url.origin!==location.origin)return;
    event.preventDefault();
    if(active){status('Finish the current export before sharing another copy.');return;}
    const task={id:nextId=(nextId%1000000000)+1,cancelled:false};active=task;lastId=task.id;
    let began=false,finished=false;
    const current=()=>{if(active!==task||task.cancelled||document.hidden)throw Error('Export stopped. Your project is kept.');};
    try{
      const response=await fetch(url.href);current();
      if(!response.ok)throw Error('The generated file could not be read. Your project is kept.');
      const blob=await response.blob();current();
      if(blob.size<1||blob.size>64*1024*1024)throw Error('Choose an export up to64MB. Your project is kept.');
      const name=anchor.download;
      if(!name||name.length>120||/[\x00-\x1f/\\]/.test(name))throw Error('Use a simple export filename. Your project is kept.');
      if(bridge.beginSave(task.id,blob.size,name)!==true)throw Error('Open the editor and finish the current operation before sharing.');
      began=true;status('Preparing a separate local export copy…');
      for(let offset=0;offset<blob.size;offset+=32768){
        const bytes=new Uint8Array(await blob.slice(offset,Math.min(blob.size,offset+32768)).arrayBuffer());current();
        let raw='';for(const byte of bytes)raw+=String.fromCharCode(byte);
        if(bridge.appendSave(task.id,btoa(raw))!==true)throw Error('Export preparation stopped. Your project is kept.');
      }
      current();if(bridge.finishSave(task.id)!==true)throw Error('The export was incomplete. Your project is kept.');
      finished=true;status('Share handoff requested. Choose a destination to keep the copy; this does not confirm it was saved.');
    }catch(error){if(active===task&&!document.hidden)status(error.message || 'Export could not finish. Your project is kept.');}
    finally{if(began&&!finished)try{bridge.cancelSave(task.id);}catch{/* Native lifecycle also cleans partial output. */}if(active===task)active=null;}
  },true);
})();
