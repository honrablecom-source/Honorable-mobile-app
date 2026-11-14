import React, {useEffect,useRef,useState} from 'react';
import workerSource from 'noble:photocraft-worker';
import {CORE_MAX,CORE_EFFECTS,CORE_BLENDS,checkedCoreProject,coreHistory,corePhotoInfo} from './photocraft-studio.mjs';
import {startPhotoCraft} from './photocraft-client.mjs';
import {prepareLocalPhoto} from './photo-import.mjs';
import {NeuralTextureStudio} from './NeuralTextureStudio.jsx';

async function raster(url,frame=null) {
  const info=corePhotoInfo(url);
  if(info.width>1024||info.height>1024)throw Error('Use a prepared local image up to1024 pixels per side.');
  const image=await new Promise((resolve,reject)=>{const value=new Image();value.onload=()=>resolve(value);value.onerror=()=>reject(Error('The local layer could not be decoded.'));value.src=url;});
  if(image.naturalWidth!==info.width||image.naturalHeight!==info.height)throw Error('The local layer dimensions do not match.');
  const canvas=document.createElement('canvas'),scale=frame?
    Math.min(frame.width/image.width,frame.height/image.height):Math.min(1,CORE_MAX/Math.max(image.width,image.height));
  canvas.width=frame?.width||Math.max(1,Math.round(image.width*scale));canvas.height=frame?.height||Math.max(1,Math.round(image.height*scale));
  const context=canvas.getContext('2d',{willReadFrequently:true});if(!context)throw Error('Raster preparation is unavailable.');
  const w=Math.max(1,Math.round(image.width*scale)),h=Math.max(1,Math.round(image.height*scale));
  context.drawImage(image,Math.floor((canvas.width-w)/2),Math.floor((canvas.height-h)/2),w,h);
  return {width:canvas.width,height:canvas.height,pixels:context.getImageData(0,0,canvas.width,canvas.height).data,png:canvas.toDataURL('image/png')};
}
function png(width,height,pixels) {
  const canvas=document.createElement('canvas');canvas.width=width;canvas.height=height;
  const context=canvas.getContext('2d');if(!context)throw Error('Raster output is unavailable.');
  context.putImageData(new ImageData(pixels,width,height),0,0);return canvas.toDataURL('image/png');
}
export function PhotoCraftStudio({texture,textureSources=[],captureTexture,onApply,onArtwork,onClose}) {
  const [project,setProject]=useState(null),[selected,setSelected]=useState(1),[preview,setPreview]=useState(null),
    [busy,setBusy]=useState(false),[approved,setApproved]=useState(false),[message,setMessage]=useState('Opening your local raster copy…'),
    [effect,setEffect]=useState('emboss'),[amount,setAmount]=useState(100),[sourceId,setSourceId]=useState(''),[neural,setNeural]=useState(null);
  const history=useRef([]),saved=useRef(null),task=useRef(null),request=useRef(2),life=useRef({mounted:true,generation:0,busy:false});
  const notify=value=>{if(life.current.mounted)setMessage(value);};
  const stop=()=>{
    life.current.generation++;life.current.busy=false;task.current?.stop();task.current=null;
    if(life.current.mounted){setBusy(false);setApproved(false);setMessage('Raster processing stopped. Your previous composition is kept.');}
  };
  useEffect(()=>{
    life.current.mounted=true;
    const generation=++life.current.generation;
    raster(texture).then(value=>{
      if(!life.current.mounted||generation!==life.current.generation||document.hidden)return;
      const first=checkedCoreProject({width:value.width,height:value.height,layers:[{id:1,name:'Original copy',png:value.png,blend:'normal',opacity:1,visible:true}]});
      saved.current=first;setProject(first);setPreview(value.png);notify('Your original copy is ready. Edits stay in this lab until you explicitly use the reviewed result.');
    }).catch(error=>{if(life.current.mounted&&generation===life.current.generation)notify(error.message);});
    const hidden=()=>{if(document.hidden){stop();setNeural(null);}};
    document.addEventListener('visibilitychange',hidden);
    return()=>{life.current.mounted=false;life.current.generation++;task.current?.stop();task.current=null;document.removeEventListener('visibilitychange',hidden);};
  },[texture]);
  const process=async(next,filter=null,undo=false,requireCurrent=()=>{})=>{
    if(!saved.current||life.current.busy||document.hidden||!life.current.mounted)return false;
    let proposal;
    try{proposal=checkedCoreProject(next);}catch(error){notify(error.message);return false;}
    const previous=saved.current,generation=++life.current.generation;
    life.current.busy=true;setBusy(true);setApproved(false);
    const current=()=>{
      requireCurrent();
      if(!life.current.mounted||generation!==life.current.generation||document.hidden||saved.current!==previous)
        throw Error('The raster operation was stopped before applying.');
    };
    let job;
    try {
      const layers=[];
      for(const layer of proposal.layers){
        const image=await raster(layer.png,{width:proposal.width,height:proposal.height});current();
        layers.push({...layer,pixels:image.pixels.buffer});
      }
      job=startPhotoCraft(workerSource,{width:proposal.width,height:proposal.height,layers,effect:filter},new URL('/drawing/',location.origin).href,
        value=>{try{current();notify(value);}catch{}});
      task.current=job;
      const result=await job.promise;current();
      if(filter)proposal=checkedCoreProject({...proposal,layers:proposal.layers.map(layer=>layer.id===filter.layerId?{...layer,png:png(proposal.width,proposal.height,result.changed)}:layer)});
      const nextPreview=png(proposal.width,proposal.height,result.preview);
      if(undo)history.current.pop();else history.current=coreHistory(history.current,previous,proposal);
      saved.current=proposal;setProject(proposal);setPreview(nextPreview);
      if(!proposal.layers.some(layer=>layer.id===selected))setSelected(proposal.layers[proposal.layers.length-1].id);
      notify(undo?'Previous raster edit restored. Originals and model stay unchanged.':'Working raster updated with the real PhotoCraft core. Review before using it.');
      return true;
    }catch(error){if(life.current.mounted&&generation===life.current.generation)notify(error.message);return false;}
    finally{job?.stop();if(task.current===job)task.current=null;if(life.current.mounted&&generation===life.current.generation){life.current.busy=false;setBusy(false);}}
  };
  const updateLayer=patch=>{
    if(!saved.current)return;
    process({...saved.current,layers:saved.current.layers.map(layer=>layer.id===selected?{...layer,...patch}:layer)});
  };
  const addLayer=async()=>{
    if(!sourceId||life.current.busy||!saved.current)return;
    const generation=life.current.generation,previous=saved.current;
    // Source selection is a read-only snapshot. Existing importer validates headers,
    // decoded dimensions and metadata removal before a new layer can be admitted.
    life.current.busy=true;setBusy(true);
    const current=()=>{if(!life.current.mounted||generation!==life.current.generation||document.hidden||saved.current!==previous)throw Error('Layer preparation stopped. Your composition is kept.');};
    try {
      const copy=await prepareLocalPhoto(captureTexture(sourceId),()=>{current();return true;});current();
      const image=await raster(copy.dataURL,{width:previous.width,height:previous.height});current();
      life.current.busy=false;setBusy(false);
      const id=request.current++;
      const next={...previous,layers:[...previous.layers,{id,name:'Photo layer '+id,png:image.png,blend:'normal',opacity:1,visible:true}]};
      if(await process(next))setSelected(id);
    }catch(error){if(life.current.mounted&&generation===life.current.generation)notify(error.message);}
    finally{if(life.current.mounted&&generation===life.current.generation){life.current.busy=false;setBusy(false);}}
  };
  const useResult=async artwork=>{
    if(!preview||!approved||life.current.busy||document.hidden||!life.current.mounted)return;
    life.current.busy=true;setBusy(true);setApproved(false);
    const generation=++life.current.generation;
    const current=()=>{if(!life.current.mounted||generation!==life.current.generation||document.hidden)throw Error('Raster copy stopped before insertion.');};
    try {
      current();const accepted=await (artwork?onArtwork(preview,current):onApply(preview));
      current();if(accepted===false)throw Error('The reviewed raster copy could not be added. Originals are kept.');onClose();
    }catch(error){if(life.current.mounted&&generation===life.current.generation)notify(error.message);}
    finally{if(life.current.mounted&&generation===life.current.generation){life.current.busy=false;setBusy(false);}}
  };
  const layer=project?.layers.find(value=>value.id===selected),rule=CORE_EFFECTS[effect];
  return <section aria-label="Raster lab with PhotoCraft core" style={{border:'1px solid #999',padding:12}}>
    <h3>Raster lab</h3>
    <p>Real PhotoCraft processing and layer blending behind phone controls. Work on up to four local8-bit sRGB layers at512px, then use a separate flattened copy. Ordinary effects are manual; Neural paint and Neural cleanup run actual local models.</p>
    {preview&&<img src={preview} alt="PhotoCraft raster composition preview" style={{display:'block',maxWidth:'100%',maxHeight:360,margin:'auto',background:'#eee'}}/>}
    <fieldset disabled={!project||busy||neural!==null} style={{padding:0,border:0}}>
      <label>Working layer<select aria-label="Raster working layer" value={selected} onChange={event=>{setSelected(Number(event.target.value));setApproved(false);}}>
        {project?.layers.map(value=><option key={value.id} value={value.id}>{value.name}</option>)}
      </select></label>
      {layer&&<>
        <label className="check-label"><input type="checkbox" checked={layer.visible} onChange={event=>updateLayer({visible:event.target.checked})}/>Layer visible</label>
        <label>Layer opacity<select aria-label="Raster layer opacity" value={layer.opacity} onChange={event=>updateLayer({opacity:Number(event.target.value)})}>{[0,0.25,0.5,0.75,1].map(value=><option key={value} value={value}>{value*100}%</option>)}</select></label>
        <label>Layer blend<select aria-label="Raster layer blend" value={layer.blend} onChange={event=>updateLayer({blend:event.target.value})}>{CORE_BLENDS.map(value=><option key={value}>{value}</option>)}</select></label>
        <div className="tool-grid">
          <button disabled={project.layers.length>=4} onClick={()=>{const id=request.current++;process({...project,layers:[...project.layers,{...layer,id,name:'Layer copy '+id}]}).then(done=>{if(done)setSelected(id);});}}>Duplicate working layer</button>
          <button disabled={project.layers.length<=1} onClick={()=>process({...project,layers:project.layers.filter(value=>value.id!==selected)})}>Remove working layer</button>
          <button disabled={project.layers[project.layers.length-1]?.id===selected} onClick={()=>{const layers=project.layers.slice(),index=layers.findIndex(value=>value.id===selected);[layers[index],layers[index+1]]=[layers[index+1],layers[index]];process({...project,layers});}}>Move layer forward</button>
          <button disabled={project.layers[0]?.id===selected} onClick={()=>{const layers=project.layers.slice(),index=layers.findIndex(value=>value.id===selected);[layers[index],layers[index-1]]=[layers[index-1],layers[index]];process({...project,layers});}}>Move layer backward</button>
        </div>
      </>}
      {textureSources.length>0&&<><label>Drawing photo layer<select aria-label="Raster drawing photo layer" value={sourceId} onChange={event=>setSourceId(event.target.value)}><option value="">Choose a visible unlocked photo</option>{textureSources.map((value,index)=><option key={value.id} value={value.id}>Photo {index+1}</option>)}</select></label><button disabled={!sourceId||project?.layers.length>=4} onClick={addLayer}>Add photo layer</button><p>Additional full photo copies fit the canvas, centered with transparent padding. Existing crop and rotation are not copied.</p></>}
      <label>PhotoCraft effect<select aria-label="PhotoCraft effect" value={effect} onChange={event=>{setEffect(event.target.value);setAmount(CORE_EFFECTS[event.target.value].value);setApproved(false);}}>{Object.entries(CORE_EFFECTS).map(([id,value])=><option key={id} value={id}>{value.label}</option>)}</select></label>
      {rule.max>rule.min&&<label>Effect amount<input aria-label="PhotoCraft effect amount" type="range" min={rule.min} max={rule.max} step={rule.step} value={amount} onChange={event=>{setAmount(Number(event.target.value));setApproved(false);}}/><output>{amount}</output></label>}
      <button disabled={!layer} onClick={()=>process(project,{id:effect,value:amount,layerId:selected})}>Apply PhotoCraft effect to working layer</button>
      <div className="tool-grid"><button disabled={!layer} onClick={()=>setNeural({kind:'style',id:layer.id,png:layer.png})}>Neural paint working layer</button><button disabled={!layer} onClick={()=>setNeural({kind:'denoise',id:layer.id,png:layer.png})}>Neural cleanup working layer</button></div>
      <button disabled={!history.current.length} onClick={()=>process(history.current[history.current.length-1],null,true)}>Undo raster edit</button>
      <label className="check-label"><input type="checkbox" checked={approved} onChange={event=>setApproved(event.target.checked)}/>I reviewed this complete composition and want a flattened copy.</label>
      <div className="tool-grid"><button disabled={!approved||!preview} onClick={()=>useResult(false)}>Use reviewed raster as model texture</button><button disabled={!approved||!preview} onClick={()=>useResult(true)}>Add reviewed raster image to drawing</button></div>
    </fieldset>
    {neural&&<NeuralTextureStudio texture={neural.png} kind={neural.kind} target="layer" onClose={()=>setNeural(null)}
      onApply={async(value,current)=>{current();const previous=saved.current;if(!previous||!previous.layers.some(item=>item.id===neural.id))return false;const image=await raster(value,{width:previous.width,height:previous.height});current();if(!life.current.mounted||document.hidden||saved.current!==previous)return false;return process({...previous,layers:previous.layers.map(item=>item.id===neural.id?{...item,png:image.png}:item)},null,false,current);}}
      onArtwork={onArtwork}/>}
    {busy&&<button onClick={stop}>Stop raster processing</button>}
    <p role="status" aria-live="polite">{message}</p>
    <button onClick={()=>{stop();onClose();}}>Close raster lab</button>
    <p>No automatic save. Keep your result before closing; layers and this lab's Undo are temporary. Final output is a flattened copy; it does not retain editable raster layers or add PSD support. Saved model/drawing copies use shared local storage after logout. Originals and the current model stay unchanged until explicit use. This is a bounded subset of PhotoCraft, not the full desktop editor.</p>
  </section>;
}
