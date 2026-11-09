export const CLONE_LIMITS=Object.freeze({side:1024,strokes:16,pointsPerStroke:128,totalPoints:1024,minRadius:2,maxRadius:96});
const clamp=(value,low,high)=>Math.max(low,Math.min(high,value));
function point(value,width,height){if(!Array.isArray(value)||value.length!==2||!value.every(Number.isFinite)||value[0]<0||value[1]<0||value[0]>width-1||value[1]>height-1)throw Error('Keep source and brush points inside the photo.');return[value[0],value[1]];}
export function cloneRepairPlan(width,height,strokes){
 if(!Number.isInteger(width)||!Number.isInteger(height)||width<8||height<8||width>CLONE_LIMITS.side||height>CLONE_LIMITS.side)throw Error('Use a photo from 8 to 1024 pixels on each side.');
 if(!Array.isArray(strokes)||!strokes.length||strokes.length>CLONE_LIMITS.strokes)throw Error('Use 1–16 repair strokes.');
 let count=0;const captured=strokes.map(stroke=>{
  if(!stroke||!Array.isArray(stroke.points)||!stroke.points.length||stroke.points.length>CLONE_LIMITS.pointsPerStroke)throw Error('Use 1–128 points per repair stroke.');
  count+=stroke.points.length;
  if(!Number.isFinite(stroke.radius)||stroke.radius<2||stroke.radius>96||!Number.isFinite(stroke.hardness)||stroke.hardness<0||stroke.hardness>1||!Number.isFinite(stroke.opacity)||stroke.opacity<.01||stroke.opacity>1)throw Error('Use brush radius 2–96 pixels, hardness 0–100% and opacity 1–100%.');
  const source=point(stroke.source,width,height),points=stroke.points.map(p=>point(p,width,height)),offset=[source[0]-points[0][0],source[1]-points[0][1]];
  return{source,points,offset,radius:stroke.radius,hardness:stroke.hardness,opacity:stroke.opacity};
 });
 if(count>CLONE_LIMITS.totalPoints)throw Error('Use up to 1,024 repair points in total.');
 return{width,height,strokes:captured,pointCount:count};
}
function validatePixels(input,width,height){if(!(input instanceof Uint8Array||input instanceof Uint8ClampedArray)||input.length!==width*height*4)throw Error('The source photo pixels are incomplete.');}
function distanceSquared(x,y,a,b){const dx=b[0]-a[0],dy=b[1]-a[1],length=dx*dx+dy*dy,t=length?clamp(((x-a[0])*dx+(y-a[1])*dy)/length,0,1):0;return(x-a[0]-t*dx)**2+(y-a[1]-t*dy)**2;}
function maskRow(stroke,width,y){
 const distances=new Float32Array(width);distances.fill(Infinity);const radius=stroke.radius+.5,points=stroke.points;
 const segment=(a,b)=>{
  if(y<Math.min(a[1],b[1])-radius||y>Math.max(a[1],b[1])+radius)return;
  let low=0,high=1;const dy=b[1]-a[1],dx=b[0]-a[0];if(Math.abs(dy)>1e-9){const t0=(y-radius-a[1])/dy,t1=(y+radius-a[1])/dy;low=Math.max(0,Math.min(t0,t1));high=Math.min(1,Math.max(t0,t1));if(low>high)return;}
  const left=Math.max(0,Math.floor(Math.min(a[0]+low*dx,a[0]+high*dx)-radius)),right=Math.min(width-1,Math.ceil(Math.max(a[0]+low*dx,a[0]+high*dx)+radius));
  for(let x=left;x<=right;x++)distances[x]=Math.min(distances[x],distanceSquared(x,y,a,b));
 };
 if(points.length===1)segment(points[0],points[0]);else for(let i=1;i<points.length;i++)segment(points[i-1],points[i]);
 return distances;
}
function sample(source,width,height,x,y){
 if(x<0||y<0||x>width-1||y>height-1)return null;
 const left=Math.floor(x),top=Math.floor(y),right=Math.min(width-1,left+1),bottom=Math.min(height-1,top+1),dx=x-left,dy=y-top;
 let red=0,green=0,blue=0,alpha=0;
 for(let i=0;i<4;i++){const px=i%2?right:left,py=i>=2?bottom:top,weight=(i%2?dx:1-dx)*(i>=2?dy:1-dy),at=(py*width+px)*4,a=source[at+3]/255*weight;red+=source[at]*a;green+=source[at+1]*a;blue+=source[at+2]*a;alpha+=a;}
 return[red,green,blue,alpha];
}
function rows(source,data,plan,stroke,first,last){
 for(let y=first;y<last;y++){
  const distances=maskRow(stroke,plan.width,y);
  for(let x=0;x<plan.width;x++){
   const distance=Math.sqrt(distances[x]),edge=clamp(stroke.radius+.5-distance,0,1),soft=stroke.hardness===1?1:clamp((stroke.radius-distance)/(stroke.radius*(1-stroke.hardness)),0,1),mask=edge*soft*stroke.opacity;if(!mask)continue;
   const color=sample(source,plan.width,plan.height,x+stroke.offset[0],y+stroke.offset[1]);if(!color||!color[3])continue;
   const at=(y*plan.width+x)*4,over=color[3]*mask,under=data[at+3]/255*(1-over),alpha=over+under,next=Math.round(alpha*255);if(!next)continue;
   data[at]=Math.round((color[0]*mask+data[at]*under)/alpha);data[at+1]=Math.round((color[1]*mask+data[at+1]*under)/alpha);data[at+2]=Math.round((color[2]*mask+data[at+2]*under)/alpha);data[at+3]=next;
  }
 }
}
function finish(source,data,plan){let changed=0;for(let i=0;i<data.length;i+=4)if(data[i]!==source[i]||data[i+1]!==source[i+1]||data[i+2]!==source[i+2]||data[i+3]!==source[i+3])changed++;if(!changed)throw Error('These strokes make no visible change. Choose visible clean texture and a different repair area.');return{data,width:plan.width,height:plan.height,strokes:plan.strokes,changed};}
/** Every stroke samples the immutable original, never previously repaired pixels. */
export function cloneRepairPixels(input,width,height,strokes){const plan=cloneRepairPlan(width,height,strokes);validatePixels(input,width,height);const data=new Uint8ClampedArray(input);for(const stroke of plan.strokes)rows(input,data,plan,stroke,0,height);return finish(input,data,plan);}
/** Bounded chunks are useful in a local worker; its owner may terminate at any time. */
export async function cloneRepairPixelsAsync(input,width,height,strokes,requireCurrent=()=>{},onProgress=()=>{},yieldTurn=()=>new Promise(resolve=>setTimeout(resolve,0))){
 requireCurrent();const plan=cloneRepairPlan(width,height,strokes);validatePixels(input,width,height);const source=new Uint8ClampedArray(input),data=new Uint8ClampedArray(source);
 for(let index=0;index<plan.strokes.length;index++)for(let row=0;row<height;row+=16){requireCurrent();rows(source,data,plan,plan.strokes[index],row,Math.min(height,row+16));onProgress((index+Math.min(1,(row+16)/height))/plan.strokes.length);await yieldTurn();}
 requireCurrent();return finish(source,data,plan);
}
