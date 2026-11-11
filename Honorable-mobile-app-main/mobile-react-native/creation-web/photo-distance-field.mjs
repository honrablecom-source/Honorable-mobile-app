/** Independent separable squared-distance implementation; mathematical reference:
 * Felzenszwalb & Huttenlocher, https://cs.brown.edu/people/pfelzens/papers/dt-final.pdf
 * No external implementation/code is embedded. Distances here are between pixel centers. */
export function distanceFieldSettings(width,height,threshold=128,spread=8){
 if(!Number.isInteger(width)||!Number.isInteger(height)||width<8||height<8||width>1024||height>1024)throw Error('Use an embedded cutout from 8 to 1024 pixels on each side.');
 if(!Number.isInteger(threshold)||threshold<1||threshold>255||!Number.isInteger(spread)||spread<1||spread>128)throw Error('Use alpha threshold 1–255 and distance range 1–128 whole pixels.');return{width,height,threshold,spread};
}
function workspace(size){return{cost:new Float64Array(size),result:new Float64Array(size),sites:new Int32Array(size),starts:new Float64Array(size+1)};}
/** Build a lower envelope from finite sites only, so empty lines never subtract Infinity. */
function squaredLine(work,length){
 const{cost,result,sites,starts}=work;let last=-1;
 for(let site=0;site<length;site++){
  if(!Number.isFinite(cost[site]))continue;let crossing=-Infinity;
  while(last>=0){const previous=sites[last];crossing=((cost[site]+site*site)-(cost[previous]+previous*previous))/(2*(site-previous));if(crossing>starts[last])break;last--;}
  last++;sites[last]=site;starts[last]=last===0?-Infinity:crossing;starts[last+1]=Infinity;
 }
 if(last<0){result.fill(Infinity,0,length);return;}
 let active=0;for(let position=0;position<length;position++){while(starts[active+1]<position)active++;const delta=position-sites[active];result[position]=delta*delta+cost[sites[active]];}
}
function rowPass(task,first,last,target){
 const{width}=task.settings,{work,mask,grid}=task;
 for(let y=first;y<last;y++){for(let x=0;x<width;x++)work.cost[x]=mask[y*width+x]===target?0:Infinity;squaredLine(work,width);grid.set(work.result.subarray(0,width),y*width);}
}
function columnPass(task,first,last,target,record=false){
 const{width,height}=task.settings,{work,mask,grid,signed}=task;
 for(let x=first;x<last;x++){
  for(let y=0;y<height;y++)work.cost[y]=grid[y*width+x];squaredLine(work,height);
  for(let y=0;y<height;y++){const index=y*width+x;grid[index]=work.result[y];if(record&&mask[index]!==target)signed[index]=(target===1?-1:1)*Math.sqrt(work.result[y]);}
 }
}
export function distanceTransformSquared(mask,width,height,targetInside=true){
 const settings=distanceFieldSettings(width,height);if(typeof targetInside!=='boolean'||!(mask instanceof Uint8Array||mask instanceof Uint8ClampedArray)||mask.length!==width*height||mask.some(value=>value!==0&&value!==1))throw Error('Use a complete binary mask and a valid target class.');
 const task={settings,mask,grid:new Float64Array(mask.length),work:workspace(Math.max(width,height))};rowPass(task,0,height,targetInside?1:0);columnPass(task,0,width,targetInside?1:0);return task.grid;
}
function capture(input,width,height,threshold,spread){
 const settings=distanceFieldSettings(width,height,threshold,spread);
 if(!(input instanceof Uint8Array||input instanceof Uint8ClampedArray)||input.length!==width*height*4)throw Error('The embedded cutout pixels are incomplete.');
 return{settings,source:new Uint8ClampedArray(input),mask:new Uint8Array(width*height),inside:0};
}
function classifyRows(task,first,last){for(let index=first*task.settings.width;index<last*task.settings.width;index++){const inside=task.source[index*4+3]>=task.settings.threshold?1:0;task.mask[index]=inside;task.inside+=inside;}}
function admit(task){
 if(!task.inside||task.inside===task.mask.length)throw Error('Use a cutout with both inside and outside pixels at this alpha threshold.');
 task.grid=new Float64Array(task.mask.length);task.signed=new Float64Array(task.mask.length);task.data=new Uint8ClampedArray(task.mask.length*4);task.work=workspace(Math.max(task.settings.width,task.settings.height));task.source=null;
}
function encodeRows(task,first,last){
 for(let index=first*task.settings.width;index<last*task.settings.width;index++){
  const distance=task.signed[index],gray=Math.max(0,Math.min(255,Math.round(128+distance*(distance>=0?127:128)/task.settings.spread))),at=index*4;task.data[at]=task.data[at+1]=task.data[at+2]=gray;task.data[at+3]=255;
 }
}
function finish(task){return{data:task.data,width:task.settings.width,height:task.settings.height,inside:task.inside,threshold:task.settings.threshold,spread:task.settings.spread};}
export function distanceFieldPixels(input,width,height,threshold=128,spread=8){
 const task=capture(input,width,height,threshold,spread);classifyRows(task,0,height);admit(task);rowPass(task,0,height,1);columnPass(task,0,width,1,true);rowPass(task,0,height,0);columnPass(task,0,width,0,true);encodeRows(task,0,height);return finish(task);
}
export async function distanceFieldPixelsAsync(input,width,height,threshold=128,spread=8,requireCurrent=()=>{},onProgress=()=>{},yieldTurn=()=>new Promise(resolve=>setTimeout(resolve,0))){
 requireCurrent();const task=capture(input,width,height,threshold,spread),total=4*height+2*width;let completed=0;
 const step=async(count)=>{completed+=count;onProgress(completed/total);await yieldTurn();};
 for(let y=0;y<height;y+=8){requireCurrent();const end=Math.min(height,y+8);classifyRows(task,y,end);await step(end-y);}requireCurrent();admit(task);
 for(const target of[1,0]){
  for(let y=0;y<height;y+=8){requireCurrent();const end=Math.min(height,y+8);rowPass(task,y,end,target);await step(end-y);}
  for(let x=0;x<width;x+=8){requireCurrent();const end=Math.min(width,x+8);columnPass(task,x,end,target,true);await step(end-x);}
 }
 for(let y=0;y<height;y+=8){requireCurrent();const end=Math.min(height,y+8);encodeRows(task,y,end);await step(end-y);}requireCurrent();return finish(task);
}
