import React,{useEffect,useRef,useState} from 'react';
import {readVectorSvg} from './svg-import-source.mjs';
import {startVectorTask} from './svg-import-task.mjs';
import {makeVectorWorker} from './svg-import-worker-client.mjs';
import {createVectorFileRequest,decodeVectorFile,vectorFileText} from './vector-files.mjs';
export function VectorImportTool({captureSource,isCurrent,beginFile,onApply,onCancel}){
  const [raw,setRaw]=useState(''),[proposal,setProposal]=useState(null),[working,setWorking]=useState(false),[picking,setPicking]=useState(false),[message,setMessage]=useState('');
  const alive=useRef(true),ticket=useRef(0),task=useRef(null),file=useRef(null),review=useRef(null),previewReady=useRef(false);
  const finishFile=attempt=>{if(file.current!==attempt)return;file.current=null;attempt.input?.remove();attempt.release();if(alive.current)setPicking(false);};
  const stop=()=>{
    ticket.current++;previewReady.current=false;task.current?.cancel();task.current=null;
    const attempt=file.current;if(attempt){attempt.native?.cancel();finishFile(attempt);if(alive.current)setRaw(attempt.previous||'');}
    if(alive.current){setWorking(false);setPicking(false);setProposal(null);}
  };
  useEffect(()=>{
    alive.current=true;const receiver=reply=>file.current?.native?.receive(reply),hidden=()=>{if(document.visibilityState==='hidden'&&(task.current||previewReady.current)){stop();setMessage('SVG preview stopped while the drawing was hidden.');}};
    window.honorableVectorFileResult=receiver;document.addEventListener('visibilitychange',hidden);
    return()=>{alive.current=false;stop();if(window.honorableVectorFileResult===receiver)delete window.honorableVectorFileResult;document.removeEventListener('visibilitychange',hidden);};
  },[]);
  useEffect(()=>{if(proposal){review.current?.focus({preventScroll:true});review.current?.scrollIntoView({block:'nearest'});}},[proposal]);
  const choose=()=>{
    stop();let attempt;
    try{
      const source=captureSource(),release=beginFile();if(!release)throw Error('Finish the current operation before choosing a file.');
      attempt={source,release,ticket:ticket.current,previous:raw};file.current=attempt;setRaw('');setPicking(true);setMessage('Choose an SVG artwork file. Your drawing stays unchanged.');
      const current=()=>alive.current&&file.current===attempt&&ticket.current===attempt.ticket&&isCurrent(source);
      const chosen=value=>{if(!current())throw Error('The drawing changed while choosing the SVG. Choose it again.');if(value!==null){setRaw(vectorFileText(value));setMessage('SVG chosen. Preview it before inserting.');}else{setRaw(attempt.previous);setMessage('No file chosen. Existing artwork and any previous SVG source are kept.');}};
      const failed=error=>{if(alive.current&&file.current===attempt)setMessage(error.message||'The SVG file could not be read.');};
      const bridge=window.HonorableDrawing;
      if(typeof bridge?.chooseVectorFile==='function'){
        attempt.native=createVectorFileRequest(bridge,{isCurrent:current});
        attempt.native.promise.then(chosen).catch(failed).finally(()=>finishFile(attempt));attempt.native.start();
      }else{
        if(location.hostname==='honorable-drawing.local')throw Error('Update Noble to choose SVG artwork files.');
        const input=document.createElement('input');input.type='file';input.accept='.svg,image/svg+xml';input.hidden=true;input.setAttribute('aria-label','Choose SVG artwork file');attempt.input=input;document.body.append(input);
        input.onchange=async()=>{if(file.current!==attempt)return;try{const selected=input.files?.[0];if(!selected){chosen(null);return;}if(selected.size>65536)throw Error('Choose an SVG file up to 64 KB.');const value=decodeVectorFile(await selected.arrayBuffer());chosen(value);}catch(error){failed(error);}finally{finishFile(attempt);}};
        input.addEventListener('cancel',()=>{if(file.current===attempt){try{chosen(null);}catch(error){failed(error);}finishFile(attempt);}});input.click();
      }
    }catch(error){if(attempt)finishFile(attempt);setMessage(error.message||'The SVG chooser could not open.');}
  };
  const preview=async()=>{
    stop();const currentTicket=ticket.current;setWorking(true);setMessage('Preparing editable artwork on this device…');
    try{
      const source=captureSource(),input=readVectorSvg(raw,{existingCount:source.existingCount}),current=()=>alive.current&&ticket.current===currentTicket&&isCurrent(source);
      const work=startVectorTask(input,{makeWorker:makeVectorWorker,isCurrent:current});task.current=work;const result=await work.promise;
      if(!current())throw Error('The drawing changed. Preview the SVG again.');previewReady.current=true;setProposal({source,input,result});setMessage('Preview ready. Insert adds a separate editable group.');
    }catch(error){if(alive.current&&ticket.current===currentTicket)setMessage(error.message||'The SVG could not be prepared.');}
    finally{if(alive.current&&ticket.current===currentTicket){task.current=null;setWorking(false);}}
  };
  const result=proposal?.result,margin=result?Math.max(8,...result.plan.map(e=>e.strokeWidth/2+4)):8;
  return <>
    <p>Bring a local SVG logo, icon or illustration into your drawing as editable paths. Choose a file, review the preview, then insert it beside your artwork.</p>
    <button disabled={working||picking} onClick={choose}>Choose SVG artwork</button>
    {picking&&<button data-vector-file-cancel="true" onClick={()=>{stop();setMessage('File selection stopped. Existing artwork is kept.');}}>Stop file selection</button>}
    <details><summary>Paste SVG source instead</summary><label>SVG source<textarea aria-label="SVG source" value={raw} maxLength={65536} spellCheck={false} disabled={working||picking} onChange={event=>{stop();setRaw(event.target.value);setMessage('SVG source changed. Preview again before inserting.');}}/></label></details>
    {raw&&<p>SVG source is ready for preview. The original file stays unchanged.</p>}
    <button disabled={!raw||working||picking} onClick={preview}>{working?'Preparing vector artwork…':'Preview SVG artwork'}</button>
    {working&&<button onClick={()=>{stop();setMessage('Preview stopped. Existing artwork is kept.');}}>Stop SVG preview</button>}
    {result&&<section aria-label="Review SVG artwork"><h4 ref={review} tabIndex={-1}>Review editable artwork</h4>
      <svg role="img" aria-label="Editable SVG artwork preview" viewBox={`${result.bounds[0]-margin} ${result.bounds[1]-margin} ${result.width+margin*2} ${result.height+margin*2}`} style={{display:'block',width:'100%',maxHeight:240,background:'#eee',border:'1px solid #ccc'}}>
        {result.plan.map((e,index)=><path key={index} d={e.points.map(([x,y],i)=>`${i?'L':'M'}${x+e.x},${y+e.y}`).join(' ')+(e.backgroundColor!=='transparent'?'Z':'')} fill={e.backgroundColor} stroke={e.strokeColor} strokeWidth={e.strokeWidth} strokeLinecap="round" strokeLinejoin="round" opacity={e.opacity/100}/>)}</svg>
      <p>{result.sourceCount} vector {result.sourceCount===1?'shape':'shapes'} → {result.plan.length} editable {result.plan.length===1?'path':'paths'}. {proposal.input.omittedHidden>0&&`${proposal.input.omittedHidden} hidden shapes are omitted.`}</p>
      <p>Curves become fine polygonal paths. Results use solid fills; outline caps and joins may differ. Zoom in to review edges. Ungroup after insertion to edit individual paths.</p>
      <button onClick={()=>{try{if(!isCurrent(proposal.source))throw Error('The drawing changed. Preview the SVG again.');onApply(result,proposal.source);}catch(error){previewReady.current=false;setProposal(null);setMessage(error.message);}}}>Insert editable SVG artwork</button>
    </section>}
    <button data-vector-file-cancel="true" onClick={()=>{stop();onCancel();}}>Keep drawing unchanged</button>
    <p aria-live="polite">{message}</p>
    <p>Up to 64 KB and 128 visible vector shapes. Convert text, images, gradients, masks and special effects to plain paths or solid colors first. Keep all artwork inside the SVG canvas. Files with unsupported content are refused. Processing stays on this device.</p>
  </>;
}
