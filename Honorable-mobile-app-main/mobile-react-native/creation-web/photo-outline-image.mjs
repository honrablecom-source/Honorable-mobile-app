import {photoBytes, MAX_SOURCE_BYTES} from './image-assets.mjs';
import {outlineCutoutPlan, outlineCutoutPixelsAsync} from './photo-outline-cutout.mjs';

/** Embedded bytes only. A preview owns all temporary pixels and cannot publish after cancellation. */
export async function outlinePhotoCutout(dataURL, selection, feather=0, requireCurrent=()=>{}, onProgress=()=>{}, signal){
 const check=()=>{if(signal?.aborted)throw Error('Photo cutout cancelled.');if(document.hidden)throw Error('Return to the drawing and preview the cutout again.');requireCurrent();};
 check();const info=photoBytes(dataURL),plan=outlineCutoutPlan(info.width,info.height,selection,feather);
 // Snapshot geometry before image decoding yields control to the editor.
 const captured={outer:plan.rings[0],holes:plan.rings.slice(1)};
 let image,canvas,timeout,stopped=false,rejectStop;
 const cancellation=new Promise((_,reject)=>{rejectStop=reject;});
 cancellation.catch(()=>{});
 const stop=message=>{if(!stopped){stopped=true;rejectStop(Error(message));}};
 const abort=()=>stop('Photo cutout cancelled.'),hide=()=>{if(document.hidden)stop('Return to the drawing and preview the cutout again.');};
 const current=()=>{if(stopped)throw Error('Photo cutout stopped. Preview again.');check();};
 const wait=promise=>Promise.race([promise,cancellation]);
 try{
  signal?.addEventListener('abort',abort,{once:true});document.addEventListener('visibilitychange',hide);
  timeout=setTimeout(()=>stop('Photo cutout took too long. Try a simpler outline.'),30000);current();
  image=new Image();image.decoding='async';
  await wait(new Promise((resolve,reject)=>{image.onload=resolve;image.onerror=()=>reject(Error('The embedded source photo could not be opened.'));image.src=dataURL;}));current();
  if(image.naturalWidth!==info.width||image.naturalHeight!==info.height)throw Error('The source photo dimensions changed.');
  image.onload=image.onerror=null;
  canvas=document.createElement('canvas');canvas.width=info.width;canvas.height=info.height;
  const context=canvas.getContext('2d',{willReadFrequently:true});if(!context)throw Error('Local photo processing is unavailable.');
  context.drawImage(image,0,0);const input=context.getImageData(0,0,info.width,info.height).data;
  const result=await wait(outlineCutoutPixelsAsync(input,info.width,info.height,captured,feather,current,onProgress));current();
  context.putImageData(new ImageData(result.data,info.width,info.height),0,0);
  const blob=await wait(new Promise((resolve,reject)=>canvas.toBlob(value=>value?resolve(value):reject(Error('The transparent PNG could not be encoded.')),'image/png')));current();
  if(blob.type!=='image/png'||blob.size<1||blob.size>MAX_SOURCE_BYTES)throw Error('The transparent PNG exceeds the local photo limit.');
  return{blob,width:info.width,height:info.height,selection:result.selection,feather:result.feather,removed:result.removed,softened:result.softened,remaining:result.kept};
 }finally{
  clearTimeout(timeout);signal?.removeEventListener('abort',abort);document.removeEventListener('visibilitychange',hide);
  if(image){image.onload=image.onerror=null;image.removeAttribute('src');}
  if(canvas)canvas.width=canvas.height=0;
 }
}
