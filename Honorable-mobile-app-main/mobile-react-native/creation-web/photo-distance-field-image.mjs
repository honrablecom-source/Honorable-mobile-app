import {photoBytes,MAX_PHOTO_BYTES} from './image-assets.mjs';
import {distanceFieldSettings} from './photo-distance-field.mjs';
import {runDistanceTask} from './photo-distance-task.mjs';
import {makeDistanceWorker} from './photo-distance-worker-client.mjs';
/** Decode the embedded original, run an owned local worker, and encode an independently owned PNG. */
export async function makeDistanceFieldPhoto(dataURL,threshold,spread,requireCurrent=()=>{},onProgress=()=>{},signal){
 const check=()=>{if(signal?.aborted)throw Error('Distance-field generation cancelled.');if(document.hidden)throw Error('Return to the drawing and preview the distance field again.');requireCurrent();};check();
 const info=photoBytes(dataURL);distanceFieldSettings(info.width,info.height,threshold,spread);
 const workerController=new AbortController();let image,canvas,timer,stopped=false,stopError,rejectStop;
 const cancellation=new Promise((_,reject)=>{rejectStop=reject;});cancellation.catch(()=>{});
 const stop=message=>{if(!stopped){stopped=true;stopError=Error(message);rejectStop(stopError);workerController.abort();}},abort=()=>stop('Distance-field generation cancelled.'),hide=()=>{if(document.hidden)stop('Return to the drawing and preview the distance field again.');};
 const current=()=>{if(stopped)throw stopError;check();},wait=promise=>Promise.race([promise,cancellation]);
 try{
  signal?.addEventListener('abort',abort,{once:true});document.addEventListener('visibilitychange',hide);timer=setTimeout(()=>stop('Distance-field generation took too long. Try a smaller photo.'),30000);current();
  image=new Image();image.decoding='async';await wait(new Promise((resolve,reject)=>{image.onload=resolve;image.onerror=()=>reject(Error('The embedded source photo could not be opened.'));image.src=dataURL;}));current();
  if(image.naturalWidth!==info.width||image.naturalHeight!==info.height)throw Error('The embedded source dimensions changed.');
  canvas=document.createElement('canvas');let side=Math.max(info.width,info.height);
  while(true){
   current();const scale=Math.min(1,side/Math.max(info.width,info.height)),width=Math.max(8,Math.floor(info.width*scale)),height=Math.max(8,Math.floor(info.height*scale)),actualSpread=Math.max(1,Math.round(spread*scale));
   canvas.width=width;canvas.height=height;let context=canvas.getContext('2d',{willReadFrequently:true});if(!context)throw Error('Local distance-field processing is unavailable.');context.imageSmoothingEnabled=true;context.imageSmoothingQuality='high';context.drawImage(image,0,0,width,height);const pixels=context.getImageData(0,0,width,height).data;canvas.width=canvas.height=0;
   const result=await wait(runDistanceTask({pixels,width,height,threshold,spread:actualSpread},makeDistanceWorker,current,onProgress,workerController.signal));current();
   canvas.width=width;canvas.height=height;context=canvas.getContext('2d');if(!context)throw Error('Local PNG encoding is unavailable.');context.putImageData(new ImageData(result.data,width,height),0,0);
   const blob=await wait(new Promise((resolve,reject)=>canvas.toBlob(value=>value?resolve(value):reject(Error('The distance-field PNG could not be encoded.')),'image/png')));current();
   if(blob.type!=='image/png'||blob.size<1)throw Error('The distance-field PNG could not be encoded.');
   if(blob.size<=MAX_PHOTO_BYTES)return{blob,width,height,threshold,spread:actualSpread,inside:result.inside,sourceWidth:info.width,sourceHeight:info.height};
   if(side<=64)throw Error('The distance-field PNG exceeds the local photo limit even at a smaller size. Use a simpler source.');
   side=Math.max(64,Math.floor(side*.75));
  }
 }finally{
  clearTimeout(timer);workerController.abort();signal?.removeEventListener('abort',abort);document.removeEventListener('visibilitychange',hide);
  if(image){image.onload=image.onerror=null;image.removeAttribute('src');}if(canvas)canvas.width=canvas.height=0;
 }
}

