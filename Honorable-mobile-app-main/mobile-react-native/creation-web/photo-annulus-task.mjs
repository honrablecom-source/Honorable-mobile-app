import {annulusSettings} from './photo-annulus.mjs';
let sequence=0;
/** One fresh local worker owns each calculation; no stale/foreign/partial result can publish. */
export async function runAnnulusTask(input,makeWorker,requireCurrent=()=>{},onProgress=()=>{},signal,timeoutMs=30000){
 const check=()=>{if(signal?.aborted)throw Error('Circular-band unwrapping cancelled.');requireCurrent();};check();
 const settings=annulusSettings(input?.sourceWidth,input?.sourceHeight,input?.width,input?.height,input?.cx,input?.cy,input?.inner,input?.outer,input?.start,input?.direction),count=settings.width*settings.height;
 if(typeof makeWorker!=='function'||!Number.isInteger(timeoutMs)||timeoutMs<1||timeoutMs>30000)throw Error('Local annulus worker is unavailable.');
 if(!(input.pixels instanceof Uint8Array||input.pixels instanceof Uint8ClampedArray)||!(input.pixels.buffer instanceof ArrayBuffer)||input.pixels.length!==settings.sourceWidth*settings.sourceHeight*4)throw Error('The annulus pixel buffer is incomplete.');
 const pixels=new Uint8ClampedArray(input.pixels);
 const id=sequence=sequence>=1e9?1:sequence+1;
 return new Promise((resolve,reject)=>{
  let lease,timer,settled=false;
  const cleanup=()=>{clearTimeout(timer);signal?.removeEventListener('abort',abort);if(lease){try{lease.worker.onmessage=lease.worker.onerror=lease.worker.onmessageerror=null;}catch{}try{lease.worker.terminate?.();}catch{}try{lease.dispose?.();}catch{}}};
  const finish=(error,result)=>{if(settled)return;settled=true;cleanup();if(error)reject(error instanceof Error?error:Error('Circular-band unwrapping failed.'));else resolve(result);};
  const abort=()=>finish(Error('Circular-band unwrapping cancelled.'));
  try{
   lease=makeWorker();if(!lease?.worker||typeof lease.dispose!=='function'||typeof lease.worker.postMessage!=='function'||typeof lease.worker.terminate!=='function')throw Error('Local annulus worker is unavailable.');
   const worker=lease.worker;
   worker.onmessage=event=>{
    if(settled||event?.data?.id!==id)return;
    try{
     check();const message=event.data;if(message.kind==='error')throw Error(typeof message.error==='string'&&message.error.length<=200?message.error:'The local annulus worker could not finish.');
     if(message.kind!=='result')throw Error('The annulus worker sent an unsupported result.');
     const result=message.result;
     if(!result||result.width!==settings.width||result.height!==settings.height||result.sourceWidth!==settings.sourceWidth||result.sourceHeight!==settings.sourceHeight||['cx','cy','inner','outer','start','direction'].some(key=>result[key]!==settings[key])||!Number.isInteger(result.visible)||result.visible<1||result.visible>count||!(result.data instanceof Uint8ClampedArray)||!(result.data.buffer instanceof ArrayBuffer)||result.data.byteOffset!==0||result.data.length!==count*4||result.data.byteLength!==result.data.buffer.byteLength)throw Error('The annulus worker returned incomplete data.');
     let visible=0;for(let at=0;at<result.data.length;at+=4){if(result.data[at+3])visible++;else if(result.data[at]||result.data[at+1]||result.data[at+2])throw Error('The annulus worker returned invalid transparent pixels.');}if(visible!==result.visible)throw Error('The annulus worker returned inconsistent pixel counts.');
     check();onProgress(1);check();finish(null,result);
    }catch(error){finish(error);}
   };
   worker.onerror=event=>{event?.preventDefault?.();finish(Error('The local annulus worker stopped. Try again.'));};worker.onmessageerror=()=>finish(Error('The local annulus pixels could not be received.'));
   signal?.addEventListener('abort',abort,{once:true});timer=setTimeout(()=>finish(Error('Circular-band unwrapping took too long. Try smaller strip dimensions.')),timeoutMs);
   check();onProgress(0);check();worker.postMessage({id,...settings,pixels},[pixels.buffer]);
  }catch(error){finish(error);}
 });
}
