#!/usr/bin/env node
// Fixed local validation only. No package installation, native build, publishing or uploads.
'use strict';
const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),{spawn}=require('node:child_process');
const browserNames=['schedule','photo-despeckle','photo-redaction','photo-luminance-mask','photo-displacement','photo-seamless','photo-normal-map','photo-distance-field','photo-palette-image','photo-channel-pack','photo-channel-split','photo-sprite-slice','photo-anaglyph','photo-cube-lut','photo-nine-slice','photo-reference-tone','photo-panorama','photo-atlas','photo-annulus','maze','picture-logic','photo-islands','photo-contours','box-net','photo-halftone','photo-blend','photo-chroma-key','photo-relight','photo-radial','photo-cube-faces','photo-document','jigsaw','photo-stitch','photo-ascii','photo-barrier','photo-registration','photo-frequency','photo-vision','photo-carve','photo-gradient','photo-stereogram'];
const onlyBrowser=process.argv.length===2?null:process.argv.length===4&&process.argv[2]==='--browser'&&browserNames.includes(process.argv[3])?process.argv[3]:undefined;
if(onlyBrowser===undefined)throw Error('Usage: node scripts/noble-web-checks.cjs [--browser <known workflow>]');
const root=path.resolve(__dirname,'..'),app=path.join(root,'Honorable-mobile-app-main'),web=path.join(app,'mobile-react-native/creation-web');
const evidence=path.join(root,'.context/noble-web-checks'),assets=path.join(evidence,'drawing');fs.mkdirSync(evidence,{recursive:true});
const list=(directory,suffix)=>fs.readdirSync(directory).filter(name=>name.endsWith(suffix)).sort().map(name=>path.join(directory,name));
// Retain full earlier logs and receipts before the next run replaces the current view.
const earlier=fs.readdirSync(evidence).filter(name=>name.endsWith('.log')||name==='verification.json');
if(earlier.length){
 const archive=path.join(evidence,'runs',new Date().toISOString().replace(/[:.]/g,'-')+'-'+process.pid);fs.mkdirSync(archive,{recursive:true});
 for(const name of earlier)fs.copyFileSync(path.join(evidence,name),path.join(archive,name));
 const manifest=path.join(assets,'noble-drawing-manifest.json');if(fs.existsSync(manifest))fs.copyFileSync(manifest,path.join(archive,'drawing-manifest.json'));
 console.log('Earlier full logs retained in '+path.relative(root,archive));
}
const completed=[];
async function run(name,args,environment={}){
 const logfile=path.join(evidence,name+'.log'),output=fs.createWriteStream(logfile);
 console.log('Running '+name+'…');
 await new Promise((resolve,reject)=>{
  const child=spawn(process.execPath,args,{cwd:root,env:{...process.env,...environment},stdio:['ignore','pipe','pipe']});
  child.stdout.pipe(output,{end:false});child.stderr.pipe(output,{end:false});
  child.once('error',error=>{output.end();reject(error);});
  child.once('close',code=>output.end(()=>{if(code===0)resolve();else{console.error(fs.readFileSync(logfile,'utf8').split('\n').slice(-90).join('\n'));reject(Error(name+' failed; full log: '+logfile));}}));
 });
 completed.push(name);console.log('PASS '+name+' — '+path.relative(root,logfile));
}
async function serve(){
 const mime={'.html':'text/html','.js':'text/javascript','.css':'text/css','.json':'application/json','.woff2':'font/woff2','.woff':'font/woff','.ttf':'font/ttf','.wasm':'application/wasm'};
 const server=http.createServer((request,response)=>{
  try{
   if(!['GET','HEAD'].includes(request.method)){response.writeHead(405).end();return;}
   const pathname=decodeURIComponent(new URL(request.url,'http://localhost').pathname).replace(/^\/drawing\//,'/');
   const file=path.resolve(assets,'.'+(pathname==='/'?'/index.html':pathname));
   if(!file.startsWith(assets+path.sep)||!fs.statSync(file).isFile()){response.writeHead(404).end();return;}
   const body=pathname==='/config.js'?Buffer.from('window.EXCALIDRAW_ASSET_PATH="/drawing/";'):fs.readFileSync(file);
   response.writeHead(200,{'Content-Type':mime[path.extname(file)]||'application/octet-stream','Cache-Control':'no-store','X-Content-Type-Options':'nosniff','Content-Security-Policy':"default-src 'self'; script-src 'self' 'wasm-unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self'; worker-src 'self' blob:; frame-src 'none'; object-src 'none'; base-uri 'none'"});response.end(request.method==='HEAD'?undefined:body);
  }catch{response.writeHead(404).end();}
 });
 await new Promise((resolve,reject)=>{server.once('error',reject);server.listen(0,'127.0.0.1',resolve);});return server;
}
(async()=>{
 if(!onlyBrowser){
 await run('drawing-unit-tests',['--test','--test-isolation=none',...list(path.join(web,'test'),'.test.mjs')]);
 await run('node-product-tests',['--test','--test-isolation=none',...list(path.join(app,'dev-server/test'),'.test.js'),path.join(app,'product/studio/project.test.js'),path.join(app,'scripts/android-sdk-config.test.cjs'),path.join(app,'scripts/release-manifest.test.cjs')]);
 }
 await run('drawing-bundle',[path.join(web,'build.mjs')],{HONORABLE_DRAWING_OUTPUT:assets});
 await run('drawing-manifest',[path.join(app,'scripts/drawing-assets.cjs'),web,assets]);
 const server=await serve();
 try{
  const environment={NOBLE_PREVIEW_URL:`http://127.0.0.1:${server.address().port}/drawing/`};
  for(const name of(onlyBrowser?[onlyBrowser]:browserNames))await run(name+'-browser',[path.join(web,'test',name+'-preview.cjs')],environment);
 }finally{await new Promise(resolve=>server.close(resolve));}
 fs.writeFileSync(path.join(evidence,'verification.json'),JSON.stringify({passed:true,scope:onlyBrowser?'focused-browser':'full',browser:onlyBrowser,completed,nativeBuild:false,deviceTested:false},null,2)+'\n');
 console.log('Focused web validation complete. Native/APK/device acceptance remains separate.');
})().catch(error=>{fs.writeFileSync(path.join(evidence,'verification.json'),JSON.stringify({passed:false,completed,error:error.message},null,2)+'\n');console.error(error.message);process.exitCode=1;});
