/** A soft mask uses encoded RGB luminance times its own alpha; source colors stay unchanged. */
export function luminanceMaskSettings(width,height,invert=false){
 if(!Number.isInteger(width)||!Number.isInteger(height)||width<8||height<8||width>1024||height>1024)throw Error('Use matching embedded photos from 8 to 1024 pixels on each side.');
 if(typeof invert!=='boolean')throw Error('Choose a valid mask direction.');return{width,height,invert};
}
function capture(source,mask,width,height,invert){
 const settings=luminanceMaskSettings(width,height,invert);
 for(const pixels of[source,mask])if(!(pixels instanceof Uint8Array||pixels instanceof Uint8ClampedArray)||pixels.length!==width*height*4)throw Error('Source and mask must have complete matching RGBA pixels.');
 return{source:new Uint8ClampedArray(source),mask:new Uint8ClampedArray(mask),data:new Uint8ClampedArray(source),settings,changed:0};
}
function rows(task,first,last){
 const{source,mask,data,settings}=task;
 for(let at=first*settings.width*4;at<last*settings.width*4;at+=4){
  const luminance=(2126*mask[at]+7152*mask[at+1]+722*mask[at+2])/(10000*255),amount=(settings.invert?1-luminance:luminance)*mask[at+3]/255;
  const alpha=Math.round(source[at+3]*amount);data[at+3]=alpha;if(alpha!==source[at+3])task.changed++;
  if(!alpha)data[at]=data[at+1]=data[at+2]=0;
 }
}
function finish(task){if(!task.changed)throw Error('This mask does not change visible source opacity. Choose another mask or direction.');return{data:task.data,width:task.settings.width,height:task.settings.height,changed:task.changed};}
export function luminanceMaskPixels(source,mask,width,height,invert=false){const task=capture(source,mask,width,height,invert);rows(task,0,height);return finish(task);}
export async function luminanceMaskPixelsAsync(source,mask,width,height,invert=false,requireCurrent=()=>{},onProgress=()=>{},yieldTurn=()=>new Promise(resolve=>setTimeout(resolve,0))){
 requireCurrent();const task=capture(source,mask,width,height,invert);
 for(let y=0;y<height;y+=16){requireCurrent();rows(task,y,Math.min(height,y+16));onProgress(Math.min(height,y+16)/height);await yieldTurn();}
 requireCurrent();return finish(task);
}
