import {photoBytes,MAX_PHOTO_BYTES} from './image-assets.mjs';
import {relightSettings,relightPixelsAsync} from './photo-relight.mjs';
export async function relightPhoto(sourceURL,maskURL,options,requireCurrent=()=>{},onProgress=()=>{},signal){
 options={...options};const check=()=>{if(signal?.aborted)throw Error('Normal-map relighting cancelled.');if(document.hidden)throw Error('Return to the drawing and preview the lighting again.');requireCurrent();};check();
 if(sourceURL===maskURL)throw Error('Choose a distinct normal map.');const sourceInfo=photoBytes(sourceURL),maskInfo=photoBytes(maskURL);relightSettings(sourceInfo.width,sourceInfo.height,options.azimuth,options.elevation,options.direct,options.ambient,options.greenDown);
 if(sourceInfo.width!==maskInfo.width||sourceInfo.height!==maskInfo.height)throw Error('Color photo and normal map must have the same embedded pixel dimensions.');
 const images=[];let canvas,timer,stopped=false,stopError,rejectStop;
 const cancellation=new Promise((_,reject)=>{rejectStop=reject;});cancellation.catch(()=>{});
 const stop=message=>{if(!stopped){stopped=true;stopError=Error(message);rejectStop(stopError);}},abort=()=>stop('Normal-map relighting cancelled.'),hide=()=>{if(document.hidden)stop('Return to the drawing and preview the lighting again.');};
 const current=()=>{if(stopped)throw stopError;check();},wait=promise=>Promise.race([promise,cancellation]);
 try{
  signal?.addEventListener('abort',abort,{once:true});document.addEventListener('visibilitychange',hide);timer=setTimeout(()=>stop('Normal-map relighting took too long. Try smaller photos.'),30000);current();
  canvas=document.createElement('canvas');
  const decode=async(dataURL,info)=>{
   const image=new Image();images.push(image);image.decoding='async';await wait(new Promise((resolve,reject)=>{image.onload=resolve;image.onerror=()=>reject(Error('An embedded photo could not be opened.'));image.src=dataURL;}));current();
   if(image.naturalWidth!==info.width||image.naturalHeight!==info.height)throw Error('The embedded source dimensions changed.');
   canvas.width=info.width;canvas.height=info.height;const context=canvas.getContext('2d',{willReadFrequently:true});if(!context)throw Error('Local normal-map relighting is unavailable.');context.drawImage(image,0,0);const pixels=context.getImageData(0,0,info.width,info.height).data;image.onload=image.onerror=null;image.removeAttribute('src');canvas.width=canvas.height=0;return pixels;
  };
  const source=await decode(sourceURL,sourceInfo),mask=await decode(maskURL,maskInfo);current();
  const result=await wait(relightPixelsAsync(source,mask,sourceInfo.width,sourceInfo.height,options.azimuth,options.elevation,options.direct,options.ambient,options.greenDown,current,onProgress));current();
  canvas.width=sourceInfo.width;canvas.height=sourceInfo.height;const context=canvas.getContext('2d');if(!context)throw Error('Local PNG encoding is unavailable.');context.putImageData(new ImageData(result.data,result.width,result.height),0,0);
  const blob=await wait(new Promise((resolve,reject)=>canvas.toBlob(value=>value?resolve(value):reject(Error('The relit PNG could not be encoded.')),'image/png')));current();
  if(blob.type!=='image/png'||blob.size<1||blob.size>MAX_PHOTO_BYTES)throw Error('The relit PNG exceeds the 192 KiB local limit. Use simpler artwork; no resizing was applied.');return{blob,width:result.width,height:result.height,changed:result.changed,azimuth:result.azimuth,elevation:result.elevation,direct:result.direct,ambient:result.ambient,greenDown:result.greenDown};
 }finally{
  clearTimeout(timer);signal?.removeEventListener('abort',abort);document.removeEventListener('visibilitychange',hide);for(const image of images){image.onload=image.onerror=null;image.removeAttribute('src');}if(canvas)canvas.width=canvas.height=0;
 }
}
