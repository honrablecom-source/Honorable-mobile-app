// Real bundled ONNX/WASM, trusted-origin CSP, React review/apply and model Undo.
// Uses generated colors only; never private media or search benchmarks.
const {chromium}=require('../../../ui-previews/node_modules/playwright');
const {createServer}=require('node:http');
const fs=require('node:fs/promises'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../../../..');
const output=process.env.NOBLE_STYLE_OUTPUT||path.join(root,'.context/noble-fast-ai50/drawing');
const evidence=path.join(root,'.context/noble-fast-ai50');
(async()=>{
  let hold=false,release=null,held=0,corrupt=false;
  const requests=[],external=[],errors=[];
  const server=createServer(async(req,res)=>{
    try {
      const name=new URL(req.url,'http://localhost').pathname.replace(/^\/drawing\//,'/');
      if(name.includes('..')){res.writeHead(403).end();return;}
      const file=path.resolve(output,'.'+(name==='/'?'/index.html':name));
      if(!file.startsWith(output+path.sep)){res.writeHead(403).end();return;}
      requests.push(name);
      if(name==='/ai/mosaic-9.onnx'&&hold){held++;await new Promise(resolve=>{release=resolve;});}
      const data=name==='/config.js'?'window.EXCALIDRAW_ASSET_PATH="/drawing/";':
        name==='/ai/mosaic-9.onnx'&&corrupt?Buffer.from('corrupted-model'):await fs.readFile(file);
      const mime={'.html':'text/html','.js':'text/javascript','.css':'text/css','.json':'application/json','.wasm':'application/wasm','.woff2':'font/woff2','.woff':'font/woff','.ttf':'font/ttf'};
      res.writeHead(200,{'Content-Type':mime[path.extname(file)]||'application/octet-stream','Cache-Control':'no-store','X-Content-Type-Options':'nosniff',
        'Content-Security-Policy':"default-src 'none'; script-src 'self' 'wasm-unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self' blob:; worker-src 'self' blob:; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'"});
      res.end(data);
    }catch{res.writeHead(404).end();}
  });
  await new Promise(r=>server.listen(0,'127.0.0.1',r));
  let browser;
  try {
    browser=await chromium.launch({headless:true,args:['--no-sandbox','--enable-unsafe-swiftshader']});
    const page=await browser.newPage({viewport:{width:390,height:844},deviceScaleFactor:1});
    page.setDefaultTimeout(30000);
    page.on('pageerror',e=>errors.push(e.message));
    const origin='http://127.0.0.1:'+server.address().port;
    page.on('request',r=>{if(/^https?:/.test(r.url())&&!r.url().startsWith(origin+'/'))external.push(r.url());});
    await page.addInitScript(()=>{
      window.__neuralWorkers=[];
      const Original=window.Worker;
      window.Worker=class extends Original {constructor(...args){super(...args);this.stopped=0;this.phases=[];this.addEventListener('message',e=>{if(e.data?.phase)this.phases.push(e.data.phase);});window.__neuralWorkers.push(this);}terminate(){this.stopped++;return super.terminate();}};
    });
    await page.goto(origin);
    await page.getByRole('button',{name:'Model',exact:true}).click();
    await page.getByRole('button',{name:'Share GLB',exact:true}).waitFor();
    const texture=await page.evaluate(()=>{
      const canvas=document.createElement('canvas');canvas.width=160;canvas.height=96;
      const c=canvas.getContext('2d');c.fillStyle='#d34523';c.fillRect(0,0,160,96);
      c.fillStyle='#1268c9';c.fillRect(10,8,60,70);c.fillStyle='#f5d324';c.beginPath();c.arc(110,48,34,0,Math.PI*2);c.fill();
      c.clearRect(0,0,6,6);return canvas.toDataURL('image/png');
    });
    await page.getByLabel('Model texture photo',{exact:true}).setInputFiles({name:'synthetic.png',mimeType:'image/png',buffer:Buffer.from(texture.split(',')[1],'base64')});
    await page.getByRole('button',{name:'Remove texture',exact:true}).waitFor();
    const save=page.getByRole('button',{name:'Save model',exact:true});await save.click();
    const baseline=await page.evaluate(()=>localStorage.getItem('noble-model-studio-v1'));
    const open=()=>page.getByRole('button',{name:'Neural painted texture',exact:true}).click();
    const tool=page.getByRole('region',{name:'Neural painted texture',exact:true});
    const paint=()=>tool.getByRole('button',{name:'Paint with local AI',exact:true}).click();
    const result=()=>tool.getByAltText('Reviewed neural painted texture',{exact:true});
    await open();await paint();const start=Date.now();await result().waitFor({timeout:120000});const firstInferenceMs=Date.now()-start;
    assert.equal(await tool.getByRole('button',{name:'Apply reviewed model texture',exact:true}).isDisabled(),true);
    assert.equal(await page.evaluate(()=>localStorage.getItem('noble-model-studio-v1')),baseline);
    const original=await tool.getByAltText('Original texture before neural painting').getAttribute('src');
    const styled=await result().getAttribute('src');assert.notEqual(styled,original);
    const pixels=await page.evaluate(async({original,styled})=>{
      const decode=url=>new Promise(resolve=>{const image=new Image();image.onload=()=>{const c=document.createElement('canvas');c.width=image.width;c.height=image.height;const ctx=c.getContext('2d');ctx.drawImage(image,0,0);resolve({width:c.width,height:c.height,data:[...ctx.getImageData(0,0,c.width,c.height).data]});};image.src=url;});
      const a=await decode(original),b=await decode(styled);let changed=0;for(let i=0;i<a.data.length;i++){if(i%4===3&&a.data[i]!==b.data[i])throw Error('Alpha changed');if(i%4!==3&&a.data[i]!==b.data[i])changed++;}return{width:b.width,height:b.height,changed};
    },{original,styled});
    assert.equal(pixels.width,160);assert.equal(pixels.height,96);assert(pixels.changed>10000);
    await tool.getByRole('checkbox').check();await tool.getByLabel('Neural paint strength').fill('0.5');
    assert.equal(await tool.getByRole('checkbox').isChecked(),false);
    await tool.getByRole('checkbox').check();await tool.getByRole('button',{name:'Apply reviewed model texture',exact:true}).click();
    await tool.waitFor({state:'detached'});await save.click();
    const applied=JSON.parse(await page.evaluate(()=>localStorage.getItem('noble-model-studio-v1')));
    assert.notEqual(applied.texture,JSON.parse(baseline).texture);assert.equal(applied.color,'#ffffff');
    await page.getByRole('button',{name:'Undo model edit',exact:true}).click();await save.click();
    assert.equal(await page.evaluate(()=>localStorage.getItem('noble-model-studio-v1')),baseline);
    // Real worker termination while its model request is held; late bytes may not apply.
    await open();hold=true;await paint();await page.waitForFunction(()=>window.__neuralWorkers.length>=2);
    const until=Date.now()+20000;while(!held&&Date.now()<until)await new Promise(r=>setTimeout(r,50));assert.equal(held,1);
    await tool.getByRole('button',{name:'Stop neural painting',exact:true}).click();
    hold=false;release();release=null;
    assert.equal(await result().count(),0);
    assert((await page.evaluate(()=>window.__neuralWorkers.map(w=>w.stopped))).every(x=>x===1));
    assert.equal(await page.evaluate(()=>localStorage.getItem('noble-model-studio-v1')),baseline);
    // Tampering is refused, never replaced by an ordinary filter masquerading as AI.
    corrupt=true;await paint();await tool.getByText(/Neural painting could not finish/).waitFor();corrupt=false;
    assert.equal(await result().count(),0);
    // Add the reviewed standalone copy through the existing protected drawing owner.
    await paint();await result().waitFor({timeout:120000});await tool.getByRole('checkbox').check();
    await tool.getByRole('button',{name:'Add painted image to drawing',exact:true}).click();
    await tool.waitFor({state:'detached'});
    await page.getByRole('button',{name:'Save draft',exact:true}).click();
    const draft=await page.evaluate(()=>JSON.parse(localStorage.getItem('honorable-drawing-v1')));
    assert.equal(draft.elements.filter(e=>e.type==='image'&&!e.isDeleted).length,1);
    // Second genuinely different model: learned luminance noise removal, not style presets.
    await page.getByRole('button',{name:'Model',exact:true}).click();
    const noisy=await page.evaluate(()=>{
      let state=79131;const random=()=>{state=(Math.imul(state,1664525)+1013904223)>>>0;return(state+1)/4294967297;};
      const canvas=document.createElement('canvas');canvas.width=160;canvas.height=96;const ctx=canvas.getContext('2d'),pixels=ctx.createImageData(160,96),clean=new Uint8ClampedArray(pixels.data.length);
      for(let y=0;y<96;y++)for(let x=0;x<160;x++){
        const i=(y*160+x)*4,value=x>=50&&x<90&&y>=25&&y<70?210:Math.round(45+130*x/159+15*Math.sin(y/14));
        const noise=Math.sqrt(-2*Math.log(random()))*Math.cos(2*Math.PI*random())*25;
        for(let c=0;c<3;c++){clean[i+c]=value;pixels.data[i+c]=Math.round(value+noise);}clean[i+3]=pixels.data[i+3]=255;
      }
      pixels.data[3]=clean[3]=0;ctx.putImageData(pixels,0,0);window.__neuralClean=[...clean];return canvas.toDataURL('image/png');
    });
    await page.getByLabel('Model texture photo',{exact:true}).setInputFiles({name:'synthetic-noise.png',mimeType:'image/png',buffer:Buffer.from(noisy.split(',')[1],'base64')});
    await page.getByRole('button',{name:'Remove texture',exact:true}).waitFor();await save.click();
    const noiseBaseline=await page.evaluate(()=>localStorage.getItem('noble-model-studio-v1'));
    await page.getByRole('button',{name:'Neural noise cleanup',exact:true}).click();
    const cleanup=page.getByRole('region',{name:'Neural noise cleanup',exact:true});
    const cleanButton=cleanup.getByRole('button',{name:'Clean noise with local AI',exact:true});
    await cleanButton.click();const noiseStart=Date.now();
    await cleanup.getByAltText('Reviewed neural noise cleanup',{exact:true}).waitFor({timeout:120000});
    const noiseInferenceMs=Date.now()-noiseStart;
    const quality=await page.evaluate(async()=>{
      const images=[...document.querySelectorAll('section[aria-label="Neural noise cleanup"] img')];
      const read=img=>{const canvas=document.createElement('canvas');canvas.width=img.naturalWidth;canvas.height=img.naturalHeight;const c=canvas.getContext('2d');c.drawImage(img,0,0);return c.getImageData(0,0,canvas.width,canvas.height).data;};
      const before=read(images[0]),after=read(images[1]),clean=window.__neuralClean;let old=0,next=0,n=0;
      for(let i=0;i<before.length;i+=4){if(before[i+3]!==after[i+3])throw Error('Noise cleanup changed alpha');if(before[i+3]!==255)continue;old+=(before[i]-clean[i])**2;next+=(after[i]-clean[i])**2;n++;}return{beforeMse:old/n,afterMse:next/n};
    });
    assert(quality.afterMse<quality.beforeMse/2,JSON.stringify(quality));
    assert.equal(await cleanup.getByRole('button',{name:'Apply reviewed model texture',exact:true}).isDisabled(),true);
    assert.equal(await page.evaluate(()=>localStorage.getItem('noble-model-studio-v1')),noiseBaseline);
    await cleanup.getByRole('checkbox').check();await cleanup.getByRole('button',{name:'Apply reviewed model texture',exact:true}).click();
    await cleanup.waitFor({state:'detached'});await save.click();assert.notEqual(await page.evaluate(()=>localStorage.getItem('noble-model-studio-v1')),noiseBaseline);
    await page.getByRole('button',{name:'Undo model edit',exact:true}).click();await save.click();assert.equal(await page.evaluate(()=>localStorage.getItem('noble-model-studio-v1')),noiseBaseline);
    // Stop while actual tiled WASM inference has started, not merely during model fetch.
    await page.getByRole('button',{name:'Neural noise cleanup',exact:true}).click();await cleanButton.click();
    await page.waitForFunction(()=>window.__neuralWorkers.at(-1)?.phases.some(x=>x.startsWith('Removing luminance noise locally')));
    await cleanup.getByRole('button',{name:'Stop neural cleanup',exact:true}).click();
    assert.equal(await cleanup.getByAltText('Reviewed neural noise cleanup',{exact:true}).count(),0);
    assert.equal(await page.evaluate(()=>window.__neuralWorkers.at(-1).stopped),1);
    await cleanup.getByRole('button',{name:'Close neural cleanup',exact:true}).click();
    await page.setViewportSize({width:320,height:740});
    const overflow=await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1);assert.equal(overflow,false);
    assert(requests.includes('/ai/ort-wasm-simd-threaded.js'));assert(requests.includes('/ai/ort-wasm-simd-threaded.wasm'));
    assert.deepEqual(external,[]);assert.deepEqual(errors,[]);
    await page.screenshot({path:path.join(evidence,'neural-painted-drawing.png'),fullPage:true});
    const receipt={model:'mosaic-9',denoiseModel:'dncnn25-128',actualBundledWasm:true,firstInferenceMs,noiseInferenceMs,noiseQuality:quality,pixels,checks:['two real neural models','review gate','strength resets approval','explicit apply','exact model Undo for both tools','saved original unchanged before apply','Stop terminates worker','corrupt weights refused','separate drawing image','actual denoise WASM Stop','generated noise MSE improved','320px no overflow','no external requests','no page errors'],external,errors};
    await fs.writeFile(path.join(evidence,'browser-receipt.json'),JSON.stringify(receipt,null,2)+'\n');console.log(JSON.stringify(receipt));
  }finally{release?.();await browser?.close();server.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
