const {chromium}=require('../../../ui-previews/node_modules/playwright');
const {createServer}=require('node:http'),fs=require('node:fs/promises'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../../../..'),evidence=path.join(root,'.context/noble-fast-photocraft');
const output=process.env.NOBLE_CORE_OUTPUT||path.join(evidence,'drawing');
(async()=>{
  let hold=false,release=null,held=0;
  const requests=[],external=[],errors=[];
  const server=createServer(async(req,res)=>{
    try{
      const name=new URL(req.url,'http://localhost').pathname.replace(/^\/drawing\//,'/'),file=path.resolve(output,'.'+(name==='/'?'/index.html':name));
      if(!file.startsWith(output+path.sep)){res.writeHead(403).end();return;}
      requests.push(name);if(hold&&name==='/photocraft/noble_photocraft_core_bg.wasm'){held++;await new Promise(r=>{release=r;});}
      const mime={'.js':'text/javascript','.html':'text/html','.css':'text/css','.json':'application/json','.wasm':'application/wasm','.woff2':'font/woff2','.woff':'font/woff','.ttf':'font/ttf'};
      const data=name==='/config.js'?'window.EXCALIDRAW_ASSET_PATH="/drawing/";':await fs.readFile(file);
      res.writeHead(200,{'Content-Type':mime[path.extname(file)]||'application/octet-stream','Cache-Control':'no-store','X-Content-Type-Options':'nosniff',
        'Content-Security-Policy':"default-src 'none'; script-src 'self' 'wasm-unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self' blob:; worker-src 'self' blob:; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'"});res.end(data);
    }catch{res.writeHead(404).end();}
  });
  await new Promise(r=>server.listen(0,'127.0.0.1',r));let browser;
  try{
    browser=await chromium.launch({headless:true,args:['--no-sandbox','--enable-unsafe-swiftshader']});
    const page=await browser.newPage({viewport:{width:390,height:844},deviceScaleFactor:1});page.setDefaultTimeout(30000);
    const origin='http://127.0.0.1:'+server.address().port;
    page.on('pageerror',e=>errors.push(e.message));page.on('request',r=>{if(/^https?:/.test(r.url())&&!r.url().startsWith(origin+'/'))external.push(r.url());});
    await page.addInitScript(()=>{window.__coreWorkers=[];const Original=window.Worker;window.Worker=class extends Original{constructor(...args){super(...args);this.stopped=0;window.__coreWorkers.push(this);}terminate(){this.stopped++;return super.terminate();}};});
    await page.goto(origin);await page.getByRole('button',{name:'Model',exact:true}).click();
    const input=await page.evaluate(()=>{const c=document.createElement('canvas');c.width=96;c.height=64;const x=c.getContext('2d');x.fillStyle='#c04020';x.fillRect(0,0,96,64);x.fillStyle='#3080d0';x.fillRect(50,10,35,40);x.clearRect(0,0,3,3);return c.toDataURL('image/png');});
    await page.getByLabel('Model texture photo',{exact:true}).setInputFiles({name:'generated-layer.png',mimeType:'image/png',buffer:Buffer.from(input.split(',')[1],'base64')});
    await page.getByRole('button',{name:'Remove texture',exact:true}).waitFor();const save=page.getByRole('button',{name:'Save model',exact:true});await save.click();
    const baseline=await page.evaluate(()=>localStorage.getItem('noble-model-studio-v1'));
    const open=()=>page.getByRole('button',{name:'Raster lab (PhotoCraft core)',exact:true}).click();
    const lab=page.getByRole('region',{name:'Raster lab with PhotoCraft core',exact:true});
    const idle=()=>page.waitForFunction(()=>{const f=document.querySelector('section[aria-label="Raster lab with PhotoCraft core"] > fieldset');return f&&!f.disabled;});
    const preview=()=>lab.getByAltText('PhotoCraft raster composition preview',{exact:true}).getAttribute('src');
    await open();await idle();const original=await preview();
    assert.equal(await lab.getByRole('button',{name:'Use reviewed raster as model texture',exact:true}).isDisabled(),true);
    await lab.getByRole('button',{name:'Duplicate working layer',exact:true}).click();await idle();
    assert.equal(await lab.getByLabel('Raster working layer').locator('option').count(),2);
    await lab.getByLabel('Raster layer blend').selectOption('multiply');await idle();
    const multiplied=await preview();assert.notEqual(multiplied,original);
    const pixel=await page.evaluate(async url=>{const image=await new Promise(r=>{const value=new Image();value.onload=()=>r(value);value.src=url;});const c=document.createElement('canvas');c.width=96;c.height=64;const x=c.getContext('2d');x.drawImage(image,0,0);return[...x.getImageData(20,20,1,1).data];},multiplied);
    assert.deepEqual(pixel,[145,16,4,255]); // round(192²/255), round(64²/255), round(32²/255).
    await lab.getByLabel('Raster layer opacity').selectOption('0.5');await idle();const beforeEffect=await preview();
    await lab.getByLabel('PhotoCraft effect',{exact:true}).selectOption('invert');await lab.getByRole('button',{name:'Apply PhotoCraft effect to working layer',exact:true}).click();await idle();assert.notEqual(await preview(),beforeEffect);
    await lab.getByRole('button',{name:'Undo raster edit',exact:true}).click();await idle();assert.equal(await preview(),beforeEffect);
    assert.equal(await page.evaluate(()=>localStorage.getItem('noble-model-studio-v1')),baseline);
    // Real neural model result flows through the real Rust layer compositor.
    const paint=()=>lab.getByRole('button',{name:'Neural paint working layer',exact:true}).click();
    const neural=lab.getByRole('region',{name:'Neural painted texture',exact:true});
    await paint();await neural.getByRole('button',{name:'Paint with local AI',exact:true}).click();await neural.getByAltText('Reviewed neural painted texture').waitFor({timeout:120000});
    await neural.getByRole('checkbox').check();await neural.getByRole('button',{name:'Apply reviewed layer pixels',exact:true}).click();await neural.waitFor({state:'detached'});await idle();
    assert.notEqual(await preview(),beforeEffect);
    await lab.getByRole('button',{name:'Undo raster edit',exact:true}).click();await idle();assert.equal(await preview(),beforeEffect);
    // Closing neural review while its asynchronous core apply is held cannot insert late pixels.
    await paint();await neural.getByRole('button',{name:'Paint with local AI',exact:true}).click();await neural.getByAltText('Reviewed neural painted texture').waitFor({timeout:120000});
    await neural.getByRole('checkbox').check();hold=true;await neural.getByRole('button',{name:'Apply reviewed layer pixels',exact:true}).click();
    let deadline=Date.now()+15000;while(!held&&Date.now()<deadline)await new Promise(r=>setTimeout(r,30));assert.equal(held,1);
    await neural.getByRole('button',{name:'Close neural texture',exact:true}).click();hold=false;release();release=null;await idle();assert.equal(await preview(),beforeEffect);
    // Stop discards an unfinished manual operation and terminates its worker.
    hold=true;await lab.getByRole('button',{name:'Apply PhotoCraft effect to working layer',exact:true}).click();
    deadline=Date.now()+15000;while(held<2&&Date.now()<deadline)await new Promise(r=>setTimeout(r,30));assert.equal(held,2);
    await lab.getByRole('button',{name:'Stop raster processing',exact:true}).click();hold=false;release();release=null;await idle();assert.equal(await preview(),beforeEffect);
    assert.equal(await page.evaluate(()=>window.__coreWorkers.at(-1).stopped),1);
    // Explicitly commit a flattened texture, then restore the original through parent Model Undo.
    await lab.getByRole('checkbox').last().check();await lab.getByRole('button',{name:'Use reviewed raster as model texture',exact:true}).click();await lab.waitFor({state:'detached'});await save.click();
    assert.notEqual(await page.evaluate(()=>localStorage.getItem('noble-model-studio-v1')),baseline);
    await page.getByRole('button',{name:'Undo model edit',exact:true}).click();await save.click();assert.equal(await page.evaluate(()=>localStorage.getItem('noble-model-studio-v1')),baseline);
    // Full reviewed result as a separately owned drawing image; no PSD/layer-save claim.
    await open();await idle();await lab.getByRole('button',{name:'Apply PhotoCraft effect to working layer',exact:true}).click();await idle();
    await lab.getByRole('checkbox').last().check();await lab.getByRole('button',{name:'Add reviewed raster image to drawing',exact:true}).click();await lab.waitFor({state:'detached'});
    await page.getByRole('button',{name:'Save draft',exact:true}).click();
    const count=await page.evaluate(()=>JSON.parse(localStorage.getItem('honorable-drawing-v1')).elements.filter(x=>x.type==='image'&&!x.isDeleted).length);assert.equal(count,1);
    await page.getByRole('button',{name:'Model',exact:true}).click();await open();await idle();
    await page.setViewportSize({width:320,height:740});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1),false);
    await lab.locator('h3').first().scrollIntoViewIfNeeded();
    await page.screenshot({path:path.join(evidence,'raster-lab-mobile.png')});
    // Internal model/raster copies have a larger budget than drawing-photo imports.
    // A valid512px texture above192KiB must not be rejected by the drawing's smaller cap.
    await lab.getByRole('button',{name:'Close raster lab',exact:true}).click();
    const large=await page.evaluate(()=>{const c=document.createElement('canvas');c.width=512;c.height=512;const x=c.getContext('2d'),p=x.createImageData(512,512);let s=12345;for(let i=0;i<p.data.length;i++){s=(Math.imul(s,1664525)+1013904223)>>>0;p.data[i]=i%4===3?255:s>>>24;}x.putImageData(p,0,0);return c.toDataURL('image/png');});
    assert(Buffer.from(large.split(',')[1],'base64').length>192*1024);
    await page.getByLabel('Model texture photo',{exact:true}).setInputFiles({name:'generated-large.png',mimeType:'image/png',buffer:Buffer.from(large.split(',')[1],'base64')});
    await open();await idle();await lab.getByLabel('PhotoCraft effect',{exact:true}).selectOption('invert');
    await lab.getByRole('button',{name:'Apply PhotoCraft effect to working layer',exact:true}).click();await idle();
    assert(await preview());
    assert(requests.includes('/photocraft/noble_photocraft_core.js'));assert(requests.includes('/photocraft/noble_photocraft_core_bg.wasm'));
    assert.deepEqual(external,[]);assert.deepEqual(errors,[]);
    const receipt={actualPhotoCraftWasm:true,actualNeuralModel:true,knownMultiplyPixel:pixel,checks:['mobile layer duplication','real alpha/blend core','opacity','real core effect','exact raster Undo','real neural-to-core composition','neural apply closed before settlement cannot commit','manual Stop terminates worker','original/model unchanged until explicit use','fresh final approval','parent Model Undo','owned separate drawing image','320px layout','512px working texture above192KiB','no external requests/page errors'],external,errors};
    await fs.writeFile(path.join(evidence,'product-browser-receipt.json'),JSON.stringify(receipt,null,2)+'\n');console.log(JSON.stringify(receipt));
  }finally{release?.();await browser?.close();server.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
