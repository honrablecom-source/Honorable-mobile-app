const {chromium}=require('../../../ui-previews/node_modules/playwright');
const assert=require('node:assert/strict'),fs=require('node:fs/promises'),path=require('node:path'),http=require('node:http');
const assets=process.env.NOBLE_TEXT_ASSETS,output=process.env.NOBLE_TEXT_OUTPUT;
const text={id:'title',type:'text',x:40,y:60,width:120,height:25,text:'Keep this title',originalText:'Keep this title',fontSize:20,fontFamily:2,lineHeight:1.25,angle:0,opacity:100,strokeColor:'#111111',locked:false,seed:1,version:1,versionNonce:1};
const draft=element=>JSON.stringify({version:1,elements:[element],appState:{viewBackgroundColor:'#ffffff',currentItemFontFamily:2}});
const portable=element=>JSON.stringify({format:'noble-drawing',version:1,name:'Imported',scene:draft(element)});
(async()=>{assert(assets&&output,'Set isolated assets and output paths.');await fs.mkdir(output,{recursive:true});
 const mime={'.html':'text/html','.js':'text/javascript','.css':'text/css','.json':'application/json','.woff2':'font/woff2','.woff':'font/woff','.ttf':'font/ttf','.wasm':'application/wasm'};
 const server=http.createServer(async(req,res)=>{try{const name=decodeURIComponent(new URL(req.url,'http://localhost').pathname).replace(/^\/drawing\//,'');const file=path.resolve(assets,name||'index.html');if(!file.startsWith(path.resolve(assets)+path.sep))throw Error('path');const value=name==='config.js'?'window.EXCALIDRAW_ASSET_PATH="/drawing/";':await fs.readFile(file);res.writeHead(200,{'Content-Type':mime[path.extname(file)]||'application/octet-stream','Cache-Control':'no-store','Content-Security-Policy':"default-src 'self'; script-src 'self' 'wasm-unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self'; worker-src 'self' blob:; frame-src 'none'; object-src 'none'; base-uri 'none'"}).end(value);}catch{res.writeHead(404).end();}});
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));const origin=`http://127.0.0.1:${server.address().port}`,url=origin+'/drawing/';let browser;
 try{

  browser=await chromium.launch({args:['--no-sandbox']});const results=[];
  for(const originalText of ['x'.repeat(2001),{unexpected:'not text'}]){
    const raw=draft({...text,originalText});const page=await browser.newPage({viewport:{width:390,height:844},acceptDownloads:true}),errors=[],external=[];
    page.on('pageerror',e=>errors.push(e.message));await page.route('**/*',route=>{const u=route.request().url();if(!u.startsWith(origin)&&!u.startsWith('data:')&&!u.startsWith('blob:')){external.push(u);return route.abort();}return route.continue();});
    await page.addInitScript(raw=>{localStorage.setItem('honorable-drawing-v1',raw);window.__writes=0;const set=Storage.prototype.setItem;Storage.prototype.setItem=function(k,v){if(k==='honorable-drawing-v1')window.__writes++;return set.call(this,k,v);};},raw);
    await page.goto(url);const keep=page.getByRole('button',{name:'Keep recovery copy',exact:true});await keep.waitFor();
    const pending=page.waitForEvent('download');await keep.click();const download=await pending;
    assert.deepEqual(JSON.parse(await fs.readFile(await download.path(),'utf8')),{format:'noble-drawing-recovery',version:1,draft:raw});
    for(const viewport of [{width:320,height:768},{width:390,height:844},{width:844,height:390}]){await page.setViewportSize(viewport);await keep.scrollIntoViewIfNeeded();assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await page.screenshot({path:path.join(output,`recovery-${results.length}-${viewport.width}.png`)});}
    assert.equal(await page.evaluate(()=>window.__writes),0);assert.equal(await page.evaluate(()=>localStorage.getItem('honorable-drawing-v1')),raw);
    await page.reload();await keep.waitFor();assert.equal(await page.evaluate(()=>window.__writes),0);assert.equal(await page.evaluate(()=>localStorage.getItem('honorable-drawing-v1')),raw);
    assert.deepEqual(errors,[]);assert.deepEqual(external,[]);results.push({source:typeof originalText,recoveryExact:true,writes:0,errors,external});await page.close();
  }
  const page=await browser.newPage({viewport:{width:390,height:844}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.addInitScript(raw=>{if(!localStorage.getItem('honorable-drawing-v1'))localStorage.setItem('honorable-drawing-v1',raw);},draft(text));
  await page.goto(url);const button=name=>page.getByRole('button',{name,exact:true});await button('Save draft').click();const before=await page.evaluate(()=>localStorage.getItem('honorable-drawing-v1'));
  await button('Projects').click();await button('Advanced transfer').click();
  for(const originalText of ['x'.repeat(2001),{unexpected:'not text'}]){
    const raw=portable({...text,originalText});await page.getByLabel('Project JSON',{exact:true}).fill(raw);
    for(const action of ['Import project JSON','Insert project artwork']){await button(action).click();await page.getByRole('status').getByText(/Use shorter editable text/).waitFor();assert.equal(await page.evaluate(()=>localStorage.getItem('honorable-drawing-v1')),before);}
    await button('Project files').click();await page.getByLabel('Choose a Noble project file',{exact:true}).setInputFiles({name:'oversized.noble.json',mimeType:'application/json',buffer:Buffer.from(raw)});await page.getByRole('status').getByText(/Use shorter editable text/).waitFor();assert.equal(await page.getByRole('region',{name:'Chosen project file',exact:true}).count(),0);assert.equal(await page.evaluate(()=>localStorage.getItem('honorable-drawing-v1')),before);await button('Advanced transfer').click();
  }
  await button('Save draft').click();assert.equal(await page.evaluate(()=>localStorage.getItem('honorable-drawing-v1')),before);assert.deepEqual(errors,[]);await page.close();
  await fs.writeFile(path.join(output,'results.json'),JSON.stringify({recovery:results,existingDrawing:'JSON open/insert and file import refused without changing draft',errors,scope:'Actual browser UI and recovery downloads. No native WebView/provider/device execution.'},null,2));
  console.log('PASS: oversized/nonstring source refused on restore/import/insert/file;exact recovery download/reload/no writes;existing drawing retained;3viewports;no page errors.');
 }finally{if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}
})().catch(error=>{console.error(error);process.exitCode=1;});
