const INK='#111111';
export const NET_UNITS_PER_MM=4;
export function boxNetOptions(input){
 if(!input||![input.width,input.height,input.depth].every(n=>Number.isFinite(n)&&n>=10&&n<=200&&Number.isInteger(n*10))||!Number.isFinite(input.tab)||input.tab<2||input.tab>20||!Number.isInteger(input.tab*10)||input.tab>Math.min(input.width,input.height,input.depth)/3)throw Error('Use dimensions 10–200 mm and tabs 2–20 mm, in 0.1 mm steps. Tabs must be at most one third of the smallest dimension.');
 if(typeof input.title!=='string'||!input.title.trim()||Array.from(input.title).length>48||/[\x00-\x1f\x7f\u200b-\u200f\u202a-\u202e\u2060-\u206f]/.test(input.title))throw Error('Use a title of 1–48 visible characters on one line.');
 return{title:input.title.trim(),width:input.width,height:input.height,depth:input.depth,tab:input.tab};
}
const round=n=>Math.round(n*1e8)/1e8,point=p=>p.map(round),key=p=>point(p).join(','),edgeKey=(a,b)=>[key(a),key(b)].sort().join('|');
/** Flat zero-thickness six-face net. Polygon edges shared twice fold; edges shared once cut. */
export function boxNetGeometry(input){
 const options=boxNetOptions(input),{width:w,height:h,depth:d,tab:t}=options,panels=[],tabs=[];
 const rect=(id,x,y,width,height)=>({id,kind:'face',width,height,points:[[x,y],[x+width,y],[x+width,y+height],[x,y+height]].map(point)});
 panels.push(rect('A',0,0,w,h),rect('B',w,0,d,h),rect('C',w+d,0,w,h),rect('D',2*w+d,0,d,h),rect('E',0,-d,w,d),rect('F',0,h,w,d));
 const flap=(face,index)=>{const a=face.points[index],b=face.points[(index+1)%4],dx=b[0]-a[0],dy=b[1]-a[1],length=Math.hypot(dx,dy),u=[dx/length,dy/length],n=[u[1],-u[0]],bevel=t/2;tabs.push({id:'T'+(tabs.length+1),kind:'tab',face:face.id,points:[b,a,[a[0]+n[0]*t+u[0]*bevel,a[1]+n[1]*t+u[1]*bevel],[b[0]+n[0]*t-u[0]*bevel,b[1]+n[1]*t-u[1]*bevel]].map(point)});};
 flap(panels[3],1);for(const i of[0,1,3])flap(panels[4],i);for(const i of[1,2,3])flap(panels[5],i);
 const edges=new Map();for(const poly of[...panels,...tabs])for(let i=0;i<poly.points.length;i++){const a=poly.points[i],b=poly.points[(i+1)%poly.points.length],k=edgeKey(a,b);if(!edges.has(k))edges.set(k,{a:[...a],b:[...b],owners:[]});edges.get(k).owners.push(poly.id);}
 if([...edges.values()].some(e=>e.owners.length>2))throw Error('The box net topology is inconsistent.');
 const cuts=[],folds=[];for(const e of edges.values())(e.owners.length===1?cuts:folds).push(e);
 const vertices=[...panels,...tabs].flatMap(p=>p.points),bounds={left:Math.min(...vertices.map(p=>p[0])),top:Math.min(...vertices.map(p=>p[1])),right:Math.max(...vertices.map(p=>p[0])),bottom:Math.max(...vertices.map(p=>p[1]))};return{options,panels,tabs,cuts,folds,bounds};
}
const wrap=(s,n)=>{const a=Array.from(s),lines=[];while(a.length)lines.push(a.splice(0,n).join(''));return lines.join('\n');};
const text=(s,x,y,fontSize)=>({type:'text',text:s,x,y,fontSize,fontFamily:2,strokeColor:INK,opacity:100,angle:0});
export function boxNetArtwork(input){const geometry=boxNetGeometry(input),{options,bounds}=geometry,scale=NET_UNITS_PER_MM,title=wrap(options.title,20),headerHeight=title.split('\n').length*30+68,offset=[16-bounds.left*scale,headerHeight-bounds.top*scale],map=p=>p.map((v,i)=>round(v*scale+offset[i])),plan=[text(title,16,0,24),text('Solid: cut    Dashed gray: fold',16,title.split('\n').length*30,14),text('Glue tabs: seven beveled flaps',16,title.split('\n').length*30+22,14)];
 for(const kind of['cuts','folds'])for(const edge of geometry[kind]){const a=map(edge.a),b=map(edge.b);plan.push({type:'line',x:a[0],y:a[1],width:Math.abs(b[0]-a[0]),height:Math.abs(b[1]-a[1]),points:[[0,0],[round(b[0]-a[0]),round(b[1]-a[1])]],strokeColor:kind==='cuts'?INK:'#777777',strokeWidth:1,strokeStyle:kind==='cuts'?'solid':'dashed',roughness:0,opacity:100,startArrowhead:null,endArrowhead:null,customData:{nobleNetEdge:kind==='cuts'?'cut':'fold',nobleNetOwners:[...edge.owners]}});}
 for(const p of geometry.panels){const [x,y]=map([p.points[0][0]+p.width/2,p.points[0][1]+p.height/2]);plan.push({...text(p.id,x-6,y-10,16),width:12,height:20,lineHeight:1.25});}
 const footer=map([bounds.left,bounds.bottom])[1]+24,details=`A,C: ${options.width} × ${options.height} mm\nB,D: ${options.depth} × ${options.height} mm\nE,F: ${options.width} × ${options.depth} mm\nTab depth: ${options.tab} mm`,note='Nominal zero-thickness geometry.\nNo material or kerf allowance.\nCheck scale using labeled dimensions\nbefore cutting; printer/export sizing\ncan change physical size.';
 plan.push(text(details,16,footer,16),text(note,16,footer+96,14));return{options,geometry,plan,width:Math.max(512,round((bounds.right-bounds.left)*scale+32)),height:footer+192,scale,offset};}
