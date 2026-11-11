export const CARVE_CONVENTION='noble-gradient-seams-v1';
export function carveSettings(width,height,axis,remove,protect=null){
 if(![width,height].every(n=>Number.isInteger(n)&&n>=8&&n<=1024))throw Error('Use a photo from 8 to 1024 whole pixels per side.');
 if(!['width','height'].includes(axis)||!Number.isInteger(remove)||remove<1||remove>128||(axis==='width'?width:height)-remove<8)throw Error('Remove 1–128 seams while keeping at least 8 pixels on the chosen axis.');
 if(width*height*remove>8388608)throw Error('Use fewer seams or a smaller source; the local work limit is 8,388,608 source-pixel seam steps.');
 if(protect!==null&&(!Array.isArray(protect)||protect.length!==4||!protect.every(Number.isInteger)||protect[0]<0||protect[1]<0||protect[2]>width||protect[3]>height||protect[0]>=protect[2]||protect[1]>=protect[3]))throw Error('Use a nonempty protected rectangle inside the original pixel grid.');
 return{sourceWidth:width,sourceHeight:height,width:width-(axis==='width'?remove:0),height:height-(axis==='height'?remove:0),axis,remove,protect:protect&&[...protect]};
}
function capture(input,width,height,axis,remove,protect){const settings=carveSettings(width,height,axis,remove,protect);if(!(input instanceof Uint8Array||input instanceof Uint8ClampedArray)||!(input.buffer instanceof ArrayBuffer)||input.length!==width*height*4)throw Error('The source RGBA pixels are incomplete.');const w=axis==='width'?width:height,h=axis==='width'?height:width,data=new Uint8ClampedArray(input.length),positions=new Uint32Array(w*h);if(axis==='width'){data.set(input);for(let i=0;i<positions.length;i++)positions[i]=i;}else for(let y=0;y<h;y++)for(let x=0;x<w;x++){const from=x*width+y,at=y*w+x;for(let k=0;k<4;k++)data[at*4+k]=input[from*4+k];positions[at]=from;}return{settings,w,h,data,positions,costs:[]};}
function* work(task){
 const {settings,h}=task;
 for(let iteration=0;iteration<settings.remove;iteration++){
  const {w,data,positions}=task,luma=new Uint32Array(w*h),parents=new Int8Array(w*h),a=new Float64Array(w),b=new Float64Array(w);
  const report=(stage,row)=>({progress:(iteration+(stage+row/h)/3)/settings.remove});
  for(let y=0;y<h;y++){for(let x=0;x<w;x++){const at=y*w+x,p=at*4,alpha=data[p+3]/255,white=255*(1-alpha);luma[at]=alpha===1?299*data[p]+587*data[p+1]+114*data[p+2]:299*Math.round(data[p]*alpha+white)+587*Math.round(data[p+1]*alpha+white)+114*Math.round(data[p+2]*alpha+white);}if((y+1)%32===0||y===h-1)yield report(0,y+1);}
  let previous=a,current=b;
  for(let y=0;y<h;y++){
   for(let x=0;x<w;x++){
    const index=y*w+x,source=positions[index],sx=source%settings.sourceWidth,sy=Math.floor(source/settings.sourceWidth),r=settings.protect;
    if(r&&sx>=r[0]&&sx<r[2]&&sy>=r[1]&&sy<r[3]){current[x]=Infinity;continue;}
    const energy=Math.abs(luma[y*w+Math.max(0,x-1)]-luma[y*w+Math.min(w-1,x+1)])+Math.abs(luma[Math.max(0,y-1)*w+x]-luma[Math.min(h-1,y+1)*w+x]);
    if(y===0){current[x]=energy;continue;}
    let best=Math.max(0,x-1);for(let px=best+1;px<=Math.min(w-1,x+1);px++)if(previous[px]<previous[best])best=px;
    current[x]=energy+previous[best];parents[index]=best-x;
   }
   [previous,current]=[current,previous];if((y+1)%32===0||y===h-1)yield report(1,y+1);
  }
  let end=0;for(let x=1;x<w;x++)if(previous[x]<previous[end])end=x;
  if(!Number.isFinite(previous[end]))throw Error('No connected seam avoids the protected rectangle. Original kept; reduce protection or remove fewer seams.');
  task.costs.push(previous[end]);const seam=new Uint16Array(h);seam[h-1]=end;for(let y=h-1;y>0;y--)seam[y-1]=seam[y]+parents[y*w+seam[y]];
  const nextData=new Uint8ClampedArray((w-1)*h*4),nextPositions=new Uint32Array((w-1)*h);
  for(let y=0;y<h;y++){const from=y*w,to=y*(w-1),cut=seam[y];nextData.set(data.subarray(from*4,(from+cut)*4),to*4);nextData.set(data.subarray((from+cut+1)*4,(from+w)*4),(to+cut)*4);nextPositions.set(positions.subarray(from,from+cut),to);nextPositions.set(positions.subarray(from+cut+1,from+w),to+cut);if((y+1)%32===0||y===h-1)yield report(2,y+1);}
  task.data=nextData;task.positions=nextPositions;task.w--;
 }
 const {width,height}=settings,data=settings.axis==='width'?task.data:new Uint8ClampedArray(width*height*4),positions=settings.axis==='width'?task.positions:new Uint32Array(width*height);
 if(settings.axis==='height')for(let y=0;y<height;y++)for(let x=0;x<width;x++){const from=x*task.w+y,to=y*width+x;for(let k=0;k<4;k++)data[to*4+k]=task.data[from*4+k];positions[to]=task.positions[from];}
 return{...settings,data,positions,costs:task.costs,convention:CARVE_CONVENTION};
}
export function carvePixels(input,width,height,axis,remove,protect=null){const iterator=work(capture(input,width,height,axis,remove,protect));let step;do{step=iterator.next();}while(!step.done);return step.value;}
export async function carvePixelsAsync(input,width,height,axis,remove,protect=null,current=()=>{},onProgress=()=>{},yieldTurn=()=>new Promise(resolve=>setTimeout(resolve,0))){current();const iterator=work(capture(input,width,height,axis,remove,protect));for(;;){current();const step=iterator.next();if(step.done){current();return step.value;}onProgress(step.value.progress);await yieldTurn();}}
