import { SVG_LIMITS } from './svg-import-limits.mjs';
import { validateDrawingElements } from './scene.mjs';
let sequence=0;
const finite=n=>Number.isFinite(n)&&Math.abs(n)<=1e6,close=(a,b)=>Math.abs(a-b)<=1e-6;
const color=v=>typeof v==='string'&&/^(?:#[\da-f]{3}|#[\da-f]{4}|#[\da-f]{6}|#[\da-f]{8}|transparent)$/.test(v);
const fields=new Set(['type','x','y','width','height','points','backgroundColor','strokeColor','strokeWidth','strokeStyle','fillStyle','roughness','angle','opacity','roundness','startArrowhead','endArrowhead']);
const resultFields=new Set(['plan','bounds','width','height','sourceCount','sampledPoints','hasCurves','hasOutlines']);
export function validateVectorResult(result,expected){
  if(!result||Object.keys(result).some(k=>!resultFields.has(k))||result.sourceCount!==expected.sourceCount||!Array.isArray(result.plan)||!result.plan.length||result.plan.length>SVG_LIMITS.paths||result.plan.length+expected.existingCount>500||!Array.isArray(result.bounds)||result.bounds.length!==4||!result.bounds.every(finite)||![result.width,result.height].every(n=>finite(n)&&n>=0)||!(result.width>0||result.height>0)||!close(result.width,result.bounds[2]-result.bounds[0])||!close(result.height,result.bounds[3]-result.bounds[1])||typeof result.hasCurves!=='boolean'||typeof result.hasOutlines!=='boolean'||!Number.isInteger(result.sampledPoints)||result.sampledPoints<2||result.sampledPoints>SVG_LIMITS.points||new TextEncoder().encode(JSON.stringify(result)).length>1024*1024)throw Error('Invalid vector result.');
  let count=0,left=Infinity,top=Infinity,right=-Infinity,bottom=-Infinity;
  for(const e of result.plan){
    if(!e||Object.keys(e).some(k=>!fields.has(k))||e.type!=='line'||!['x','y','width','height'].every(k=>finite(e[k]))||e.width<0||e.height<0||!color(e.backgroundColor)||!color(e.strokeColor)||!Number.isFinite(e.strokeWidth)||e.strokeWidth<=0||e.strokeWidth>64||e.strokeStyle!=='solid'||e.fillStyle!=='solid'||e.roughness!==0||e.angle!==0||e.roundness!==null||e.startArrowhead!==null||e.endArrowhead!==null||!Number.isFinite(e.opacity)||e.opacity<=0||e.opacity>100||!Array.isArray(e.points)||e.points.length<2||e.points.some(p=>!Array.isArray(p)||p.length!==2||!p.every(finite))||e.points[0][0]!==0||e.points[0][1]!==0)throw Error('Invalid vector path.');
    count+=e.points.length;if(count>32768)throw Error('Too many vector points.');
    const xs=e.points.map(p=>p[0]),ys=e.points.map(p=>p[1]);if(!close(e.width,Math.max(...xs)-Math.min(...xs))||!close(e.height,Math.max(...ys)-Math.min(...ys)))throw Error('Invalid vector geometry.');
    if(e.backgroundColor!=='transparent'&&(e.points.length<4||!close(e.points[e.points.length-1][0],0)||!close(e.points[e.points.length-1][1],0)))throw Error('Incomplete vector fill.');
    for(const [x,y] of e.points){const X=x+e.x,Y=y+e.y,margin=e.strokeColor==='transparent'?0:e.strokeWidth/2,v=expected.viewport;if(v&&(X-margin < -1e-6||Y-margin < -1e-6||X+margin>v.width+1e-6||Y+margin>v.height+1e-6))throw Error('Vector exceeds its canvas.');left=Math.min(left,X);right=Math.max(right,X);top=Math.min(top,Y);bottom=Math.max(bottom,Y);}
  }
  if(![left,top,right,bottom].every((v,i)=>close(v,result.bounds[i])))throw Error('Vector bounds do not match.');
  validateDrawingElements(result.plan.map((e,i)=>({...e,id:`vector-result-${i}`})));return result;
}
function workerInput(input){
  if(!input||!Array.isArray(input.items)||!input.items.length||input.items.length>SVG_LIMITS.items||!Number.isInteger(input.existingCount)||input.existingCount<0||input.existingCount>500)throw Error('Prepare the SVG artwork again.');
  const items=input.items.map(e=>{if(!e||typeof e.d!=='string'||!e.d.length||e.d.length>16384||!Array.isArray(e.matrix)||e.matrix.length!==6||!e.matrix.every(finite)||!color(e.fill)||!color(e.stroke)||!Number.isFinite(e.opacity)||e.opacity<=0||e.opacity>100||!Number.isFinite(e.strokeWidth)||e.strokeWidth<0||e.strokeWidth>64||!['nonzero','evenodd'].includes(e.fillRule))throw Error('Prepare supported SVG shapes again.');return{d:e.d,matrix:[...e.matrix],fill:e.fill,stroke:e.stroke,strokeWidth:e.strokeWidth,opacity:e.opacity,fillRule:e.fillRule};});
  if(new TextEncoder().encode(JSON.stringify(items)).length>SVG_LIMITS.bytes)throw Error('The SVG geometry is too large.');let viewport;
  if(input.viewport){if(![input.viewport.width,input.viewport.height].every(n=>Number.isFinite(n)&&n>=1&&n<=512))throw Error('The SVG canvas is invalid.');viewport={width:input.viewport.width,height:input.viewport.height};}
  return{items,existingCount:input.existingCount,...(viewport?{viewport}:{})};
}
export function startVectorTask(input,{makeWorker,isCurrent,timeoutMs=5000,schedule=setTimeout,clear=clearTimeout}={}){
  const id=sequence=sequence>=1e9?1:sequence+1;let handle,timer,done=false,rejectPromise;
  const dispose=()=>{if(timer!=null)clear(timer);if(handle){handle.worker.onmessage=null;handle.worker.onerror=null;try{handle.worker.terminate();}finally{handle.dispose();}}};
  const promise=new Promise((resolve,reject)=>{rejectPromise=reject;const finish=(error,value)=>{if(done)return;done=true;try{dispose();}catch{}if(error)reject(error);else resolve(value);};
    try{if(typeof makeWorker!=='function'||typeof isCurrent!=='function'||!isCurrent())throw Error('The drawing changed. Preview the SVG again.');if(!Number.isFinite(timeoutMs)||timeoutMs<1||timeoutMs>5000)throw Error('SVG preview timing is unavailable.');
      const payload=workerInput(input),expected={sourceCount:payload.items.length,existingCount:payload.existingCount,viewport:payload.viewport};try{handle=makeWorker();}catch{throw Error('Local SVG processing could not start. Try again.');}
      handle.worker.onmessage=event=>{if(done||event.data?.id!==id)return;try{if(!isCurrent())throw Error('Stale');}catch{finish(Error('The drawing changed. Preview the SVG again.'));return;}if(event.data.kind==='error'){finish(Error(typeof event.data.error==='string'?event.data.error.slice(0,240):'Local SVG processing stopped. Try again.'));return;}if(event.data.kind!=='result')return;try{finish(null,validateVectorResult(event.data.result,expected));}catch{finish(Error('The local SVG result was incomplete. Preview again.'));}};
      handle.worker.onerror=event=>{event.preventDefault?.();finish(Error('Local SVG processing stopped. Try again.'));};timer=schedule(()=>finish(Error('SVG processing took too long. Use simpler artwork.')),timeoutMs);handle.worker.postMessage({id,input:payload});
    }catch(error){finish(error instanceof Error?error:Error('Local SVG processing could not start.'));}
  });return{promise,cancel(){if(done)return;done=true;try{dispose();}catch{}rejectPromise(Error('SVG preview stopped. The drawing is unchanged.'));}};
}
