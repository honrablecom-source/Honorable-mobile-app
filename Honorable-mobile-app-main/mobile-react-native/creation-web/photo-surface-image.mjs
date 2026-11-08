import {photoBytes,MAX_SOURCE_BYTES} from './image-assets.mjs';
import {surfaceMockupPlan,surfaceMockupPixelsAsync} from './photo-surface-mockup.mjs';

/** Two bounded embedded sources; all transient canvases and decoder handlers have one lifetime. */
export async function surfacePhotoMockup(backgroundURL,artworkURL,corners,opacity=1,requireCurrent=()=>{},onProgress=()=>{},signal){
 const check=()=>{if(signal?.aborted)throw Error('Surface mockup cancelled.');if(document.hidden)throw Error('Return to the drawing and preview the mockup again.');requireCurrent();};
 check();const background=photoBytes(backgroundURL),artwork=photoBytes(artworkURL),plan=surfaceMockupPlan(background.width,background.height,artwork.width,artwork.height,corners,opacity);
 let timeout,stopped=false,rejectStop;const images=[],canvases=[];
 const cancellation=new Promise((_,reject)=>{rejectStop=reject;});cancellation.catch(()=>{});
 const stop=message=>{if(!stopped){stopped=true;rejectStop(Error(message));}},abort=()=>stop('Surface mockup cancelled.'),hide=()=>{if(document.hidden)stop('Return to the drawing and preview the mockup again.');};
 const current=()=>{if(stopped)throw Error('Surface mockup stopped. Preview again.');check();},wait=promise=>Promise.race([promise,cancellation]);
 const decode=async(dataURL,info,label)=>{
  current();const image=new Image();images.push(image);image.decoding='async';
  await wait(new Promise((resolve,reject)=>{image.onload=resolve;image.onerror=()=>reject(Error(`The embedded ${label} photo could not be opened.`));image.src=dataURL;}));current();
  if(image.naturalWidth!==info.width||image.naturalHeight!==info.height)throw Error(`The ${label} photo dimensions changed.`);
  const canvas=document.createElement('canvas');canvases.push(canvas);canvas.width=info.width;canvas.height=info.height;const context=canvas.getContext('2d',{willReadFrequently:true});if(!context)throw Error('Local photo processing is unavailable.');context.drawImage(image,0,0);const data=context.getImageData(0,0,info.width,info.height).data;
  image.onload=image.onerror=null;image.removeAttribute('src');canvas.width=canvas.height=0;current();return data;
 };
 try{
  signal?.addEventListener('abort',abort,{once:true});document.addEventListener('visibilitychange',hide);timeout=setTimeout(()=>stop('Surface mockup took too long. Try a smaller photo.'),30000);current();
  const base=await decode(backgroundURL,background,'background'),art=await decode(artworkURL,artwork,'artwork');current();
  const result=await wait(surfaceMockupPixelsAsync(base,background.width,background.height,art,artwork.width,artwork.height,plan.corners,opacity,current,onProgress));current();
  const canvas=document.createElement('canvas');canvases.push(canvas);canvas.width=background.width;canvas.height=background.height;const context=canvas.getContext('2d');if(!context)throw Error('Local PNG output is unavailable.');context.putImageData(new ImageData(result.data,result.width,result.height),0,0);
  const blob=await wait(new Promise((resolve,reject)=>canvas.toBlob(value=>value?resolve(value):reject(Error('The surface mockup PNG could not be encoded.')),'image/png')));current();
  if(blob.type!=='image/png'||blob.size<1||blob.size>MAX_SOURCE_BYTES)throw Error('The mockup PNG exceeds the local photo limit.');
  return{blob,width:result.width,height:result.height,corners:result.corners,opacity:result.opacity,changed:result.changed};
 }finally{
  clearTimeout(timeout);signal?.removeEventListener('abort',abort);document.removeEventListener('visibilitychange',hide);
  for(const image of images){image.onload=image.onerror=null;image.removeAttribute('src');}
  for(const canvas of canvases)canvas.width=canvas.height=0;
 }
}
