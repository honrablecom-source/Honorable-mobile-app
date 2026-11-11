import {photoBytes,MAX_SOURCE_BYTES} from './image-assets.mjs';
import {despeckleSettings,despecklePixelsAsync} from './photo-despeckle.mjs';
/** Decode the embedded original, run bounded chunks locally, and encode an independently owned PNG. */
export async function despecklePhoto(dataURL,threshold,strength,requireCurrent=()=>{},onProgress=()=>{},signal){
 const check=()=>{if(signal?.aborted)throw Error('Photo cleanup cancelled.');if(document.hidden)throw Error('Return to the drawing and preview cleanup again.');requireCurrent();};check();
 const info=photoBytes(dataURL);despeckleSettings(info.width,info.height,threshold,strength);
 let image,canvas,timer,stopped=false,stopError,rejectStop;
 const cancellation=new Promise((_,reject)=>{rejectStop=reject;});cancellation.catch(()=>{});
 const stop=message=>{if(!stopped){stopped=true;stopError=Error(message);rejectStop(stopError);}},abort=()=>stop('Photo cleanup cancelled.'),hide=()=>{if(document.hidden)stop('Return to the drawing and preview cleanup again.');};
 const current=()=>{if(stopped)throw stopError;check();},wait=promise=>Promise.race([promise,cancellation]);
 try{
  signal?.addEventListener('abort',abort,{once:true});document.addEventListener('visibilitychange',hide);timer=setTimeout(()=>stop('Photo cleanup took too long. Try a smaller photo.'),30000);current();
  image=new Image();image.decoding='async';await wait(new Promise((resolve,reject)=>{image.onload=resolve;image.onerror=()=>reject(Error('The embedded source photo could not be opened.'));image.src=dataURL;}));current();
  if(image.naturalWidth!==info.width||image.naturalHeight!==info.height)throw Error('The embedded source dimensions changed.');
  canvas=document.createElement('canvas');canvas.width=info.width;canvas.height=info.height;let context=canvas.getContext('2d',{willReadFrequently:true});if(!context)throw Error('Local photo processing is unavailable.');context.drawImage(image,0,0);const pixels=context.getImageData(0,0,info.width,info.height).data;
  image.onload=image.onerror=null;image.removeAttribute('src');canvas.width=canvas.height=0;
  const result=await wait(despecklePixelsAsync(pixels,info.width,info.height,threshold,strength,current,onProgress));current();
  canvas.width=info.width;canvas.height=info.height;context=canvas.getContext('2d');if(!context)throw Error('Local PNG encoding is unavailable.');context.putImageData(new ImageData(result.data,info.width,info.height),0,0);
  const blob=await wait(new Promise((resolve,reject)=>canvas.toBlob(value=>value?resolve(value):reject(Error('The cleaned PNG could not be encoded.')),'image/png')));current();
  if(blob.type!=='image/png'||blob.size<1||blob.size>MAX_SOURCE_BYTES)throw Error('The cleaned PNG exceeds the local photo limit.');
  return{blob,width:info.width,height:info.height,changed:result.changed};
 }finally{
  clearTimeout(timer);signal?.removeEventListener('abort',abort);document.removeEventListener('visibilitychange',hide);
  if(image){image.onload=image.onerror=null;image.removeAttribute('src');}if(canvas)canvas.width=canvas.height=0;
 }
}
