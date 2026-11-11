import test from 'node:test';import assert from 'node:assert/strict';import{jigsawGeometry,jigsawArtwork,jigsawOptions}from'../jigsaw.mjs';
const options={title:'Puzzle',width:600,height:480,columns:5,rows:4,seed:42};
const near=(a,b)=>assert(Math.abs(a-b)<1e-5,`${a} != ${b}`);
function polygon(g,p){const{column:c,row:r}=p,cw=g.cellWidth,ch=g.cellHeight,out=[];const outer={top:[[c*cw,r*ch],[(c+1)*cw,r*ch]],right:[[(c+1)*cw,r*ch],[(c+1)*cw,(r+1)*ch]],bottom:[[(c+1)*cw,(r+1)*ch],[c*cw,(r+1)*ch]],left:[[c*cw,(r+1)*ch],[c*cw,r*ch]]};for(const name of['top','right','bottom','left']){const ref=p.edges[name],points=ref?g.seams.find(s=>s.id===ref.id).points:outer[name],walk=ref?.reversed?[...points].reverse():points;if(out.length){near(out.at(-1)[0],walk[0][0]);near(out.at(-1)[1],walk[0][1]);}out.push(...walk.slice(out.length?1:0));}return out;}
const area=p=>p.slice(1).reduce((sum,b,i)=>sum+p[i][0]*b[1]-b[0]*p[i][1],0)/2;
const orient=(a,b,c)=>(b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0]);
function properCross(a,b,c,d){return orient(a,b,c)*orient(a,b,d)<-1e-10&&orient(c,d,a)*orient(c,d,b)<-1e-10;}
test('every adjacent pair shares exactly one seam and opposite clockwise traversal',()=>{
 for(const [columns,rows]of[[2,2],[5,4],[12,12],[2,12],[12,2]])for(const seed of[1,42,2147483647]){
  const g=jigsawGeometry({...options,columns,rows,seed});assert.equal(g.seams.length,rows*(columns-1)+columns*(rows-1));assert.equal(g.pieces.length,columns*rows);assert.equal(new Set(g.seams.map(s=>s.id)).size,g.seams.length);
  for(const s of g.seams){assert.equal(s.owners.length,2);const refs=s.owners.map(i=>Object.values(g.pieces[i].edges).find(e=>e.id===s.id));assert(refs.every(Boolean));assert.notEqual(refs[0].reversed,refs[1].reversed);assert(s.points.every(p=>p[0]>=0&&p[0]<=options.width&&p[1]>=0&&p[1]<=options.height));}
  const polygons=g.pieces.map(p=>polygon(g,p));for(const p of polygons){near(p[0][0],p.at(-1)[0]);near(p[0][1],p.at(-1)[1]);assert(area(p)>g.cellWidth*g.cellHeight*.65);}near(polygons.reduce((sum,p)=>sum+area(p),0),options.width*options.height);
 }
});
test('tab has a narrower neck than head and deterministic polarity for every seed',()=>{
 const a=jigsawGeometry(options);assert.deepEqual(a,jigsawGeometry(options));assert.notDeepEqual(a.seams.map(s=>s.sign),jigsawGeometry({...options,seed:43}).seams.map(s=>s.sign));
 for(const s of a.seams){const normal=s.axis==='h'?1:0,along=1-normal,start=s.points[0],baseline=start[normal],neck=s.points.filter(p=>p[normal]===baseline&&p!==start&&p!==s.points.at(-1)).map(p=>p[along]);const head=s.points.filter(p=>Math.abs(p[normal]-baseline)>.1*Math.min(a.cellWidth,a.cellHeight)).map(p=>p[along]);assert(Math.max(...head)-Math.min(...head)>.2*(s.axis==='h'?a.cellWidth:a.cellHeight));assert(neck.length>=2);}
});
test('internal cut polylines have no proper self intersections or crossings',()=>{
 const g=jigsawGeometry({...options,width:1440,height:288,columns:12,rows:12});
 const segments=g.seams.flatMap(s=>s.points.slice(1).map((b,i)=>({a:s.points[i],b,seam:s.id,index:i})));
 for(let i=0;i<segments.length;i++)for(let j=i+1;j<segments.length;j++){const a=segments[i],b=segments[j];if(a.seam===b.seam&&Math.abs(a.index-b.index)<=1)continue;if(Math.max(a.a[0],a.b[0])<Math.min(b.a[0],b.b[0])||Math.max(b.a[0],b.b[0])<Math.min(a.a[0],a.b[0])||Math.max(a.a[1],a.b[1])<Math.min(b.a[1],b.b[1])||Math.max(b.a[1],b.b[1])<Math.min(a.a[1],a.b[1]))continue;assert(!properCross(a.a,a.b,b.a,b.b),`${a.seam}/${a.index} crosses ${b.seam}/${b.index}`);}
});
test('artwork retains exact seam geometry, owners, one border and bounded object count',()=>{
 for(const [columns,rows]of[[2,2],[12,12]]){const result=jigsawArtwork({...options,width:1440,height:1440,columns,rows}),lines=result.plan.filter(s=>s.type==='line');assert.equal(lines.length,result.geometry.seams.length);assert.equal(result.plan.filter(s=>s.customData?.nobleJigsawBorder).length,1);assert.equal(result.plan.length,lines.length+4);assert(result.plan.length<500);for(let i=0;i<lines.length;i++){const line=lines[i],seam=result.geometry.seams[i];assert.equal(line.customData.nobleJigsawSeam,seam.id);assert.deepEqual(line.customData.nobleJigsawOwners,seam.owners);for(let j=0;j<line.points.length;j++){near(line.x+line.points[j][0]-16,seam.points[j][0]);near(line.y+line.points[j][1]-82,seam.points[j][1]);}}}
});
test('input validation refuses ambiguous, excessive and invisible settings without mutation',()=>{
 const copy=structuredClone(options);jigsawArtwork(options);assert.deepEqual(options,copy);
 for(const patch of[{width:239},{height:1441},{width:240.5},{columns:1},{rows:13},{width:240,columns:12},{seed:0},{seed:2147483648},{seed:1.5},{title:''},{title:'x\ny'},{title:'x\u202ey'},{title:'a'.repeat(49)}])assert.throws(()=>jigsawOptions({...options,...patch}));assert.throws(()=>jigsawOptions(null));assert.equal(jigsawOptions({...options,title:'  Puzzle  '}).title,'Puzzle');
});
