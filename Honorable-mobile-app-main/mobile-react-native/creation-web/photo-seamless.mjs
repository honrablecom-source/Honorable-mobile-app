/** Match opposite texture edges using a tapered premultiplied-alpha blend, without changing dimensions. */
export function seamlessSettings(width,height,blend){
 if(!Number.isInteger(width)||!Number.isInteger(height)||width<8||height<8||width>1024||height>1024)throw Error('Use an embedded photo from 8 to 1024 pixels on each side.');
 if(!Number.isInteger(blend)||blend<1||blend>Math.floor(Math.min(width,height)/2))throw Error('Use a whole-pixel blend width from 1 to half the shorter photo edge.');
 return{width,height,blend};
}
function capture(input,width,height,blend){
 const settings=seamlessSettings(width,height,blend);
 if(!(input instanceof Uint8Array||input instanceof Uint8ClampedArray)||input.length!==width*height*4)throw Error('The embedded photo pixels are incomplete.');
 const source=new Uint8ClampedArray(input);return{source,horizontal:new Uint8ClampedArray(source),data:null,settings};
}
function mix(input,output,at,other,weight){
 const a=input[at+3]/255*(1-weight),b=input[other+3]/255*weight,alpha=a+b,opacity=Math.round(alpha*255);
 output[at+3]=opacity;
 for(let channel=0;channel<3;channel++)output[at+channel]=opacity?Math.round((input[at+channel]*a+input[other+channel]*b)/alpha):0;
}
function horizontalRows(task,first,last){
 const {width,blend}=task.settings;
 for(let y=first;y<last;y++)for(let d=0;d<blend;d++){
  const left=(y*width+d)*4,right=(y*width+width-1-d)*4,weight=.5*(1-d/blend);
  mix(task.source,task.horizontal,left,right,weight);mix(task.source,task.horizontal,right,left,weight);
 }
}
function verticalRows(task,first,last){
 const {width,height,blend}=task.settings;
 for(let d=first;d<last;d++)for(let x=0;x<width;x++){
  const top=(d*width+x)*4,bottom=((height-1-d)*width+x)*4,weight=.5*(1-d/blend);
  mix(task.horizontal,task.data,top,bottom,weight);mix(task.horizontal,task.data,bottom,top,weight);
 }
}
function finish(task){
 let changed=0;for(let at=0;at<task.source.length;at+=4)if((task.source[at+3]||task.data[at+3])&&[0,1,2,3].some(channel=>task.source[at+channel]!==task.data[at+channel]))changed++;
 if(!changed)throw Error('This edge blend makes no visible change. Keep the original or use a different photo.');
 return{data:task.data,width:task.settings.width,height:task.settings.height,changed};
}
export function seamlessPixels(input,width,height,blend){const task=capture(input,width,height,blend);horizontalRows(task,0,height);task.data=new Uint8ClampedArray(task.horizontal);verticalRows(task,0,blend);return finish(task);}
export async function seamlessPixelsAsync(input,width,height,blend,requireCurrent=()=>{},onProgress=()=>{},yieldTurn=()=>new Promise(resolve=>setTimeout(resolve,0))){
 requireCurrent();const task=capture(input,width,height,blend),total=height+blend;
 for(let y=0;y<height;y+=8){requireCurrent();horizontalRows(task,y,Math.min(height,y+8));onProgress(Math.min(height,y+8)/total);await yieldTurn();}
 requireCurrent();task.data=new Uint8ClampedArray(task.horizontal);
 for(let d=0;d<blend;d+=8){requireCurrent();verticalRows(task,d,Math.min(blend,d+8));onProgress((height+Math.min(blend,d+8))/total);await yieldTurn();}
 requireCurrent();return finish(task);
}
