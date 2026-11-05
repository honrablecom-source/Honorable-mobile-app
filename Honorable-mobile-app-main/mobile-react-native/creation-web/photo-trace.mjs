import MagicWand from'magic-wand-tool';import{selectConnectedColor}from'./photo-wand.mjs';import{photoBytes}from'./image-assets.mjs';
export const MAX_TRACE_EDGES=12000,MAX_TRACE_PATHS=32,MAX_TRACE_POINTS=8192;
export function traceConnectedColor(input,width,height,seed,tolerance,simplify=1){
 if(!Number.isFinite(simplify)||simplify<0||simplify>4)throw Error('Use outline simplification from 0 to 4 source pixels.');
 const selection=selectConnectedColor(input,width,height,seed,tolerance),mask=selection.mask;let edges=0;
 // Check boundary complexity before the library allocates contour point objects.
 for(let y=0;y<height;y++)for(let x=0;x<width;x++){const i=y*width+x;if(!mask.data[i])continue;for(const neighbor of[x>0?i-1:-1,x<width-1?i+1:-1,y>0?i-width:-1,y<height-1?i+width:-1])if(neighbor<0||!mask.data[neighbor])edges++;if(edges>MAX_TRACE_EDGES)throw Error('This outline is too complex. Choose a simpler region or a smaller source photo.');}
 const contours=MagicWand.traceContours(mask);if(!contours.length||contours.length>MAX_TRACE_PATHS)throw Error('Use a region with 1–32 traceable outlines. Tiny or very complex selections are not supported.');
 const reduced=simplify===0?contours:MagicWand.simplifyContours(contours,simplify,4);let count=0;
 const plan=reduced.map(contour=>{const points=contour.points;if(!Array.isArray(points)||points.length<4||points.some(p=>!Number.isFinite(p.x)||!Number.isFinite(p.y)||p.x<0||p.y<0||p.x>=width||p.y>=height))throw Error('The outline is too small or invalid. Choose a larger region.');
  const first=points[0],last=points[points.length-1];if(first.x!==last.x||first.y!==last.y)throw Error('The outline could not be closed. Choose another region.');
  count+=points.length;if(count>MAX_TRACE_POINTS)throw Error('This trace has too many points. Increase simplification or choose a simpler region.');
  const left=Math.min(...points.map(p=>p.x)),top=Math.min(...points.map(p=>p.y)),right=Math.max(...points.map(p=>p.x)),bottom=Math.max(...points.map(p=>p.y));
  if(right-left<1||bottom-top<1)throw Error('The outline is too thin. Choose a region at least two pixels wide and high.');
  return{type:'line',x:left,y:top,width:right-left,height:bottom-top,points:points.map(p=>[p.x-left,p.y-top]),strokeColor:'#111111',backgroundColor:'transparent',strokeWidth:2,roughness:0,opacity:100,angle:0,startArrowhead:null,endArrowhead:null,roundness:null};
 });
 return{width,height,plan,selected:selection.selected,pointCount:count,innerCount:reduced.filter(c=>c.inner).length};
}
export async function tracePhotoColor(dataURL,seed,tolerance,simplify,requireCurrent=()=>{}){
 requireCurrent();const info=photoBytes(dataURL),image=new Image();await new Promise((resolve,reject)=>{image.onload=resolve;image.onerror=()=>reject(Error('The embedded source photo could not be opened.'));image.src=dataURL;});requireCurrent();if(image.naturalWidth!==info.width||image.naturalHeight!==info.height)throw Error('The source dimensions changed.');
 const canvas=document.createElement('canvas');try{canvas.width=info.width;canvas.height=info.height;const context=canvas.getContext('2d',{willReadFrequently:true});if(!context)throw Error('Local outline tracing is unavailable.');context.drawImage(image,0,0);const pixels=context.getImageData(0,0,info.width,info.height).data;await new Promise(resolve=>setTimeout(resolve,0));requireCurrent();const result=traceConnectedColor(pixels,info.width,info.height,seed,tolerance,simplify);requireCurrent();return result;}finally{canvas.width=canvas.height=0;}
}
