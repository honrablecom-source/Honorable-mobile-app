/** Nonlinear texture warp: red controls horizontal sampling, green vertical; 128 is neutral. */
export function displacementSettings(width,height,horizontal,vertical){
 if(!Number.isInteger(width)||!Number.isInteger(height)||width<8||height<8||width>1024||height>1024)throw Error('Use matching embedded photos from 8 to 1024 pixels on each side.');
 if(!Number.isFinite(horizontal)||!Number.isFinite(vertical)||Math.abs(horizontal)>96||Math.abs(vertical)>96||(!horizontal&&!vertical))throw Error('Use sampling offsets within ±96 pixels, with at least one nonzero offset.');
 return{width,height,horizontal,vertical};
}
function capture(source,map,width,height,horizontal,vertical){
 const settings=displacementSettings(width,height,horizontal,vertical);
 for(const pixels of[source,map])if(!(pixels instanceof Uint8Array||pixels instanceof Uint8ClampedArray)||pixels.length!==width*height*4)throw Error('Source and displacement map need complete matching RGBA pixels.');
 return{source:new Uint8ClampedArray(source),map:new Uint8ClampedArray(map),data:new Uint8ClampedArray(source.length),settings,changed:0};
}
const direction=value=>value<128?(value-128)/128:(value-128)/127;
function sample(source,width,height,x,y){
 const left=Math.floor(x),top=Math.floor(y),dx=x-left,dy=y-top;let alpha=0,red=0,green=0,blue=0;
 for(let row=0;row<2;row++)for(let column=0;column<2;column++){
  const px=left+column,py=top+row;if(px<0||py<0||px>=width||py>=height)continue;
  const at=(py*width+px)*4,weight=(column?dx:1-dx)*(row?dy:1-dy)*source[at+3]/255;
  alpha+=weight;red+=source[at]*weight;green+=source[at+1]*weight;blue+=source[at+2]*weight;
 }
 const opacity=Math.round(alpha*255);return opacity?[Math.round(red/alpha),Math.round(green/alpha),Math.round(blue/alpha),opacity]:[0,0,0,0];
}
function rows(task,first,last){
 const {source,map,data,settings}=task,{width,height,horizontal,vertical}=settings;
 for(let y=first;y<last;y++)for(let x=0;x<width;x++){
  const at=(y*width+x)*4,weight=map[at+3]/255,color=sample(source,width,height,x+direction(map[at])*horizontal*weight,y+direction(map[at+1])*vertical*weight);data.set(color,at);
  if((source[at+3]||color[3])&&color.some((value,index)=>value!==source[at+index]))task.changed++;
 }
}
function finish(task){if(!task.changed)throw Error('This map makes no visible change. Choose another map or sampling offsets.');return{data:task.data,width:task.settings.width,height:task.settings.height,changed:task.changed};}
export function displacementPixels(source,map,width,height,horizontal,vertical){const task=capture(source,map,width,height,horizontal,vertical);rows(task,0,height);return finish(task);}
export async function displacementPixelsAsync(source,map,width,height,horizontal,vertical,requireCurrent=()=>{},onProgress=()=>{},yieldTurn=()=>new Promise(resolve=>setTimeout(resolve,0))){
 requireCurrent();const task=capture(source,map,width,height,horizontal,vertical);
 for(let y=0;y<height;y+=16){requireCurrent();rows(task,y,Math.min(height,y+16));onProgress(Math.min(height,y+16)/height);await yieldTurn();}
 requireCurrent();return finish(task);
}
