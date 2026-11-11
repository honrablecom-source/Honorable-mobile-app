export const GRADIENT_CONVENTION='noble-poisson-rectangle-v1';
export const GRADIENT_TOLERANCE=1e-5;
const MAX_ITERATIONS=512;
export function gradientSettings(sourceWidth,sourceHeight,width,height,rect,placement){
 if(![sourceWidth,sourceHeight,width,height].every(n=>Number.isInteger(n)&&n>=8&&n<=1024))throw Error('Use source and destination photos from 8 to 1024 pixels per side.');
 if(!Array.isArray(rect)||rect.length!==4||!rect.every(Number.isInteger)||!Array.isArray(placement)||placement.length!==2||!placement.every(Number.isInteger))throw Error('Enter whole source-patch and destination coordinates.');
 const[x,y,w,h]=rect,[dx,dy]=placement;
 if(w<1||h<1||w>64||h>64||x<1||y<1||x+w>=sourceWidth||y+h>=sourceHeight||dx<1||dy<1||dx+w>=width||dy+h>=height)throw Error('Use a 1–64 pixel patch with a one-pixel border inside both photos.');
 return{sourceWidth,sourceHeight,width,height,rect:[...rect],placement:[...placement]};
}
function apply(values,out,w,h){for(let y=0;y<h;y++)for(let x=0;x<w;x++){const i=y*w+x;out[i]=4*values[i]-(x?values[i-1]:0)-(x+1<w?values[i+1]:0)-(y?values[i-w]:0)-(y+1<h?values[i+w]:0);}}
function* solve(b,w,h,maxIterations){
 const size=w*h,values=new Float64Array(size),residual=new Float64Array(b),direction=new Float64Array(b),product=new Float64Array(size);
 let square=0,maximum=0;for(let i=0;i<size;i++){square+=residual[i]**2;maximum=Math.max(maximum,Math.abs(residual[i]));}
 if(maximum<=GRADIENT_TOLERANCE)return{values,residual:maximum,iterations:0};
 for(let iteration=1;iteration<=maxIterations;iteration++){
  apply(direction,product,w,h);let denominator=0;for(let i=0;i<size;i++)denominator+=direction[i]*product[i];
  if(!(denominator>0)||!Number.isFinite(denominator))throw Error('The gradient solver became unstable. No copy was prepared.');
  const alpha=square/denominator;let nextSquare=0;maximum=0;
  for(let i=0;i<size;i++){values[i]+=alpha*direction[i];residual[i]-=alpha*product[i];nextSquare+=residual[i]**2;maximum=Math.max(maximum,Math.abs(residual[i]));}
  if(maximum<=GRADIENT_TOLERANCE||iteration===maxIterations){
   apply(values,product,w,h);maximum=0;for(let i=0;i<size;i++)maximum=Math.max(maximum,Math.abs(b[i]-product[i]));
   if(maximum<=GRADIENT_TOLERANCE)return{values,residual:maximum,iterations:iteration};
   // Restart from the actual residual if recursive roundoff has understated it.
   nextSquare=0;for(let i=0;i<size;i++){residual[i]=b[i]-product[i];direction[i]=residual[i];nextSquare+=residual[i]**2;}square=nextSquare;
  }else{const beta=nextSquare/square;for(let i=0;i<size;i++)direction[i]=residual[i]+beta*direction[i];square=nextSquare;}
  if(iteration%8===0)yield iteration;
 }
 throw Error(`The gradient solver did not converge within ${maxIterations} iterations. No copy was prepared.`);
}
/** Exposes the bounded discrete solver for independently manufactured linear-system checks. */
export function solveGradientRect(input,w,h,maxIterations=MAX_ITERATIONS){
 if(![w,h].every(n=>Number.isInteger(n)&&n>=1&&n<=64)||!Number.isInteger(maxIterations)||maxIterations<1||maxIterations>MAX_ITERATIONS||!(input instanceof Float64Array)||!(input.buffer instanceof ArrayBuffer)||input.length!==w*h||!input.every(Number.isFinite))throw Error('Use a finite bounded rectangular gradient system.');
 const iterator=solve(new Float64Array(input),w,h,maxIterations);let step;do{step=iterator.next();}while(!step.done);return step.value;
}
function capture(source,sw,sh,destination,dw,dh,rect,placement){const settings=gradientSettings(sw,sh,dw,dh,rect,placement);for(const[pixels,w,h]of[[source,sw,sh],[destination,dw,dh]])if(!(pixels instanceof Uint8Array||pixels instanceof Uint8ClampedArray)||!(pixels.buffer instanceof ArrayBuffer)||pixels.length!==w*h*4)throw Error('Both photos need complete RGBA pixels.');return{settings,source:new Uint8ClampedArray(source),data:new Uint8ClampedArray(destination)};}
function* work(task){
 const{settings,source,data}=task,{sourceWidth:sw,sourceHeight:sh,width,height,rect:[sx,sy,pw,ph],placement:[dx,dy]}=settings;
 const flatten=(pixels,first,last)=>{for(let p=first;p<last;p+=4){const alpha=pixels[p+3]/255,white=255*(1-alpha);for(let c=0;c<3;c++)pixels[p+c]=Math.round(pixels[p+c]*alpha+white);pixels[p+3]=255;}};
 let doneRows=0;for(const[pixels,w,h]of[[source,sw,sh],[data,width,height]])for(let y=0;y<h;y+=32){const end=Math.min(h,y+32);flatten(pixels,y*w*4,end*w*4);doneRows+=end-y;yield .1*doneRows/(sh+height);}
 const changedMask=new Uint8Array(pw*ph),iterations=[],residuals=[],solutions=[];let clippedChannels=0;
 for(let c=0;c<3;c++){
  const b=new Float64Array(pw*ph);
  for(let y=0;y<ph;y++)for(let x=0;x<pw;x++){
   const i=y*pw+x,sp=((sy+y)*sw+sx+x)*4+c,dp=((dy+y)*width+dx+x)*4+c;
   b[i]=4*source[sp]-source[sp-4]-source[sp+4]-source[sp-sw*4]-source[sp+sw*4];
   if(x===0)b[i]+=data[dp-4];if(x===pw-1)b[i]+=data[dp+4];if(y===0)b[i]+=data[dp-width*4];if(y===ph-1)b[i]+=data[dp+width*4];
  }
  const iterator=solve(b,pw,ph,MAX_ITERATIONS);let result;
  for(;;){const step=iterator.next();if(step.done){result=step.value;break;}yield .1+.9*(c+step.value/MAX_ITERATIONS)/3;}
  iterations.push(result.iterations);residuals.push(result.residual);solutions.push(result.values);
  for(let y=0;y<ph;y++)for(let x=0;x<pw;x++){const i=y*pw+x,p=((dy+y)*width+dx+x)*4+c,value=result.values[i],next=Math.round(Math.max(0,Math.min(255,value)));if(value<0||value>255)clippedChannels++;if(next!==data[p])changedMask[i]=1;data[p]=next;}
  yield .1+.9*(c+1)/3;
 }
 return{...settings,data,iterations,residuals,solutions,solutionErrorBound:Math.max(...residuals)*Math.min((pw+1)**2,(ph+1)**2)/8,clippedChannels,changed:changedMask.reduce((sum,n)=>sum+n,0),tolerance:GRADIENT_TOLERANCE,convention:GRADIENT_CONVENTION};
}
export function gradientPastePixels(source,sw,sh,destination,dw,dh,rect,placement){const iterator=work(capture(source,sw,sh,destination,dw,dh,rect,placement));let step;do{step=iterator.next();}while(!step.done);return step.value;}
export async function gradientPastePixelsAsync(source,sw,sh,destination,dw,dh,rect,placement,current=()=>{},onProgress=()=>{},yieldTurn=()=>new Promise(resolve=>setTimeout(resolve,0))){current();const iterator=work(capture(source,sw,sh,destination,dw,dh,rect,placement));for(;;){current();const step=iterator.next();if(step.done){current();return step.value;}onProgress(step.value);await yieldTurn();}}
