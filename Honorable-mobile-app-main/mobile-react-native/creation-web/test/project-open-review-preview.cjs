const { chromium } = require('../../../ui-previews/node_modules/playwright');
const { withProjectView } = require('./project-navigation.cjs');
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const output=process.env.NOBLE_REVIEW_OUTPUT||path.resolve(__dirname,'../../../../.context/project-open-review');fs.mkdirSync(output,{recursive:true});
const url=process.env.NOBLE_PREVIEW_URL||'http://127.0.0.1:4196/after/drawing/';
(async()=>{
 const {saveProject,writeProjects,exportProject}=await import('../projects.mjs');
 const box=id=>({id,type:'rectangle',x:40,y:40,width:100,height:80,angle:0,opacity:100,strokeColor:'#111111',backgroundColor:'#dddddd',fillStyle:'solid',strokeWidth:1,roughness:0,groupIds:[],boundElements:null,seed:1,version:1,versionNonce:1});
 const original=JSON.stringify({version:1,elements:[box('original')],appState:{viewBackgroundColor:'#ffffff'}}),shelf=writeProjects(saveProject([],'Incoming',[box('incoming')],{}));
 const browser=await chromium.launch({args:['--no-sandbox']});let page;
 try{
  page=await browser.newPage({viewport:{width:390,height:844}});const errors=[],external=[];
  page.on('pageerror',e=>errors.push(e.message));await page.route('**/*',route=>{const u=route.request().url();if(!u.startsWith(new URL(url).origin)&&!u.startsWith('data:')&&!u.startsWith('blob:')){external.push(u);return route.abort();}return route.continue();});
  await page.addInitScript(({original,shelf})=>{if(!sessionStorage.getItem('open-seeded')){localStorage.setItem('honorable-drawing-v1',original);localStorage.setItem('noble-drawing-projects-v1',shelf);sessionStorage.setItem('open-seeded','yes');}},{original,shelf});
  await page.goto(url);const save=async()=>{await page.getByRole('button',{name:'Save draft',exact:true}).click();return page.evaluate(()=>localStorage.getItem('honorable-drawing-v1'));};
  const projects=async()=>{const b=page.getByRole('button',{name:'Projects',exact:true});if(await b.getAttribute('aria-expanded')!=='true')await b.click();};
  const open=async()=>{await projects();await withProjectView(page,'saved',()=>page.getByRole('button',{name:'Open Incoming',exact:true}).click());await page.waitForFunction(()=>!window.honorableDrawingBusy);};
  const before=await save();await open();assert.equal(await save(),before,'Open must not replace current work before review');
  const review=page.getByRole('region',{name:'Review drawing replacement',exact:true});await review.waitFor();
  await review.getByRole('button',{name:'Keep current drawing',exact:true}).click();assert.equal(await save(),before);assert.equal(await review.count(),0);
  await open();await withProjectView(page,'saved',()=>page.getByLabel('Project name',{exact:true}).fill('Incoming'));await page.getByRole('button',{name:'Save named composition',exact:true}).click();
  const savedReview=page.getByRole('region',{name:'Review saved replacement',exact:true});await savedReview.waitFor();assert.equal(await review.count(),0);
  await page.getByRole('button',{name:'Open Incoming',exact:true}).click();await review.waitFor();assert.equal(await savedReview.count(),0);await review.getByRole('button',{name:'Keep current drawing',exact:true}).click();assert.equal(await save(),before);
  await open();await review.waitFor();await withProjectView(page,'files',async()=>{});assert.equal(await review.count(),0);assert.equal(await save(),before);
  const incoming=exportProject('Incoming',[box('incoming')],{}),newer=exportProject('Newer',[box('newer')],{});
  await withProjectView(page,'advanced',()=>page.getByLabel('Project JSON',{exact:true}).fill(incoming));await page.getByRole('button',{name:'Import project JSON',exact:true}).click();await review.waitFor();
  await page.getByLabel('Project JSON',{exact:true}).fill(newer);assert.equal(await review.count(),0,'Editing incoming JSON must dismiss the old review');assert.equal(await save(),before);
  await page.getByRole('button',{name:'Import project JSON',exact:true}).click();await review.getByRole('heading',{name:'Open “Newer” instead?',exact:true}).waitFor();await page.getByRole('button',{name:'Export project JSON',exact:true}).click();assert.equal(await review.count(),0,'Replacing JSON via export must dismiss the old review');assert.equal(await save(),before);
  const choose=async raw=>{await withProjectView(page,'files',async()=>{});const picking=page.waitForEvent('filechooser');await page.getByRole('button',{name:'Choose project file',exact:true}).click();await(await picking).setFiles({name:'synthetic.noble.json',mimeType:'application/json',buffer:Buffer.from(raw)});await page.waitForFunction(()=>!window.honorableDrawingBusy);};
  await choose(incoming);await page.getByRole('button',{name:'Open chosen project',exact:true}).click();await review.waitFor();await choose(newer);assert.equal(await review.count(),0,'Choosing a newer file must dismiss the old review');assert.equal(await save(),before);
  await page.getByRole('button',{name:'Open chosen project',exact:true}).click();await review.getByRole('heading',{name:'Open “Newer” instead?',exact:true}).waitFor();await review.getByRole('button',{name:'Keep current drawing',exact:true}).click();assert.equal(await save(),before);
  // Insert actual editable artwork to create history; Undo during review invalidates approval.
  await page.getByRole('button',{name:'Layouts',exact:true}).click();await page.getByRole('button',{name:'Starter design',exact:true}).click();await page.getByRole('button',{name:'Insert editable starter design',exact:true}).click();
  const withStarter=await save();assert(JSON.parse(withStarter).elements.length>1);await open();await review.waitFor();assert.equal(await review.getByRole('heading').evaluate(e=>e===document.activeElement),true);
  await page.getByTestId('button-undo').click();const changed=await save();assert.notEqual(changed,withStarter);
  await review.getByRole('button',{name:'Replace current drawing',exact:true}).click();await page.getByRole('status').getByText(/drawing changed after the review/).waitFor();assert.equal(await save(),changed);assert.equal(await review.count(),0);
  await open();await review.waitFor();for(const width of [320,390,844]){await page.setViewportSize({width,height:844});await review.scrollIntoViewIfNeeded();assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await page.screenshot({path:path.join(output,`review-${width}.png`)});}
  await review.getByRole('button',{name:'Replace current drawing',exact:true}).click();await page.getByRole('status').getByText(/Composition loaded/).waitFor();assert.equal(JSON.parse(await save()).elements[0].id,'incoming');
  // Undo may renumber fractional indices and revision stamps; array order and all artwork fields must match.
  await page.getByTestId('button-undo').click();const undoScene=raw=>JSON.parse(raw).elements.map(({version,versionNonce,updated,index,...element})=>element);assert.deepEqual(undoScene(await save()),undoScene(changed));await open();await review.getByRole('button',{name:'Replace current drawing',exact:true}).click();await page.getByRole('status').getByText(/Composition loaded/).waitFor();
  await page.reload();assert.equal(JSON.parse(await save()).elements[0].id,'incoming');assert.equal(await page.evaluate(()=>localStorage.getItem('noble-drawing-projects-v1')),shelf);assert.deepEqual(errors,[]);assert.deepEqual(external,[]);
  await page.close();page=await browser.newPage();await page.addInitScript(shelf=>localStorage.setItem('noble-drawing-projects-v1',shelf),shelf);await page.goto(url);await page.getByRole('button',{name:'Save draft',exact:true}).click();await page.getByRole('button',{name:'Projects',exact:true}).click();await withProjectView(page,'saved',()=>page.getByRole('button',{name:'Open Incoming',exact:true}).click());await page.getByRole('status').getByText(/Composition loaded/).waitFor();assert.equal(await page.getByRole('region',{name:'Review drawing replacement',exact:true}).count(),0);
  fs.writeFileSync(path.join(output,'review.json'),JSON.stringify({cancelPreserved:true,taskSwitchDismissed:true,staleApprovalRefused:true,explicitReplace:true,undoPreserved:true,reopenPreserved:true,shelfUnchanged:true,emptyOpensDirectly:true,pageErrors:errors,externalRequests:external,scope:'Actual browser with synthetic scenes;Android/device pending'},null,2));console.log('PASS: Open review/cancel/task dismissal/stale approval/explicit replacement/Undo/reopen/empty direct open;unchanged named shelf;phone widths;no page/external errors. Browser only.');
 }catch(error){if(page)await page.screenshot({path:path.join(output,'failure.png')});throw error;}finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
