import {checkedCoreJob} from './photocraft-studio.mjs';
import {PHOTOCRAFT_CORE} from './photocraft-core/pin.mjs';
let started=false;
self.onmessage=async event=>{
  if(started)return;started=true;
  const request=event.data;
  try {
    if(!Number.isSafeInteger(request?.id)||request.id<1)throw Error('Invalid core request.');
    checkedCoreJob(request);
    const base=new URL(request.base);
    if(!['http:','https:'].includes(base.protocol)||base.pathname!=='/drawing/'||base.search||base.hash||base.username||base.password)
      throw Error('Use local bundled core assets.');
    self.postMessage({id:request.id,phase:'Opening the local PhotoCraft processing core…'});
    const wasm=await fetch(new URL('photocraft/noble_photocraft_core_bg.wasm',base));
    if(!wasm.ok)throw Error('Bundled core is unavailable.');
    const bytes=await wasm.arrayBuffer(),pin=PHOTOCRAFT_CORE['noble_photocraft_core_bg.wasm'];
    const digest=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',bytes)),x=>x.toString(16).padStart(2,'0')).join('');
    if(bytes.byteLength!==pin.bytes||digest!==pin.sha256)throw Error('Bundled core integrity failed.');
    const core=await import(new URL('photocraft/noble_photocraft_core.js',base).href);
    await core.default({module_or_path:bytes});
    const {width,height,effect,id}=request;
    let changed=null;
    const layers=request.layers.map(layer=>({...layer,pixels:new Uint8Array(layer.pixels)}));
    if(effect){
      self.postMessage({id,phase:'Applying the reviewed PhotoCraft effect locally…'});
      const layer=layers.find(value=>value.id===effect.layerId);
      changed=core.apply_filter(width,height,layer.pixels,effect.id,effect.value);
      if(changed.length!==width*height*4)throw Error('Incomplete core effect.');
      layer.pixels=changed;
    }
    self.postMessage({id,phase:'Compositing local layers with PhotoCraft…'});
    let preview=new Uint8Array(width*height*4);
    for(const layer of layers)if(layer.visible&&layer.opacity>0)
      preview=core.blend_layers(width,height,preview,layer.pixels,layer.blend,layer.opacity);
    const reply={id,width,height,preview:preview.buffer,layerId:effect?.layerId};
    const transfers=[preview.buffer];
    if(changed){reply.changed=changed.buffer;transfers.push(changed.buffer);}
    self.postMessage(reply,transfers);
  }catch{self.postMessage({id:request?.id,error:'PhotoCraft processing could not finish. Your previous composition was kept.'});}
};
