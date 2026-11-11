// Machado/Oliveira/Fernandes 2009, author-hosted severity 1.0 matrices.
// Our explicit encoding contract: encoded-sRGB alpha matte, then linear-sRGB simulation.
export const VISION_CONVENTION='noble-vision-srgb-v1';
export const VISION_MODELS=Object.freeze([
  {id:'normal',name:'Original composite',matrix:null},
  {id:'protan',name:'Protan simulation',matrix:[.152286,1.052583,-.204868,.114503,.786281,.099216,-.003882,-.048116,1.051998]},
  {id:'deutan',name:'Deutan simulation',matrix:[.367322,.860646,-.227968,.280085,.672501,.047413,-.011820,.042940,.968881]},
  {id:'tritan',name:'Tritan simulation',matrix:[1.255528,-.076749,-.178779,-.078411,.930809,.147602,.004733,.691367,.303900]},
].map(model=>Object.freeze({...model,matrix:model.matrix&&Object.freeze(model.matrix)})));
const linear=Array.from({length:256},(_,i)=>{const c=i/255;return c<=.04045?c/12.92:((c+.055)/1.055)**2.4;});
function encoded(value){const c=Math.max(0,Math.min(1,value));return Math.round(255*(c<=.0031308?12.92*c:1.055*c**(1/2.4)-.055));}
export function visionSettings(width,height,backdrop='#ffffff'){
  if(![width,height].every(n=>Number.isInteger(n)&&n>=8&&n<=1024))throw Error('Use a whole photo from 8 to 1024 pixels on each side.');
  if(typeof backdrop!=='string'||!/^#[0-9a-f]{6}$/i.test(backdrop))throw Error('Use a six-digit opaque backdrop color.');
  return{width,height,backdrop:backdrop.toLowerCase()};
}
function capture(input,width,height,backdrop){
  const settings=visionSettings(width,height,backdrop);
  if(!(input instanceof Uint8Array||input instanceof Uint8ClampedArray)||!(input.buffer instanceof ArrayBuffer)||input.length!==width*height*4)throw Error('The photo RGBA pixels are incomplete.');
  return{...settings,source:new Uint8ClampedArray(input),matte:[1,3,5].map(i=>parseInt(settings.backdrop.slice(i,i+2),16)),views:VISION_MODELS.map(model=>({id:model.id,name:model.name,data:new Uint8ClampedArray(input.length)}))};
}
function rows(task,first,last){
  for(let p=first*task.width*4;p<last*task.width*4;p+=4){
    const alpha=task.source[p+3]/255,base=task.matte.map((c,k)=>Math.round(task.source[p+k]*alpha+c*(1-alpha))),rgb=base.map(c=>linear[c]);
    task.views[0].data.set([...base,255],p);
    for(let v=1;v<4;v++){const m=VISION_MODELS[v].matrix,out=task.views[v].data;for(let k=0;k<3;k++)out[p+k]=encoded(m[k*3]*rgb[0]+m[k*3+1]*rgb[1]+m[k*3+2]*rgb[2]);out[p+3]=255;}
  }
}
function finish({width,height,backdrop,views}){return{width,height,backdrop,views,severity:1,convention:VISION_CONVENTION};}
export function visionPixels(input,width,height,backdrop='#ffffff'){const task=capture(input,width,height,backdrop);rows(task,0,height);return finish(task);}
export async function visionPixelsAsync(input,width,height,backdrop='#ffffff',current=()=>{},onProgress=()=>{},yieldTurn=()=>new Promise(resolve=>setTimeout(resolve,0))){
  current();const task=capture(input,width,height,backdrop);
  for(let y=0;y<height;y+=32){current();rows(task,y,Math.min(height,y+32));onProgress(Math.min(height,y+32)/height);await yieldTurn();}
  current();return finish(task);
}
export function visionSheetPlan(width,height,backdrop,ids,x=0,y=0){
  const settings=visionSettings(width,height,backdrop);
  if(!Array.isArray(ids)||ids.length!==4||!ids.every(id=>typeof id==='string'&&/^[a-f0-9]{64}$/.test(id))||![x,y].every(Number.isFinite))throw Error('Use all four reviewed color-vision PNGs.');
  const scale=220/Math.max(width,height),w=width*scale,h=height*scale,plan=[];
  for(let i=0;i<4;i++){
    const model=VISION_MODELS[i],left=x+(i%2)*264,top=y+Math.floor(i/2)*(h+76);
    const customData={nobleVision:{model:model.id,severity:model.matrix?1:0,backdrop:settings.backdrop,convention:VISION_CONVENTION,width,height}};
    plan.push({type:'image',x:left,y:top,width:w,height:h,fileId:ids[i],scale:[1,1],status:'saved',crop:null,angle:0,opacity:100,strokeColor:'transparent',backgroundColor:'transparent',roughness:0,groupIds:[],boundElements:null,locked:false,customData},
      {type:'text',x:left,y:top+h+8,text:`${model.name}\n${settings.backdrop} · ${width} × ${height}`,width:240,height:35,fontSize:14,fontFamily:2,lineHeight:1.25,textAlign:'left',verticalAlign:'top',autoResize:true,strokeColor:'#111111',backgroundColor:'transparent',strokeWidth:1,roughness:0,groupIds:[],boundElements:null,locked:false,angle:0,opacity:100});
  }
  return plan;
}
