import {cloneRepairPlan} from './photo-clone-repair.mjs';
let sequence=0;
/** Each preview owns a new worker. No partial/foreign/stale worker result can publish. */
export async function runCloneTask(input,makeWorker,requireCurrent=()=>{},onProgress=()=>{},signal,timeoutMs=30000){
 const check=()=>{if(signal?.aborted)throw Error('Clone repair cancelled.');requireCurrent();};check();
 if(typeof makeWorker!=='function'||!Number.isInteger(timeoutMs)||timeoutMs<1||timeoutMs>30000)throw Error('Local repair worker is unavailable.');
 const plan=cloneRepairPlan(input?.width,input?.height,input?.strokes);
 if(!(input.pixels instanceof Uint8Array||input.pixels instanceof Uint8ClampedArray)||!(input.pixels.buffer instanceof ArrayBuffer)||input.pixels.length!==plan.width*plan.height*4)throw Error('The source photo pixels are incomplete.');
 // A transferable private copy preserves the caller's input and snapshot before asynchronous work.
 const pixels=new Uint8ClampedArray(input.pixels),id=sequence=sequence>=1e9?1:sequence+1;
 return new Promise((resolve,reject)=>{
  let lease,timer,settled=false,progress=-1;
  const cleanup=()=>{clearTimeout(timer);signal?.removeEventListener('abort',abort);if(lease){const worker=lease.worker;if(worker){try{worker.onmessage=worker.onerror=worker.onmessageerror=null;}catch{}try{worker.terminate?.();}catch{}}try{lease.dispose?.();}catch{}}};
  const finish=(error,value)=>{if(settled)return;settled=true;cleanup();if(error)reject(error instanceof Error?error:Error('Clone repair could not finish.'));else resolve(value);};
  const abort=()=>finish(Error('Clone repair cancelled.'));
  try{
   lease=makeWorker();if(!lease?.worker||typeof lease.dispose!=='function'||typeof lease.worker.postMessage!=='function'||typeof lease.worker.terminate!=='function')throw Error('Local repair worker is unavailable.');
   const worker=lease.worker;
   worker.onmessage=event=>{
    if(settled||event?.data?.id!==id)return;
    try{
     check();const message=event.data;
     if(message.kind==='progress'){
      const value=message.progress;if(!Number.isFinite(value)||value<0||value>1||value<progress)throw Error('The repair worker sent invalid progress.');progress=value;onProgress(value);check();return;
     }
     if(message.kind==='error')throw Error(typeof message.error==='string'&&message.error.length<=200?message.error:'The repair worker could not finish.');
     if(message.kind!=='result')throw Error('The repair worker sent an unsupported result.');
     const result=message.result;
     if(!result||result.width!==plan.width||result.height!==plan.height||!(result.data instanceof Uint8ClampedArray)||!(result.data.buffer instanceof ArrayBuffer)||result.data.byteOffset!==0||result.data.length!==plan.width*plan.height*4||result.data.buffer.byteLength!==result.data.byteLength||!Number.isInteger(result.changed)||result.changed<1||result.changed>plan.width*plan.height)throw Error('The repair worker returned incomplete pixels.');
     check();finish(null,{data:result.data,width:result.width,height:result.height,changed:result.changed});
    }catch(error){finish(error);}
   };
   worker.onerror=event=>{event?.preventDefault?.();finish(Error('The local repair worker stopped. Try shorter strokes.'));};
   worker.onmessageerror=()=>finish(Error('The local repair pixels could not be received.'));
   signal?.addEventListener('abort',abort,{once:true});timer=setTimeout(()=>finish(Error('Clone repair took too long. Try shorter strokes or a smaller photo.')),timeoutMs);
   check();worker.postMessage({id,width:plan.width,height:plan.height,pixels,strokes:plan.strokes},[pixels.buffer]);
  }catch(error){finish(error);}
 });
}
