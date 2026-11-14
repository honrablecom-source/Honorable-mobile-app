const {chromium}=require('../../ui-previews/node_modules/playwright');
const {createServer}=require('node:http');
const fs=require('node:fs/promises'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.resolve(process.env.NOBLE_IMAGE_ASSETS||path.join(__dirname,'../android/app/src/main/assets/creative-editors/image'));
const out=path.resolve(process.env.NOBLE_AI_EVIDENCE||path.join(__dirname,'../../../../.context/ai-studio/browser'));
const fixture=process.env.NOBLE_AI_OBJECT_FIXTURE;
if(!fixture)throw Error('Set NOBLE_AI_OBJECT_FIXTURE to an explicitly licensed object-photo fixture. No personal images are accepted by this test command.');
(async()=>{
  await fs.mkdir(out,{recursive:true});
  const server=createServer(async(req,res)=>{
    try{
      const url=new URL(req.url,'http://localhost');if(!url.pathname.startsWith('/image/'))throw Error('path');
      let name=url.pathname.slice('/image/'.length)||'index.html';const file=path.resolve(root,name);
      if(!file.startsWith(root+path.sep))throw Error('path');
      res.setHeader('Content-Type',name.endsWith('.wasm')?'application/wasm':name.endsWith('.js')?'text/javascript':name.endsWith('.html')?'text/html; charset=utf-8':'application/octet-stream');
      res.setHeader('Content-Security-Policy',"default-src 'none'; script-src 'self' 'wasm-unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; media-src 'self' blob:; connect-src 'self' blob:; worker-src 'self' blob:; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'");
      res.end(await fs.readFile(file).catch(async error=>{if(error.code!=='ENOENT'||!file.endsWith('.wasm'))throw error;return require('node:zlib').gunzipSync(await fs.readFile(file+'.gzip'));}));
    }catch{res.writeHead(404).end();}
  });
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  let browser;
  try{
    browser=await chromium.launch({headless:true,args:['--no-sandbox','--use-angle=swiftshader','--enable-unsafe-swiftshader']});
    const page=await browser.newPage({viewport:{width:390,height:844}}),errors=[],external=[];
    const origin='http://127.0.0.1:'+server.address().port;
    page.on('pageerror',error=>errors.push(error.message));
    page.on('request',request=>{if(/^https?:/.test(request.url())&&!request.url().startsWith(origin+'/'))external.push(request.url());});
    await page.addInitScript(()=>{
      window.__shareCopies=[];let task=null;
      const create=URL.createObjectURL.bind(URL);
      URL.createObjectURL=blob=>{if(blob.type==='image/png')window.__lastPNGBlob=blob;return create(blob);};
      window.NobleCreative={beginSave(id,length,title){if(task)return false;task={id,length,title,raw:''};return true;},appendSave(id,chunk){if(task?.id!==id)return false;task.raw+=atob(chunk);return true;},finishSave(id){if(task?.id!==id||task.raw.length!==task.length)return false;window.__shareCopies.push(task);task=null;return true;},cancelSave(id){if(task?.id===id)task=null;}};
    });
    await page.goto(origin+'/image/?empty&webgl');
    await page.waitForFunction(()=>window.nobleImage&&!document.getElementById('photocraft_loading'),null,{timeout:120000});
    await page.evaluate(async()=>{
      const c=document.createElement('canvas');c.width=32;c.height=24;c.getContext('2d').fillRect(0,0,32,24);
      const b=await new Promise(r=>c.toBlob(r));nobleImage.importFile('Keep original.png',new Uint8Array(await b.arrayBuffer()));
    });
    await page.waitForFunction(async()=>(await nobleImage.request('ui.inspect',{})).result.document?.width===32);
    await page.evaluate(()=>nobleImage.request('engine.execute',{command:'layer.duplicate',params:{name:'Keep editable layer'}}));
    const original=await page.evaluate(async()=>(await nobleImage.request('ui.inspect',{})).result.document);
    await page.getByRole('button',{name:'AI photo tools',exact:true}).click();
    await page.keyboard.press('Control+z');
    assert.deepEqual(await page.evaluate(async()=>(await nobleImage.request('ui.inspect',{})).result.document),original);
    await page.locator('[data-file]').setInputFiles(fixture);
    await page.waitForFunction(()=>!document.querySelector('[data-run]').disabled);
    await page.getByRole('button',{name:'Run local AI',exact:true}).click();
    await page.waitForFunction(()=>!document.querySelector('[data-approve]').disabled,null,{timeout:180000});
    assert.equal(await page.locator('[data-apply]').isEnabled(),false);
    assert.deepEqual(await page.evaluate(async()=>(await nobleImage.request('ui.inspect',{})).result.document),original);
    const objectStats=await page.evaluate(()=>{
      const c=document.querySelector('[data-after]'),d=c.getContext('2d').getImageData(0,0,c.width,c.height).data;
      let transparent=0,opaque=0;for(let i=3;i<d.length;i+=4){if(d[i]<32)transparent++;if(d[i]>224)opaque++;}
      return {width:c.width,height:c.height,transparent,opaque,pixels:c.width*c.height};
    });
    assert.ok(objectStats.transparent>500&&objectStats.opaque>500,JSON.stringify(objectStats));
    const alphaHash=()=>page.locator('[data-after]').evaluate(async canvas=>{const d=canvas.getContext('2d').getImageData(0,0,canvas.width,canvas.height).data,a=new Uint8Array(canvas.width*canvas.height);for(let i=0;i<a.length;i++)a[i]=d[i*4+3];return Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',a))).join(',');});
    const initialMask=await alphaHash();
    await page.locator('[data-approve]').check();
    await page.locator('[data-erase]').click();
    await page.locator('[data-after]').scrollIntoViewIfNeeded();
    const box=await page.locator('[data-after]').boundingBox();
    await page.mouse.move(box.x+box.width*.5,box.y+box.height*.5);await page.mouse.down();
    await page.mouse.move(box.x+box.width*.52,box.y+box.height*.5);await page.mouse.up();
    assert.notEqual(await alphaHash(),initialMask);
    assert.equal(await page.locator('[data-approve]').isChecked(),false);
    await page.locator('[data-mask-undo]').click();assert.equal(await alphaHash(),initialMask);
    const objectPng=await page.locator('[data-after]').evaluate(canvas=>canvas.toDataURL('image/png').split(',')[1]);
    await fs.writeFile(path.join(out,'object-cutout.png'),Buffer.from(objectPng,'base64'));
    await page.screenshot({path:path.join(out,'object-review.png'),fullPage:true});
    await page.locator('[data-approve]').check();
    await page.getByRole('button',{name:'Export PNG copy',exact:true}).click();
    await page.waitForFunction(()=>window.__shareCopies.some(copy=>copy.title==='AI-object.png'));
    const exportBytes=await page.evaluate(()=>window.__shareCopies.find(copy=>copy.title==='AI-object.png').raw);
    const actualBlob=await page.evaluate(async()=>Array.from(new Uint8Array(await window.__lastPNGBlob.arrayBuffer())));
    assert.deepEqual(Buffer.from(exportBytes,'binary'),Buffer.from(actualBlob));
    await page.getByRole('button',{name:'Open copy in image studio',exact:true}).click();
    await page.waitForFunction(()=>document.querySelector('#noble-ai-dialog').hidden);
    await page.waitForFunction(async()=>(await nobleImage.request('ui.inspect',{})).result.document?.width>32);
    const applied=await page.evaluate(async()=>(await nobleImage.request('ui.inspect',{})).result);
    assert.ok(applied.views.length>=2,'AI must open a separate document');
    await page.getByRole('button',{name:'AI photo tools',exact:true}).click();
    await page.locator('[data-tool]').selectOption('denoise');
    assert.equal(await page.locator('[data-approve]').isChecked(),false);
    const noisePng=await page.evaluate(()=>{
      const c=document.createElement('canvas');c.width=c.height=256;const ctx=c.getContext('2d'),d=ctx.createImageData(256,256);let seed=17;
      const random=()=>{seed=(seed*1664525+1013904223)>>>0;return (seed+.5)/4294967296;};
      for(let i=0;i<d.data.length;i+=4){const noise=Math.sqrt(-2*Math.log(random()))*Math.cos(2*Math.PI*random())*25;for(let k=0;k<3;k++)d.data[i+k]=Math.max(0,Math.min(255,128+noise));d.data[i+3]=255;}
      ctx.putImageData(d,0,0);return c.toDataURL('image/png').split(',')[1];
    });
    await page.locator('[data-file]').setInputFiles({name:'generated-noise.png',mimeType:'image/png',buffer:Buffer.from(noisePng,'base64')});
    await page.waitForFunction(()=>!document.querySelector('[data-run]').disabled);
    await page.locator('[data-run]').click();
    await page.waitForFunction(()=>document.querySelector('[data-status]').textContent.includes('Cleaning noise locally'),null,{timeout:120000});
    await page.locator('[data-stop]').click();
    assert.equal(await page.locator('[data-apply]').isEnabled(),false);
    await page.locator('[data-run]').click();
    await page.waitForFunction(()=>!document.querySelector('[data-approve]').disabled,null,{timeout:180000});
    const denoise=await page.evaluate(()=>{
      const values=['before','after'].map(name=>{const c=document.querySelector(`[data-${name}]`),d=c.getContext('2d').getImageData(0,0,c.width,c.height).data;let error=0,n=0;for(let y=24;y<c.height-24;y++)for(let x=24;x<c.width-24;x++){const i=(y*c.width+x)*4;error+=(d[i]-128)**2;n++;}return error/n;});return {beforeMse:values[0],afterMse:values[1]};
    });
    assert.ok(denoise.afterMse<denoise.beforeMse*.3,JSON.stringify(denoise));
    await page.locator('[data-approve]').check();
    await page.locator('[data-tool]').selectOption('style');
    assert.equal(await page.locator('[data-apply]').isEnabled(),false);
    await page.locator('[data-run]').click();
    await page.waitForFunction(()=>!document.querySelector('[data-approve]').disabled,null,{timeout:180000});
    const styleStats=await page.evaluate(()=>{
      const a=document.querySelector('[data-before]'),b=document.querySelector('[data-after]'),x=a.getContext('2d').getImageData(0,0,a.width,a.height).data,y=b.getContext('2d').getImageData(0,0,b.width,b.height).data;let changed=0;for(let i=0;i<x.length;i++)if(i%4!==3&&x[i]!==y[i])changed++;return {width:b.width,height:b.height,changed};
    });
    assert.ok(styleStats.width<=224&&styleStats.changed>1000);
    await page.locator('[data-approve]').check();
    await page.evaluate(()=>{Object.defineProperty(document,'hidden',{configurable:true,value:true});document.dispatchEvent(new Event('visibilitychange'));Object.defineProperty(document,'hidden',{configurable:true,value:false});});
    assert.equal(await page.locator('[data-approve]').isChecked(),false);
    assert.equal(await page.locator('[data-apply]').isEnabled(),false);
    assert.deepEqual(errors,[]);assert.deepEqual(external,[]);
    const report={objectStats,denoise,styleStats,maskBrushAndExactUndo:true,maskEditRevokesApproval:true,originalPreserved:true,separateDocument:true,exactNativeExportBytes:true,stopDuringInference:true,toolChangeRevokesApproval:true,simulatedBackgroundRevokesApproval:true,errors,external,note:'Actual local model and full-editor browser workflow; not Android WebView/physical device acceptance. Object fixture must be separately licensed.'};
    await fs.writeFile(path.join(out,'ai-studio.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report));
  }finally{await browser?.close();server.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
