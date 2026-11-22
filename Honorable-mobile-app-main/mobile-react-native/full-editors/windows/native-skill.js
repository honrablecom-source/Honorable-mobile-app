// Both editors supply the command metadata of the engine currently loaded in the window.
export function checkCommand(commands,id) {
  const item=commands.find(entry=>entry.id===id);
  if (!item) throw Error('This command is missing from the installed editor.');
  if (item.enabled===false) throw Error(item.disabledReason || item.label+' is disabled in the current editing context.');
  return item;
}

export function resultSummary(value,depth=0,budget={nodes:800}) {
  if (depth>5 || --budget.nodes<0) return undefined;
  if (value==null || typeof value==='boolean' || typeof value==='number') return value;
  if (typeof value==='string') {
    if (value.length>512 || /(?:[a-z]:[\\/]|^[/\\]|^(?:blob|data|file):)/i.test(value)) return undefined;
    return value;
  }
  if (Array.isArray(value)) {
    if (value.length>32 && value.every(n=>typeof n==='number')) return {count:value.length};
    return value.slice(0,24).map(item=>resultSummary(item,depth+1,budget));
  }
  const out={};
  for (const [key,entry] of Object.entries(value).slice(0,40)) {
    if (/path|pixels|bytes|rgba|base64|thumbnail|bitmap|buffer|blob|uri|url|metadata|clipboard|waveform|png|jpeg/i.test(key)) continue;
    const summary=resultSummary(entry,depth+1,budget);
    if (summary!==undefined) out[key]=summary;
  }
  return out;
}
export function photoContext(doc) {
  if (!doc) return {documentId:0,revision:0,layers:[]};
  const layers=doc.layers.slice(0,256).map(({id,name,kind,visible,opacity})=>({id,name,kind,visible,opacity}));
  return {documentId:doc.documentId,revision:doc.revision,name:doc.name,width:doc.width,height:doc.height,activeLayer:doc.activeLayer,layers,layerCount:doc.layers.length,canUndo:doc.canUndo,canRedo:doc.canRedo};
}
