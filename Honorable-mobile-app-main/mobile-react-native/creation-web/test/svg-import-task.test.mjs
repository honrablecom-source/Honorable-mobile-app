import test from 'node:test';
import assert from 'node:assert/strict';
import { startVectorTask, validateVectorResult } from '../svg-import-task.mjs';
import { vectorArtwork } from '../svg-import-geometry.mjs';
const item={d:'M0 0H100V100H0Z M20 20V80H80V20Z',matrix:[1,0,0,1,0,0],fill:'#ff000080',stroke:'transparent',strokeWidth:1,opacity:100,fillRule:'nonzero'};
const input=()=>({items:[{...item,matrix:[...item.matrix]}],existingCount:2,viewport:{width:100,height:100}});
function fixture(current=()=>true,value=input(),more={}) {
  const state={terminated:0,disposed:0,cleared:0};const worker={postMessage:value=>state.sent=value,terminate:()=>state.terminated++};
  const task=startVectorTask(value,{makeWorker:()=>({worker,dispose:()=>state.disposed++}),isCurrent:current,schedule:fn=>{state.timeout=fn;return 1;},clear:()=>state.cleared++,...more});
  return {task,state,worker,result:()=>vectorArtwork(state.sent.input)};
}
const cleaned=state=>{assert.equal(state.terminated,1);assert.equal(state.disposed,1);assert.equal(state.cleared,1);};
test('SVG task resolves matching output only and disposes worker URL/timer exactly once',async()=>{
  const {task,state,worker,result}=fixture(),receive=worker.onmessage;receive({data:{id:state.sent.id+1,kind:'result',result:result()}});assert.equal(state.disposed,0);receive({data:{id:state.sent.id,kind:'result',result:result()}});assert.equal((await task.promise).plan.length,1);task.cancel();receive({data:{id:state.sent.id,kind:'error',error:'late'}});cleaned(state);
});
test('SVG task cancellation and late completion never revive a result',async()=>{
  const {task,state,worker,result}=fixture(),receive=worker.onmessage;task.cancel();await assert.rejects(task.promise,/stopped/);receive({data:{id:state.sent.id,kind:'result',result:result()}});task.cancel();cleaned(state);
});
test('SVG deadline cleans resources and permits no late result',async()=>{
  const {task,state,worker,result}=fixture(),receive=worker.onmessage;state.timeout();await assert.rejects(task.promise,/too long/);receive({data:{id:state.sent.id,kind:'result',result:result()}});cleaned(state);
});
test('changed and throwing document guards reject actual computed results',async()=>{
  for(const throwing of [false,true]){let changed=false;const {task,state,worker,result}=fixture(()=>{if(changed&&throwing)throw Error('Private guard detail');return !changed;});changed=true;worker.onmessage({data:{id:state.sent.id,kind:'result',result:result()}});await assert.rejects(task.promise,/drawing changed/);cleaned(state);}
});
test('SVG worker payload is independent and excludes document metadata and nonselected content',async()=>{
  const value=input();value.rawXML='PRIVATE XML';value.files={private:'PRIVATE PHOTO'};value.items[0].id='PRIVATE ID';value.items[0].title='PRIVATE TITLE';
  const {task,state,worker,result}=fixture(()=>true,value);assert.deepEqual(Object.keys(state.sent.input).sort(),['existingCount','items','viewport']);assert(!JSON.stringify(state.sent).includes('PRIVATE'));value.items.length=0;value.existingCount=500;worker.onmessage({data:{id:state.sent.id,kind:'result',result:result()}});assert.equal((await task.promise).sourceCount,1);cleaned(state);
});
test('malformed vector result metadata, bounds, styles and paths refuse before preview',async()=>{
  const mutations=[r=>({...r,privateMetadata:'forbidden'}),r=>({...r,sourceCount:2}),r=>({...r,plan:[]}),r=>({...r,bounds:[0,0,99,100]}),r=>({...r,width:NaN}),r=>({...r,height:-1}),r=>({...r,hasCurves:null}),r=>({...r,sampledPoints:99999}),r=>({...r,plan:[{...r.plan[0],type:'text'}]}),r=>({...r,plan:[{...r.plan[0],id:'private'}]}),r=>({...r,plan:[{...r.plan[0],backgroundColor:'url(#x)'}]}),r=>({...r,plan:[{...r.plan[0],strokeWidth:NaN}]}),r=>({...r,plan:[{...r.plan[0],points:[[1,0],[100,0],[100,100],[0,0]]}]}),r=>({...r,plan:[{...r.plan[0],width:99}]}),r=>({...r,plan:[{...r.plan[0],points:r.plan[0].points.slice(0,-1)}]})];
  for(const mutate of mutations){const {task,state,worker,result}=fixture();worker.onmessage({data:{id:state.sent.id,kind:'result',result:mutate(result())}});await assert.rejects(task.promise,/incomplete/);cleaned(state);}
});
test('worker computation/browser/send errors dispose their ownership',async()=>{
  for(const kind of ['model','browser']){const {task,state,worker}=fixture();if(kind==='model')worker.onmessage({data:{id:state.sent.id,kind:'error',error:'Use simpler paths.'}});else worker.onerror({preventDefault(){}});await assert.rejects(task.promise);cleaned(state);}
  let terminated=0,disposed=0;const task=startVectorTask(input(),{isCurrent:()=>true,makeWorker:()=>({worker:{terminate:()=>terminated++,postMessage(){throw Error('Synthetic transfer refusal');}},dispose:()=>disposed++})});await assert.rejects(task.promise,/transfer refusal/);assert.equal(terminated,1);assert.equal(disposed,1);
});
test('stale input and invalid payload refuse before constructing a worker',async()=>{
  let calls=0;const makeWorker=()=>{calls++;throw Error('Private constructor detail');};let task=startVectorTask(input(),{makeWorker,isCurrent:()=>false});await assert.rejects(task.promise,/drawing changed/);assert.equal(calls,0);
  for(const patch of [{items:[]},{existingCount:501},{viewport:{width:NaN,height:100}},{items:[{...item,opacity:NaN}]},{items:[{...item,d:'x'.repeat(16385)}]}]){task=startVectorTask({...input(),...patch},{makeWorker,isCurrent:()=>true});await assert.rejects(task.promise);assert.equal(calls,0);}
  task=startVectorTask(input(),{makeWorker,isCurrent:()=>true});await assert.rejects(task.promise,/could not start/);assert.equal(calls,1);
});
test('horizontal and vertical outline paths keep valid zero dimensions',()=>{
  for(const d of ['M10 10H90','M10 10V90']){const result=vectorArtwork({...input(),items:[{...item,d,fill:'transparent',stroke:'#111111'}]});assert.doesNotThrow(()=>validateVectorResult(result,{sourceCount:1,existingCount:2}));}
});
