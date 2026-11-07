const {chromium}=require('../../../ui-previews/node_modules/playwright');
const assert=require('node:assert/strict'),fs=require('node:fs/promises'),path=require('node:path'),http=require('node:http');
const assets=process.env.NOBLE_IMAGE_ASSETS,output=process.env.NOBLE_IMAGE_OUTPUT;
const rect=(id,x,color)=>({id,type:'rectangle',x,y:90,width:120,height:100,angle:0,opacity:100,strokeColor:color,backgroundColor:color,fillStyle:'solid',strokeWidth:1,roughness:0,locked:false,groupIds:[],boundElements:null,seed:1,version:1,versionNonce:1});
const fixture=[{...rect('chosen',40,'#ff0000'),groupIds:['card'],boundElements:[{id:'label',type:'text'}]},{id:'label',type:'text',text:'My card',originalText:'My card',x:60,y:125,width:80,height:25,fontSize:20,fontFamily:2,lineHeight:1.25,angle:0,opacity:100,strokeColor:'#111111',containerId:'chosen',groupIds:['card'],boundElements:null,locked:false,seed:1,version:1,versionNonce:1},rect('outside',300,'#0000ff'),{...rect('hidden',500,'#00ff00'),opacity:0}];
(async()=>{assert(assets&&output,'Set isolated drawing assets and output paths.');await fs.mkdir(output,{recursive:true});
 const mime={'.html':'text/html','.js':'text/javascript','.css':'text/css','.json':'application/json','.woff2':'font/woff2','.woff':'font/woff','.ttf':'font/ttf','.wasm':'application/wasm'};
 const server=http.createServer(async(req,res)=>{try{const name=decodeURIComponent(new URL(req.url,'http://localhost').pathname).replace(/^\/drawing\//,'');const file=path.resolve(assets,name||'index.html');if(!file.startsWith(path.resolve(assets)+path.sep))throw Error('path');const value=name==='config.js'?'window.EXCALIDRAW_ASSET_PATH="/drawing/";':await fs.readFile(file);res.writeHead(200,{'Content-Type':mime[path.extname(file)]||'application/octet-stream','Cache-Control':'no-store','Content-Security-Policy':"default-src 'self'; script-src 'self' 'wasm-unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self'; worker-src 'self' blob:; frame-src 'none'; object-src 'none'; base-uri 'none'"}).end(value);}catch{res.writeHead(404).end();}});
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));const origin=`http://127.0.0.1:${server.address().port}`,url=origin+'/drawing/';let browser;
 try{
  browser=await chromium.launch({args:['--no-sandbox']});const page=await browser.newPage({viewport:{width:390,height:844},reducedMotion:'reduce'}),errors=[],external=[];
  page.on('pageerror',error=>errors.push(error.message));await page.route('**/*',route=>{const u=route.request().url();if(!u.startsWith(origin)&&!u.startsWith('data:')&&!u.startsWith('blob:')){external.push(u);return route.abort();}return route.continue();});
  await page.addInitScript(fixture=>{
    if(!localStorage.getItem('honorable-drawing-v1'))localStorage.setItem('honorable-drawing-v1',JSON.stringify({version:1,elements:fixture,appState:{viewBackgroundColor:'#ffffff',currentItemFontFamily:2}}));
    window.__images=[];window.__jobs=[];window.__transfers={};
    window.__usePng=value=>{if(window.__deny)return false;if(window.__throw)throw Error('Synthetic image bridge failure');window.__images.push(value);if(window.__autoReply)window.honorableExportResult('Synthetic legacy image completion');return window.__legacy?undefined:true;};
    window.HonorableDrawing={usePng:window.__usePng,beginPrintImage:(id,length)=>{window.__transfers[id]={length,parts:[]};return true;},appendPrintImageChunk:(id,part)=>{window.__transfers[id].parts.push(part);return true;},finishPrintImage:id=>{const t=window.__transfers[id];const value=t.parts.join('');if(value.length!==t.length)throw Error('Incomplete synthetic print');window.__jobs.push({id,value});},releasePrintImage:()=>{}};
  },fixture);
  await page.goto(url);const button=name=>page.getByRole('button',{name,exact:true});
  const waitIdle=()=>page.waitForFunction(()=>window.honorableDrawingBusy===false);
  const save=async()=>{await button('Save draft').click();return page.evaluate(()=>localStorage.getItem('honorable-drawing-v1'));};
  const reply=async(message='Synthetic image preparation cancelled')=>{await page.evaluate(message=>window.honorableExportResult(message),message);await waitIdle();};
  const selectCard=async()=>{await button('Objects').click();await page.getByRole('button',{name:'Find object 1 on canvas',exact:true}).click();await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(()=>requestAnimationFrame(()=>requestAnimationFrame(resolve))))));const point=await page.waitForFunction(()=>{
    for(const canvas of document.querySelectorAll('.excalidraw canvas')){
      const ctx=canvas.getContext('2d');if(!ctx)continue;const box=canvas.getBoundingClientRect();if(!box.width||!box.height)continue;
      const data=ctx.getImageData(0,0,canvas.width,canvas.height).data;
      for(let y=8;y<canvas.height-8;y+=8)for(let x=8;x<canvas.width-8;x+=8){
        const p=(y*canvas.width+x)*4;if(data[p]<240||data[p+1]>15||data[p+2]>15||data[p+3]<250)continue;
        const cx=box.left+x*box.width/canvas.width,cy=box.top+y*box.height/canvas.height;
        if(cx<0||cx>=innerWidth||cy<0||cy>=innerHeight)continue;
        if(document.elementFromPoint(cx,cy)?.tagName==='CANVAS')return{x:cx,y:cy};
      }
    }return false;
  });await page.keyboard.press('v');await page.mouse.click(...Object.values(await point.jsonValue()));};

  await save();await button('Projects').click();await button('Image copies').click();await button('Use selected artwork').click();await page.getByRole('status').getByText(/Select artwork on the canvas first/).waitFor();await waitIdle();assert.equal(await page.evaluate(()=>window.__images.length),0);
  await button('Close panel').click();await selectCard();const before=await save();
  await button('Use image').click();await page.waitForFunction(()=>window.__images.length===1);assert(await button('Save draft').isDisabled());
  await page.evaluate(()=>{window.honorableSvgShareResult({requestId:999,error:'Foreign SVG callback'});window.honorablePrintResult({requestId:999,closed:true});});assert.equal(await page.evaluate(()=>window.honorableDrawingBusy),true);
  await reply();assert.equal(await save(),before);
  await button('Projects').click();await button('Image copies').click();await button('Use selected artwork').click();await page.waitForFunction(()=>window.__images.length===2);await reply();assert.equal(await save(),before);
  await page.getByLabel('Use transparent image background',{exact:true}).check();await button('Use selected artwork').click();await page.waitForFunction(()=>window.__images.length===3);await reply();assert.equal(await save(),before);
  await page.evaluate(()=>{window.__deny=true;});await button('Use image').click();await page.getByRole('status').getByText(/not active or another operation is running/).waitFor();await waitIdle();assert.equal(await page.evaluate(()=>window.__images.length),3);assert.equal(await save(),before);
  await page.evaluate(()=>{window.__deny=false;window.__throw=true;});await button('Use image').click();await page.getByRole('status').getByText(/Synthetic image bridge failure/).waitFor();await waitIdle();assert.equal(await save(),before);
  await page.evaluate(()=>{window.__throw=false;delete window.HonorableDrawing.usePng;});await button('Use image').click();await page.getByRole('status').getByText(/Android app to use the image/).waitFor();await waitIdle();assert.equal(await save(),before);
  await page.evaluate(()=>{window.HonorableDrawing.usePng=window.__usePng;window.__legacy=true;window.__autoReply=true;});await button('Use selected artwork').click();await page.waitForFunction(()=>window.__images.length===4);await waitIdle();assert.equal(await save(),before);
  await page.evaluate(()=>{window.__legacy=false;window.__autoReply=false;});await button('Use selected artwork').click();await page.waitForFunction(()=>window.__images.length===5);await reply();
  await button('Print or PDF').click();await button('Print whole drawing').click();await page.waitForFunction(()=>window.__jobs.length===1);await page.evaluate(()=>window.honorableExportResult('Orphan image callback'));assert.equal(await page.evaluate(()=>window.honorableDrawingBusy),true);assert(await button('Print whole drawing').isDisabled());await page.evaluate(()=>window.honorablePrintResult({requestId:window.__jobs[0].id,closed:true}));await waitIdle();
  await button('Image copies').click();
  for(const mode of ['onerror','onabort']){
    await page.evaluate(mode=>{window.__readAsDataURL=FileReader.prototype.readAsDataURL;FileReader.prototype.readAsDataURL=function(){this[mode]?.(new Event(mode));};},mode);
    await button('Use image').click();await page.getByRole('status').getByText(mode==='onerror'?/Could not prepare the image/:/Image preparation stopped/).waitFor();await waitIdle();await page.evaluate(()=>{FileReader.prototype.readAsDataURL=window.__readAsDataURL;});assert.equal(await page.evaluate(()=>window.__images.length),5);assert.equal(await save(),before);
  }
  const pixels=await page.evaluate(async()=>{const out=[];for(const value of window.__images){const i=new Image();i.src=value;await i.decode();const c=document.createElement('canvas');c.width=i.width;c.height=i.height;const ctx=c.getContext('2d');ctx.drawImage(i,0,0);const data=ctx.getImageData(0,0,c.width,c.height).data;let red=0,blue=0,green=0,ink=0,transparent=0;for(let p=0;p<data.length;p+=4){if(data[p]===255&&data[p+1]===0&&data[p+2]===0&&data[p+3]===255)red++;if(data[p]===0&&data[p+1]===0&&data[p+2]===255&&data[p+3]===255)blue++;if(data[p]===0&&data[p+1]===255&&data[p+2]===0)green++;if(data[p]===17&&data[p+1]===17&&data[p+2]===17&&data[p+3]===255)ink++;if(data[p+3]===0)transparent++;}out.push({width:c.width,height:c.height,red,blue,green,ink,transparent});}return out;});
  await fs.writeFile(path.join(output,'pixels.json'),JSON.stringify(pixels,null,2));
  assert(pixels[0].red>1000&&pixels[0].blue>1000);assert.equal(pixels[0].transparent,0);assert(pixels[1].red>1000&&pixels[1].width<pixels[0].width);assert.equal(pixels[1].blue,0);assert(pixels[1].ink>20);assert.equal(pixels[1].transparent,0);assert(pixels[2].red>1000&&pixels[2].transparent>100);assert.equal(pixels[2].blue,0);assert(pixels.every(p=>p.green===0));
  for(const viewport of [{width:320,height:768},{width:390,height:844},{width:844,height:390}]){await page.setViewportSize(viewport);await button('Use selected artwork').scrollIntoViewIfNeeded();assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await page.screenshot({path:path.join(output,`handoff-${viewport.width}.png`)});}
  const handoffs=await page.evaluate(()=>window.__images);for(let index=0;index<handoffs.length;index++)await fs.writeFile(path.join(output,`image-${index}.png`),Buffer.from(handoffs[index].split(',')[1],'base64'));
  assert.equal(await save(),before);await page.reload();assert.equal(await save(),before);assert.deepEqual(errors,[]);assert.deepEqual(external,[]);
  await fs.writeFile(path.join(output,'results.json'),JSON.stringify({pixels,errors,external,scope:'Actual Excalidraw UI and synthetic native image/print boundaries. No Android bitmap/provider/Activity/device execution.'},null,2));
  console.log('PASS: whole/selected/transparent PNG pixels; selection and exact draft retained after failure/retry; Boolean refusal, legacy undefined reply, missing/throwing bridge, reader error/abort, orphan callback cannot unlock print; reload and3viewports;zero page/external errors. Native launch remains unverified.');
 }finally{if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}
})().catch(error=>{console.error(error);process.exitCode=1;});
