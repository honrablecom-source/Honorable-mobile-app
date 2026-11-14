import {dirname,resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {readFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {verifyAiInputs} from './ai-inputs.mjs';
import {OBJECT_MODEL} from './ai-object.mjs';
import {STYLE_MODEL} from '../creation-web/neural-style.mjs';
import {DENOISE_MODEL} from '../creation-web/neural-denoise.mjs';
const root=dirname(fileURLToPath(import.meta.url));
const bundle=resolve(process.argv[2]||resolve(root,'../android/app/src/main/assets/creative-editors/image'));
verifyAiInputs(bundle);
const receipt=JSON.parse(readFileSync(resolve(bundle,'bundle.json')));
for(const [name,pin] of Object.entries(receipt.files)){
  if(!/^[A-Za-z0-9_./-]+$/.test(name)||name.split('/').some(part=>!part||part==='.'||part==='..'))throw Error('Invalid bundled path.');
  const bytes=readFileSync(resolve(bundle,name));
  if(bytes.length!==pin.bytes||createHash('sha256').update(bytes).digest('hex')!==pin.sha256)throw Error('A bundled image studio asset changed.');
}
for(const pin of [OBJECT_MODEL,STYLE_MODEL,DENOISE_MODEL]){
  const bytes=readFileSync(resolve(bundle,'ai',pin.file));
  if(bytes.length!==pin.bytes||createHash('sha256').update(bytes).digest('hex')!==pin.sha256)throw Error('A pinned studio neural model changed.');
}
console.log('Studio AI source, bundled assets and all three exact neural models verified; device acceptance remains separate.');
