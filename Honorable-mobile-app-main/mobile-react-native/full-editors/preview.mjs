// Loopback-only review of the actual packaged engines. No upload/account API.
import {createServer} from 'node:http';
import {readFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
import {gunzipSync} from 'node:zlib';

const args=process.argv.slice(2);
if(args.length&&!(args.length===2&&args[0]==='--port'&&/^\d+$/.test(args[1])))throw Error('Usage: node full-editors/preview.mjs [--port 4181]');
const port=args.length?Number(args[1]):4181;
if(!Number.isInteger(port)||port<1||port>65535)throw Error('Choose a port from1to65535.');
const root=fileURLToPath(new URL('../android/app/src/main/assets/creative-editors/',import.meta.url));
const assets=new Map();
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
const csp="default-src 'none'; script-src 'self' 'wasm-unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; media-src 'self' blob:; connect-src 'self' blob:; worker-src 'self' blob:; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'";
const landing=Buffer.from(`<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Noble studio review</title><style>body{max-width:640px;margin:48px auto;padding:0 24px;font:18px/1.5 system-ui;color:#111;background:white}a{display:block;padding:16px;margin:16px 0;border:1px solid #111;border-radius:12px;color:#111;text-decoration:none}small{color:#555}</style><h1>Noble studio review</h1><p>Open the real image or video engine from this checkout. Your local files stay in the browser; this server accepts no uploads.</p><a href="/image/?empty&amp;webgl">Image studio · local AI, layers and PNG export</a><a href="/video/?empty&amp;webgl">Video studio · timeline and video export</a><p>Save projects and exported copies to a destination you can reopen. Browser storage can keep imported copies after you close a tab.</p><small>This is a browser review, not the Android app. Account search, native sharing, Android lifecycle and phone performance are not tested here. The published APK may use an earlier source.</small></html>`);
const mime=name=>name.endsWith('.wasm')?'application/wasm':name.endsWith('.js')?'text/javascript':name.endsWith('.html')?'text/html; charset=utf-8':name.endsWith('.json')?'application/json':name.endsWith('.woff2')?'font/woff2':name.endsWith('.woff')?'font/woff':name.endsWith('.png')?'image/png':name.endsWith('.svg')?'image/svg+xml':'application/octet-stream';
const server=createServer((req,res)=>{
  res.setHeader('Content-Security-Policy',csp);res.setHeader('Cache-Control','no-store');res.setHeader('X-Content-Type-Options','nosniff');res.setHeader('Cross-Origin-Resource-Policy','same-origin');
  if(req.headers.host!==`127.0.0.1:${port}`&&req.headers.host!==`localhost:${port}`){res.writeHead(403).end();return;}
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
