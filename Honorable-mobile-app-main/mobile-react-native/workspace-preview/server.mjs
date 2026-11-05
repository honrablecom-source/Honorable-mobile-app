import {createServer} from 'node:http';
import {readFile,stat} from 'node:fs/promises';
import {watch} from 'node:fs';
import {spawn} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
const root=path.dirname(fileURLToPath(import.meta.url)),output=path.resolve(root,'../../../.context/noble-mobile-preview'),drawing=path.resolve(root,'../../../.context/noble-editor-preview');
const index=process.argv.indexOf('--port'),port=index<0?4177:Number(process.argv[index+1]);
if(!Number.isInteger(port)||port<1024||port>65535)throw Error('Choose a port from 1024 to 65535.');
let building=false,again=false;
async function compile(){if(building){again=true;return;}building=true;try{await new Promise((resolve,reject)=>{const child=spawn(process.execPath,['build.mjs'],{cwd:root,env:process.env,stdio:'inherit'});child.on('error',reject);child.on('exit',code=>code===0?resolve():reject(Error('Mobile preview compilation failed.')));});}finally{building=false;if(again){again=false;compile().catch(console.error);}}}
async function compileDrawing(){await new Promise((resolve,reject)=>{const child=spawn(process.execPath,['build.mjs'],{cwd:path.resolve(root,'../creation-web'),env:{...process.env,HONORABLE_DRAWING_OUTPUT:drawing},stdio:'inherit'});child.on('error',reject);child.on('exit',code=>code===0?resolve():reject(Error('Drawing preview assets are missing. Run npm ci in creation-web first.')));});}
await compileDrawing();
await compile();
const mime={'.html':'text/html','.js':'text/javascript','.css':'text/css','.woff2':'font/woff2','.woff':'font/woff','.ttf':'font/ttf','.wasm':'application/wasm','.json':'application/json','.png':'image/png','.jpg':'image/jpeg'};
const server=createServer(async(request,response)=>{try{
 if(request.method!=='GET'&&request.method!=='HEAD'){response.writeHead(405).end();return;}
 const pathname=decodeURIComponent(new URL(request.url,'http://localhost').pathname),isDrawing=pathname.startsWith('/drawing/'),base=isDrawing?drawing:output;
 let relative=isDrawing?pathname.slice('/drawing'.length):pathname;if(relative==='/')relative='/index.html';
 const file=path.resolve(base,'.'+relative);if(!file.startsWith(base+path.sep)){response.writeHead(403).end();return;}
 if(!(await stat(file)).isFile()){response.writeHead(404).end();return;}
 const body=isDrawing&&relative==='/config.js'?Buffer.from('window.EXCALIDRAW_ASSET_PATH="/drawing/";'):await readFile(file);
 response.writeHead(200,{'Content-Type':mime[path.extname(file)]||'application/octet-stream','Cache-Control':'no-store','X-Content-Type-Options':'nosniff','Referrer-Policy':'no-referrer','Content-Security-Policy':"default-src 'self'; script-src 'self' 'wasm-unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; media-src blob: data:; font-src 'self' data:; connect-src 'self'; worker-src 'self' blob:; frame-src 'self'; object-src 'none'; base-uri 'none'; form-action 'none'"});response.end(request.method==='HEAD'?undefined:body);
 }catch{response.writeHead(404).end();}});
server.listen(port,'0.0.0.0',()=>console.log(`Noble interactive mobile preview: http://localhost:${port} · forward this port privately in the workspace Ports panel.`));
let timer;const watchers=[root,path.join(root,'../src'),path.resolve(root,'../../android-app/app/src/main/java/app/honorable/editor'),path.resolve(root,'../android/app/src/main/java/com/honorablemobile')].map(directory=>watch(directory,{recursive:directory!==root},(_,name)=>{if(!name||!/^.*\.(jsx|mjs|tsx?|css|html|kt|png|json)$/.test(name)||name==='server.mjs')return;clearTimeout(timer);timer=setTimeout(()=>compile().catch(console.error),200);}));
function close(){clearTimeout(timer);watchers.forEach(watcher=>watcher.close());server.close();}process.on('SIGTERM',close);process.on('SIGINT',close);
