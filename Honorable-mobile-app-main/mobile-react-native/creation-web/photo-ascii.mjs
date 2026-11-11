export const ASCII_RAMP='@%#*+=-:. ';
export function asciiSettings(width,height,columns,rows,ramp=ASCII_RAMP,invert=false){
 if(![width,height].every(n=>Number.isInteger(n)&&n>=8&&n<=1024))throw Error('Use source artwork 8–1024 pixels per side.');
 if(!Number.isInteger(columns)||!Number.isInteger(rows)||columns<8||columns>80||rows<4||rows>40||columns>width||rows>height)throw Error('Use 8–80 text columns and 4–40 rows, no larger than the source.');
 if(typeof ramp!=='string'||ramp.length<2||ramp.length>16||/[^\x20-\x7e]/.test(ramp)||new Set(ramp).size!==ramp.length)throw Error('Use 2–16 distinct printable ASCII characters in dark-to-light order. Spaces count.');
 if(typeof invert!=='boolean')throw Error('Choose a valid tone reversal.');return{width,height,columns,rows,ramp,invert};
}
function* sample(input,width,height,columns,rows,ramp,invert,current){
 const settings=asciiSettings(width,height,columns,rows,ramp,invert);if(!(input instanceof Uint8Array||input instanceof Uint8ClampedArray)||!(input.buffer instanceof ArrayBuffer)||input.length!==width*height*4)throw Error('The ASCII source RGBA pixels are incomplete.');current();const source=new Uint8ClampedArray(input),tones=[];
 for(let row=0;row<rows;row++){
  for(let column=0;column<columns;column++){
   let sum=0;const left=column*width,right=(column+1)*width,top=row*height,bottom=(row+1)*height;
   for(let y=Math.floor(top/rows);y<Math.ceil(bottom/rows);y++){const wy=Math.min((y+1)*rows,bottom)-Math.max(y*rows,top);for(let x=Math.floor(left/columns);x<Math.ceil(right/columns);x++){const wx=Math.min((x+1)*columns,right)-Math.max(x*columns,left),i=(y*width+x)*4,a=source[i+3],luma=54*source[i]+183*source[i+1]+19*source[i+2];sum+=(a*luma+(255-a)*65280)*wx*wy;}}
   tones.push(Math.round(sum/(65280*width*height)));
  }
  current();yield;
 }
 current();return{...settings,tones};
}
export function asciiPixels(input,width,height,columns,rows,ramp=ASCII_RAMP,invert=false,current=()=>{}){const iterator=sample(input,width,height,columns,rows,ramp,invert,current);let step=iterator.next();while(!step.done)step=iterator.next();return step.value;}
export async function asciiPixelsAsync(input,width,height,columns,rows,ramp=ASCII_RAMP,invert=false,current=()=>{},onProgress=()=>{},yieldTurn=()=>new Promise(resolve=>setTimeout(resolve,0))){current();const iterator=sample(input,width,height,columns,rows,ramp,invert,current);let step=iterator.next(),done=0;while(!step.done){onProgress(++done/rows);await yieldTurn();current();step=iterator.next();}return step.value;}
export function asciiText(proof){const settings=asciiSettings(proof?.width,proof?.height,proof?.columns,proof?.rows,proof?.ramp,proof?.invert);if(!Array.isArray(proof.tones)||proof.tones.length!==settings.columns*settings.rows||!proof.tones.every(n=>Number.isInteger(n)&&n>=0&&n<=255))throw Error('The ASCII tone review is incomplete. Preview again.');const lines=[];for(let row=0;row<settings.rows;row++){let line='';for(let c=0;c<settings.columns;c++){const tone=proof.tones[row*settings.columns+c],value=settings.invert?255-tone:tone;line+=settings.ramp[Math.floor(value*settings.ramp.length/256)];}lines.push(line);}return lines.join('\n');}
const text=(value,x,y,size)=>({type:'text',text:value,x,y,fontSize:size,fontFamily:2,strokeColor:'#111111',opacity:100,angle:0});
export function asciiArtwork(title,proof){
 if(typeof title!=='string'||!title.trim()||Array.from(title).length>48||/[\x00-\x1f\x7f\u200b-\u200f\u202a-\u202e\u2060-\u206f]/.test(title))throw Error('Use a title of 1–48 visible characters on one line.');
 const settings=asciiSettings(proof?.width,proof?.height,proof?.columns,proof?.rows,proof?.ramp,proof?.invert),value=asciiText(proof),letters=Array.from(title.trim()),heading=[];while(letters.length)heading.push(letters.splice(0,24).join(''));const top=heading.length*30+24,bodyWidth=settings.columns*9.6,bodyHeight=settings.rows*20,width=Math.max(512,bodyWidth+32),plan=[text(heading.join('\n'),16,0,24),{type:'rectangle',x:0,y:top-8,width,height:bodyHeight+16,strokeColor:'transparent',backgroundColor:'#ffffff',fillStyle:'solid',strokeWidth:1,roughness:0,opacity:100,customData:{nobleAsciiPlate:true}}];
 // Preserve the editor's 2000-character per-object limit without truncating the portable text.
 const lines=value.split('\n'),rowsPerBlock=Math.floor(2001/(settings.columns+1));
 for(let start=0;start<settings.rows;start+=rowsPerBlock){const count=Math.min(rowsPerBlock,settings.rows-start),block=lines.slice(start,start+count).join('\n');plan.push({...text(block,16,top+start*20,16),fontFamily:3,width:bodyWidth,height:count*20,lineHeight:1.25,autoResize:false,textAlign:'left',customData:{nobleAsciiGrid:{columns:settings.columns,rows:settings.rows,ramp:settings.ramp,invert:settings.invert,startRow:start,rowCount:count}}});}
 plan.push(text(`${settings.columns} × ${settings.rows} character cells\nMonospace font; spacing may vary in other apps.`,16,top+bodyHeight+24,14));
 return{title:title.trim(),proof:{...settings,tones:[...proof.tones]},text:value,visible:value.trim().length>0,plan,width,height:top+bodyHeight+96};
}
