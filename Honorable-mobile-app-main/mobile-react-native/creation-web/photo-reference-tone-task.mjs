import {referenceToneSettings} from './photo-reference-tone.mjs';
let sequence=0;
/** One fresh local worker owns each calculation; no stale/foreign/partial result can publish. */
export async function runReferenceToneTask(input,makeWorker,requireCurrent=()=>{},onProgress=()=>{},signal,timeoutMs=30000){
 const check=()=>{if(signal?.aborted)throw Error('Reference tone matching cancelled.');requireCurrent();};check();
 const settings=referenceToneSettings(input?.width,input?.height,input?.referenceWidth,input?.referenceHeight,input?.amount),count=settings.width*settings.height;
 if(typeof makeWorker!=='function'||!Number.isInteger(timeoutMs)||timeoutMs<1||timeoutMs>30000)throw Error('Local tone-matching worker is unavailable.');
 if(!(input.pixels instanceof Uint8Array||input.pixels instanceof Uint8ClampedArray)||!(input.pixels.buffer instanceof ArrayBuffer)||input.pixels.length!==count*4)throw Error('The photo pixels are incomplete.');
 if(!(input.reference instanceof Uint8Array||input.reference instanceof Uint8ClampedArray)||!(input.reference.buffer instanceof ArrayBuffer)||input.reference.length!==settings.referenceWidth*settings.referenceHeight*4)throw Error('Both photo pixel buffers must be complete.');
 const pixels=new Uint8ClampedArray(input.pixels),reference=new Uint8ClampedArray(input.reference),alpha=new Uint8Array(count);let sourceWeight=0,referenceWeight=0;for(let at=3,i=0;at<pixels.length;at+=4)sourceWeight+=alpha[i++]=pixels[at];for(let at=3;at<reference.length;at+=4)referenceWeight+=reference[at];if(!sourceWeight||!referenceWeight)throw Error('Both photos need visible pixels for tone matching.');
 const id=sequence=sequence>=1e9?1:sequence+1;
 return new Promise((resolve,reject)=>{
  let lease,timer,settled=false;
  const cleanup=()=>{clearTimeout(timer);signal?.removeEventListener('abort',abort);if(lease){try{lease.worker.onmessage=lease.worker.onerror=lease.worker.onmessageerror=null;}catch{}try{lease.worker.terminate?.();}catch{}try{lease.dispose?.();}catch{}}};
  const finish=(error,result)=>{if(settled)return;settled=true;cleanup();if(error)reject(error instanceof Error?error:Error('Reference tone matching failed.'));else resolve(result);};
  const abort=()=>finish(Error('Reference tone matching cancelled.'));
  try{
   lease=makeWorker();if(!lease?.worker||typeof lease.dispose!=='function'||typeof lease.worker.postMessage!=='function'||typeof lease.worker.terminate!=='function')throw Error('Local tone-matching worker is unavailable.');
   const worker=lease.worker;
   worker.onmessage=event=>{
    if(settled||event?.data?.id!==id)return;
    try{
     check();const message=event.data;if(message.kind==='error')throw Error(typeof message.error==='string'&&message.error.length<=200?message.error:'The local tone-matching worker could not finish.');
     if(message.kind!=='result')throw Error('The tone-matching worker sent an unsupported result.');
     const result=message.result;
     if(!result||result.width!==settings.width||result.height!==settings.height||result.amount!==settings.amount||result.referenceWidth!==settings.referenceWidth||result.referenceHeight!==settings.referenceHeight||result.sourceWeight!==sourceWeight||result.referenceWeight!==referenceWeight||!Number.isInteger(result.changed)||result.changed<1||result.changed>count||!(result.data instanceof Uint8ClampedArray)||!(result.data.buffer instanceof ArrayBuffer)||result.data.byteOffset!==0||result.data.length!==count*4||result.data.byteLength!==result.data.buffer.byteLength)throw Error('The tone-matching worker returned incomplete data.');
     for(let at=3;at<result.data.length;at+=4)if(result.data[at]!==alpha[(at-3)/4])throw Error('The tone-matching worker changed source alpha.');
     check();onProgress(1);check();finish(null,result);
    }catch(error){finish(error);}
   };
   worker.onerror=event=>{event?.preventDefault?.();finish(Error('The local tone-matching worker stopped. Try again.'));};worker.onmessageerror=()=>finish(Error('The local tone-matched pixels could not be received.'));
   signal?.addEventListener('abort',abort,{once:true});timer=setTimeout(()=>finish(Error('Reference tone matching took too long. Try a smaller photo.')),timeoutMs);
   check();onProgress(0);check();worker.postMessage({id,...settings,pixels,reference},[pixels.buffer,reference.buffer]);
  }catch(error){finish(error);}
 });
}
