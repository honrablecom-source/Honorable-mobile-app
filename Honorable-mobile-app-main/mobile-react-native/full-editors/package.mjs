// Package only an explicitly built, pinned full engine. Runtime acceptance is separate.
import {readFileSync,writeFileSync,mkdirSync,existsSync,readdirSync,copyFileSync} from 'node:fs';
import {dirname,resolve,join,relative,sep} from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import {gzipSync} from 'node:zlib';
import {execFileSync} from 'node:child_process';
const root=dirname(fileURLToPath(import.meta.url));
const [kind,sourceArg,webArg,noticesArg,outArg]=process.argv.slice(2);
if(!['image','video'].includes(kind)||![sourceArg,webArg,noticesArg,outArg].every(Boolean))throw Error('Usage: node full-editors/package.mjs image|video SOURCE WEB NOTICES FRESH_OUTPUT. Runtime acceptance is separate.');
const source=resolve(sourceArg),web=resolve(webArg),notices=resolve(noticesArg),out=resolve(outArg);
if(existsSync(out))throw Error('Choose a fresh output directory; existing assets are kept.');
const pins=JSON.parse(readFileSync(join(root,'upstreams.json'),'utf8')),pin=pins[kind];
const hash=bytes=>createHash('sha256').update(bytes).digest('hex');
const revision=execFileSync('git',['-C',source,'rev-parse','HEAD'],{encoding:'utf8'}).trim();
if(revision!==pin.revision)throw Error('Unexpected upstream revision.');
const patch=readFileSync(join(root,'patches',pin.patch));
const actualPatch=execFileSync('git',['-C',source,'diff','--binary']);
if(!actualPatch.equals(patch))throw Error('Built checkout differs from the reviewed mobile source patch.');
const inventory=JSON.parse(readFileSync(join(notices,'index.json'),'utf8'));
if(inventory.version!==1||inventory.crate!==pin.crate||inventory.packages.length<150||existsSync(join(notices,'MISSING.json')))throw Error('A complete pinned notices collection is required.');
const wasm=readFileSync(join(web,'editor_bg.wasm'));
if(wasm.length<1000000||wasm.length>96*1024*1024||!wasm.subarray(0,8).equals(Buffer.from([0,97,115,109,1,0,0,0])))throw Error('Actual bounded compiled WebAssembly is required.');
let legal='Noble modified '+pin.repository+' at '+revision+'\nUpstream code: MIT OR Apache-2.0. Dependency/source notices follow unchanged.\n\n';
legal+='Rust1.95.0 standard-library original copyright/license notice\n'+readFileSync(join(root,'licenses/rust-1.95.0/COPYRIGHT-library.html'),'utf8')+'\n\n';
for(const pkg of inventory.packages){legal+=`${pkg.name} ${pkg.version}\nLicense: ${pkg.license}\nSource: ${pkg.source}\n`;for(const file of pkg.notices){const noticePath=resolve(notices,file.file);if(!noticePath.startsWith(notices+sep))throw Error('Invalid notice path.');const data=readFileSync(noticePath);if(data.length!==file.bytes||hash(data)!==file.sha256)throw Error('Changed dependency notice.');legal+='\n--- '+file.file+' ---\n'+data.toString('utf8')+'\n';}legal+='\n';}
mkdirSync(out,{recursive:true});
function copy(from,name){const target=join(out,name);mkdirSync(dirname(target),{recursive:true});copyFileSync(from,target);}
copy(join(web,'editor.js'),'editor.js');
writeFileSync(join(out,'editor_bg.wasm.gz'),gzipSync(wasm,{level:9}));
function copyTree(from,prefix){for(const item of readdirSync(from,{withFileTypes:true})){const path=join(from,item.name),name=prefix+item.name;if(item.isDirectory())copyTree(path,name+'/');else if(item.isFile())copy(path,name);}}
if(existsSync(join(web,'snippets')))copyTree(join(web,'snippets'),'snippets/');
copyTree(join(root,'pages',kind),'');
copy(join(root,'host-bridge.js'),'host-bridge.js');
if(kind==='video')copy(join(source,'apps/filmcraft-web/web/audio-worklet.js'),'audio-worklet.js');
writeFileSync(join(out,'THIRD_PARTY_NOTICES.txt'),legal);
writeFileSync(join(out,'provenance.json'),JSON.stringify({version:1,kind,repository:pin.repository,sourceRevision:revision,patchSha256:hash(patch),lockSha256:hash(readFileSync(join(source,'Cargo.lock'))),bindgen:pin.bindgen,profile:pin.profile,config:pin.config,wasm:{compression:"gzip",bytes:wasm.length,sha256:hash(wasm)},experimental:true,deviceTested:false,dependencyPackages:inventory.packages.length},null,2)+'\n');
const files={};function pinTree(dir){for(const item of readdirSync(dir,{withFileTypes:true})){const path=join(dir,item.name);if(item.isDirectory())pinTree(path);else{const name=relative(out,path).split('\\').join('/'),bytes=readFileSync(path);if(bytes.length<1||bytes.length>96*1024*1024)throw Error('Invalid asset size.');files[name]={bytes:bytes.length,sha256:hash(bytes)};}}}pinTree(out);
writeFileSync(join(out,'bundle.json'),JSON.stringify({version:1,kind,sourceRevision:revision,files},null,2)+'\n');
console.log(JSON.stringify({kind,files:Object.keys(files).length,bytes:Object.values(files).reduce((n,f)=>n+f.bytes,0),experimental:true,deviceTested:false}));
