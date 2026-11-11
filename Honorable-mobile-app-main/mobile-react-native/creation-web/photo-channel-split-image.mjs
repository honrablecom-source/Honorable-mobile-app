import {photoBytes,MAX_PHOTO_BYTES} from './image-assets.mjs';
import {channelSplitSettings,channelSplitPixelsAsync} from './photo-channel-split.mjs';
export async function makeChannelSplitPhoto(dataURL,requireCurrent=()=>{},onProgress=()=>{},signal){
 const check=()=>{if(signal?.aborted)throw Error('Channel extraction cancelled.');if(document.hidden)throw Error('Return to the drawing and preview the channel maps again.');requireCurrent();};check();
 const info=photoBytes(dataURL);channelSplitSettings(info.width,info.height);let image,canvas,timer,stopped=false,stopError,rejectStop;
 const cancellation=new Promise((_,reject)=>{rejectStop=reject;});cancellation.catch(()=>{});
 const stop=message=>{if(!stopped){stopped=true;stopError=Error(message);rejectStop(stopError);}},abort=()=>stop('Channel extraction cancelled.'),hide=()=>{if(document.hidden)stop('Return to the drawing and preview the channel maps again.');};
 const current=()=>{if(stopped)throw stopError;check();},wait=promise=>Promise.race([promise,cancellation]);
 try{
  signal?.addEventListener('abort',abort,{once:true});document.addEventListener('visibilitychange',hide);timer=setTimeout(()=>stop('Channel extraction took too long. Try a smaller texture.'),30000);current();
  image=new Image();image.decoding='async';await wait(new Promise((resolve,reject)=>{image.onload=resolve;image.onerror=()=>reject(Error('The embedded RGB source could not be opened.'));image.src=dataURL;}));current();if(image.naturalWidth!==info.width||image.naturalHeight!==info.height)throw Error('The embedded source dimensions changed.');
  canvas=document.createElement('canvas');canvas.width=info.width;canvas.height=info.height;let context=canvas.getContext('2d',{willReadFrequently:true});if(!context)throw Error('Local channel extraction is unavailable.');context.drawImage(image,0,0);const pixels=context.getImageData(0,0,info.width,info.height).data;canvas.width=canvas.height=0;
  const result=await wait(channelSplitPixelsAsync(pixels,info.width,info.height,current,onProgress));current();const blobs=[];
  for(let channel=0;channel<3;channel++){canvas.width=info.width;canvas.height=info.height;context=canvas.getContext('2d');if(!context)throw Error('Local PNG encoding is unavailable.');context.putImageData(new ImageData(result.maps[channel],info.width,info.height),0,0);const blob=await wait(new Promise((resolve,reject)=>canvas.toBlob(value=>value?resolve(value):reject(Error('A channel-map PNG could not be encoded.')),'image/png')));current();if(blob.type!=='image/png'||blob.size<1||blob.size>MAX_PHOTO_BYTES)throw Error('A channel-map PNG exceeds the 192 KiB local photo limit. Use a smaller or simpler source texture.');blobs.push(blob);canvas.width=canvas.height=0;}
  return{blobs,width:info.width,height:info.height,different:result.different};
 }finally{clearTimeout(timer);signal?.removeEventListener('abort',abort);document.removeEventListener('visibilitychange',hide);if(image){image.onload=image.onerror=null;image.removeAttribute('src');}if(canvas)canvas.width=canvas.height=0;}
}
