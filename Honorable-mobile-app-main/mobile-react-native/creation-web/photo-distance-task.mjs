import {distanceFieldSettings} from './photo-distance-field.mjs';
let sequence=0;
/** One fresh local worker owns each calculation; no stale/foreign/partial result can publish. */
export async function runDistanceTask(input,makeWorker,requireCurrent=()=>{},onProgress=()=>{},signal,timeoutMs=30000){
 const check=()=>{if(signal?.aborted)throw Error('Distance measurement cancelled.');requireCurrent();};check();
 const settings=distanceFieldSettings(input?.width,input?.height,input?.threshold,input?.spread),count=settings.width*settings.height;
 if(typeof makeWorker!=='function'||!Number.isInteger(timeoutMs)||timeoutMs<1||timeoutMs>30000)throw Error('Local distance worker is unavailable.');
 if(!(input.pixels instanceof Uint8Array||input.pixels instanceof Uint8ClampedArray)||!(input.pixels.buffer instanceof ArrayBuffer)||input.pixels.length!==count*4)throw Error('The cutout pixels are incomplete.');
 const pixels=new Uint8ClampedArray(input.pixels);let inside=0;for(let at=3;at<pixels.length;at+=4)if(pixels[at]>=settings.threshold)inside++;
 if(!inside||inside===count)throw Error('Use a cutout with both inside and outside pixels at this alpha threshold.');
 const id=sequence=sequence>=1e9?1:sequence+1;
 return new Promise((resolve,reject)=>{
  let lease,timer,settled=false;
  const cleanup=()=>{clearTimeout(timer);signal?.removeEventListener('abort',abort);if(lease){try{lease.worker.onmessage=lease.worker.onerror=lease.worker.onmessageerror=null;}catch{}try{lease.worker.terminate?.();}catch{}try{lease.dispose?.();}catch{}}};
  const finish=(error,result)=>{if(settled)return;settled=true;cleanup();if(error)reject(error instanceof Error?error:Error('Distance measurement failed.'));else resolve(result);};
  const abort=()=>finish(Error('Distance measurement cancelled.'));
  try{
   lease=makeWorker();if(!lease?.worker||typeof lease.dispose!=='function'||typeof lease.worker.postMessage!=='function'||typeof lease.worker.terminate!=='function')throw Error('Local distance worker is unavailable.');
   const worker=lease.worker;
   worker.onmessage=event=>{
    if(settled||event?.data?.id!==id)return;
    try{
     check();const message=event.data;if(message.kind==='error')throw Error(typeof message.error==='string'&&message.error.length<=200?message.error:'The local distance worker could not finish.');
     if(message.kind!=='result')throw Error('The distance worker sent an unsupported result.');
     const result=message.result;
     if(!result||result.width!==settings.width||result.height!==settings.height||result.threshold!==settings.threshold||result.spread!==settings.spread||result.inside!==inside||!(result.data instanceof Uint8ClampedArray)||!(result.data.buffer instanceof ArrayBuffer)||result.data.byteOffset!==0||result.data.length!==count*4||result.data.byteLength!==result.data.buffer.byteLength)throw Error('The distance worker returned incomplete data.');
     for(let at=0;at<result.data.length;at+=4)if(result.data[at]!==result.data[at+1]||result.data[at]!==result.data[at+2]||result.data[at+3]!==255)throw Error('The distance worker returned invalid grayscale pixels.');
     check();onProgress(1);check();finish(null,result);
    }catch(error){finish(error);}
   };
   worker.onerror=event=>{event?.preventDefault?.();finish(Error('The local distance worker stopped. Try again.'));};worker.onmessageerror=()=>finish(Error('The local distance pixels could not be received.'));
   signal?.addEventListener('abort',abort,{once:true});timer=setTimeout(()=>finish(Error('Distance measurement took too long. Try a smaller cutout.')),timeoutMs);
   check();onProgress(0);check();worker.postMessage({id,...settings,pixels},[pixels.buffer]);
  }catch(error){finish(error);}
 });
}
