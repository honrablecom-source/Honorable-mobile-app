// Explicit opt-in build. Never downloads executable tools or mutates an existing checkout.
import {spawnSync} from 'node:child_process';
import {readFileSync,existsSync,mkdirSync} from 'node:fs';
import {resolve,dirname,join} from 'node:path';
import {fileURLToPath} from 'node:url';
const root=dirname(fileURLToPath(import.meta.url));
const pins=JSON.parse(readFileSync(join(root,'upstreams.json'),'utf8'));
const [kind,workArg]=process.argv.slice(2);
if(!['image','video'].includes(kind)||!workArg)throw Error('Usage: node full-editors/build.mjs image|video /absolute/empty-build-directory. Requires Rust1.95 with wasm32 target and the pinned wasm-bindgen CLI on PATH.');
const work=resolve(workArg),pin=pins[kind],source=join(work,'source'),target=join(work,'target'),output=join(work,'web');
function run(command,args,options={}){const r=spawnSync(command,args,{stdio:'inherit',...options});if(r.error)throw r.error;if(r.status!==0)throw Error(`${command} failed (${r.status})`);}
function version(command,args){const r=spawnSync(command,args,{encoding:'utf8'});if(r.error||r.status!==0)throw Error(`${command} unavailable`);return r.stdout.trim();}
if(!version('rustc',['--version']).startsWith('rustc 1.95.0 '))throw Error('Use pinned Rust1.95.0.');
if(version('wasm-bindgen',['--version'])!==`wasm-bindgen ${pin.bindgen}`)throw Error(`Use wasm-bindgen ${pin.bindgen}.`);
if(existsSync(source))throw Error('Choose a fresh build directory; existing source is never overwritten.');
mkdirSync(work,{recursive:true});run('git',['init',source]);
run('git',['-C',source,'remote','add','origin',pin.repository]);run('git',['-C',source,'fetch','--depth','1','origin',pin.revision]);run('git',['-C',source,'checkout','--detach',pin.revision]);
run('git',['-C',source,'apply','--check',join(root,'patches',pin.patch)]);run('git',['-C',source,'apply',join(root,'patches',pin.patch)]);
const args=['build','-p',pin.crate,'--target','wasm32-unknown-unknown','--profile',pin.profile,'--locked','-j','2'];for(const setting of pin.config)args.push('--config',setting);
run('cargo',args,{cwd:source,env:{...process.env,CARGO_TARGET_DIR:target}});
mkdirSync(output,{recursive:true});run('wasm-bindgen',[join(target,'wasm32-unknown-unknown',pin.profile,pin.wasm),'--target','web','--out-dir',output,'--out-name','editor','--no-typescript']);
console.log(`Compiled ${kind} engine to ${output}. This is an engine build, not mobile UI/import/export acceptance or an app package.`);
