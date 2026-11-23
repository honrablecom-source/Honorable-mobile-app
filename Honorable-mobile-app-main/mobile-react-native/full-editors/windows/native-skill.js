// Both editors supply the command metadata of the engine currently loaded in the window.
export function searchEditorCommands(rows,query) {
  const words = value => value.replace(/([a-z])([A-Z])/g,'$1 $2').toLowerCase().split(/[^\p{L}\p{N}]+/u).filter(Boolean);
  const terms=words(query);
  if (!terms.length) return rows;
  const phrase=terms.join(' ');
  const matches=[];
  for (const row of rows) {
    const id=words(row.id).join(' ');
    const tool=words(row.tool || row.name || '').join(' ');
    const label=words(row.label || '').join(' ');
    const text=id+' '+tool+' '+label+' '+row.id.toLowerCase();
    if (!terms.every(term=>text.includes(term))) continue;
    const rank=id===phrase || tool===phrase ? 0 : label===phrase ? 1 : id.endsWith(' '+phrase) ? 2 : id.includes(phrase) ? 3 : 4;
    matches.push({row,rank});
  }
  return matches.sort((a,b)=>a.rank-b.rank).map(match=>match.row);
}

export function checkCommand(commands,id) {
  const item=commands.find(entry=>entry.id===id);
  if (!item) throw Error('This command is missing from the installed editor.');
  if (item.enabled===false) {
    if (id.startsWith('paint.') && item.disabledReason==='no active layer') {
      throw Error('No layer is selected. Call get_photo_context, choose a visible pixel layer with photo_layer_select, then retry the stroke.');
    }
    throw Error(item.disabledReason || item.label+' is disabled in the current editing context.');
  }
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
