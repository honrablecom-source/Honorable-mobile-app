import { SVG_LIMITS } from './svg-import-limits.mjs';
let sequence=0;
export function vectorFileText(text){
  if(typeof text!=='string'||!text.length||new TextEncoder().encode(text).length>SVG_LIMITS.bytes)throw Error('Choose a nonempty SVG file up to 64 KB.');
  for(let i=0;i<text.length;i++){const code=text.charCodeAt(i);if(code>=0xd800&&code<=0xdbff){const next=text.charCodeAt(++i);if(!(next>=0xdc00&&next<=0xdfff))throw Error('The SVG file contains invalid Unicode.');}else if(code>=0xdc00&&code<=0xdfff)throw Error('The SVG file contains invalid Unicode.');}
  const value=text.replace(/^\uFEFF/,'');if(!value.length)throw Error('Choose a nonempty SVG file up to 64 KB.');return value;
}
export function decodeVectorFile(bytes){
  if(!(bytes instanceof ArrayBuffer)&&!ArrayBuffer.isView(bytes))throw Error('The SVG file could not be read.');
  const view=bytes instanceof ArrayBuffer?new Uint8Array(bytes):new Uint8Array(bytes.buffer,bytes.byteOffset,bytes.byteLength);
  if(!view.length||view.length>SVG_LIMITS.bytes)throw Error('Choose a nonempty SVG file up to 64 KB.');
  try{return vectorFileText(new TextDecoder('utf-8',{fatal:true}).decode(view));}catch(error){if(error instanceof TypeError)throw Error('Save this SVG as UTF-8 before importing.');throw error;}
}
export function createVectorFileRequest(bridge,{isCurrent}={}){
  const id=sequence=sequence>=1e9?1:sequence+1;let phase='idle',admitted=false,released=false,queued=null,resolvePromise,rejectPromise;
  const promise=new Promise((resolve,reject)=>{resolvePromise=resolve;rejectPromise=reject;});
  const release=()=>{if(admitted&&!released){released=true;try{bridge.releaseVectorFile(id);}catch{}}};
  const finish=(error,value)=>{if(phase==='done')return;phase='done';release();if(error)rejectPromise(error);else resolvePromise(value);};
  const current=()=>{try{return typeof isCurrent==='function'&&isCurrent();}catch{return false;}};
  const receive=message=>{
    if(phase==='done'||!message||message.requestId!==id)return;
    if(phase==='starting'){if(!queued)queued=message;return;}
    if(phase!=='pending')return;
    phase='reading';
    try{
      if(!current())throw Error('The drawing changed while choosing the SVG. Choose it again.');
      if(Object.keys(message).some(key=>!['requestId','length','cancelled','error'].includes(key)))throw Error('The SVG file response was incomplete.');
      if(message.cancelled){finish(null,null);return;}
      if(message.error)throw Error('The SVG file could not be read. Choose a supported local file.');
      if(!Number.isInteger(message.length)||message.length<1||message.length>SVG_LIMITS.bytes)throw Error('Choose a nonempty SVG file up to 64 KB.');
      let raw='',offset=0;
      for(let count=0;offset<message.length;count++){
        if(phase==='done'||count>=64||!current())throw Error('The SVG file transfer stopped. Choose it again.');
        const part=bridge.readVectorFileChunk(id,offset);
        if(phase==='done')return;
        if(typeof part!=='string'||!part.length||part.length>16384||offset+part.length>message.length)throw Error('The SVG file transfer was incomplete.');
        raw+=part;offset+=part.length;
      }
      if(!current())throw Error('The drawing changed while choosing the SVG. Choose it again.');
      finish(null,vectorFileText(raw));
    }catch(error){finish(error instanceof Error?error:Error('The SVG file could not be read.'));}
  };
  return{id,promise,receive,start(){
    if(phase!=='idle')return;phase='starting';
    try{
      if(!current())throw Error('The drawing changed. Choose the SVG again.');
      if(!bridge||!['chooseVectorFile','readVectorFileChunk','releaseVectorFile'].every(name=>typeof bridge[name]==='function'))throw Error('Update Noble to choose SVG artwork files.');
      const accepted=bridge.chooseVectorFile(id);admitted=accepted===true;
      if(phase==='done'){release();return;}
      if(!admitted)throw Error('Another file or image operation is running. Try again when ready.');
      phase='pending';if(queued){const reply=queued;queued=null;receive(reply);}
    }catch(error){finish(error instanceof Error?error:Error('The SVG file chooser could not open.'));}
  },cancel(){finish(Error('SVG file selection stopped. The drawing is unchanged.'));}};
}
