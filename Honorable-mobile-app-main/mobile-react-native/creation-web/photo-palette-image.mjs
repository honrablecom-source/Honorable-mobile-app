export function parseImagePalette(input){
 if(typeof input!=='string'||input.length>512||!input.trim())throw Error('Enter 2–16 distinct opaque HEX colors, separated by spaces, commas or new lines.');
 const values=input.trim().split(/[\s,]+/);if(values.length<2||values.length>16)throw Error('Use 2–16 distinct colors.');
 const seen=new Set();return values.map(value=>{if(!/^#[\da-f]{6}$/i.test(value))throw Error('Use colors as #RRGGBB, without alpha or labels.');const hex=value.toLowerCase();if(seen.has(hex))throw Error('Use distinct colors; duplicate HEX entries are not allowed.');seen.add(hex);return{hex,rgb:[1,3,5].map(index=>parseInt(hex.slice(index,index+2),16))};});
}
export function paletteImageSettings(width,height,palette,dither=true){
 if(!Number.isInteger(width)||!Number.isInteger(height)||width<8||height<8||width>1024||height>1024)throw Error('Use an embedded photo from 8 to 1024 pixels on each side.');
 if(typeof dither!=='boolean')throw Error('Choose a valid dithering mode.');return{width,height,palette:parseImagePalette(palette),dither};
}
function capture(input,width,height,palette,dither){
 const settings=paletteImageSettings(width,height,palette,dither);
 if(!(input instanceof Uint8Array||input instanceof Uint8ClampedArray)||input.length!==width*height*4)throw Error('The embedded photo pixels are incomplete.');
 return{settings,source:new Uint8ClampedArray(input),data:new Uint8ClampedArray(input.length),current:new Float64Array((width+2)*3),next:new Float64Array((width+2)*3),used:new Uint32Array(settings.palette.length),changed:0};
}
function processRows(task,first,last){
 const{settings,source,data}=task,{width,palette,dither}=settings,matte=palette[0].rgb;
 for(let y=first;y<last;y++){
  const direction=dither&&y%2?-1:1;
  for(let position=0;position<width;position++){
   const x=direction===1?position:width-1-position,at=(y*width+x)*4,errorAt=(x+1)*3,alpha=source[at+3]/255;
   const color=[0,1,2].map(channel=>Math.max(0,Math.min(255,source[at+channel]*alpha+matte[channel]*(1-alpha)+(dither?task.current[errorAt+channel]:0))));
   let chosen=0,best=Infinity;for(let index=0;index<palette.length;index++){const rgb=palette[index].rgb,distance=(color[0]-rgb[0])**2+(color[1]-rgb[1])**2+(color[2]-rgb[2])**2;if(distance<best){best=distance;chosen=index;}}
   const rgb=palette[chosen].rgb;data.set([...rgb,255],at);task.used[chosen]++;if(source[at+3]!==255||rgb.some((value,channel)=>value!==source[at+channel]))task.changed++;
   if(dither)for(let channel=0;channel<3;channel++){
    // Clamp before quantization: diffusion errors remain bounded even for out-of-gamut palettes.
    const error=color[channel]-rgb[channel];task.current[errorAt+direction*3+channel]+=error*7/16;task.next[errorAt-direction*3+channel]+=error*3/16;task.next[errorAt+channel]+=error*5/16;task.next[errorAt+direction*3+channel]+=error/16;
   }
  }
  const previous=task.current;task.current=task.next;task.next=previous;task.next.fill(0);
 }
}
function finish(task){
 if(!task.changed)throw Error('This photo already uses these palette colors without a visible change. Choose another palette or keep the original.');
 return{data:task.data,width:task.settings.width,height:task.settings.height,changed:task.changed,colors:task.settings.palette.map(color=>color.hex),used:Array.from(task.used)};
}
export function paletteImagePixels(input,width,height,palette,dither=true){const task=capture(input,width,height,palette,dither);processRows(task,0,height);return finish(task);}
export async function paletteImagePixelsAsync(input,width,height,palette,dither=true,requireCurrent=()=>{},onProgress=()=>{},yieldTurn=()=>new Promise(resolve=>setTimeout(resolve,0))){
 requireCurrent();const task=capture(input,width,height,palette,dither);
 for(let y=0;y<height;y+=8){requireCurrent();processRows(task,y,Math.min(height,y+8));onProgress(Math.min(height,y+8)/height);await yieldTurn();}
 requireCurrent();return finish(task);
}
