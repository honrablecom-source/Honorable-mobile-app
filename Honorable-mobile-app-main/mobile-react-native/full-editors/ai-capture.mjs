// Read-only flattened copies from the pinned full engine. Internal snapshot
// downloads are always swallowed, including late cancelled exports, so they can
// never accidentally reach the native share bridge.
const prefix='Noble-AI-snapshot-'+Array.from(crypto.getRandomValues(new Uint32Array(4)),value=>value.toString(16).padStart(8,'0')).join('')+'-';
let active=null,enginePending=0,sequence=0;
window.addEventListener('click',event=>{
  const link=event.target instanceof Element?event.target.closest('a[download]'):null;
  if(!link||!link.download.startsWith(prefix))return;
  let url;try{url=new URL(link.href,location.href);}catch{return;}
  if(url.protocol!=='blob:'||url.origin!==location.origin)return;
  event.preventDefault();event.stopImmediatePropagation();
  const owner=active;
  if(!owner||owner.settled||link.download!==owner.name||owner.received||!owner.current()||document.hidden)return;
  owner.received=true;
  (async()=>{
    try{
      const response=await fetch(url.href,{signal:owner.abort.signal});
      if(!response.ok)throw Error('Snapshot unavailable.');
      const blob=await response.blob();
      if(owner.settled||active!==owner||!owner.current()||document.hidden)throw Error('Snapshot stopped.');
      if(blob.size<1||blob.size>12*1024*1024)throw Error('Choose a PNG/JPEG working copy up to12MB.');
      owner.resolveDownload(blob);
    }catch(error){owner.rejectDownload(error);}
  })();
},true);

export function startDocumentSnapshot(request,current=()=>true){
  if(active||enginePending)throw Error('The previous document snapshot is still finishing. Wait or choose a local photo.');
  const owner={name:prefix+(++sequence)+'.png',current,settled:false,received:false,abort:new AbortController()};
  active=owner;let resolveTask,rejectTask;
  const promise=new Promise((resolve,reject)=>{resolveTask=resolve;rejectTask=reject;});
  const download=new Promise((resolve,reject)=>{owner.resolveDownload=resolve;owner.rejectDownload=reject;});
  download.catch(()=>{});
  const guard=()=>{if(owner.settled||active!==owner||!current()||document.hidden)throw Error('Snapshot stopped. Your document is unchanged.');};
  const finish=(error,blob)=>{
    if(owner.settled)return;owner.settled=true;clearTimeout(owner.timer);owner.abort.abort();
    if(active===owner)active=null;
    if(error){owner.rejectDownload(error);rejectTask(error);}else resolveTask(blob);
  };
  owner.timer=setTimeout(()=>finish(Error('The document snapshot timed out. Your layered project is unchanged.')),45000);
  const rpc=(method,params={})=>{
    guard();enginePending++;
    return Promise.resolve().then(()=>{guard();return request(method,params);}).finally(()=>{enginePending--;});
  };
  (async()=>{
    try{
      const before=await rpc('ui.inspect');guard();
      const doc=before?.document;
      if(!doc||!Number.isInteger(doc.width)||!Number.isInteger(doc.height)||doc.width<1||doc.height<1||doc.width>16384||doc.height>16384||doc.width*doc.height>16*1024*1024)
        throw Error('Open an image document up to16megapixels, or choose a local photo.');
      const saved=rpc('app.save',{path:'/exports/'+owner.name}).then(value=>{
        if(!owner.received)throw Error('The image studio could not prepare a local working copy.');return value;
      });
      const [,blob]=await Promise.all([saved,download]);guard();
      const after=await rpc('ui.inspect');guard();
      if(JSON.stringify(before.document)!==JSON.stringify(after?.document))throw Error('The document changed. Capture a fresh working copy before using AI.');
      finish(null,blob);
    }catch(error){finish(error instanceof Error?error:Error('The working copy could not be prepared. Your document is unchanged.'));}
  })();
  return {promise,stop(){finish(Error('Snapshot stopped. Your layered project is unchanged.'));}};
}
