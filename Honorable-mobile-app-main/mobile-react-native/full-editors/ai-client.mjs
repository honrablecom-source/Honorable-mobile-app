export function startStudioAi(workerSource, image, kind, modelRgba, progress=()=>{}) {
  if(!['object','denoise','style'].includes(kind))throw Error('Choose an available AI tool.');
  const id=Math.floor(Math.random()*900000000)+1;
  let worker,url,timer,settled=false,rejectTask;
  const clean=()=>{clearTimeout(timer);worker?.terminate();if(url)URL.revokeObjectURL(url);};
  const promise=new Promise((resolve,reject)=>{
    rejectTask=reject;
    const finish=(error,result)=>{if(settled)return;settled=true;clean();error?reject(error):resolve(result);};
    try{
      url=URL.createObjectURL(new Blob([workerSource],{type:'text/javascript'}));worker=new Worker(url);
      worker.onerror=()=>finish(Error('AI is unavailable in this WebView. Your original is unchanged.'));
      worker.onmessage=event=>{
        const value=event.data;
        if(settled||value?.id!==id)return;
        if(value.error)return finish(Error(value.error));
        if(typeof value.phase==='string'){progress(value.phase);return;}
        if(value.width!==image.width||value.height!==image.height||!(value.pixels instanceof ArrayBuffer)||value.pixels.byteLength!==image.rgba.byteLength)
          return finish(Error('The AI result was incomplete. Your original is unchanged.'));
        finish(null,new Uint8ClampedArray(value.pixels));
      };
      timer=setTimeout(()=>finish(Error('AI timed out. Your original is unchanged.')),180000);
      const copy=image.rgba.slice().buffer,modelCopy=modelRgba?.slice().buffer;
      const transfers=modelCopy?[copy,modelCopy]:[copy];
      worker.postMessage({id,kind,base:new URL('/image/',location.origin).href,width:image.width,height:image.height,rgba:copy,modelRgba:modelCopy},transfers);
    }catch(error){finish(error);}
  });
  return {promise,stop(){if(settled)return;settled=true;clean();rejectTask(Error('AI stopped. Your original is unchanged.'));}};
}
