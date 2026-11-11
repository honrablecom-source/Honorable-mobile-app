import test from 'node:test';
import assert from 'node:assert/strict';
import{visionPixels,visionPixelsAsync,visionSettings,visionSheetPlan,VISION_MODELS}from'../photo-vision.mjs';
function solid(rgba,w=8,h=8){return Uint8ClampedArray.from({length:w*h*4},(_,i)=>rgba[i%4]);}
test('primary matrix columns produce independently calculated encoded linear unit responses',()=>{
 const encode=x=>Math.round(255*(Math.max(0,Math.min(1,x))<=.0031308?12.92*Math.max(0,Math.min(1,x)):1.055*Math.max(0,Math.min(1,x))**(1/2.4)-.055));
 const columns=[[[.152286,.114503,-.003882],[1.052583,.786281,-.048116],[-.204868,.099216,1.051998]],[[.367322,.280085,-.011820],[.860646,.672501,.042940],[-.227968,.047413,.968881]],[[1.255528,-.078411,.004733],[-.076749,.930809,.691367],[-.178779,.147602,.303900]]];
 for(let axis=0;axis<3;axis++){const rgb=[0,0,0];rgb[axis]=255;const result=visionPixels(solid([...rgb,255]),8,8);for(let m=0;m<3;m++)assert.deepEqual([...result.views[m+1].data.slice(0,4)],[...columns[m][axis].map(encode),255]);}
});
test('every neutral tone stays neutral; alpha matte is rounded before simulation',()=>{
 for(let c=0;c<256;c++)for(const view of visionPixels(solid([c,c,c,255]),8,8).views)assert.deepEqual([...view.data.slice(0,4)],[c,c,c,255]);
 const a=visionPixels(solid([255,0,80,0]),8,8,'#123456'),b=visionPixels(solid([1,250,0,0]),8,8,'#123456');assert.deepEqual(a,b);
 assert.deepEqual([...visionPixels(solid([200,100,0,128]),8,8,'#000000').views[0].data.slice(0,4)],[100,50,0,255]);
});
test('async privately captures input, reports progress and cancels after the final yield',async()=>{
 const source=solid([70,110,180,120],8,65),expected=visionPixels(source,8,65,'#203040'),progress=[];
 const actual=await visionPixelsAsync(source,8,65,'#203040',()=>{},p=>progress.push(p),async()=>source.fill(0));assert.deepEqual(actual,expected);assert.deepEqual(progress,[32/65,64/65,1]);
 let stale=false;await assert.rejects(visionPixelsAsync(source,8,65,'#ffffff',()=>{if(stale)throw Error('stale');},()=>{},async()=>{stale=true;}),/stale/);
 let final=false;await assert.rejects(visionPixelsAsync(source,8,65,'#ffffff',()=>{if(final)throw Error('final');},p=>{if(p===1)final=true;},async()=>{}),/final/);
});
test('rejects invalid grids, incomplete pixels, invalid mattes and immutable model changes',()=>{
 for(const dimensions of [[7,8],[8,1025],[8.5,8]])assert.throws(()=>visionSettings(...dimensions));
 assert.throws(()=>visionPixels(new Uint8Array(8),8,8));assert.throws(()=>visionSettings(8,8,'#fff'));
 assert.throws(()=>{VISION_MODELS[1].matrix[0]=0;},TypeError);
});

test('four-view layout preserves grid, metadata and duplicate neutral PNG identities',()=>{
 const ids=Array(4).fill('a'.repeat(64)),plan=visionSheetPlan(80,40,'#ABCDEF',ids,10,20);assert.equal(plan.length,8);
 const images=plan.filter(p=>p.type==='image');assert.equal(images.length,4);assert.deepEqual(images.map(p=>p.customData.nobleVision.model),['normal','protan','deutan','tritan']);
 for(const image of images){assert.equal(image.width/image.height,2);assert.equal(image.fileId,ids[0]);assert.equal(image.customData.nobleVision.backdrop,'#abcdef');assert.equal(image.opacity,100);}
 assert.throws(()=>visionSheetPlan(80,40,'#ffffff',ids.slice(1)));assert.throws(()=>visionSheetPlan(80,40,'#ffffff',ids,NaN));
});

test('maximum grid retains all four complete opaque planes without resizing',()=>{
 const source=solid([0,0,0,0],1024,1024),result=visionPixels(source,1024,1024,'#808080');assert.equal(result.width,1024);assert.equal(result.height,1024);
 for(const view of result.views){assert.equal(view.data.length,4194304);for(let p=0;p<view.data.length;p++)assert.equal(view.data[p],[128,128,128,255][p%4]);}assert.equal(source[3],0);
});
