import React,{useEffect,useRef,useState} from 'react';
import {outlineCutoutPlan} from './photo-outline-cutout.mjs';
import {outlinePhotoCutout} from './photo-outline-image.mjs';
const empty=()=>({rings:[[]],closed:[false],active:0});
const checker={backgroundColor:'#eee',backgroundImage:'conic-gradient(#fff 25%,transparent 0 50%,#fff 0 75%,transparent 0)',backgroundSize:'16px 16px'};
export function PhotoOutlineCutoutTool({target,isCurrent,onApply,onCancel}){
 const [drawing,setDrawing]=useState(empty),[feather,setFeather]=useState('0'),[x,setX]=useState('0'),[y,setY]=useState('0'),[proposal,setProposal]=useState(null),[url,setUrl]=useState(''),[busy,setBusy]=useState(false),[message,setMessage]=useState('');
 const life=useRef({alive:true,revision:0,controller:null});
 const invalidate=()=>{life.current.revision++;life.current.controller?.abort();life.current.controller=null;setProposal(null);setBusy(false);};
 useEffect(()=>{life.current.alive=true;const hidden=()=>{if(document.hidden){invalidate();setMessage('Preview closed while the drawing was hidden. Review your outline and preview again.');}};document.addEventListener('visibilitychange',hidden);return()=>{life.current.alive=false;life.current.revision++;life.current.controller?.abort();document.removeEventListener('visibilitychange',hidden);};},[]);
 useEffect(()=>{if(!proposal){setUrl('');return;}const next=URL.createObjectURL(proposal.blob);setUrl(next);return()=>URL.revokeObjectURL(next);},[proposal]);
 const edit=next=>{invalidate();setDrawing(next);setMessage('');};
 const addPoint=(px,py)=>{
  if(busy||drawing.closed[drawing.active])return;
  if(!Number.isFinite(px)||!Number.isFinite(py)||px<0||px>target.width||py<0||py>target.height){setMessage('Enter a point inside the photo.');return;}
  if(drawing.rings[drawing.active].length>=128||drawing.rings.flat().length>=256){setMessage('Use up to 128 points per outline and 256 in total.');return;}
  const rings=drawing.rings.map((ring,i)=>i===drawing.active?[...ring,[px,py]]:ring);edit({...drawing,rings});setX(String(Math.round(px*100)/100));setY(String(Math.round(py*100)/100));
 };
 const closeOutline=()=>{try{outlineCutoutPlan(target.width,target.height,{outer:drawing.rings[0],holes:drawing.rings.slice(1)},Number(feather));edit({...drawing,closed:drawing.closed.map((value,i)=>i===drawing.active?true:value)});}catch(error){setMessage(error.message);}};
 const preview=async()=>{
  invalidate();const revision=life.current.revision,controller=new AbortController();life.current.controller=controller;
  const current=()=>{if(!life.current.alive||revision!==life.current.revision||controller.signal.aborted||document.hidden||!isCurrent())throw Error('The source or drawing changed. Open Outline cutout again.');};
  try{
   current();if(drawing.closed.some(value=>!value))throw Error('Close each outline before previewing.');if(feather.trim()==='')throw Error('Enter inward feathering from 0 to 24 pixels.');
   setBusy(true);setMessage('Preparing a transparent copy on this device…');
   const result=await outlinePhotoCutout(target.dataURL,{outer:drawing.rings[0],holes:drawing.rings.slice(1)},Number(feather),current,progress=>{current();setMessage(`Preparing cutout… ${Math.round(progress*100)}%`);},controller.signal);
   current();setProposal({...result,current});setMessage('Review the transparent preview before inserting.');
  }catch(error){if(life.current.alive&&revision===life.current.revision)setMessage(error.message);}
  finally{if(life.current.alive&&revision===life.current.revision)setBusy(false);}
 };
 const insert=async()=>{if(!proposal||busy)return;setBusy(true);try{proposal.current();await onApply(proposal,proposal.current);}catch(error){if(life.current.alive)setMessage(error.message);}finally{if(life.current.alive)setBusy(false);}};
 const ring=drawing.rings[drawing.active],allClosed=drawing.closed.every(Boolean),pointCount=drawing.rings.reduce((n,r)=>n+r.length,0);
 return <><h3>Outline cutout</h3><p>Tap around the subject you want to keep, then close the outline. Everything outside becomes transparent. Add a hole to remove an enclosed area, such as the space inside a handle.</p><p>This uses the full embedded photo before crop, rotation, flip or opacity. Your original stays unchanged; Insert creates a separate flattened PNG copy.</p>
 <svg role="img" aria-label="Photo outline selection" viewBox={`0 0 ${target.width} ${target.height}`} style={{display:'block',width:'100%',height:'auto',touchAction:'manipulation',cursor:busy?'wait':'crosshair',...checker}} onClick={event=>{const bounds=event.currentTarget.getBoundingClientRect();if(bounds.width&&bounds.height)addPoint(Math.max(0,Math.min(target.width,(event.clientX-bounds.left)/bounds.width*target.width)),Math.max(0,Math.min(target.height,(event.clientY-bounds.top)/bounds.height*target.height)));}}>
  <image href={target.dataURL} x="0" y="0" width={target.width} height={target.height}/>
  {drawing.rings.map((points,i)=><g key={i} pointerEvents="none"><polyline points={[...points,...(drawing.closed[i]&&points.length?[points[0]]:[])].map(p=>p.join(',')).join(' ')} fill={drawing.closed[i]?'#ffffff22':'none'} stroke="#fff" strokeWidth="5" vectorEffect="non-scaling-stroke"/><polyline points={[...points,...(drawing.closed[i]&&points.length?[points[0]]:[])].map(p=>p.join(',')).join(' ')} fill="none" stroke="#111" strokeWidth="2" strokeDasharray={i?'5 3':undefined} vectorEffect="non-scaling-stroke"/>{points.map((p,j)=><circle key={j} cx={p[0]} cy={p[1]} r={Math.max(target.width,target.height)/100} fill={i===drawing.active?'#111':'#fff'} stroke="#fff" strokeWidth="1" vectorEffect="non-scaling-stroke"/>)}</g>)}
 </svg>
 <p aria-live="polite">{drawing.active===0?'Outer outline':`Hole ${drawing.active}`}: {ring.length} points, {drawing.closed[drawing.active]?'closed':'tap to add points'}. {pointCount}/256 total points.</p>
 <label>Editing outline<select aria-label="Editing cutout outline" value={drawing.active} disabled={busy} onChange={event=>edit({...drawing,active:Number(event.target.value)})}>{drawing.rings.map((_,i)=><option key={i} value={i}>{i===0?'Outer outline':`Hole ${i}`}</option>)}</select></label>
 <div className="tool-grid"><label>Point X<input aria-label="Cutout point X" type="number" min="0" max={target.width} step="any" value={x} disabled={busy} onChange={event=>setX(event.target.value)}/></label><label>Point Y<input aria-label="Cutout point Y" type="number" min="0" max={target.height} step="any" value={y} disabled={busy} onChange={event=>setY(event.target.value)}/></label></div>
 <button disabled={busy||drawing.closed[drawing.active]} onClick={()=>{if(x.trim()===''||y.trim()===''){setMessage('Enter both point coordinates.');return;}addPoint(Number(x),Number(y));}}>Add point by coordinates</button>
 <div className="tool-grid"><button disabled={busy||ring.length<3||drawing.closed[drawing.active]} onClick={closeOutline}>Close outline</button><button disabled={busy||!ring.length} onClick={()=>edit({...drawing,rings:drawing.rings.map((r,i)=>i===drawing.active?r.slice(0,-1):r),closed:drawing.closed.map((value,i)=>i===drawing.active?false:value)})}>Undo last point</button><button disabled={busy||!allClosed||drawing.rings.length>=8} onClick={()=>edit({rings:[...drawing.rings,[]],closed:[...drawing.closed,false],active:drawing.rings.length})}>Add enclosed hole</button>{drawing.active>0&&<button disabled={busy} onClick={()=>edit({rings:drawing.rings.filter((_,i)=>i!==drawing.active),closed:drawing.closed.filter((_,i)=>i!==drawing.active),active:0})}>Remove this hole</button>}<button disabled={busy||!pointCount} onClick={()=>edit(empty())}>Start outline again</button></div>
 <label>Inward feathering (photo pixels)<input aria-label="Cutout inward feathering" type="number" min="0" max="24" step="0.5" value={feather} disabled={busy} onChange={event=>{invalidate();setFeather(event.target.value);setMessage('');}}/></label><p>0 keeps a crisp antialiased edge. Higher values soften inward; excluded areas stay transparent. Small details can disappear. Use up to seven separate holes; outlines cannot cross or touch.</p>
 <button disabled={busy||!allClosed} onClick={preview}>{busy?'Preparing local copy…':'Preview outline cutout'}</button>
 {proposal&&url&&<><div style={checker}><img src={url} alt="Manual outline cutout preview" style={{display:'block',maxWidth:'100%',height:'auto',margin:'12px auto'}}/></div><p>{proposal.width} × {proposal.height} pixels before project storage limits. {proposal.remaining.toLocaleString()} visible pixels remain. The new copy is inserted beside the original and may be reduced to fit local storage. Undo removes the insertion.</p><button disabled={busy} onClick={insert}>Insert outline cutout</button></>}
 <button data-photo-cutout-cancel="true" onClick={()=>{invalidate();onCancel();}}>Keep original only</button><p role="status" aria-live="polite">{message}</p><p>All processing stays on this device. To export just the cutout, select it and open Projects → Image copies, choose a transparent background and Use selected artwork. Keep PNG in the photo editor to retain transparency.</p></>;
}
