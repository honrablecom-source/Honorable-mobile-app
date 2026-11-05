const { withProjectView } = require('./project-navigation.cjs');
// Real isolated editor scenarios. Synthetic inputs only; no Android claims.
const {chromium}=require('../../../ui-previews/node_modules/playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const root=path.resolve(__dirname,'../../../..'),url=process.env.NOBLE_PREVIEW_URL||'http://127.0.0.1:4176';
(async()=>{const browser=await chromium.launch({args:['--no-sandbox']});try{
 const page=await browser.newPage({viewport:{width:390,height:844}});const errors=[],external=[];
 page.on('pageerror',e=>errors.push(e.message));
 await page.route('**/*',route=>{if(!route.request().url().startsWith(url)&&!route.request().url().startsWith('data:')&&!route.request().url().startsWith('blob:')){external.push(route.request().url());return route.abort();}return route.continue();});
 await page.goto(url);await page.getByRole('button',{name:'Layouts',exact:true}).click();
 const save=async()=>{await page.getByRole('button',{name:'Save draft',exact:true}).click();return page.evaluate(()=>JSON.parse(localStorage.getItem('honorable-drawing-v1')));};
 const close=async()=>{const button=page.getByRole('button',{name:'Close panel',exact:true});if(await button.count())await button.click();await page.waitForTimeout(200);};
 const panel=()=>page.getByRole('button',{name:'Layouts',exact:true}).click();
 for(const [kind,configure,expected]of [
  ['chart',async()=>{await page.getByLabel('Chart title',{exact:true}).fill('Monthly change');await page.getByLabel('Chart rows',{exact:true}).fill('Opening | -10\nMiddle | 20\nClosing | 0');},'Monthly change'],
  ['calendar',async()=>{await page.getByLabel('Calendar month',{exact:true}).fill('2024-02');},'29'],
  ['storyboard',async()=>{await page.getByLabel('Storyboard title',{exact:true}).fill('Synthetic storyboard');await page.getByLabel('Scene briefs',{exact:true}).fill('Opening view\nA closer look at the scenery\nClosing scene');},'Scene 3']
 ]){
  await page.getByRole('button',{name:({chart:'Data chart',calendar:'Month calendar',storyboard:'Storyboard sheet'})[kind],exact:true}).click();
  await configure();await page.getByRole('button',{name:`Insert editable ${kind}`,exact:true}).click();
  let saved=await save();assert(saved.elements.some(e=>e.text===expected),await page.getByRole('status').innerText());
  assert.equal(new Set(saved.elements.map(e=>e.id)).size,saved.elements.length);
  const count=saved.elements.length;
  await close();await page.screenshot({path:path.join(root,`.context/noble-editor-review/layout-${kind}-390.png`)});
  await page.getByTestId('button-undo').click();assert.equal((await save()).elements.length,0);
  await page.getByTestId('button-redo').click();assert.equal((await save()).elements.length,count);
  await page.reload();assert.equal((await save()).elements.length,count);
  // Reopen via the saved project format; the layouts remain ordinary editable objects.
  await page.getByRole('button',{name:'Projects',exact:true}).click();await withProjectView(page, 'advanced', () => page.getByRole('button',{name:'Export project JSON',exact:true}).click());
  const portable=JSON.parse(await withProjectView(page, 'advanced', () => page.getByLabel('Project JSON',{exact:true}).inputValue()));assert.equal(JSON.parse(portable.scene).elements.length,count);
  // New clean page isolates each layout's history, with no production storage involved.
  await page.evaluate(()=>localStorage.clear());await page.reload();await panel();
 }
 await page.getByLabel('Chart rows',{exact:true}).fill('A | NaN');await page.getByRole('button',{name:'Insert editable chart',exact:true}).click();assert.equal((await save()).elements.length,0);
 for(const width of [320,390,844]){await page.setViewportSize({width,height:844});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await page.screenshot({path:path.join(root,`.context/noble-editor-review/layout-panel-${width}.png`)});}
 await page.evaluate(()=>{
  const shape=(id,x,size,color)=>({id,type:'rectangle',x,y:0,width:size,height:size,angle:0,opacity:100,strokeColor:'#111111',backgroundColor:color,fillStyle:'solid',strokeStyle:'solid',strokeWidth:1,roughness:0,seed:1,version:1,versionNonce:1,isDeleted:false,groupIds:[],boundElements:null,link:null,locked:false});
  localStorage.setItem('honorable-drawing-v1',JSON.stringify({version:1,elements:[shape('blend-a',0,40,'#000000'),shape('blend-b',280,100,'#ffffff')],appState:{viewBackgroundColor:'#ffffff'}}));
 });
 await page.setViewportSize({width:390,height:844});await page.reload();
 await page.locator('.excalidraw canvas').last().click({position:{x:20,y:200}});await page.keyboard.press('Control+a');
 await page.getByRole('button',{name:'Arrange',exact:true}).click();await page.getByRole('button',{name:'Shape blend',exact:true}).click();await page.getByLabel('Blend intermediate steps',{exact:true}).fill('3');
 await page.getByRole('button',{name:'Blend selected endpoint shapes',exact:true}).click();
 let blend=await save();assert.equal(blend.elements.length,5,await page.getByRole('status').innerText());
 assert.equal(blend.elements[3].x,140);assert.equal(blend.elements[3].width,70);assert.equal(blend.elements[3].backgroundColor,'#808080');
 await close();await page.screenshot({path:path.join(root,'.context/noble-editor-review/layout-blend-390.png')});
 await page.getByTestId('button-undo').click();assert.equal((await save()).elements.length,2);
 await page.getByTestId('button-redo').click();assert.equal((await save()).elements.length,5);
 await page.reload();assert.equal((await save()).elements.length,5);
 assert.deepEqual(errors,[]);assert.deepEqual(external,[]);
 console.log('PASS: four real editable layout/blend workflows; native drawing Undo/Redo, reload and JSON recovery; invalid input preservation; 320/390/844px; zero external requests/page errors. Android pending.');
 }finally{await browser.close();}})().catch(e=>{console.error(e);process.exitCode=1;});
