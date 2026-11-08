// A manual selection, independent of person segmentation and color connectivity.
const MAX_SIDE=1024,EPS=1e-8;
const cross=(a,b,c)=>(b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0]);
const on=(a,b,p)=>Math.abs(cross(a,b,p))<=EPS&&p[0]>=Math.min(a[0],b[0])-EPS&&p[0]<=Math.max(a[0],b[0])+EPS&&p[1]>=Math.min(a[1],b[1])-EPS&&p[1]<=Math.max(a[1],b[1])+EPS;
const touches=(a,b,c,d)=>{const x=cross(a,b,c),y=cross(a,b,d),u=cross(c,d,a),v=cross(c,d,b);return(x*y<0&&u*v<0)||on(a,b,c)||on(a,b,d)||on(c,d,a)||on(c,d,b);};
const area=ring=>ring.reduce((n,a,i)=>{const b=ring[(i+1)%ring.length];return n+a[0]*b[1]-b[0]*a[1];},0)/2;
export function insideOutline(point,ring){let inside=false;for(let i=0,j=ring.length-1;i<ring.length;j=i++){const a=ring[i],b=ring[j];if((a[1]>point[1])!==(b[1]>point[1])&&point[0]<(b[0]-a[0])*(point[1]-a[1])/(b[1]-a[1])+a[0])inside=!inside;}return inside;}
function validateRing(points,width,height){
 if(!Array.isArray(points)||points.length<3||points.length>128)throw Error('Use 3–128 points for each closed outline.');
 const ring=points.map(p=>{if(!Array.isArray(p)||p.length!==2||!p.every(Number.isFinite)||p[0]<0||p[0]>width||p[1]<0||p[1]>height)throw Error('Keep every outline point inside the photo.');return[p[0],p[1]];});
 if(ring.length>3&&Math.hypot(ring[0][0]-ring[ring.length-1][0],ring[0][1]-ring[ring.length-1][1])<1e-9)ring.pop();
 for(let i=0;i<ring.length;i++){
  const a=ring[(i+ring.length-1)%ring.length],b=ring[i],c=ring[(i+1)%ring.length];
  if(Math.hypot(c[0]-b[0],c[1]-b[1])<.5)throw Error('Place outline points at least half a pixel apart.');
  if(Math.abs(cross(a,b,c))<EPS&&(a[0]-b[0])*(c[0]-b[0])+(a[1]-b[1])*(c[1]-b[1])>0)throw Error('Draw an outline without crossing or retracing its edges.');
  for(let j=i+2;j<ring.length;j++){if(i===0&&j===ring.length-1)continue;if(touches(b,c,ring[j],ring[(j+1)%ring.length]))throw Error('Draw an outline without crossing or touching its own edges.');}
 }
 if(Math.abs(area(ring))<1)throw Error('Outline an area of at least one photo pixel.');return ring;
}
export function outlineCutoutPlan(width,height,selection,feather=0){
 if(!Number.isInteger(width)||!Number.isInteger(height)||width<8||height<8||width>MAX_SIDE||height>MAX_SIDE)throw Error('Use a photo from 8 to 1024 pixels on each side.');
 if(!selection||!Array.isArray(selection.holes??[])||(selection.holes??[]).length>7||!Number.isFinite(feather)||feather<0||feather>24)throw Error('Use one outer outline, up to seven holes and feathering from 0 to 24 pixels.');
 const rings=[validateRing(selection.outer,width,height),...(selection.holes??[]).map(r=>validateRing(r,width,height))];
 if(rings.reduce((n,r)=>n+r.length,0)>256)throw Error('Use up to 256 outline points in total.');
 for(let i=1;i<rings.length;i++){
  if(!insideOutline(rings[i][0],rings[0]))throw Error('Keep each hole inside the outer outline.');
  for(let j=0;j<i;j++){
   if(j>0&&(insideOutline(rings[i][0],rings[j])||insideOutline(rings[j][0],rings[i])))throw Error('Keep holes separate, without nesting.');
   for(let a=0;a<rings[i].length;a++)for(let b=0;b<rings[j].length;b++)if(touches(rings[i][a],rings[i][(a+1)%rings[i].length],rings[j][b],rings[j][(b+1)%rings[j].length]))throw Error('Keep holes separate and away from the outer outline edges.');
  }
 }
 const edges=rings.flatMap(r=>r.map((a,i)=>({a,b:r[(i+1)%r.length]})));
 return{width,height,feather,rings,edges,area:Math.abs(area(rings[0]))-rings.slice(1).reduce((n,r)=>n+Math.abs(area(r)),0)};
}
function coverageRow(plan,y){
 const coverage=new Float32Array(plan.width);
 // Four subpixel rows plus exact horizontal interval coverage avoids triangle seams.
 for(const offset of [.125,.375,.625,.875]){
  const py=y+offset,xs=[];
  for(const{a,b}of plan.edges)if((a[1]>py)!==(b[1]>py))xs.push(a[0]+(py-a[1])*(b[0]-a[0])/(b[1]-a[1]));
  xs.sort((a,b)=>a-b);if(xs.length%2)throw Error('The outline could not be rasterized. Adjust its points.');
  for(let i=0;i<xs.length;i+=2){const left=Math.max(0,xs[i]),right=Math.min(plan.width,xs[i+1]);for(let x=Math.max(0,Math.floor(left));x<Math.min(plan.width,Math.ceil(right));x++)coverage[x]+=(Math.min(x+1,right)-Math.max(x,left))*.25;}
 }
 return coverage;
}
function distanceSquared(x,y,a,b){const dx=b[0]-a[0],dy=b[1]-a[1],t=Math.max(0,Math.min(1,((x-a[0])*dx+(y-a[1])*dy)/(dx*dx+dy*dy)));return(x-a[0]-t*dx)**2+(y-a[1]-t*dy)**2;}
function featherRow(plan,y){
 const r=plan.feather,distances=new Float32Array(plan.width);distances.fill(r*r);const cy=y+.5;
 for(const{a,b}of plan.edges){
  if(cy<Math.min(a[1],b[1])-r||cy>Math.max(a[1],b[1])+r)continue;
  let low=0,high=1;const dy=b[1]-a[1],dx=b[0]-a[0];
  if(Math.abs(dy)>EPS){const t0=(cy-r-a[1])/dy,t1=(cy+r-a[1])/dy;low=Math.max(0,Math.min(t0,t1));high=Math.min(1,Math.max(t0,t1));if(low>high)continue;}
  const x0=a[0]+dx*low,x1=a[0]+dx*high,start=Math.max(0,Math.floor(Math.min(x0,x1)-r-.5)),end=Math.min(plan.width,Math.ceil(Math.max(x0,x1)+r+.5));
  for(let x=start;x<end;x++)distances[x]=Math.min(distances[x],distanceSquared(x+.5,cy,a,b));
 }
 return distances;
}
function renderRows(input,plan,output,stats,first,last){
 for(let y=first;y<last;y++){
  const coverage=coverageRow(plan,y),distance=plan.feather>0?featherRow(plan,y):null;
  for(let x=0;x<plan.width;x++){
   const at=(y*plan.width+x)*4,alpha=input[at+3];if(!alpha)continue;stats.visible++;
   const mask=Math.max(0,Math.min(1,coverage[x]))*(distance?Math.min(1,Math.sqrt(distance[x])/plan.feather):1),next=Math.round(alpha*mask);output[at+3]=next;
   if(next){output[at]=input[at];output[at+1]=input[at+1];output[at+2]=input[at+2];stats.kept++;if(next<alpha)stats.softened++;}else stats.removed++;
   stats.removedAlpha+=alpha-next;
  }
 }
}
function pixels(input,width,height){if(!(input instanceof Uint8Array||input instanceof Uint8ClampedArray)||input.length!==width*height*4)throw Error('The source photo pixels are incomplete.');}
function finish(plan,data,stats){if(!stats.visible||!stats.kept)throw Error('Keep some visible photo inside the outline.');if(!stats.removedAlpha)throw Error('This outline keeps every visible pixel. Move an edge inward or add a hole.');return{data,width:plan.width,height:plan.height,selection:{outer:plan.rings[0],holes:plan.rings.slice(1)},feather:plan.feather,...stats};}
export function outlineCutoutPixels(input,width,height,selection,feather=0){const plan=outlineCutoutPlan(width,height,selection,feather);pixels(input,width,height);const data=new Uint8ClampedArray(input.length),stats={visible:0,kept:0,removed:0,softened:0,removedAlpha:0};renderRows(input,plan,data,stats,0,height);return finish(plan,data,stats);}

/** Yields between bounded row chunks; the supplied guard owns source/document lifetime. */
export async function outlineCutoutPixelsAsync(input,width,height,selection,feather=0,requireCurrent=()=>{},onProgress=()=>{},yieldTurn=()=>new Promise(resolve=>setTimeout(resolve,0))){
 requireCurrent();const plan=outlineCutoutPlan(width,height,selection,feather);pixels(input,width,height);const data=new Uint8ClampedArray(input.length),stats={visible:0,kept:0,removed:0,softened:0,removedAlpha:0};
 for(let row=0;row<height;row+=16){requireCurrent();renderRows(input,plan,data,stats,row,Math.min(height,row+16));onProgress(Math.min(1,(row+16)/height));await yieldTurn();}
 requireCurrent();return finish(plan,data,stats);
}
