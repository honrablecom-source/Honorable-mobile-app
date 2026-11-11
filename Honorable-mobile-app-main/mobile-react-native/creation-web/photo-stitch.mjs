import{parseImagePalette}from'./photo-palette-image.mjs';
export function stitchSettings(width,height,columns,rows,paletteText){
 if(![width,height].every(n=>Number.isInteger(n)&&n>=8&&n<=1024))throw Error('Use source artwork 8–1024 pixels per side.');
 if(![columns,rows].every(n=>Number.isInteger(n)&&n>=4&&n<=16)||columns>width||rows>height)throw Error('Use 4–16 stitch columns and rows, no larger than the source.');
 return{width,height,columns,rows,paletteText:parseImagePalette(paletteText).map(p=>p.hex).join(' ')};
}
function* sample(input,width,height,columns,rows,paletteText,current){
 const settings=stitchSettings(width,height,columns,rows,paletteText);if(!(input instanceof Uint8Array||input instanceof Uint8ClampedArray)||!(input.buffer instanceof ArrayBuffer)||input.length!==width*height*4)throw Error('The stitch source RGBA pixels are incomplete.');current();const source=new Uint8ClampedArray(input),colors=[];
 // Integer rectangle overlaps give exact fractional-cell area weights. Each cell's total weight is width*height.
 for(let row=0;row<rows;row++){
  for(let column=0;column<columns;column++){
   const sums=[0,0,0],left=column*width,right=(column+1)*width,top=row*height,bottom=(row+1)*height;
   for(let y=Math.floor(top/rows);y<Math.ceil(bottom/rows);y++){
    const wy=Math.min((y+1)*rows,bottom)-Math.max(y*rows,top);
    for(let x=Math.floor(left/columns);x<Math.ceil(right/columns);x++){
     const wx=Math.min((x+1)*columns,right)-Math.max(x*columns,left),i=(y*width+x)*4,a=source[i+3],weight=wx*wy;
     for(let channel=0;channel<3;channel++)sums[channel]+=(a*source[i+channel]+(255-a)*255)*weight;
    }
   }
   colors.push(sums.map(n=>Math.round(n/(255*width*height))));current();yield;
  }
 }
 current();return{...settings,colors};
}
export function stitchPixels(input,width,height,columns,rows,paletteText,current=()=>{}){const iterator=sample(input,width,height,columns,rows,paletteText,current);let step=iterator.next();while(!step.done)step=iterator.next();return step.value;}
export async function stitchPixelsAsync(input,width,height,columns,rows,paletteText,current=()=>{},onProgress=()=>{},yieldTurn=()=>new Promise(resolve=>setTimeout(resolve,0))){current();const iterator=sample(input,width,height,columns,rows,paletteText,current);let step=iterator.next(),done=0;while(!step.done){onProgress(++done/(columns*rows));await yieldTurn();current();step=iterator.next();}return step.value;}
export function stitchAssignment(rgb,palette){let best=0,distance=Infinity;for(let i=0;i<palette.length;i++){const next=palette[i].rgb.reduce((sum,v,c)=>sum+(v-rgb[c])**2,0);if(next<distance){best=i;distance=next;}}return best;}
const text=(value,x,y,size)=>({type:'text',text:value,x,y,fontSize:size,fontFamily:2,strokeColor:'#111111',opacity:100,angle:0});
export function stitchArtwork(title,proof,fabricCount){
 if(typeof title!=='string'||!title.trim()||Array.from(title).length>48||/[\x00-\x1f\x7f\u200b-\u200f\u202a-\u202e\u2060-\u206f]/.test(title))throw Error('Use a title of 1–48 visible characters on one line.');
 if(!Number.isInteger(fabricCount)||fabricCount<6||fabricCount>40)throw Error('Use a whole fabric count from 6 to 40 stitches per inch.');
 const settings=stitchSettings(proof?.width,proof?.height,proof?.columns,proof?.rows,proof?.paletteText);
 if(!Array.isArray(proof.colors)||proof.colors.length!==settings.columns*settings.rows||!proof.colors.every(rgb=>Array.isArray(rgb)&&rgb.length===3&&rgb.every(n=>Number.isInteger(n)&&n>=0&&n<=255)))throw Error('The stitch color review is incomplete. Preview again.');
 const palette=parseImagePalette(settings.paletteText),colors=proof.colors.map(p=>[...p]),assignments=colors.map(rgb=>stitchAssignment(rgb,palette)),counts=palette.map((_,i)=>assignments.filter(n=>n===i).length),letters=Array.from(title.trim()),heading=[];while(letters.length)heading.push(letters.splice(0,24).join(''));
 const cell=28,left=16,top=heading.length*30+52,chartWidth=settings.columns*cell,chartHeight=settings.rows*cell,plan=[text(heading.join('\n'),left,0,24),text(`${settings.columns} × ${settings.rows} stitches · ${fabricCount} stitches/inch`,left,heading.length*30,14),{type:'rectangle',x:left,y:top,width:chartWidth,height:chartHeight,strokeColor:'#111111',backgroundColor:'#ffffff',fillStyle:'solid',strokeWidth:1,roughness:0,opacity:100,customData:{nobleStitchGrid:true}}];
 const line=(x,y,w,h)=>({type:'line',x,y,width:w,height:h,points:[[0,0],[w,h]],strokeColor:'#777777',strokeWidth:1,roughness:0,opacity:100,startArrowhead:null,endArrowhead:null});
 for(let c=1;c<settings.columns;c++)plan.push(line(left+c*cell,top,0,chartHeight));for(let r=1;r<settings.rows;r++)plan.push(line(left,top+r*cell,chartWidth,0));
 assignments.forEach((index,i)=>plan.push({...text(String.fromCharCode(65+index),left+(i%settings.columns)*cell+7,top+Math.floor(i/settings.columns)*cell+4,16),width:14,height:20,lineHeight:1.25,customData:{nobleStitchCell:i,nobleStitchPalette:index}}));
 let keyTop=top+chartHeight+24;plan.push(text('Color key · counts include every grid cell',left,keyTop,14));keyTop+=26;
 palette.forEach((p,i)=>{plan.push({type:'rectangle',x:left,y:keyTop+i*30,width:20,height:20,strokeColor:'#111111',backgroundColor:p.hex,fillStyle:'solid',strokeWidth:1,roughness:0,opacity:100,customData:{nobleStitchKey:i}},text(`${String.fromCharCode(65+i)}  ${p.hex.toUpperCase()}  ${counts[i]} stitches`,left+32,keyTop+i*30,14));});
 const footer=keyTop+palette.length*30+12,sizeInches=[settings.columns/fabricCount,settings.rows/fabricCount];plan.push(text(`Approx. ${sizeInches[0].toFixed(2)} × ${sizeInches[1].toFixed(2)} inches\nAt stated fabric count; no border allowance.\nRGB colors are guides, not thread-brand matches.`,left,footer,14));
 return{title:title.trim(),fabricCount,proof:{...settings,colors},assignments,counts,palette:palette.map(p=>({hex:p.hex,rgb:[...p.rgb]})),sizeInches,plan,width:Math.max(512,chartWidth+32),height:footer+80};
}
