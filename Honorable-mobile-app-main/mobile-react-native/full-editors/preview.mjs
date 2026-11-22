// Loopback-only review of the actual packaged engines. No upload/account API.
import {createServer} from 'node:http';
import {readFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
import {gunzipSync} from 'node:zlib';
import {dispatch, planWithOllama, protocol} from './editing-agent/mcp.mjs';
import {createDesktopMcp} from './editing-agent/desktop-mcp.mjs';

const args=process.argv.slice(2);
if(args.length&&!(args.length===2&&args[0]==='--port'&&/^\d+$/.test(args[1])))throw Error('Usage: node full-editors/preview.mjs [--port 4181]');
const port=args.length?Number(args[1]):4181;
if(!Number.isInteger(port)||port<1||port>65535)throw Error('Choose a port from1to65535.');
const root=fileURLToPath(new URL('../android/app/src/main/assets/creative-editors/',import.meta.url));
const assets=new Map();
const model=process.env.NOBLE_EDIT_MODEL;
const desktopMcp=process.env.NOBLE_WINDOWS_DESKTOP==='1' && process.parentPort
  ? createDesktopMcp(process.parentPort) : null;
if(model && (!/^[\w.-]+(?::[\w.-]+)?$/.test(model) || /cloud/i.test(model))) {
  throw Error('Choose a local Ollama model name.');
}
for(const kind of ['image','video']){
  const receipt=JSON.parse(await readFile(path.join(root,kind,'bundle.json'),'utf8'));
  if(receipt.version!==1||receipt.kind!==kind)throw Error('Invalid studio receipt.');
  for(const [name,pin] of Object.entries(receipt.files)){
    if(!/^[A-Za-z0-9_./-]+$/.test(name)||name.split('/').some(x=>!x||x==='.'||x==='..')||!Number.isInteger(pin.bytes)||pin.bytes<1||pin.bytes>96*1024*1024)throw Error('Invalid studio asset.');
    const bytes=await readFile(path.join(root,kind,name));
    if(bytes.length!==pin.bytes||createHash('sha256').update(bytes).digest('hex')!==pin.sha256)throw Error('Studio assets differ from their receipt. Rebuild them before review.');
    const uri=`/${kind}/${name}`;
    assets.set(uri,bytes);
    if(name.endsWith('.wasm.gzip'))assets.set(uri.slice(0,-5),gunzipSync(bytes,{maxOutputLength:96*1024*1024}));
  }
}
if(model){
  assets.set('/video/noble-agent.js',await readFile(new URL('./editing-agent/panel.js',import.meta.url)));
  const html=assets.get('/video/index.html').toString('utf8');
  assets.set('/video/index.html',Buffer.from(html.replace('</body>','<script defer src="/video/noble-agent.js"></script></body>')));
}
const csp="default-src 'none'; script-src 'self' 'wasm-unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; media-src 'self' blob:; connect-src 'self' blob:; worker-src 'self' blob:; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'";
const landing=Buffer.from(`<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Noble studio review</title><style>body{max-width:640px;margin:48px auto;padding:0 24px;font:18px/1.5 system-ui;color:#111;background:white}a{display:block;padding:16px;margin:16px 0;border:1px solid #111;border-radius:12px;color:#111;text-decoration:none}small{color:#555}</style><h1>Noble studio review</h1><p>Open the real image or video engine from this checkout. Your local files stay in the browser; this server accepts no uploads.</p><a href="/image/?empty&amp;webgl">Image studio · local AI, layers and PNG export</a><a href="/video/?empty&amp;webgl">Video studio · timeline and video export</a><p>Save projects and exported copies to a destination you can reopen. Browser storage can keep imported copies after you close a tab.</p><small>This is a browser review, not the Android app. Account search, native sharing, Android lifecycle and phone performance are not tested here. The published APK may use an earlier source.</small></html>`);
if(process.env.NOBLE_WINDOWS_DESKTOP==='1'){
  for(const name of ['home.html','home.css','image-studio-capuchin.jpg','video-studio-tiger.jpg','library.js','video-samples.js','storyboard.js','cinematic-panel.js','cinematic-media.js','video-mcp.js','video-controls.js', 'video-changes.js', 'video-export.js','workspace.js','workspace.css','photo-workspace.js','photo-workspace.css','photo-agent.js','photo-settings.js','noble.png','semantic-bundle.js','model_int8.onnx','tokenizer.json']){
    assets.set('/desktop/'+name,await readFile(new URL('./windows/'+name,import.meta.url)));
  }
  assets.set('/desktop/photo-recipe.js',await readFile(new URL('./editing-agent/photo-recipe.mjs',import.meta.url)));
  const desktopBootstrap=assets.get('/video/bootstrap.js').toString('utf8')
    .replace('Retry or update Android System WebView.', 'Retry with compatibility settings.');
  assets.set('/video/bootstrap.js',Buffer.from(desktopBootstrap));
  const desktopImageBootstrap=assets.get('/image/bootstrap.js').toString('utf8')
    .replace('Close and reopen it with an updated Android System WebView.','Close Image Studio and try opening it again.');
  assets.set('/image/bootstrap.js',Buffer.from(desktopImageBootstrap));
  for(const kind of ['image','video']){
    const page='/' + kind + '/index.html';
    const tools=kind==='video'?'<script defer src="/desktop/storyboard.js"></script><script type="module" src="/desktop/cinematic-panel.js"></script>':'';
    const styles=kind==='image'?'<link rel="stylesheet" href="/desktop/photo-workspace.css">':'';
    const photo=kind==='image'?'<script type="module" src="/desktop/photo-workspace.js"></script>':'';
    const html=assets.get(page).toString('utf8')
      .replace('</head>','<link rel="stylesheet" href="/desktop/workspace.css">'+styles+'</head>')
      .replace('</body>',tools+'<script type="module" src="/desktop/workspace.js"></script>'+photo+'</body>');
    assets.set(page,Buffer.from(html));
  }
}
const mime=name=>name.endsWith('.css')?'text/css; charset=utf-8':name.endsWith('.wasm')?'application/wasm':name.endsWith('.js')?'text/javascript':name.endsWith('.html')?'text/html; charset=utf-8':name.endsWith('.json')?'application/json':name.endsWith('.woff2')?'font/woff2':name.endsWith('.woff')?'font/woff':name.endsWith('.png')?'image/png':name.endsWith('.jpg')?'image/jpeg':name.endsWith('.svg')?'image/svg+xml':'application/octet-stream';
const server=createServer(async(req,res)=>{
  res.setHeader('Content-Security-Policy',csp);res.setHeader('Cache-Control','no-store');res.setHeader('X-Content-Type-Options','nosniff');res.setHeader('Cross-Origin-Resource-Policy','same-origin');
  if(req.headers.host!==`127.0.0.1:${port}`&&req.headers.host!==`localhost:${port}`){res.writeHead(403).end();return;}
  if(desktopMcp && req.url==='/mcp/desktop') return desktopMcp(req,res);
  if(model && req.url==='/mcp/status' && req.method==='GET' && process.env.NOBLE_WINDOWS_DESKTOP==='1'){
    let ready=false;
    let running=false;
    try{
      const reply=await fetch('http://127.0.0.1:11434/api/tags',{redirect:'error',signal:AbortSignal.timeout(2500)});
      running=reply.ok;
      if(running) ready=(await reply.json()).models?.some(item=>item.name===model)===true;
    }catch{}
    res.writeHead(200,{'Content-Type':'application/json'}).end(JSON.stringify({model,running,ready}));
    return;
  }
  if(model && req.url==='/mcp' && req.method==='POST'){
    const origin=req.headers.origin;
    if((origin && origin!==`http://${req.headers.host}`) ||
       req.headers['mcp-protocol-version']!==protocol ||
       !req.headers['content-type']?.startsWith('application/json')){
      res.writeHead(403).end();return;
    }
    try{
      const chunks=[];
      let size=0;
      for await(const chunk of req){
        size+=chunk.length;
        if(size>32000){res.writeHead(413).end();return;}
        chunks.push(chunk);
      }
      const desktop=process.env.NOBLE_WINDOWS_DESKTOP==='1';
      const result=await dispatch(JSON.parse(Buffer.concat(chunks)),args=>planWithOllama(args,model),desktop?model:undefined,desktop?{model}:undefined);
      res.writeHead(200,{'Content-Type':'application/json'}).end(JSON.stringify(result));
    }catch{res.writeHead(400).end();}
    return;
  }
  if(req.method!=='GET'&&req.method!=='HEAD'){res.writeHead(405,{'Allow':'GET, HEAD'}).end();return;}
  let name;try{name=new URL(req.url,'http://127.0.0.1').pathname;}catch{res.writeHead(400).end();return;}
  if(name==='/image/'||name==='/video/')name+='index.html';
  const bytes=name==='/'?landing:assets.get(name);
  if(!bytes){res.writeHead(404).end();return;}
  res.setHeader('Content-Type',name==='/'?'text/html; charset=utf-8':mime(name));res.setHeader('Content-Length',bytes.length);
  res.end(req.method==='HEAD'?undefined:bytes);
});
server.on('error',error=>{console.error(error.code==='EADDRINUSE'?'Preview port already in use. Choose another port.':'Preview server could not start.');process.exitCode=1;});
server.listen(port,'127.0.0.1',()=>console.log(`Noble browser review: http://127.0.0.1:${port}/ — real studio assets; no Android/production-account acceptance.`));
