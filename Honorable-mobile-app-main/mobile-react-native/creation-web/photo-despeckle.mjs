/** Local 3×3 RGB median filtering for impulse speckles; original alpha is retained exactly. */
export function despeckleSettings(width,height,threshold,strength){
 if(!Number.isInteger(width)||!Number.isInteger(height)||width<8||height<8||width>1024||height>1024)throw Error('Use an embedded photo from 8 to 1024 pixels on each side.');
 if(!Number.isInteger(threshold)||threshold<1||threshold>255||!Number.isInteger(strength)||strength<1||strength>100)throw Error('Use a difference threshold of 1–255 and strength of 1–100%.');
 return{width,height,threshold,strength};
}
function capture(input,width,height,threshold,strength){
 const plan=despeckleSettings(width,height,threshold,strength);
 if(!(input instanceof Uint8Array||input instanceof Uint8ClampedArray)||input.length!==width*height*4)throw Error('The embedded photo pixels are incomplete.');
 const source=new Uint8ClampedArray(input);return{plan,source,data:new Uint8ClampedArray(source),changed:0};
}
function rows(task,first,last){
 const {source,data,plan}=task,{width,height,threshold,strength}=plan;
 const red=[],green=[],blue=[],sort=(a,b)=>a-b;
 for(let y=first;y<last;y++)for(let x=0;x<width;x++){
  const at=(y*width+x)*4;if(!source[at+3])continue;
  red.length=green.length=blue.length=0;
  for(let dy=-1;dy<=1;dy++)for(let dx=-1;dx<=1;dx++){
   const px=Math.max(0,Math.min(width-1,x+dx)),py=Math.max(0,Math.min(height-1,y+dy)),sample=(py*width+px)*4;
   if(source[sample+3]){red.push(source[sample]);green.push(source[sample+1]);blue.push(source[sample+2]);}
  }
  const middle=Math.floor(red.length/2),median=[red.sort(sort)[middle],green.sort(sort)[middle],blue.sort(sort)[middle]];
  if(Math.max(...median.map((value,index)=>Math.abs(value-source[at+index])))<threshold)continue;
  let different=false;
  for(let channel=0;channel<3;channel++){data[at+channel]=Math.round(source[at+channel]+(median[channel]-source[at+channel])*strength/100);if(data[at+channel]!==source[at+channel])different=true;}
  if(different)task.changed++;
 }
}
function finish(task){
 if(!task.changed)throw Error('No pixels meet these cleanup settings. Lower the threshold or keep the original.');
 return{data:task.data,width:task.plan.width,height:task.plan.height,changed:task.changed};
}
export function despecklePixels(input,width,height,threshold=30,strength=100){const task=capture(input,width,height,threshold,strength);rows(task,0,height);return finish(task);}
/** Eight-row chunks release the main thread; cancellation/current ownership is checked before every chunk. */
export async function despecklePixelsAsync(input,width,height,threshold,strength,requireCurrent=()=>{},onProgress=()=>{},yieldTurn=()=>new Promise(resolve=>setTimeout(resolve,0))){
 requireCurrent();const task=capture(input,width,height,threshold,strength);
 for(let row=0;row<height;row+=8){requireCurrent();rows(task,row,Math.min(height,row+8));onProgress(Math.min(height,row+8)/height);await yieldTurn();}
 requireCurrent();return finish(task);
}
