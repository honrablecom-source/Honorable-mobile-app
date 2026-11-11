import {photoBytes,MAX_PHOTO_BYTES} from './image-assets.mjs';
import {channelPackSettings} from './photo-channel-pack.mjs';
import {runChannelPackTask} from './photo-channel-pack-task.mjs';
import {makeChannelPackWorker} from './photo-channel-pack-worker-client.mjs';
export async function makeChannelPackPhoto(urls,requireCurrent=()=>{},onProgress=()=>{},signal){
 const check=()=>{if(signal?.aborted)throw Error('Channel packing cancelled.');if(document.hidden)throw Error('Return to the drawing and preview the channel pack again.');requireCurrent();};check();
 if(!Array.isArray(urls)||urls.length!==3||new Set(urls).size!==3)throw Error('Choose three distinct embedded source photos for the channels.');
 const infos=urls.map(url=>photoBytes(url)),{width,height}=channelPackSettings(infos[0].width,infos[0].height);
 if(infos.some(info=>info.width!==width||info.height!==height))throw Error('All three channel maps must have matching embedded pixel dimensions.');
 const images=[],controller=new AbortController();let canvas,timer,stopped=false,stopError,rejectStop;
 const cancellation=new Promise((_,reject)=>{rejectStop=reject;});cancellation.catch(()=>{});
 const stop=message=>{if(!stopped){stopped=true;stopError=Error(message);rejectStop(stopError);controller.abort();}},abort=()=>stop('Channel packing cancelled.'),hide=()=>{if(document.hidden)stop('Return to the drawing and preview the channel pack again.');};
 const current=()=>{if(stopped)throw stopError;check();},wait=promise=>Promise.race([promise,cancellation]);
 try{
  signal?.addEventListener('abort',abort,{once:true});document.addEventListener('visibilitychange',hide);timer=setTimeout(()=>stop('Channel packing took too long. Try smaller source maps.'),30000);current();canvas=document.createElement('canvas');
  const inputs=[];
  for(const url of urls){const image=new Image();images.push(image);image.decoding='async';await wait(new Promise((resolve,reject)=>{image.onload=resolve;image.onerror=()=>reject(Error('An embedded channel map could not be opened.'));image.src=url;}));current();if(image.naturalWidth!==width||image.naturalHeight!==height)throw Error('The embedded source dimensions changed.');canvas.width=width;canvas.height=height;const context=canvas.getContext('2d',{willReadFrequently:true});if(!context)throw Error('Local channel packing is unavailable.');context.drawImage(image,0,0);inputs.push(context.getImageData(0,0,width,height).data);canvas.width=canvas.height=0;image.removeAttribute('src');}
  const result=await wait(runChannelPackTask({inputs,width,height},makeChannelPackWorker,current,onProgress,controller.signal));current();canvas.width=width;canvas.height=height;const context=canvas.getContext('2d');if(!context)throw Error('Local PNG encoding is unavailable.');context.putImageData(new ImageData(result.data,width,height),0,0);
  const blob=await wait(new Promise((resolve,reject)=>canvas.toBlob(value=>value?resolve(value):reject(Error('The channel-pack PNG could not be encoded.')),'image/png')));current();
  if(blob.type!=='image/png'||blob.size<1||blob.size>MAX_PHOTO_BYTES)throw Error('The exact channel-pack PNG exceeds the 192 KiB local photo limit. Use smaller or simpler source maps.');return{blob,width,height,changed:result.changed};
 }finally{clearTimeout(timer);controller.abort();signal?.removeEventListener('abort',abort);document.removeEventListener('visibilitychange',hide);for(const image of images){image.onload=image.onerror=null;image.removeAttribute('src');}if(canvas)canvas.width=canvas.height=0;}
}
