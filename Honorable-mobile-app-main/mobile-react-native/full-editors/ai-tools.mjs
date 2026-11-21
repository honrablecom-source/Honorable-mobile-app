import workerSource from 'noble:studio-ai-worker';
import {imageInfo} from '../creation-web/image-assets.mjs';
import {startStudioAi} from './ai-client.mjs';
import {alphaSnapshot,restoreAlpha,paintAlpha} from './ai-mask.mjs';
import {mixColorPixels} from './ai-color.mjs';
import {startDocumentSnapshot} from './ai-capture.mjs';

const tools={subject:{label:'Color just the subject',side:1024,note:'Find the foreground, then blend in learned color and tone through its soft mask. Review edges and adjust strength. Up to 1024 px.'},
  object:{label:'Object cutout',side:1024,note:'Find the main foreground object. Soft edges and complex scenes can need manual correction. Up to 1024 px.'},
  color:{label:'Neural color & tone',side:1024,note:'A learned color-and-tone suggestion, not calibrated white balance. Review the colors and adjust strength. Up to 1024 px.'},
  denoise:{label:'Neural noise cleanup',side:256,note:'Reduce luminance noise with a learned model. This is a small 256 px working copy, not detail recovery.'},
  style:{label:'Neural painting',side:224,note:'Reinterpret the photo with the Mosaic neural model. This creates a small 224 px artwork, not a restored photo.'}};
const hasColorStrength = kind => kind==='color'||kind==='subject';
const style=document.createElement('style');
style.textContent=`#noble-ai-bar{position:fixed;top:0;left:0;right:0;height:48px;box-sizing:border-box;display:flex;align-items:center;justify-content:space-between;padding:0 8px;border-bottom:1px solid #ddd;background:white;z-index:20;font:13px system-ui;color:#444}#noble-ai-bar button,#noble-ai-dialog button{min-height:44px;padding:8px 14px;color:#111;background:#fff;border:1px solid #888;border-radius:5px;font:inherit}#noble-ai-dialog button:disabled{opacity:.45}#noble-ai-dialog{position:fixed;inset:0;z-index:40;overflow:auto;background:white;color:#111;padding:12px;box-sizing:border-box;font:15px system-ui;overscroll-behavior:contain}#noble-ai-dialog[hidden]{display:none}#noble-ai-dialog header{display:flex;justify-content:space-between;align-items:center;gap:12px}#noble-ai-dialog h2{font-size:20px;margin:4px 0}#noble-ai-dialog p{line-height:1.45}#noble-ai-dialog select,#noble-ai-dialog input[type=file]{font:inherit;max-width:100%;min-height:44px;margin:8px 0}#noble-ai-dialog .actions{display:flex;gap:8px;flex-wrap:wrap;margin:12px 0}#noble-ai-dialog figure{margin:12px 0}#noble-ai-dialog canvas{max-width:100%;height:auto;max-height:42vh;object-fit:contain;background:repeating-conic-gradient(#eee 0% 25%,#fff 0% 50%) 50%/16px 16px;border:1px solid #ddd}#noble-ai-dialog label{display:block;line-height:1.5}#noble-ai-dialog input[type=checkbox]{width:22px;height:22px;vertical-align:middle;margin:10px 8px 10px 0}#noble-ai-dialog [role=status]{min-height:24px}#photocraft_canvas{top:48px!important;height:calc(100% - 48px)!important}`;
document.head.appendChild(style);
style.textContent += `
  #noble-ai-dialog{padding:18px max(18px,calc((100vw - 640px)/2)) 32px}
  #noble-ai-dialog header{margin-bottom:14px}
  #noble-ai-dialog h2{font-size:26px;letter-spacing:-.7px}
  #noble-ai-dialog button{border-color:#ccc;border-radius:10px}
  #noble-ai-dialog p{margin:8px 0;color:#444}
  #noble-ai-dialog select{display:block;width:100%;padding:0 10px;border:1px solid #ccc;border-radius:10px;background:#fff;color:#171717}
  #noble-ai-dialog input[type=file]{display:block;width:100%}
  #noble-ai-dialog input[type=range],#noble-ai-dialog input[type=checkbox]{accent-color:#171717}
  #noble-ai-dialog input[type=file]::file-selector-button{min-height:44px;margin-right:8px;border:1px solid #ccc;border-radius:10px;padding:0 12px;background:#fff;color:#171717}
  #noble-ai-dialog [data-review]:not([hidden]){padding-bottom:16px;margin-bottom:18px;border-bottom:1px solid #e4e4e4}
  #noble-ai-dialog [data-run]:enabled{background:#171717;color:#fff;border-color:#171717}
  #noble-ai-dialog [data-compare]{gap:4px;padding:4px;background:#f2f2f2;border-radius:12px}
  #noble-ai-dialog [data-compare] button{flex:1;border-color:transparent;background:transparent}
  #noble-ai-dialog [data-compare] button[aria-pressed=true]{background:#fff;border-color:#d5d5d5;font-weight:600}
  #noble-ai-dialog [hidden]{display:none!important}
  #noble-ai-dialog figure{padding:10px;border:1px solid #e4e4e4;border-radius:14px;text-align:center;min-height:100px}
  #noble-ai-dialog figure canvas{border-radius:8px}
  #noble-ai-dialog figcaption{margin-bottom:8px;color:#555}
`;
const bar=document.createElement('div');bar.id='noble-ai-bar';
bar.innerHTML='<span>Image studio · on-device AI</span><button type="button" disabled>AI photo tools</button>';
document.body.appendChild(bar);
const openButton=bar.querySelector('button');
const panel=document.createElement('section');panel.id='noble-ai-dialog';panel.hidden=true;panel.setAttribute('role','dialog');panel.setAttribute('aria-modal','true');panel.setAttribute('aria-labelledby','noble-ai-title');
panel.innerHTML=`<header><h2 id="noble-ai-title">AI photo tools</h2><button type="button" data-close>Close</button></header>
<div role="status" aria-live="polite" data-status></div>
<section data-review aria-label="Review your result" hidden>
<div class="actions" data-compare role="group" aria-label="Compare photo versions" hidden>
  <button type="button" data-original-choice aria-pressed="false">Original</button>
  <button type="button" data-result-choice aria-pressed="true">AI result</button>
</div>
<figure data-original-view hidden><figcaption>Original working copy</figcaption><canvas data-before width="1" height="1" hidden></canvas></figure>
<figure data-result-view hidden><figcaption data-result-caption>AI preview</figcaption><canvas data-after width="1" height="1" hidden></canvas></figure>
<div data-color hidden><label>Color strength <output data-strength-label>70%</output><input data-strength type="range" min="0" max="100" value="70"></label><p>Release the slider to update the preview. Review each strength change before using the copy.</p></div>
<div data-refine hidden><p>Refine object edges before approval. Paint on the preview; scroll outside it.</p><div class="actions"><button type="button" data-erase aria-pressed="false">Erase background</button><button type="button" data-restore aria-pressed="false">Restore subject</button><button type="button" data-mask-undo disabled>Undo mask stroke</button></div><label>Brush size <input data-radius type="range" min="2" max="128" value="24"></label></div>
<label><input type="checkbox" data-approve disabled>Use this AI copy</label>
<div class="actions"><button type="button" data-apply disabled>Open copy in image studio</button><button type="button" data-export disabled>Export PNG copy</button></div>
<p>Copies are separate from your source. Confirm the destination after exporting.</p>
</section>
<label>Tool <select data-tool><option value="object">Object cutout</option><option value="subject">Color just the subject</option><option value="color">Neural color &amp; tone</option><option value="denoise">Neural noise cleanup</option><option value="style">Neural painting</option></select></label>
<p data-note></p><label>Source photo <input data-file type="file" accept="image/png,image/jpeg"></label>
<div class="actions"><button type="button" data-current>Use current document</button></div><p data-source></p>
<p>Local processing. PNG or JPEG up to 12 MB and 16 megapixels.</p>
<div class="actions"><button type="button" data-run disabled>Run local AI</button><button type="button" data-stop disabled>Stop</button></div>`;
document.body.appendChild(panel);
const get=name=>panel.querySelector(`[data-${name}]`);
const selection=get('tool'),picker=get('file'),run=get('run'),stop=get('stop'),approval=get('approve'),apply=get('apply'),exportButton=get('export'),before=get('before'),after=get('after');
let epoch=0,source=null,working=null,result=null,task=null,capture=null,preparing=false,brush=null,stroke=null,maskHistory=[],strengthPending=false;
const current=token=>!panel.hidden&&!document.hidden&&epoch===token;
const status=text=>{get('status').textContent=text;};
function controls(){
  const busy=!!task||preparing||!!stroke;
  picker.disabled=selection.disabled=busy;run.disabled=!source||busy;stop.disabled=!busy;
  get('current').disabled=busy||!window.nobleImage;
  approval.disabled=!result||busy||strengthPending;
  apply.disabled=exportButton.disabled=!result||!approval.checked||busy||strengthPending;
  get('original-choice').disabled=get('result-choice').disabled=busy;
}
function compare(showResult) {
  get('review').hidden = !result;
  get('compare').hidden = !result;
  get('original-view').hidden = !result || showResult;
  get('result-view').hidden = !result || !showResult;
  get('original-choice').setAttribute('aria-pressed', String(!showResult));
  get('result-choice').setAttribute('aria-pressed', String(showResult));
  get('color').hidden = !showResult || !hasColorStrength(result?.kind);
  get('refine').hidden = !showResult || result?.kind !== 'object';
}
get('original-choice').onclick = () => compare(false);
get('result-choice').onclick = () => compare(true);
function discardResult(){if(stroke&&after.hasPointerCapture(stroke.pointer))after.releasePointerCapture(stroke.pointer);result=null;approval.checked=false;after.hidden=true;after.width=after.height=1;maskHistory=[];stroke=null;brush=null;strengthPending=false;compare(true);get('mask-undo').disabled=true;get('erase').setAttribute('aria-pressed','false');get('restore').setAttribute('aria-pressed','false');after.style.touchAction='auto';controls();}
function cancel(message='AI stopped. Your original and editor document are unchanged.'){
  epoch++;task?.stop();capture?.stop();task=capture=null;preparing=false;discardResult();if(!panel.hidden)status(message);controls();
}
function close(){cancel();source=working=null;picker.value='';get('source').textContent='';before.hidden=true;before.width=before.height=1;panel.hidden=true;openButton.focus();}
function draw(canvas,image){canvas.width=image.width;canvas.height=image.height;canvas.getContext('2d').putImageData(new ImageData(image.rgba,image.width,image.height),0,0);canvas.hidden=false;}
function resized(bitmap,side,stretch=false){
  const scale=Math.min(1,side/Math.max(bitmap.width,bitmap.height));
  const canvas=document.createElement('canvas');canvas.width=stretch?side:Math.max(1,Math.floor(bitmap.width*scale));canvas.height=stretch?side:Math.max(1,Math.floor(bitmap.height*scale));
  const ctx=canvas.getContext('2d');if(!ctx)throw Error('Photo preparation is unavailable.');
  ctx.drawImage(bitmap,0,0,canvas.width,canvas.height);
  return {width:canvas.width,height:canvas.height,rgba:ctx.getImageData(0,0,canvas.width,canvas.height).data};
}
openButton.onclick=()=>{panel.hidden=false;get('note').textContent=tools[selection.value].note;status('Choose a photo to begin.');get('close').focus();controls();};
get('close').onclick=close;stop.onclick=()=>cancel();
panel.onkeydown=event=>{event.stopPropagation();if(event.key==='Escape'){event.preventDefault();close();}else if(event.key==='Tab'){
  const items=[...panel.querySelectorAll('button,input,select')].filter(el=>!el.disabled&&el.tabIndex>=0&&el.getClientRects().length>0);const first=items[0],last=items.at(-1);
  if(event.shiftKey&&document.activeElement===first){event.preventDefault();last.focus();}else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first.focus();}
}};
for(const name of ['keyup','keypress','wheel'])panel.addEventListener(name,event=>event.stopPropagation());
selection.onchange=()=>{cancel('Tool changed. Run AI and review a fresh preview.');working=null;before.hidden=true;get('note').textContent=tools[selection.value].note;};
async function preparePhoto(file,token,label){
  let bitmap;
  try{
    if(!file||file.size<1||file.size>12*1024*1024)throw Error('Choose a PNG or JPEG up to 12 MB.');
    const bytes=new Uint8Array(await file.arrayBuffer());if(!current(token))return;
    const info=imageInfo(bytes);
    bitmap=await createImageBitmap(new Blob([bytes],{type:info.mimeType}),{imageOrientation:'from-image'});
    if(!current(token))return;
    if(bitmap.width*bitmap.height!==info.width*info.height)throw Error('Photo dimensions could not be verified.');
    const copy=resized(bitmap,1024);
    source=copy;get('source').textContent=label;status('Photo ready. Run AI, then review the preview.');
  }catch(error){if(current(token))status(error.message||'This photo could not be opened.');}
  finally{bitmap?.close();}
}
picker.onchange=async()=>{
  cancel('Preparing a metadata-free working copy…');source=working=null;before.hidden=true;get('source').textContent='';
  const file=picker.files?.[0],token=epoch;preparing=true;controls();
  try{await preparePhoto(file,token,'Local photo working copy');}finally{if(epoch===token){preparing=false;controls();}}
};
get('current').onclick=async()=>{
  if(task||preparing)return;cancel('Preparing a flattened working copy. The layered document stays intact…');
  source=working=null;picker.value='';get('source').textContent='';before.hidden=true;
  const token=epoch;preparing=true;controls();
  try{
    const request=async(method,params={})=>{const reply=await window.nobleImage.request(method,params);if(!reply.ok)throw Error('The image studio could not prepare a working copy. Choose a PNG/JPEG photo or try again.');return reply.result;};
    const owned=startDocumentSnapshot(request,()=>current(token));capture=owned;
    const blob=await owned.promise;if(!current(token)||capture!==owned)return;capture=null;
    await preparePhoto(blob,token,'Current document working copy · layered source unchanged');
  }catch(error){if(current(token))status(error.message||'The working copy could not be prepared. Your project is unchanged.');}
  finally{if(epoch===token){capture=null;preparing=false;controls();}}
};
run.onclick=async()=>{
  if(!source||task||preparing)return;cancel('Preparing local AI…');
  const token=epoch,kind=selection.value;preparing=true;controls();
  try{
    const sourceCanvas=document.createElement('canvas');draw(sourceCanvas,source);
    working=resized(sourceCanvas,tools[kind].side);draw(before,working);
    async function infer(modelKind, phase) {
      const side = modelKind==='object'?320:modelKind==='color'?256:0;
      const model = side?resized(sourceCanvas,side,true).rgba:undefined;
      const owned=startStudioAi(workerSource,working,modelKind,model,text=>{if(current(token))status(phase+text);});
      task=owned;preparing=false;controls();
      const pixels=await owned.promise;
      return current(token)&&task===owned?pixels:null;
    }
    let foreground;
    if(kind==='subject'){
      foreground=await infer('object','Detecting the subject · ');
      if(!foreground)return;
    }
    const pixels=await infer(kind==='subject'?'color':kind,kind==='subject'?'Applying learned color · ':'');
    if(!pixels)return;
    task=null;result={width:working.width,height:working.height,rgba:pixels,kind,token,foreground};
    if(hasColorStrength(kind)){
      result.rawPixels=pixels;
      result.rgba=mixColorPixels(working,pixels,Number(get('strength').value)/100,foreground);
    }
    get('result-caption').textContent=tools[kind].label;
    draw(after,result);compare(true);status(`Review the ${result.width} × ${result.height} AI copy before saving or applying it.`);controls();
    panel.scrollTop=0;get('result-choice').focus({preventScroll:true});
  }catch(error){if(current(token)){task=null;preparing=false;status(error.message||'AI could not finish. Your original is unchanged.');controls();}}
};
approval.onchange=controls;
get('strength').oninput=()=>{if(!hasColorStrength(result?.kind))return;strengthPending=true;approval.checked=false;get('strength-label').textContent=get('strength').value+'%';controls();};
function updateStrength(){
  if(!result||!hasColorStrength(result.kind)||!current(result.token)){strengthPending=false;controls();return;}
  result.rgba=mixColorPixels(working,result.rawPixels,Number(get('strength').value)/100,result.foreground);draw(after,result);approval.checked=false;strengthPending=false;controls();
}
get('strength').onchange=get('strength').onpointercancel=updateStrength;
get('strength').onblur=()=>{if(strengthPending)updateStrength();};
for(const mode of ['erase','restore'])get(mode).onclick=()=>{
  if(!result||result.kind!=='object'||preparing||task)return;
  brush=brush===mode?null:mode;for(const name of ['erase','restore'])get(name).setAttribute('aria-pressed',String(brush===name));after.style.touchAction=brush?'none':'auto';
};
function paint(event){
  if(!stroke||!result||!working||!current(result.token)||result.kind!=='object')return;
  const rect=after.getBoundingClientRect();
  paintAlpha(working,result,(event.clientX-rect.left)*after.width/rect.width,(event.clientY-rect.top)*after.height/rect.height,stroke.radius,stroke.mode);
  approval.checked=false;draw(after,result);controls();
}
after.onpointerdown=event=>{
  if(!brush||!result||result.kind!=='object'||preparing||task||!current(result.token))return;
  event.preventDefault();stroke={pointer:event.pointerId,mode:brush,radius:Number(get('radius').value)};after.setPointerCapture(event.pointerId);
  maskHistory.push(alphaSnapshot(result));while(maskHistory.length>8)maskHistory.shift();get('mask-undo').disabled=false;paint(event);
};
after.onpointermove=event=>{if(stroke?.pointer===event.pointerId)paint(event);};
after.onpointerup=after.onpointercancel=event=>{if(stroke?.pointer===event.pointerId){stroke=null;if(after.hasPointerCapture(event.pointerId))after.releasePointerCapture(event.pointerId);controls();}};
after.onlostpointercapture=()=>{stroke=null;controls();};
get('mask-undo').onclick=()=>{
  if(!result||!maskHistory.length||preparing||task)return;
  restoreAlpha(result,maskHistory.pop());approval.checked=false;draw(after,result);get('mask-undo').disabled=!maskHistory.length;controls();
};
async function output(mode){
  const owned=result;
  if(!owned||!approval.checked||!current(owned.token)||task||preparing||stroke||strengthPending)return;
  preparing=true;controls();
  try{
    const blob=await new Promise((resolve,reject)=>after.toBlob(value=>value?resolve(value):reject(Error('PNG export could not finish.')),'image/png'));
    if(!current(owned.token)||result!==owned||!approval.checked)return;
    if(blob.size<1||blob.size>8*1024*1024)throw Error('The generated PNG is too large.');
    const name=`AI-${owned.kind}.png`;
    if(mode==='apply'){
      const bytes=new Uint8Array(await blob.arrayBuffer());
      if(!current(owned.token)||result!==owned||!approval.checked)return;
      if(!window.nobleImage)throw Error('The image studio is not ready.');
      window.nobleImage.importFile(name,bytes);close();
    }else{
      const url=URL.createObjectURL(blob),link=document.createElement('a');link.href=url;link.download=name;document.body.appendChild(link);link.click();link.remove();setTimeout(()=>URL.revokeObjectURL(url),60000);
      status('Export copy requested. Choose a destination and check that the file was saved.');
    }
  }catch(error){if(current(owned.token))status(error.message||'The copy could not be prepared. Your original is unchanged.');}
  finally{if(epoch===owned.token){preparing=false;controls();}}
}
apply.onclick=()=>output('apply');exportButton.onclick=()=>output('export');
document.addEventListener('visibilitychange',()=>{if(document.hidden)cancel('AI paused. Run it again and review a fresh preview.');});
window.addEventListener('pagehide',()=>{cancel();source=working=null;});
const initialTool = new URLSearchParams(location.search).get('ai');
let initialToolOpened = false;
function ready() {
  openButton.disabled = false;
  if (!initialToolOpened && Object.prototype.hasOwnProperty.call(tools, initialTool)) {
    initialToolOpened = true;
    selection.value = initialTool;
    openButton.click();
  }
}
window.addEventListener('noble-image-ready', ready);
if (window.nobleImage) ready();
