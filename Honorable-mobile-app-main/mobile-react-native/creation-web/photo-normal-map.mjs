/** RGB encodes the unit vector (-heightDx, +heightDy, 1), with optional image-down green. */
export function normalMapSettings(width,height,strength,greenUp=true,wrap=true){
 if(!Number.isInteger(width)||!Number.isInteger(height)||width<8||height<8||width>1024||height>1024)throw Error('Use an embedded height photo from 8 to 1024 pixels on each side.');
 if(!Number.isFinite(strength)||strength<0||strength>32||typeof greenUp!=='boolean'||typeof wrap!=='boolean')throw Error('Use height influence from 0 to 32 and valid sampling directions.');return{width,height,strength,greenUp,wrap};
}
function capture(input,width,height,strength,greenUp,wrap){
 const settings=normalMapSettings(width,height,strength,greenUp,wrap);
 if(!(input instanceof Uint8Array||input instanceof Uint8ClampedArray)||input.length!==width*height*4)throw Error('The embedded height pixels are incomplete.');
 return{source:new Uint8ClampedArray(input),heights:new Float64Array(width*height),data:new Uint8ClampedArray(input.length),settings};
}
function heightRows(task,first,last){const{source,heights,settings}=task;for(let index=first*settings.width;index<last*settings.width;index++){const at=index*4;heights[index]=(2126*source[at]+7152*source[at+1]+722*source[at+2])/(10000*255)*source[at+3]/255;}}
function normalRows(task,first,last){
 const{heights,data,settings}=task,{width,height,strength,greenUp,wrap}=settings;
 const sample=(x,y)=>heights[(wrap?(y+height)%height:Math.max(0,Math.min(height-1,y)))*width+(wrap?(x+width)%width:Math.max(0,Math.min(width-1,x)))];
 for(let y=first;y<last;y++)for(let x=0;x<width;x++){
  const dx=(sample(x+1,y)-sample(x-1,y))*.5*strength,dy=(sample(x,y+1)-sample(x,y-1))*.5*strength,length=Math.hypot(dx,dy,1),at=(y*width+x)*4;
  data[at]=Math.round((1-dx/length)*127.5);data[at+1]=Math.round((1+(greenUp?dy:-dy)/length)*127.5);data[at+2]=Math.round((1+1/length)*127.5);data[at+3]=255;
 }
}
function finish(task){return{data:task.data,width:task.settings.width,height:task.settings.height};}
export function normalMapPixels(input,width,height,strength=4,greenUp=true,wrap=true){const task=capture(input,width,height,strength,greenUp,wrap);heightRows(task,0,height);normalRows(task,0,height);return finish(task);}
export async function normalMapPixelsAsync(input,width,height,strength=4,greenUp=true,wrap=true,requireCurrent=()=>{},onProgress=()=>{},yieldTurn=()=>new Promise(resolve=>setTimeout(resolve,0))){
 requireCurrent();const task=capture(input,width,height,strength,greenUp,wrap);
 for(let y=0;y<height;y+=8){requireCurrent();heightRows(task,y,Math.min(height,y+8));onProgress(Math.min(height,y+8)/(height*2));await yieldTurn();}
 for(let y=0;y<height;y+=8){requireCurrent();normalRows(task,y,Math.min(height,y+8));onProgress(.5+Math.min(height,y+8)/(height*2));await yieldTurn();}
 requireCurrent();return finish(task);
}
