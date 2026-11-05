const { withProjectView } = require('./project-navigation.cjs');
// Real local-editor regression with synthetic media only. Requires the drawing preview.
const {chromium}=require('../../../ui-previews/node_modules/playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const url=process.env.NOBLE_PREVIEW_URL||'http://127.0.0.1:4177/drawing/';
const origin=new URL(url).origin;
const output=path.resolve(__dirname,'../../../../.context/palette-runtime-review');
fs.mkdirSync(output,{recursive:true});
const shape=(id,x,color,opacity=100)=>({id,type:'rectangle',x,y:0,width:30,height:30,angle:0,opacity,strokeColor:'#111111',backgroundColor:color,fillStyle:'solid',strokeWidth:1,strokeStyle:'solid',roughness:0,seed:1,version:1,versionNonce:1,isDeleted:false,groupIds:[],boundElements:null,link:null,locked:false});
const fixtures=[shape('red-alpha',0,'#ff000080',50),shape('blue',60,'#0000ff'),{...shape('hidden',120,'#00ff00',0),customData:{nobleHidden:{opacity:100,locked:false}},locked:true}];
(async()=>{const browser=await chromium.launch({args:['--no-sandbox']});try{
const page=await browser.newPage({viewport:{width:390,height:844}}),errors=[],external=[];
page.on('pageerror',error=>errors.push(error.message));
page.on('console',message=>{if(message.type()==='error'&&message.text().includes('Content Security Policy'))errors.push(message.text());});
await page.route('**/*',route=>{const target=route.request().url();if(!target.startsWith(origin)&&!target.startsWith('blob:')&&!target.startsWith('data:')){external.push(target);return route.abort();}return route.continue();});
await page.addInitScript(({fixtures})=>{if(!localStorage.getItem('honorable-drawing-v1'))localStorage.setItem('honorable-drawing-v1',JSON.stringify({version:1,elements:fixtures,appState:{viewBackgroundColor:'#ffffff',currentItemFontFamily:2}}));window.HonorableDrawing={usePng:value=>{window.__png=value;window.honorableExportResult('Synthetic PNG ready');}};},{fixtures});
await page.goto(url);await page.getByRole('button',{name:'Layouts',exact:true}).waitFor();
const save=async()=>{await page.getByRole('button',{name:'Save draft',exact:true}).click();return page.evaluate(()=>JSON.parse(localStorage.getItem('honorable-drawing-v1')));};
const before=await save();assert.equal(before.elements.length,3);
await page.getByRole('button',{name:'Layouts',exact:true}).click();await page.getByRole('button',{name:'Artwork color key',exact:true}).click();await page.getByLabel('Color-key title',{exact:true}).fill('Palette review');
await page.getByRole('button',{name:'Insert editable color key',exact:true}).click();
const after=await save();assert.equal(after.elements.length,13,await page.getByRole('status').innerText());
const swatches=after.elements.filter(e=>e.type==='rectangle'&&!fixtures.some(f=>f.id===e.id));
assert.deepEqual(swatches.map(e=>e.backgroundColor).sort(),['#0000ff','#111111','#11111180','#ff000040'].sort());
for(const old of before.elements){const current=after.elements.find(e=>e.id===old.id);for(const field of ['x','y','width','height','strokeColor','backgroundColor','opacity','customData'])assert.deepEqual(current[field],old[field],`${old.id}.${field}`);}
assert(!after.elements.some(e=>e.text?.includes('#00ff00')));
assert.equal(new Set(after.elements.map(e=>e.id)).size,13);
await page.screenshot({path:path.join(output,'palette-390.png')});
await page.getByTestId('button-undo').click();assert.equal((await save()).elements.length,3);
await page.getByTestId('button-redo').click();assert.equal((await save()).elements.length,13);
await page.reload();assert.equal((await save()).elements.length,13);
await page.getByRole('button',{name:'Projects',exact:true}).click();await withProjectView(page, 'advanced', () => page.getByRole('button',{name:'Export project JSON',exact:true}).click());
const portable=JSON.parse(await withProjectView(page, 'advanced', () => page.getByLabel('Project JSON',{exact:true}).inputValue()));assert.equal(JSON.parse(portable.scene).elements.length,13);
await withProjectView(page, 'advanced', () => page.getByRole('button',{name:'Prepare vector SVG',exact:true}).click());const svg=await withProjectView(page, 'advanced', () => page.getByLabel('Vector SVG',{exact:true}).inputValue());assert(svg.includes('#ff000040'));assert(svg.includes('#11111180'));fs.writeFileSync(path.join(output,'palette.svg'),svg);
await page.getByRole('button',{name:'Close panel',exact:true}).click();await page.getByRole('button',{name:'Use image',exact:true}).click();await page.waitForFunction(()=>window.__png?.startsWith('data:image/png;base64,'));
const data=await page.evaluate(()=>window.__png);fs.writeFileSync(path.join(output,'palette.png'),Buffer.from(data.split(',')[1],'base64'));
const colors=await page.evaluate(async()=>{const image=await createImageBitmap(new Blob([Uint8Array.from(atob(window.__png.split(',')[1]),c=>c.charCodeAt(0))],{type:'image/png'}));const canvas=document.createElement('canvas');canvas.width=image.width;canvas.height=image.height;const ctx=canvas.getContext('2d');ctx.drawImage(image,0,0);const d=ctx.getImageData(0,0,canvas.width,canvas.height).data;let redAlpha=0,blue=0;for(let i=0;i<d.length;i+=4){if(d[i]===255&&Math.abs(d[i+1]-191)<=1&&Math.abs(d[i+2]-191)<=1&&d[i+3]===255)redAlpha++;if(d[i]===0&&d[i+1]===0&&d[i+2]===255&&d[i+3]===255)blue++;}return{width:image.width,height:image.height,redAlpha,blue};});
assert(colors.redAlpha>9000&&colors.blue>9000,'Expected filled swatches in PNG; original30×30 shapes alone cannot supply these pixel counts');fs.writeFileSync(path.join(output,'png-review.json'),JSON.stringify(colors,null,2));
for(const width of [320,390,844]){await page.setViewportSize({width,height:844});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));}
assert.deepEqual(errors,[]);assert.deepEqual(external,[]);
console.log('PASS: actual C036 insertion,4 expected editable swatches with combined alpha,hidden-color exclusion,source visual fields preserved,unique IDs,one-step Undo/Redo,reload/editable JSON,SVG alpha and PNG pixel presence,320/390/844px,no page errors/external requests. Browser only; no native WebView/APK/device acceptance.');
}finally{await browser.close();}})().catch(error=>{console.error(error);process.exitCode=1;});
