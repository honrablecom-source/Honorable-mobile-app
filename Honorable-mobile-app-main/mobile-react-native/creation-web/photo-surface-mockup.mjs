import perspectiveTransform from 'perspective-transform';
const LIMIT=1024,EPS=1e-8;
const cross=(a,b,x,y)=>(b.x-a.x)*(y-a.y)-(b.y-a.y)*(x-a.x);
function dimensions(width,height){if(!Number.isInteger(width)||!Number.isInteger(height)||width<8||height<8||width>LIMIT||height>LIMIT)throw Error('Use embedded photos from 8 to 1024 pixels on each side.');}
function pixels(input,width,height){dimensions(width,height);if(!(input instanceof Uint8Array||input instanceof Uint8ClampedArray)||input.length!==width*height*4)throw Error('The embedded photo pixels are incomplete.');}
function mapped(matrix,x,y){const denominator=matrix[6]*x+matrix[7]*y+matrix[8];if(!Number.isFinite(denominator)||Math.abs(denominator)<EPS)throw Error('This surface produces an unstable perspective. Choose a wider surface.');return[(matrix[0]*x+matrix[1]*y+matrix[2])/denominator,(matrix[3]*x+matrix[4]*y+matrix[5])/denominator,denominator];}
/** Coordinates mark pixel boundaries; artwork fills the four corners without automatic cropping. */
export function surfaceMockupPlan(width,height,artWidth,artHeight,corners,opacity=1){
 dimensions(width,height);dimensions(artWidth,artHeight);
 if(!Number.isFinite(opacity)||opacity<=0||opacity>1)throw Error('Use artwork opacity above 0 and up to 100%.');
 if(!Array.isArray(corners)||corners.length!==4||corners.some(p=>!p||!Number.isFinite(p.x)||!Number.isFinite(p.y)||p.x<0||p.y<0||p.x>width||p.y>height))throw Error('Choose four corners inside the background photo.');
 const points=corners.map(({x,y})=>({x,y}));let twiceArea=0;
 for(let i=0;i<4;i++){
  const a=points[i],b=points[(i+1)%4],c=points[(i+2)%4];
  if(Math.hypot(b.x-a.x,b.y-a.y)<2||cross(a,b,c.x,c.y)<=EPS)throw Error('Choose a convex surface clockwise: top-left, top-right, bottom-right, bottom-left. Keep corners apart.');
  twiceArea+=a.x*b.y-b.x*a.y;
 }
 if(twiceArea<32)throw Error('Choose a surface covering at least 16 photo pixels.');
 const source=[0,0,artWidth,0,artWidth,artHeight,0,artHeight],transform=perspectiveTransform(source,points.flatMap(p=>[p.x,p.y])),inverse=[...transform.coeffsInv];
 if(inverse.length!==9||inverse.some(value=>!Number.isFinite(value)))throw Error('This surface produces an unstable perspective.');
 let sign=0;
 for(let i=0;i<4;i++){const [x,y,denominator]=mapped(inverse,points[i].x,points[i].y);if(sign&&Math.sign(denominator)!==sign||Math.hypot(x-source[i*2],y-source[i*2+1])>.01)throw Error('The perspective could not retain your chosen corners.');sign=Math.sign(denominator);}
 const pixelAligned=Math.abs(inverse[0]-1)<EPS&&Math.abs(inverse[4]-1)<EPS&&Math.abs(inverse[1])+Math.abs(inverse[3])+Math.abs(inverse[6])+Math.abs(inverse[7])<EPS&&Math.abs(inverse[2]-Math.round(inverse[2]))<EPS&&Math.abs(inverse[5]-Math.round(inverse[5]))<EPS;
 return{width,height,artWidth,artHeight,corners:points,inverse,opacity,pixelAligned,bounds:{left:Math.max(0,Math.floor(Math.min(...points.map(p=>p.x)))),right:Math.min(width,Math.ceil(Math.max(...points.map(p=>p.x)))),top:Math.max(0,Math.floor(Math.min(...points.map(p=>p.y)))),bottom:Math.min(height,Math.ceil(Math.max(...points.map(p=>p.y))))}};
}
function sample(input,width,height,sx,sy){
 const x=Math.max(0,Math.min(width-1,sx-.5)),y=Math.max(0,Math.min(height-1,sy-.5)),left=Math.floor(x),top=Math.floor(y),right=Math.min(width-1,left+1),bottom=Math.min(height-1,top+1),dx=x-left,dy=y-top;
 let alpha=0,red=0,green=0,blue=0;
 for(let i=0;i<4;i++){
  const px=i%2?right:left,py=i>=2?bottom:top,weight=(i%2?dx:1-dx)*(i>=2?dy:1-dy),at=(py*width+px)*4,a=input[at+3]/255*weight;
  alpha+=a;red+=input[at]*a;green+=input[at+1]*a;blue+=input[at+2]*a;
 }
 return[red,green,blue,alpha];
}
function rows(base,art,plan,data,stats,first,last){
 const {width,corners,inverse,opacity,artWidth,artHeight,bounds}=plan;
 for(let y=Math.max(first,bounds.top);y<Math.min(last,bounds.bottom);y++)for(let x=bounds.left;x<bounds.right;x++){
  let red=0,green=0,blue=0,alpha=0;
  // Supersample both geometric coverage and premultiplied color, then source-over once.
  for(const dy of [.25,.75])for(const dx of [.25,.75]){
   const px=x+dx,py=y+dy;if(corners.some((a,i)=>cross(a,corners[(i+1)%4],px,py)<-EPS))continue;
   const [sx,sy]=mapped(inverse,plan.pixelAligned?x+.5:px,plan.pixelAligned?y+.5:py),rgba=sample(art,artWidth,artHeight,sx,sy),weight=opacity/4;
   red+=rgba[0]*weight;green+=rgba[1]*weight;blue+=rgba[2]*weight;alpha+=rgba[3]*weight;
  }
  if(alpha<=0)continue;
  const at=(y*width+x)*4,under=base[at+3]/255*(1-alpha),total=alpha+under,nextAlpha=Math.round(total*255);if(!nextAlpha)continue;
  data[at]=Math.round((red+base[at]*under)/total);data[at+1]=Math.round((green+base[at+1]*under)/total);data[at+2]=Math.round((blue+base[at+2]*under)/total);data[at+3]=nextAlpha;
  if(data[at]!==base[at]||data[at+1]!==base[at+1]||data[at+2]!==base[at+2]||data[at+3]!==base[at+3])stats.changed++;
 }
}
function finish(plan,data,stats){if(!stats.changed)throw Error('The artwork makes no visible pixel change. Choose visible artwork or another surface.');return{...plan,data,...stats};}
export function surfaceMockupPixels(base,width,height,art,artWidth,artHeight,corners,opacity=1){
 pixels(base,width,height);pixels(art,artWidth,artHeight);const plan=surfaceMockupPlan(width,height,artWidth,artHeight,corners,opacity),data=new Uint8ClampedArray(base),stats={changed:0};rows(base,art,plan,data,stats,0,height);return finish(plan,data,stats);
}
export async function surfaceMockupPixelsAsync(base,width,height,art,artWidth,artHeight,corners,opacity=1,requireCurrent=()=>{},onProgress=()=>{},yieldTurn=()=>new Promise(resolve=>setTimeout(resolve,0))){
 requireCurrent();pixels(base,width,height);pixels(art,artWidth,artHeight);const plan=surfaceMockupPlan(width,height,artWidth,artHeight,corners,opacity),data=new Uint8ClampedArray(base),stats={changed:0};
 for(let row=0;row<height;row+=8){requireCurrent();rows(base,art,plan,data,stats,row,Math.min(height,row+8));onProgress(Math.min(1,(row+8)/height));await yieldTurn();}
 requireCurrent();return finish(plan,data,stats);
}
