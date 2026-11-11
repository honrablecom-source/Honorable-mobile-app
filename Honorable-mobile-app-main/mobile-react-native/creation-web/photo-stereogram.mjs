export const STEREOGRAM_CONVENTION='noble-sirds-row-constraints-v1';
export const DEPTH_UNITS=65025000;
export function stereogramSettings(width,height,period,seed,invert=false){if(!Number.isInteger(width)||width<64||width>1024||!Number.isInteger(height)||height<8||height>1024)throw Error('Use a depth map 64–1024 pixels wide and 8–1024 pixels high.');if(!Number.isInteger(period)||period<16||period>128||period*4>width)throw Error('Use a base spacing from 16 to 128 pixels, with at least four repeats across the image.');if(!Number.isInteger(seed)||seed<0||seed>4294967295||typeof invert!=='boolean')throw Error('Use a whole seed from 0 to 4294967295 and a valid depth polarity.');return{width,height,period,seed,invert};}
export function stereogramSeparation(depth,period){if(!Number.isInteger(depth)||depth<0||depth>DEPTH_UNITS||!Number.isInteger(period)||period<16||period>128)throw Error('Use valid normalized depth units and base spacing.');const numerator=2*period*(3*DEPTH_UNITS-depth),denominator=6*DEPTH_UNITS-depth;return Math.floor((2*numerator+denominator)/(2*denominator));}
function root(parents,x){let top=x;while(parents[top]!==top)top=parents[top];while(parents[x]!==x){const next=parents[x];parents[x]=top;x=next;}return top;}
function capture(input,width,height,period,seed,invert){const settings=stereogramSettings(width,height,period,seed,invert);if(!(input instanceof Uint8Array||input instanceof Uint8ClampedArray)||!(input.buffer instanceof ArrayBuffer)||input.length!==width*height*4)throw Error('The depth-map RGBA pixels are incomplete.');return{settings,source:new Uint8ClampedArray(input),depth:new Uint32Array(width*height),bits:new Uint8Array(width*height),groups:new Uint16Array(width*height),constraints:0,occluded:0,clippedPairs:0,components:0};}
function* work(task){const{settings:{width,height,period,seed,invert},source,depth,bits,groups}=task;for(let y=0;y<height;y++){for(let x=0;x<width;x++){const p=(y*width+x)*4,luma=299*source[p]+587*source[p+1]+114*source[p+2];depth[y*width+x]=(invert?255000-luma:luma)*source[p+3];}if((y+1)%16===0||y===height-1)yield .2*(y+1)/height;}
 const parents=new Uint16Array(width),colors=new Uint8Array(width);
 for(let y=0;y<height;y++){
  for(let x=0;x<width;x++)parents[x]=x;let components=width;
  for(let x=0;x<width;x++){
   const d=depth[y*width+x],separation=stereogramSeparation(d,period),left=x-Math.floor((separation+(y&1))/2),right=left+separation;
   if(left<0||right>=width){task.clippedPairs++;continue;}
   let visible=true;for(let t=1;(6*DEPTH_UNITS-d)*t<=period*(DEPTH_UNITS-d);t++){const rayRise=(6*DEPTH_UNITS-d)*t;if(period*(depth[y*width+x-t]-d)>=rayRise||period*(depth[y*width+x+t]-d)>=rayRise){visible=false;break;}}
   if(!visible){task.occluded++;continue;}task.constraints++;const a=root(parents,left),b=root(parents,right);if(a!==b){parents[Math.min(a,b)]=Math.max(a,b);components--;}
  }
  let state=(seed^Math.imul(y+1,0x9e3779b1))>>>0;for(let x=width-1;x>=0;x--){state=(Math.imul(state,1664525)+1013904223)>>>0;colors[x]=state>>>31;}for(let x=0;x<width;x++){const canonical=root(parents,x);groups[y*width+x]=canonical;bits[y*width+x]=colors[canonical];}task.components+=components;
  if((y+1)%8===0||y===height-1)yield .2+.8*(y+1)/height;
 }
 return{...task.settings,bits,depth,groups,constraints:task.constraints,occluded:task.occluded,clippedPairs:task.clippedPairs,components:task.components,convention:STEREOGRAM_CONVENTION};
}
export function stereogramPixels(input,width,height,period,seed,invert=false){const iterator=work(capture(input,width,height,period,seed,invert));let step;do{step=iterator.next();}while(!step.done);return step.value;}
export async function stereogramPixelsAsync(input,width,height,period,seed,invert=false,current=()=>{},onProgress=()=>{},yieldTurn=()=>new Promise(resolve=>setTimeout(resolve,0))){current();const iterator=work(capture(input,width,height,period,seed,invert));for(;;){current();const step=iterator.next();if(step.done){current();return step.value;}onProgress(step.value);await yieldTurn();}}
