import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
const hash = bytes => createHash('sha256').update(bytes).digest('hex');
export function coreInputs(root) {
  const files = {};
  const visit = (directory,prefix='') => {
    for (const name of fs.readdirSync(directory).sort()) {
      if (!prefix && ['pkg','provenance.json','pin.mjs'].includes(name)) continue;
      if (name.startsWith('.') || name === 'target') throw Error('Hidden files and build caches do not belong in the PhotoCraft source tree.');
      const file=path.join(directory,name),relative=prefix?prefix+'/'+name:name,info=fs.lstatSync(file);
      if(info.isSymbolicLink()) throw Error('PhotoCraft source inputs must not be links.');
      if(info.isDirectory())visit(file,relative);
      else if(info.isFile()){const bytes=fs.readFileSync(file);files[relative]={bytes:bytes.length,sha256:hash(bytes)};}
      else throw Error('PhotoCraft source inputs must be regular files.');
    }
  };
  visit(root);
  const upstream=JSON.parse(fs.readFileSync(path.join(root,'UPSTREAM.json'),'utf8'));
  for (const [name,pin] of Object.entries(upstream.files)) {
    if (!files[name] || files[name].sha256 !== pin.sha256) throw Error('Pinned PhotoCraft production source changed: '+name);
  }
  return files;
}
export function runtimePins(root) {
  const files = {};
  for (const name of ['noble_photocraft_core.js','noble_photocraft_core_bg.wasm']) {
    const file=path.join(root,'pkg',name),info=fs.lstatSync(file);
    if(!info.isFile()||info.isSymbolicLink())throw Error('PhotoCraft runtime assets must be regular files.');
    const bytes=fs.readFileSync(file);files[name]={bytes:bytes.length,sha256:hash(bytes)};
  }
  return files;
}
export function pinSource(runtime) {
  return 'export const PHOTOCRAFT_CORE = Object.freeze('+JSON.stringify(runtime)+');\n';
}
export function verifyCore(root) {
  const receipt=JSON.parse(fs.readFileSync(path.join(root,'provenance.json'),'utf8'));
  if(receipt.version!==1 || JSON.stringify(coreInputs(root))!==JSON.stringify(receipt.sources) ||
     JSON.stringify(runtimePins(root))!==JSON.stringify(receipt.runtime) ||
     fs.readFileSync(path.join(root,'pin.mjs'),'utf8')!==pinSource(receipt.runtime))
    throw Error('PhotoCraft source/runtime changed. Reproduce and review npm run build:photocraft before bundling.');
  return receipt;
}
