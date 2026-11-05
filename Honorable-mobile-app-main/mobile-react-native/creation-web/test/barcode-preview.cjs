const { withProjectView } = require('./project-navigation.cjs');
// Actual local renderer/export, synthetic content only. No native/APK build.
const {chromium}=require('../../../ui-previews/node_modules/playwright');
const zxing=require('@zxing/library');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const output=path.resolve(__dirname,'../../../../.context/noble-fast-barcode/browser');
fs.mkdirSync(output,{recursive:true});
const url=process.env.NOBLE_PREVIEW_URL||'http://127.0.0.1:4182/drawing/';
const origin=new URL(url).origin;
const fixture={id:'original',type:'rectangle',x:-40,y:-40,width:30,height:30,angle:0,opacity:100,strokeColor:'#111111',backgroundColor:'#ddd',fillStyle:'solid',strokeWidth:1,strokeStyle:'solid',roughness:0,seed:1,version:1,versionNonce:1,isDeleted:false,groupIds:[],boundElements:null,link:null,locked:false};
(async()=>{const browser=await chromium.launch({args:['--no-sandbox']});try{
 const page=await browser.newPage({viewport:{width:390,height:844}}),errors=[],external=[];
 page.on('pageerror',e=>errors.push(e.message));page.on('console',m=>{if(m.type()==='error'&&m.text().includes('Content Security Policy'))errors.push(m.text());});
 await page.route('**/*',route=>{const target=route.request().url();if(!target.startsWith(origin)&&!target.startsWith('data:')&&!target.startsWith('blob:')){external.push(target);return route.abort();}return route.continue();});
 await page.addInitScript(({fixture})=>{if(!localStorage.getItem('honorable-drawing-v1'))localStorage.setItem('honorable-drawing-v1',JSON.stringify({version:1,elements:[fixture],appState:{viewBackgroundColor:'#ffffff',currentItemFontFamily:2}}));window.HonorableDrawing={usePng:png=>{window.__png=png;window.honorableExportResult('Synthetic barcode PNG ready');}};},{fixture});
 await page.goto(url);await page.getByRole('button',{name:'Layouts',exact:true}).waitFor();
 const save=async()=>{await page.getByRole('button',{name:'Save draft',exact:true}).click();return page.evaluate(()=>JSON.parse(localStorage.getItem('honorable-drawing-v1')));};
 const before=await save();await page.getByRole('button',{name:'Layouts',exact:true}).click();
 assert.equal(await page.getByLabel('Barcode text',{exact:true}).count(),0);
 await page.getByLabel('Chart title',{exact:true}).fill('Retained chart');
 await page.getByRole('button',{name:'Barcode label',exact:true}).click();
 assert.equal(await page.getByLabel('Chart title',{exact:true}).count(),0);
 await page.getByRole('button',{name:'Insert editable barcode',exact:true}).click();assert((await page.getByRole('status').innerText()).includes('Enter the text'));assert.equal((await save()).elements.length,1);
 await page.getByLabel('Barcode text',{exact:true}).fill('🙂'.repeat(25));await page.getByRole('button',{name:'Insert editable barcode',exact:true}).click();assert((await page.getByRole('status').innerText()).includes('80 printable'));assert.equal((await save()).elements.length,1);
 const content='NOBLE-ITEM-042';
 await page.getByLabel('Barcode text',{exact:true}).fill(content);await page.getByLabel('Barcode caption',{exact:true}).fill('Find my work');
 await page.getByRole('button',{name:'Data chart',exact:true}).click();assert.equal(await page.getByLabel('Chart title',{exact:true}).inputValue(),'Retained chart');
 await page.getByRole('button',{name:'Barcode label',exact:true}).click();assert.equal(await page.getByLabel('Barcode text',{exact:true}).inputValue(),content);
 assert.equal(await page.getByRole('button',{name:'Barcode label',exact:true}).getAttribute('aria-pressed'),'true');
 for(const width of [320,390,844]){await page.setViewportSize({width,height:844});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));}
 await page.setViewportSize({width:390,height:844});await page.screenshot({path:path.join(output,'controls-390.png')});
 await page.getByRole('button',{name:'Insert editable barcode',exact:true}).click();let saved=await save();const created=saved.elements.filter(e=>e.id!=='original');assert(created.length>20);assert(created.length<500);const group=created[0].groupIds[0];assert(group&&created.every(e=>e.groupIds.length===1&&e.groupIds[0]===group));
 const original=saved.elements.find(e=>e.id==='original');for(const key of ['x','y','width','height','backgroundColor','opacity'])assert.equal(original[key],before.elements[0][key]);
 const count=saved.elements.length;await page.getByTestId('button-undo').click();assert.equal((await save()).elements.length,1);await page.getByTestId('button-redo').click();assert.equal((await save()).elements.length,count);
 await page.getByRole('button',{name:'Layouts',exact:true}).click();await page.getByRole('button',{name:'Barcode label',exact:true}).click();await page.getByLabel('Barcode text',{exact:true}).fill('SECOND-42');await page.getByRole('button',{name:'Insert editable barcode',exact:true}).click();
 const second=await save();const firstIds=new Set(saved.elements.map(e=>e.id));const newcomers=second.elements.filter(e=>!firstIds.has(e.id));assert(newcomers.length>20);assert(newcomers.every(e=>e.groupIds[0]&&e.groupIds[0]!==group));
 await page.getByTestId('button-undo').click();assert.equal((await save()).elements.length,count);
 await page.reload();assert.equal((await save()).elements.length,count);
 await page.screenshot({path:path.join(output,'artwork-390.png')});
 await page.getByRole('button',{name:'Projects',exact:true}).click();await withProjectView(page, 'advanced', () => page.getByRole('button',{name:'Export project JSON',exact:true}).click());const portable=JSON.parse(await withProjectView(page, 'advanced', () => page.getByLabel('Project JSON',{exact:true}).inputValue()));assert.equal(JSON.parse(portable.scene).elements.length,count);
 await withProjectView(page, 'advanced', () => page.getByRole('button',{name:'Prepare vector SVG',exact:true}).click());const svg=await withProjectView(page, 'advanced', () => page.getByLabel('Vector SVG',{exact:true}).inputValue());fs.writeFileSync(path.join(output,'barcode.svg'),svg);
 await page.getByRole('button',{name:'Close panel',exact:true}).click();await page.getByRole('button',{name:'Use image',exact:true}).click();await page.waitForFunction(()=>window.__png?.startsWith('data:image/png;base64,'));const png=await page.evaluate(()=>window.__png);fs.writeFileSync(path.join(output,'barcode.png'),Buffer.from(png.split(',')[1],'base64'));
 const review=[];
 for(const [kind,source]of [['png',png],['svg',svg]]){
  const pixels=await page.evaluate(async({kind,source})=>{const image=new Image();let objectURL;const src=kind==='svg'?(objectURL=URL.createObjectURL(new Blob([source],{type:'image/svg+xml'}))):source;await new Promise((resolve,reject)=>{image.onload=resolve;image.onerror=()=>reject(Error('Image could not decode'));image.src=src;});const canvas=document.createElement('canvas');canvas.width=image.naturalWidth;canvas.height=image.naturalHeight;const context=canvas.getContext('2d');context.fillStyle='#ffffff';context.fillRect(0,0,canvas.width,canvas.height);context.drawImage(image,0,0);const data=Array.from(context.getImageData(0,0,canvas.width,canvas.height).data);if(objectURL)URL.revokeObjectURL(objectURL);return{width:canvas.width,height:canvas.height,data};},{kind,source});
  const gray=new Uint8ClampedArray(pixels.width*pixels.height);for(let i=0;i<gray.length;i++)gray[i]=(pixels.data[i*4]+2*pixels.data[i*4+1]+pixels.data[i*4+2])/4;
  const bitmap=new zxing.BinaryBitmap(new zxing.HybridBinarizer(new zxing.RGBLuminanceSource(gray,pixels.width,pixels.height)));
  const decoded=new zxing.Code128Reader().decode(bitmap,new Map([[zxing.DecodeHintType.TRY_HARDER,true]]));assert.equal(decoded.getText(),content);review.push({format:kind,width:pixels.width,height:pixels.height,decodedExactly:true});
 }
 fs.writeFileSync(path.join(output,'decode-review.json'),JSON.stringify(review,null,2));
 const license=await page.request.get(new URL('THIRD_PARTY/jsbarcode-MIT-LICENSE.txt',url).href);assert.equal(license.status(),200);assert((await license.text()).includes('Johan Lindell'));
 assert.deepEqual(errors,[]);assert.deepEqual(external,[]);
 console.log('PASS: Code128 selected form/phone bounds,invalid input preserves original,grouped editable insertion,Undo/Redo,reload/projectJSON,actualPNG+SVG independentZXingdecode,bundledMITnotice,noexternal/CSPerrors. Browser only,no Android/APK/device.');
}finally{await browser.close();}})().catch(e=>{console.error(e);process.exitCode=1;});
