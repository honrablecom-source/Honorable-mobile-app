import {readFileSync,writeFileSync,mkdirSync,copyFileSync} from 'node:fs';
import {createRequire} from 'node:module';
import {dirname,join,resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import {OBJECT_MODEL} from './ai-object.mjs';
import {STYLE_MODEL} from '../creation-web/neural-style.mjs';
import {DENOISE_MODEL} from '../creation-web/neural-denoise.mjs';
import {collectAiInputs} from './ai-inputs.mjs';
const root=dirname(fileURLToPath(import.meta.url)),creation=resolve(root,'../creation-web');
const hash=bytes=>createHash('sha256').update(bytes).digest('hex');
export async function packageAi(out){
  const inputs=collectAiInputs();
  const modules=process.env.NOBLE_EDITOR_NODE_MODULES||join(creation,'node_modules');
  const require=createRequire(join(modules,'../package.json'));
  const {build}=require('esbuild');
  const worker=await build({entryPoints:[join(root,'ai-worker.mjs')],bundle:true,minify:true,write:false,format:'iife',platform:'browser',tsconfigRaw:{compilerOptions:{}},target:['chrome90'],nodePaths:[modules],legalComments:'inline'});
  const plugin={name:'studio-inline-ai-worker',setup(builder){
    builder.onResolve({filter:/^noble:studio-ai-worker$/},()=>({path:'worker',namespace:'studio-worker'}));
    builder.onLoad({filter:/.*/,namespace:'studio-worker'},()=>({contents:worker.outputFiles[0].text,loader:'text'}));
  }};
  await build({entryPoints:[join(root,'ai-tools.mjs')],plugins:[plugin],bundle:true,minify:true,format:'iife',platform:'browser',tsconfigRaw:{compilerOptions:{}},target:['chrome90'],nodePaths:[modules],legalComments:'inline',outfile:join(out,'ai-tools.js')});
  mkdirSync(join(out,'ai'),{recursive:true});
  for(const pin of [OBJECT_MODEL,STYLE_MODEL,DENOISE_MODEL]){
    const source=join(pin===OBJECT_MODEL?root:creation,'ai-assets',pin.file),bytes=readFileSync(source);
    if(bytes.length!==pin.bytes||hash(bytes)!==pin.sha256)throw Error('A pinned studio neural model changed.');
    writeFileSync(join(out,'ai',pin.file),bytes);
  }
  const runtime=JSON.parse(readFileSync(join(creation,'ai-assets/provenance.json'))).runtimeFiles;
  for(const [name,pin] of Object.entries(runtime)){
    if(!['ort-wasm-simd-threaded.js','ort-wasm-simd-threaded.wasm'].includes(name))throw Error('Unexpected neural runtime.');
    const bytes=readFileSync(join(modules,'onnxruntime-web/dist',pin.source));
    if(bytes.length!==pin.bytes||hash(bytes)!==pin.sha256)throw Error('The pinned neural runtime changed.');
    writeFileSync(join(out,'ai',name),bytes);
  }
  for(const [source,name] of [[join(root,'ai-assets/provenance.json'),'object-provenance.json'],[join(creation,'ai-assets/provenance.json'),'style-provenance.json'],[join(creation,'ai-assets/dncnn-provenance.json'),'denoise-provenance.json']])writeFileSync(join(out,'ai',name),readFileSync(source,'utf8').replace(/\r\n/g,'\n'));
  const licenseFiles=[join(root,'ai-assets/U2NET-LICENSE.txt'),join(root,'ai-assets/REMBG-LICENSE.txt'),join(creation,'THIRD_PARTY/NEURAL-STYLE-BSD.txt'),join(creation,'THIRD_PARTY/DNCNN-MIT.txt'),join(creation,'THIRD_PARTY/ORT-WEB-MIT.txt')];
  const notice='Noble local AI image tools. U2NETP: Xuebin Qin et al., Apache-2.0; ONNX distribution/preprocessing reference: Daniel Gatis/rembg, MIT. Mosaic neural style: ONNX Model Zoo/PyTorch contributors, BSD-3-Clause. DnCNN: KAIR/cszn, MIT. ONNX Runtime Web1.20.1: Microsoft, MIT. Original license texts follow. Model/source pins are in ai/*provenance.json. No media is uploaded.\n\n';
  writeFileSync(join(out,'AI-NOTICES.txt'),notice+licenseFiles.map(file=>readFileSync(file,'utf8').replace(/\r\n/g,'\n')).join('\n\n'));
  if(JSON.stringify(inputs)!==JSON.stringify(collectAiInputs()))throw Error('AI source changed during packaging. Choose fresh output and retry.');
  writeFileSync(join(out,'ai-build.json'),JSON.stringify({version:1,inputs,deviceTested:false},null,2)+'\n');
}
