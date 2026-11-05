const { withProjectView } = require('./project-navigation.cjs');
// Actual drawing renderer, isolated synthetic scene. No native build or device claim.
const {chromium}=require('../../../ui-previews/node_modules/playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const output=path.resolve(__dirname,'../../../../.context/dependency-runtime-review');
fs.mkdirSync(output,{recursive:true});
const url=process.env.NOBLE_PREVIEW_URL||'http://127.0.0.1:4177/drawing/';
const origin=new URL(url).origin;
const fixture={id:'original',type:'rectangle',x:-200,y:-200,width:30,height:30,angle:0,opacity:100,strokeColor:'#111111',backgroundColor:'#ddd',fillStyle:'solid',strokeWidth:1,strokeStyle:'solid',roughness:0,seed:1,version:1,versionNonce:1,isDeleted:false,groupIds:[],boundElements:null,link:null,locked:false};
function bindings(elements,expectedNodes,expectedEdges){
 const boxes=elements.filter(e=>e.type==='rectangle'&&e.id!=='original'),arrows=elements.filter(e=>e.type==='arrow');
 assert.equal(boxes.length,expectedNodes);assert.equal(arrows.length,expectedEdges);
 const ids=new Set(boxes.map(e=>e.id));
 for(const arrow of arrows){assert(ids.has(arrow.startBinding?.elementId));assert(ids.has(arrow.endBinding?.elementId));for(const id of [arrow.startBinding.elementId,arrow.endBinding.elementId])assert(boxes.find(e=>e.id===id).boundElements.some(bound=>bound.id===arrow.id));}
 for(const box of boxes){const label=elements.find(e=>e.type==='text'&&e.containerId===box.id);assert(label);assert(box.boundElements.some(bound=>bound.id===label.id));}
 assert.equal(new Set(elements.map(e=>e.id)).size,elements.length);
}
(async()=>{const browser=await chromium.launch({args:['--no-sandbox']});try{
 const page=await browser.newPage({viewport:{width:1440,height:900},deviceScaleFactor:1}),errors=[],external=[];
 page.on('pageerror',e=>errors.push(e.message));page.on('console',m=>{if(m.type()==='error'&&m.text().includes('Content Security Policy'))errors.push(m.text());});
 await page.route('**/*',route=>{const target=route.request().url();if(!target.startsWith(origin)&&!target.startsWith('data:')&&!target.startsWith('blob:')){external.push(target);return route.abort();}return route.continue();});
 await page.addInitScript(({fixture})=>{if(!localStorage.getItem('honorable-drawing-v1'))localStorage.setItem('honorable-drawing-v1',JSON.stringify({version:1,elements:[fixture],appState:{viewBackgroundColor:'#ffffff',currentItemFontFamily:2}}));window.HonorableDrawing={usePng:png=>{window.__png=png;window.honorableExportResult('Synthetic diagram PNG ready');}};},{fixture});
 await page.goto(url);await page.getByRole('button',{name:'Layouts',exact:true}).waitFor();
 const save=async()=>{await page.getByRole('button',{name:'Save draft',exact:true}).click();return page.evaluate(()=>JSON.parse(localStorage.getItem('honorable-drawing-v1')));};
 const before=await save();assert.equal(before.elements.length,1);
 await page.getByRole('button',{name:'Layouts',exact:true}).click();await page.getByRole('button',{name:'Dependency diagram',exact:true}).click();await page.getByLabel('Dependency diagram title',{exact:true}).fill('Launch plan');
 await page.getByLabel('Dependency rows',{exact:true}).fill('Draft -> Edit\nEdit -> Draft');await page.getByRole('button',{name:'Insert editable dependency diagram',exact:true}).click();
 assert((await page.getByRole('status').innerText()).includes('cycle'));assert.equal((await save()).elements.length,1);
 await page.getByLabel('Dependency rows',{exact:true}).fill('Draft -> Edit\nDraft -> Artwork\nEdit -> Review\nArtwork -> Review\nReview -> Publish');await page.getByRole('button',{name:'Insert editable dependency diagram',exact:true}).click();
 let saved=await save();assert.equal(saved.elements.length,18);bindings(saved.elements,5,5);
 const original=saved.elements.find(e=>e.id==='original');for(const field of ['x','y','width','height','backgroundColor','opacity'])assert.equal(original[field],before.elements[0][field]);
 await page.getByTestId('button-undo').click();assert.equal((await save()).elements.length,1);
 await page.getByTestId('button-redo').click();saved=await save();bindings(saved.elements,5,5);
 await page.reload();saved=await save();bindings(saved.elements,5,5);
 await page.screenshot({path:path.join(output,'diagram-1440.png')});
 await page.getByRole('button',{name:'Projects',exact:true}).click();await withProjectView(page, 'advanced', () => page.getByRole('button',{name:'Export project JSON',exact:true}).click());
 const portable=JSON.parse(await withProjectView(page, 'advanced', () => page.getByLabel('Project JSON',{exact:true}).inputValue()));bindings(JSON.parse(portable.scene).elements,5,5);
 await withProjectView(page, 'advanced', () => page.getByRole('button',{name:'Prepare vector SVG',exact:true}).click());const svg=await withProjectView(page, 'advanced', () => page.getByLabel('Vector SVG',{exact:true}).inputValue());assert(svg.includes('Draft')&&svg.includes('Review')&&svg.includes('<path'));fs.writeFileSync(path.join(output,'diagram.svg'),svg);
 await page.getByRole('button',{name:'Close panel',exact:true}).click();await page.getByRole('button',{name:'Use image',exact:true}).click();await page.waitForFunction(()=>window.__png?.startsWith('data:image/png;base64,'));fs.writeFileSync(path.join(output,'diagram.png'),Buffer.from((await page.evaluate(()=>window.__png)).split(',')[1],'base64'));
 // A second insertion must resolve fresh IDs and independent bindings.
 await page.getByRole('button',{name:'Layouts',exact:true}).click();await page.getByRole('button',{name:'Dependency diagram',exact:true}).click();await page.getByLabel('Dependency diagram title',{exact:true}).fill('Second plan');await page.getByLabel('Dependency rows',{exact:true}).fill('A -> B\nB -> C');await page.getByRole('button',{name:'Insert editable dependency diagram',exact:true}).click();saved=await save();bindings(saved.elements,8,7);
 await page.getByTestId('button-undo').click();bindings((await save()).elements,5,5);
 for(const width of [320,390,844]){await page.setViewportSize({width,height:844});await page.getByRole('button',{name:'Layouts',exact:true}).click();assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await page.getByRole('button',{name:'Close panel',exact:true}).click();}
 assert.deepEqual(errors,[]);assert.deepEqual(external,[]);
 console.log('PASS: actual C038 conversion creates reciprocal node/label/arrow bindings; cycle preserves artwork; one-step Undo/Redo; reload/editable JSON/SVG/PNG; repeated IDs/bindings independent;320/390/844px;no page/CSP/external errors. Dragging and Android runtime remain unverified.');
}finally{await browser.close();}})().catch(e=>{console.error(e);process.exitCode=1;});
