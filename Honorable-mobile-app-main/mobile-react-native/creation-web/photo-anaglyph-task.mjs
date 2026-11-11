import {anaglyphSettings} from './photo-anaglyph.mjs';
let sequence=0;
/** One fresh local worker owns each calculation; no stale/foreign/partial result can publish. */
export async function runAnaglyphTask(input,makeWorker,requireCurrent=()=>{},onProgress=()=>{},signal,timeoutMs=30000){
 const check=()=>{if(signal?.aborted)throw Error('Anaglyph composition cancelled.');requireCurrent();};check();
 const settings=anaglyphSettings(input?.width,input?.height,input?.shift,input?.swap),count=settings.width*settings.height;
 if(typeof makeWorker!=='function'||!Number.isInteger(timeoutMs)||timeoutMs<1||timeoutMs>30000)throw Error('Local anaglyph worker is unavailable.');
 if(!Array.isArray(input.inputs)||input.inputs.length!==2||input.inputs.some(p=>!(p instanceof Uint8Array||p instanceof Uint8ClampedArray)||!(p.buffer instanceof ArrayBuffer)||p.length!==count*4))throw Error('Both stereo-view pixel buffers must be complete.');
 const inputs=input.inputs.map(p=>new Uint8ClampedArray(p));
 const id=sequence=sequence>=1e9?1:sequence+1;
 return new Promise((resolve,reject)=>{
  let lease,timer,settled=false;
  const cleanup=()=>{clearTimeout(timer);signal?.removeEventListener('abort',abort);if(lease){try{lease.worker.onmessage=lease.worker.onerror=lease.worker.onmessageerror=null;}catch{}try{lease.worker.terminate?.();}catch{}try{lease.dispose?.();}catch{}}};
  const finish=(error,result)=>{if(settled)return;settled=true;cleanup();if(error)reject(error instanceof Error?error:Error('Anaglyph composition failed.'));else resolve(result);};
  const abort=()=>finish(Error('Anaglyph composition cancelled.'));
  try{
   lease=makeWorker();if(!lease?.worker||typeof lease.dispose!=='function'||typeof lease.worker.postMessage!=='function'||typeof lease.worker.terminate!=='function')throw Error('Local anaglyph worker is unavailable.');
   const worker=lease.worker;
   worker.onmessage=event=>{
    if(settled||event?.data?.id!==id)return;
    try{
     check();const message=event.data;if(message.kind==='error')throw Error(typeof message.error==='string'&&message.error.length<=200?message.error:'The local anaglyph worker could not finish.');
     if(message.kind!=='result')throw Error('The anaglyph worker sent an unsupported result.');
     const result=message.result;
     if(!result||result.width!==settings.width||result.height!==settings.height||result.shift!==settings.shift||result.swap!==settings.swap||!Number.isInteger(result.changed)||result.changed<1||result.changed>count||!(result.data instanceof Uint8ClampedArray)||!(result.data.buffer instanceof ArrayBuffer)||result.data.byteOffset!==0||result.data.length!==count*4||result.data.byteLength!==result.data.buffer.byteLength)throw Error('The anaglyph worker returned incomplete data.');
     for(let at=3;at<result.data.length;at+=4)if(result.data[at]!==255)throw Error('The anaglyph worker returned nonopaque data pixels.');
     check();onProgress(1);check();finish(null,result);
    }catch(error){finish(error);}
   };
   worker.onerror=event=>{event?.preventDefault?.();finish(Error('The local anaglyph worker stopped. Try again.'));};worker.onmessageerror=()=>finish(Error('The local anaglyph pixels could not be received.'));
   signal?.addEventListener('abort',abort,{once:true});timer=setTimeout(()=>finish(Error('Anaglyph composition took too long. Try smaller views.')),timeoutMs);
   check();onProgress(0);check();worker.postMessage({id,...settings,inputs},inputs.map(p=>p.buffer));
  }catch(error){finish(error);}
 });
}
