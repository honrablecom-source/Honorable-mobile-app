import * as ort from 'onnxruntime-web/wasm';
import {STYLE_MODEL,STYLE_SIDE,checkedStyleImage,styleTensor,stylePixels} from '../creation-web/neural-style.mjs';
import {DENOISE_MODEL,checkedDenoiseImage,noiseTiles,noiseTensor,mergeNoiseTile} from '../creation-web/neural-denoise.mjs';
import {OBJECT_MODEL,objectTensor,objectPixels,checkedObjectImage} from './ai-object.mjs';
let started=false;
self.onmessage=async event=>{
  if(started)return;started=true;
  let session;
  const {id,base,kind,width,height,rgba,modelRgba}=event.data||{};
  try{
    if(!Number.isSafeInteger(id)||id<1||!['object','denoise','style'].includes(kind))throw Error('Invalid job.');
    const directory=new URL(base);
    if(directory.origin!==self.location.origin||directory.pathname!=='/image/'||directory.search||directory.hash||directory.username||directory.password)throw Error('Local assets required.');
    const pin=kind==='object'?OBJECT_MODEL:kind==='denoise'?DENOISE_MODEL:STYLE_MODEL;
    const check=kind==='object'?checkedObjectImage:kind==='denoise'?checkedDenoiseImage:checkedStyleImage;
    const image=check({width,height,rgba:new Uint8ClampedArray(rgba)});
    self.postMessage({id,phase:'Checking the bundled model…'});
    const response=await fetch(new URL('ai/'+pin.file,directory));
    if(!response.ok)throw Error('Model unavailable.');
    const model=await response.arrayBuffer();
    if(model.byteLength!==pin.bytes)throw Error('Incomplete model.');
    const digest=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',model)),v=>v.toString(16).padStart(2,'0')).join('');
    if(digest!==pin.sha256)throw Error('Model integrity failed.');
    ort.env.wasm.numThreads=1;ort.env.wasm.proxy=false;ort.env.wasm.initTimeout=30000;
    ort.env.wasm.wasmPaths={wasm:new URL('ai/ort-wasm-simd-threaded.wasm',directory).href,mjs:new URL('ai/ort-wasm-simd-threaded.js',directory).href};
    ort.env.logLevel='error';
    self.postMessage({id,phase:'Running AI locally…'});
    session=await ort.InferenceSession.create(model,{executionProviders:['wasm'],graphOptimizationLevel:'all'});
    let output;
    if(kind==='object'){
      const input={width:320,height:320,rgba:new Uint8ClampedArray(modelRgba)};
      if(session.inputNames.length!==1)throw Error('Unexpected graph.');
      const results=await session.run({[session.inputNames[0]]:new ort.Tensor('float32',objectTensor(input),[1,3,320,320])});
      const mask=results[session.outputNames[0]];output=objectPixels(image,mask.data,mask.dims);
    }else if(kind==='denoise'){
      if(session.inputNames[0]!=='noisy'||session.outputNames[0]!=='clean')throw Error('Unexpected graph.');
      output=image.rgba.slice();const tiles=noiseTiles(image);
      for(const [index,tile] of tiles.entries()){
        self.postMessage({id,phase:`Cleaning noise locally ${index+1}/${tiles.length}…`});
        const result=await session.run({noisy:new ort.Tensor('float32',noiseTensor(image,tile),[1,1,128,128])});
        mergeNoiseTile(image,output,tile,result.clean.data,result.clean.dims);
      }
    }else{
      if(session.inputNames[0]!=='input1'||session.outputNames[0]!=='output1')throw Error('Unexpected graph.');
      const result=await session.run({input1:new ort.Tensor('float32',styleTensor(image).tensor,[1,3,STYLE_SIDE,STYLE_SIDE])});
      output=stylePixels(image,result.output1.data,result.output1.dims);
    }
    self.postMessage({id,width,height,pixels:output.buffer},[output.buffer]);
  }catch{
    self.postMessage({id,error:'The local AI tool could not finish. Try a clearer photo or reopen with an updated WebView. Your original and editor document are unchanged.'});
  }finally{await session?.release().catch(()=>{});}
};
