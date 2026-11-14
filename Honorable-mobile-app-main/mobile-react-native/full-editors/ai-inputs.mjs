import {readFileSync} from 'node:fs';
import {dirname,join,resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
const root=dirname(fileURLToPath(import.meta.url));
export function collectAiInputs(){
  const files=['ai-inputs.mjs','ai-object.mjs','ai-mask.mjs','ai-color.mjs','ai-capture.mjs','ai-worker.mjs','ai-client.mjs','ai-tools.mjs','package-ai.mjs','build-ai.mjs','export-color.py',
    'pages/image/index.html','pages/image/bootstrap.js','ai-assets/provenance.json','ai-assets/U2NET-LICENSE.txt','ai-assets/REMBG-LICENSE.txt',
    '../creation-web/neural-style.mjs','../creation-web/neural-denoise.mjs','../creation-web/image-assets.mjs',
    '../creation-web/ai-assets/provenance.json','../creation-web/ai-assets/dncnn-provenance.json',
    '../creation-web/THIRD_PARTY/NEURAL-STYLE-BSD.txt','../creation-web/THIRD_PARTY/DNCNN-MIT.txt','../creation-web/THIRD_PARTY/ORT-WEB-MIT.txt',
    'color-assets/provenance.json','color-assets/upstream/models.py','color-assets/upstream/LICENSE'];
  const inputs=Object.fromEntries(files.map(name=>{const bytes=Buffer.from(readFileSync(resolve(root,name),'utf8').replace(/\r\n/g,'\n'));return [name,{bytes:bytes.length,sha256:createHash('sha256').update(bytes).digest('hex')}];}));
  for(const name of ['color-assets/upstream/classifier.pth','color-assets/upstream/LUTs.pth','color-assets/adaptive-color.onnx','color-assets/lut-bases.f32']){
    const bytes=readFileSync(resolve(root,name));inputs[name]={bytes:bytes.length,sha256:createHash('sha256').update(bytes).digest('hex')};
  }
  return inputs;
}
export function verifyAiInputs(bundle){
  const receipt=JSON.parse(readFileSync(join(bundle,'ai-build.json'),'utf8'));
  if(receipt.version!==1||JSON.stringify(receipt.inputs)!==JSON.stringify(collectAiInputs()))throw Error('Studio AI assets are stale. Regenerate them from the current pinned source before building.');
  return receipt;
}
