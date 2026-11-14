// Rebuild the pinned processing libraries; ordinary drawing builds verify/reuse
// the reviewed runtime, so Rust is not silently installed by npm or the app.
import fs from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';
import {coreInputs,runtimePins,pinSource,verifyCore} from './photocraft-core/verify.mjs';
const directory=path.dirname(fileURLToPath(import.meta.url)),root=path.join(directory,'photocraft-core');
const target=path.resolve(process.env.NOBLE_CORE_TARGET||path.join(os.tmpdir(),'noble-photocraft-core-build'));
if(target===root||target.startsWith(root+path.sep))throw Error('Put the Cargo build cache outside the source tree.');
const cargo=process.env.NOBLE_CARGO||'cargo',bindgen=process.env.NOBLE_WASM_BINDGEN||'wasm-bindgen';
function run(command,args,options={}) {
  const result=spawnSync(command,args,{cwd:root,env:{...process.env,CARGO_TARGET_DIR:target},encoding:'utf8',...options});
  if(result.error)throw result.error;
  if(result.status!==0)throw Error((result.stderr||result.stdout||'Core build failed').slice(-20000));
  return result.stdout.trim();
}
const compiler=run(cargo,['--version']),binding=run(bindgen,['--version']);
if(!compiler.startsWith('cargo 1.95.0 ')||binding!=='wasm-bindgen 0.2.106')throw Error('Use the documented Rust1.95.0 and wasm-bindgen0.2.106 toolchain.');
const before=coreInputs(root);
run(cargo,['build','--locked','--release','--target','wasm32-unknown-unknown','-p','noble-photocraft-core']);
await fs.mkdir(path.join(root,'pkg'),{recursive:true});
run(bindgen,[path.join(target,'wasm32-unknown-unknown/release/noble_photocraft_core.wasm'),'--target','web','--out-dir',path.join(root,'pkg')]);
if(JSON.stringify(before)!==JSON.stringify(coreInputs(root)))throw Error('PhotoCraft sources changed during compilation. Rebuild after freezing them.');
const runtime=runtimePins(root),receipt={version:1,upstreamRevision:'f143ce58498f201a8dfc9c07f5e24147c6a22bee',compiler,binding,sources:before,runtime,
  scope:'Five pinned production processing libraries plus bounded Noble mobile adapter; no desktop UI or full editor/PSD/iOS/device/store parity claim'};
await fs.writeFile(path.join(root,'provenance.json'),JSON.stringify(receipt,null,2)+'\n');
await fs.writeFile(path.join(root,'pin.mjs'),pinSource(runtime));
verifyCore(root);
console.log('Reproduced pinned PhotoCraft mobile processing core and source/runtime hashes. Native/device integration is separate.');
