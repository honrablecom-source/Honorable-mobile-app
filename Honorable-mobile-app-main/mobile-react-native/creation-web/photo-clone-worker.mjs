import {cloneRepairPixelsAsync} from './photo-clone-repair.mjs';
let claimed=false;
self.onmessage=async event=>{
 const {id,width,height,pixels,strokes}=event.data||{};
 if(claimed||!Number.isInteger(id)||id<1||id>1e9)return;claimed=true;
 try{
  const result=await cloneRepairPixelsAsync(pixels,width,height,strokes,()=>{},progress=>self.postMessage({id,kind:'progress',progress}));
  self.postMessage({id,kind:'result',result:{width:result.width,height:result.height,data:result.data,changed:result.changed}},[result.data.buffer]);
 }catch(error){self.postMessage({id,kind:'error',error:error instanceof Error?error.message.slice(0,200):'The clone repair could not finish.'});}
};
