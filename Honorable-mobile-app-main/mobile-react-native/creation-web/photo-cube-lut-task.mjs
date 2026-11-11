import {cubeLutSettings,MAX_CUBE_TEXT} from './photo-cube-lut.mjs';
let sequence=0;
/** One fresh local worker owns each calculation; no stale/foreign/partial result can publish. */
export async function runCubeLutTask(input,makeWorker,requireCurrent=()=>{},onProgress=()=>{},signal,timeoutMs=30000){
 const check=()=>{if(signal?.aborted)throw Error('LUT grading cancelled.');requireCurrent();};check();
 const settings=cubeLutSettings(input?.width,input?.height,input?.strength),count=settings.width*settings.height;
 if(typeof makeWorker!=='function'||!Number.isInteger(timeoutMs)||timeoutMs<1||timeoutMs>30000)throw Error('Local LUT worker is unavailable.');
 if(!(input.pixels instanceof Uint8Array||input.pixels instanceof Uint8ClampedArray)||!(input.pixels.buffer instanceof ArrayBuffer)||input.pixels.length!==count*4)throw Error('The photo pixels are incomplete.');
 if(typeof input.text!=='string'||!input.text.trim()||input.text.length>MAX_CUBE_TEXT)throw Error('Paste a complete 3D .cube table up to 2 MiB.');
 const pixels=new Uint8ClampedArray(input.pixels),alpha=Uint8Array.from({length:count},(_,i)=>pixels[i*4+3]),text=input.text;
 const id=sequence=sequence>=1e9?1:sequence+1;
 return new Promise((resolve,reject)=>{
  let lease,timer,settled=false;
  const cleanup=()=>{clearTimeout(timer);signal?.removeEventListener('abort',abort);if(lease){try{lease.worker.onmessage=lease.worker.onerror=lease.worker.onmessageerror=null;}catch{}try{lease.worker.terminate?.();}catch{}try{lease.dispose?.();}catch{}}};
  const finish=(error,result)=>{if(settled)return;settled=true;cleanup();if(error)reject(error instanceof Error?error:Error('LUT grading failed.'));else resolve(result);};
  const abort=()=>finish(Error('LUT grading cancelled.'));
  try{
   lease=makeWorker();if(!lease?.worker||typeof lease.dispose!=='function'||typeof lease.worker.postMessage!=='function'||typeof lease.worker.terminate!=='function')throw Error('Local LUT worker is unavailable.');
   const worker=lease.worker;
   worker.onmessage=event=>{
    if(settled||event?.data?.id!==id)return;
    try{
     check();const message=event.data;if(message.kind==='error')throw Error(typeof message.error==='string'&&message.error.length<=200?message.error:'The local LUT worker could not finish.');
     if(message.kind!=='result')throw Error('The LUT worker sent an unsupported result.');
     const result=message.result;
     if(!result||result.width!==settings.width||result.height!==settings.height||result.strength!==settings.strength||!Number.isInteger(result.changed)||result.changed<1||result.changed>count||!Number.isInteger(result.size)||result.size<2||result.size>33||typeof result.title!=='string'||result.title.length>128||/[\x00-\x1f\x7f-\uffff]/.test(result.title)||![result.domainMin,result.domainMax].every(a=>Array.isArray(a)&&a.length===3&&a.every(n=>Number.isFinite(n)&&Math.abs(n)<=1e37))||result.domainMin.some((n,c)=>n>=result.domainMax[c])||!(result.data instanceof Uint8ClampedArray)||!(result.data.buffer instanceof ArrayBuffer)||result.data.byteOffset!==0||result.data.length!==count*4||result.data.byteLength!==result.data.buffer.byteLength)throw Error('The LUT worker returned incomplete data.');
     for(let at=3;at<result.data.length;at+=4)if(result.data[at]!==alpha[(at-3)/4])throw Error('The LUT worker changed source alpha.');
     check();onProgress(1);check();finish(null,result);
    }catch(error){finish(error);}
   };
   worker.onerror=event=>{event?.preventDefault?.();finish(Error('The local LUT worker stopped. Try again.'));};worker.onmessageerror=()=>finish(Error('The local LUT pixels could not be received.'));
   signal?.addEventListener('abort',abort,{once:true});timer=setTimeout(()=>finish(Error('LUT grading took too long. Try a smaller photo.')),timeoutMs);
   check();onProgress(0);check();worker.postMessage({id,...settings,pixels,text},[pixels.buffer]);
  }catch(error){finish(error);}
 });
}
