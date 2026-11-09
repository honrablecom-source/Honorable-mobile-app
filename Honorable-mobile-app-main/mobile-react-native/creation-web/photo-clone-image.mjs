import {photoBytes,MAX_SOURCE_BYTES} from './image-assets.mjs';
import {cloneRepairPlan} from './photo-clone-repair.mjs';
import {runCloneTask} from './photo-clone-task.mjs';
/** Decode only embedded copies, then process in an owned local worker and encode a separate PNG. */
export async function repairPhotoClone(dataURL,strokes,makeWorker,requireCurrent=()=>{},onProgress=()=>{},signal){
 const check=()=>{if(signal?.aborted)throw Error('Clone repair cancelled.');if(document.hidden)throw Error('Return to the drawing and preview the repair again.');requireCurrent();};check();
 if(typeof makeWorker!=='function')throw Error('Local repair worker is unavailable.');
 const info=photoBytes(dataURL),plan=cloneRepairPlan(info.width,info.height,strokes),controller=new AbortController();
 let image,canvas,timer,stopped=false,stopError,rejectStop;
 const cancellation=new Promise((_,reject)=>{rejectStop=reject;});cancellation.catch(()=>{});
 const stop=message=>{if(!stopped){stopped=true;stopError=Error(message);rejectStop(stopError);controller.abort();}},abort=()=>stop('Clone repair cancelled.'),hide=()=>{if(document.hidden)stop('Return to the drawing and preview the repair again.');};
 const current=()=>{if(stopped)throw stopError;check();},wait=promise=>Promise.race([promise,cancellation]);
 try{
  signal?.addEventListener('abort',abort,{once:true});document.addEventListener('visibilitychange',hide);timer=setTimeout(()=>stop('Clone repair took too long. Use shorter strokes or a smaller photo.'),30000);current();
  image=new Image();image.decoding='async';await wait(new Promise((resolve,reject)=>{image.onload=resolve;image.onerror=()=>reject(Error('The embedded source photo could not be opened.'));image.src=dataURL;}));current();
  if(image.naturalWidth!==info.width||image.naturalHeight!==info.height)throw Error('The embedded source dimensions changed.');
  canvas=document.createElement('canvas');canvas.width=info.width;canvas.height=info.height;let context=canvas.getContext('2d',{willReadFrequently:true});if(!context)throw Error('Local photo processing is unavailable.');context.drawImage(image,0,0);const pixels=context.getImageData(0,0,info.width,info.height).data;
  image.onload=image.onerror=null;image.removeAttribute('src');canvas.width=canvas.height=0;
  const result=await wait(runCloneTask({pixels,width:info.width,height:info.height,strokes:plan.strokes},makeWorker,current,onProgress,controller.signal));current();
  canvas.width=info.width;canvas.height=info.height;context=canvas.getContext('2d');if(!context)throw Error('Local PNG encoding is unavailable.');context.putImageData(new ImageData(result.data,info.width,info.height),0,0);
  const blob=await wait(new Promise((resolve,reject)=>canvas.toBlob(value=>value?resolve(value):reject(Error('The repaired PNG could not be encoded.')),'image/png')));current();
  if(blob.type!=='image/png'||blob.size<1||blob.size>MAX_SOURCE_BYTES)throw Error('The repaired PNG exceeds the local photo limit.');
  return{blob,width:info.width,height:info.height,changed:result.changed};
 }finally{
  clearTimeout(timer);controller.abort();signal?.removeEventListener('abort',abort);document.removeEventListener('visibilitychange',hide);
  if(image){image.onload=image.onerror=null;image.removeAttribute('src');}if(canvas)canvas.width=canvas.height=0;
 }
}
