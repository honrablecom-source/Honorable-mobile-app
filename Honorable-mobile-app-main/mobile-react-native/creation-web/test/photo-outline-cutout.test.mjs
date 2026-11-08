import test from 'node:test';import assert from 'node:assert/strict';
import {outlineCutoutPlan,outlineCutoutPixels,outlineCutoutPixelsAsync} from '../photo-outline-cutout.mjs';
const box=(x,y,w,h)=>[[x,y],[x+w,y],[x+w,y+h],[x,y+h]];
const photo=(w,h,alpha=255)=>{const a=new Uint8ClampedArray(w*h*4);for(let y=0;y<h;y++)for(let x=0;x<w;x++){const p=(y*w+x)*4;a[p]=37+x;a[p+1]=61+y;a[p+2]=113;a[p+3]=alpha;}return a;};
const at=(out,x,y)=>Array.from(out.data.slice((y*out.width+x)*4,(y*out.width+x)*4+4));
test('manual outer outline and hole retain exact interior RGBA and zero all removed channels',()=>{const input=photo(8,8),before=new Uint8ClampedArray(input),selection={outer:box(1,1,6,6),holes:[box(3,3,2,2)]},original=JSON.stringify(selection),out=outlineCutoutPixels(input,8,8,selection);assert.equal(out.kept,32);assert.equal(out.removed,32);assert.deepEqual(at(out,1,1),[38,62,113,255]);assert.deepEqual(at(out,0,0),[0,0,0,0]);assert.deepEqual(at(out,3,3),[0,0,0,0]);assert.deepEqual(input,before);assert.equal(JSON.stringify(selection),original);});
test('ring direction and explicitly repeated closure do not change selection coverage',()=>{const input=photo(12,12),outer=box(1,1,10,10),hole=box(4,4,3,3),a=outlineCutoutPixels(input,12,12,{outer,holes:[hole]});for(const selection of [{outer:[...outer].reverse(),holes:[[...hole].reverse()]},{outer:[...outer,outer[0]],holes:[[...hole,hole[0]]]}])assert.deepEqual(outlineCutoutPixels(input,12,12,selection).data,a.data);});
test('concave outlines preserve the chosen subject rather than its bounding rectangle',()=>{const out=outlineCutoutPixels(photo(8,8),8,8,{outer:[[1,1],[7,1],[7,3],[3,3],[3,7],[1,7]]});assert.equal(out.kept,20);assert.deepEqual(at(out,4,4),[0,0,0,0]);assert.equal(at(out,2,5)[3],255);});
test('diagonal boundaries use partial alpha with no interior seams',()=>{const out=outlineCutoutPixels(photo(8,8),8,8,{outer:[[0,0],[8,0],[0,8]]});assert.equal(at(out,7,0)[3],128);assert.equal(at(out,0,7)[3],128);assert.equal(at(out,2,2)[3],255);assert.equal(at(out,7,7)[3],0);let alpha=0;for(let i=3;i<out.data.length;i+=4)alpha+=out.data[i];assert(Math.abs(alpha/255-32)<.02);});
test('existing partial alpha multiplies coverage and preserves visible RGB',()=>{const out=outlineCutoutPixels(photo(8,8,128),8,8,{outer:[[0,0],[8,0],[0,8]]});assert.deepEqual(at(out,7,0),[44,61,113,64]);assert.deepEqual(at(out,1,1),[38,62,113,128]);});
test('inward feathering softens both outer and hole boundaries without revealing excluded pixels',()=>{const out=outlineCutoutPixels(photo(12,12),12,12,{outer:box(1,1,10,10),holes:[box(5,5,2,2)]},2);assert.equal(at(out,1,4)[3],64);assert.equal(at(out,4,5)[3],64);assert.equal(at(out,3,3)[3],255);assert.deepEqual(at(out,5,5),[0,0,0,0]);assert.deepEqual(at(out,0,4),[0,0,0,0]);assert(out.softened>0);});
test('multiple independent holes are removed while the solid connecting area remains',()=>{const out=outlineCutoutPixels(photo(16,16),16,16,{outer:box(1,1,14,14),holes:[box(3,3,3,3),box(9,9,3,3)]});assert.equal(out.kept,196-18);assert.equal(at(out,4,4)[3],0);assert.equal(at(out,10,10)[3],0);assert.equal(at(out,8,8)[3],255);});
test('crossed, retraced, touching, outside and tiny outlines refuse without mutation',()=>{const input=photo(16,16),before=new Uint8ClampedArray(input);for(const outer of [[[1,1],[12,12],[1,12],[12,1]],[[1,1],[12,1],[6,1],[6,12],[1,12]],[[1,1],[1,1],[12,12]],[[0,0],[17,0],[0,10]],[[1,1],[1.1,1],[1,1.1]]])assert.throws(()=>outlineCutoutPixels(input,16,16,{outer}));assert.deepEqual(input,before);});
test('holes cannot touch/cross the outer boundary or overlap/nest each other',()=>{const outer=box(1,1,14,14);for(const holes of [[box(0,4,3,3)],[box(1,4,3,3)],[box(3,3,5,5),box(6,6,5,5)],[box(3,3,8,8),box(5,5,2,2)]])assert.throws(()=>outlineCutoutPlan(16,16,{outer,holes}));});
test('empty, already-transparent, unchanged and out-of-bound photo requests refuse',()=>{assert.throws(()=>outlineCutoutPixels(photo(8,8),8,8,{outer:box(0,0,8,8)}),/every visible/);assert.throws(()=>outlineCutoutPixels(photo(8,8,0),8,8,{outer:box(1,1,6,6)}),/visible photo/);assert.throws(()=>outlineCutoutPixels(photo(8,8),8,8,{outer:box(0,0,1,1),holes:[box(.1,.1,.8,.8)]}));for(const patch of [NaN,-1,25])assert.throws(()=>outlineCutoutPlan(8,8,{outer:box(1,1,6,6)},patch));assert.throws(()=>outlineCutoutPlan(7,8,{outer:box(1,1,3,3)}));assert.throws(()=>outlineCutoutPixels([],8,8,{outer:box(1,1,6,6)}));});
test('outline point/ring budgets are checked before rasterization',()=>{assert.throws(()=>outlineCutoutPlan(16,16,{outer:box(1,1,14,14),holes:Array.from({length:8},()=>box(3,3,2,2))}));const ring=(n,r,cx,cy)=>Array.from({length:n},(_,i)=>[cx+Math.cos(i*Math.PI*2/n)*r,cy+Math.sin(i*Math.PI*2/n)*r]);assert.throws(()=>outlineCutoutPlan(128,128,{outer:ring(128,60,64,64),holes:[ring(128,15,64,64),ring(32,3,90,90)]}),/256/);});
test('chunked processing matches the pure kernel and yields progress',async()=>{const input=photo(32,32),selection={outer:box(1,1,30,30),holes:[box(12,12,5,5)]};let yields=0;const progress=[];const actual=await outlineCutoutPixelsAsync(input,32,32,selection,2,()=>{},n=>progress.push(n),async()=>{yields++;});assert.deepEqual(actual.data,outlineCutoutPixels(input,32,32,selection,2).data);assert.equal(yields,2);assert.equal(progress.at(-1),1);});
test('changed source during chunked processing rejects without publishing or changing input',async()=>{const input=photo(32,32),before=new Uint8ClampedArray(input);let current=true;await assert.rejects(outlineCutoutPixelsAsync(input,32,32,{outer:box(1,1,30,30)},0,()=>{if(!current)throw Error('Changed source');},()=>{},async()=>{current=false;}),/Changed source/);assert.deepEqual(input,before);});

// Independent dense point sampling: no scanline intersections or renderer helpers.
function referenceInside(x,y,ring){let winding=0;for(let i=0;i<ring.length;i++){const a=ring[i],b=ring[(i+1)%ring.length],side=(b[0]-a[0])*(y-a[1])-(x-a[0])*(b[1]-a[1]);if(a[1]<=y&&b[1]>y&&side>0)winding++;if(a[1]>y&&b[1]<=y&&side<0)winding--;}return winding!==0;}
test('subpixel concave polygons agree with independent dense winding samples',()=>{
 for(let trial=0;trial<12;trial++){
  const n=7+trial%5,outer=Array.from({length:n},(_,i)=>{const angle=i*Math.PI*2/n+.037*trial,r=i%2?4.1:6.7;return[8+Math.cos(angle)*r,8+Math.sin(angle)*r];}),hole=box(7.15,7.23,1.4,1.3),out=outlineCutoutPixels(photo(16,16),16,16,{outer,holes:[hole]});
  for(let y=0;y<16;y++)for(let x=0;x<16;x++){
   let kept=0;for(const dy of [.125,.375,.625,.875])for(let sx=0;sx<256;sx++){const px=x+(sx+.5)/256,py=y+dy;if(referenceInside(px,py,outer)&&!referenceInside(px,py,hole))kept++;}
   assert(Math.abs(at(out,x,y)[3]-Math.round(255*kept/1024))<=1,`trial ${trial}, pixel ${x},${y}`);
  }
 }
});
test('maximum photo size yields every 16 rows and leaves the source intact',async()=>{
 const input=photo(1024,1024),before=new Uint8ClampedArray(input);let yields=0;
 const out=await outlineCutoutPixelsAsync(input,1024,1024,{outer:box(1,1,1022,1022),holes:[box(400,400,100,100)]},24,()=>{},()=>{},async()=>{yields++;});
 assert.equal(yields,64);assert.equal(out.data.length,input.length);assert.deepEqual(input,before);assert.equal(at(out,450,450)[3],0);assert.equal(at(out,200,200)[3],255);assert.equal(at(out,0,0)[3],0);
});
test('selection edits after processing starts cannot change the captured mask',async()=>{
 const input=photo(32,32),selection={outer:box(1,1,30,30),holes:[box(12,12,5,5)]},expected=outlineCutoutPixels(input,32,32,selection,1);let changed=false;
 const out=await outlineCutoutPixelsAsync(input,32,32,selection,1,()=>{},()=>{},async()=>{if(!changed){selection.outer[0][0]=25;selection.holes.length=0;changed=true;}});
 assert.deepEqual(out.data,expected.data);assert.deepEqual(out.selection,expected.selection);
});
test('cancel before work or on the last yield never publishes a result',async()=>{
 const input=photo(16,16),before=new Uint8ClampedArray(input),selection={outer:box(1,1,14,14)};let yields=0;
 await assert.rejects(outlineCutoutPixelsAsync(input,16,16,selection,0,()=>{throw Error('Closed');},()=>{},async()=>{yields++;}),/Closed/);assert.equal(yields,0);
 let live=true;await assert.rejects(outlineCutoutPixelsAsync(input,16,16,selection,0,()=>{if(!live)throw Error('Closed');},()=>{},async()=>{live=false;yields++;}),/Closed/);assert.equal(yields,1);assert.deepEqual(input,before);
});
