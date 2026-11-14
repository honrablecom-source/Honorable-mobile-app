import {checkedCoreJob} from './photocraft-studio.mjs';
export function startPhotoCraft(source,job,base,progress=()=>{},factory=url=>new Worker(url)) {
  checkedCoreJob(job);
  const directory=new URL(base);
  if(directory.origin!==location.origin||directory.pathname!=='/drawing/'||directory.search||directory.hash)
    throw Error('PhotoCraft only loads bundled local assets.');
  const id=Math.floor(Math.random()*900000000)+1;
  let worker,url,timer,settled=false,rejectTask;
  const cleanup=()=>{clearTimeout(timer);worker?.terminate();if(url)URL.revokeObjectURL(url);};
  const promise=new Promise((resolve,reject)=>{
    rejectTask=reject;
    const finish=(error,value)=>{if(settled)return;settled=true;cleanup();if(error)reject(error);else resolve(value);};
    try {
      url=URL.createObjectURL(new Blob([source],{type:'text/javascript'}));worker=factory(url);
      worker.onerror=()=>finish(Error('The local processing core could not start. Your composition was kept.'));
      worker.onmessage=event=>{
        const value=event.data;if(settled||value?.id!==id)return;
        if(value.error){finish(Error(String(value.error)));return;}
        if(typeof value.phase==='string'){progress(value.phase);return;}
        if(value.width!==job.width||value.height!==job.height||!(value.preview instanceof ArrayBuffer)||value.preview.byteLength!==job.width*job.height*4||
           (job.effect&&(!(value.changed instanceof ArrayBuffer)||value.changed.byteLength!==job.width*job.height*4||value.layerId!==job.effect.layerId))){
          finish(Error('The local processing core returned an incomplete image. Your composition was kept.'));return;
        }
        finish(null,{preview:new Uint8ClampedArray(value.preview),changed:job.effect?new Uint8ClampedArray(value.changed):null,layerId:value.layerId});
      };
      timer=setTimeout(()=>finish(Error('The effect timed out. Your composition was kept.')),90000);
      const layers=job.layers.map(layer=>({...layer,pixels:layer.pixels.slice(0)}));
      worker.postMessage({...job,id,base:directory.href,layers},layers.map(layer=>layer.pixels));
    }catch(error){finish(error);}
  });
  return {promise,stop(){if(settled)return;settled=true;cleanup();rejectTask(Error('PhotoCraft processing stopped. Your composition was kept.'));}};
}
