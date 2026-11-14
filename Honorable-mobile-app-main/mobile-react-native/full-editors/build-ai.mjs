// Add the local AI workflow to a verified editor bundle, using fresh staging output.
import {readFileSync,writeFileSync,existsSync,cpSync,readdirSync} from 'node:fs';
import {resolve,join,relative,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import {packageAi} from './package-ai.mjs';
const root=dirname(fileURLToPath(import.meta.url));
const [baseArg,outArg]=process.argv.slice(2);
if(!baseArg||!outArg)throw Error('Usage: node full-editors/build-ai.mjs EXISTING_IMAGE_BUNDLE FRESH_OUTPUT');
const base=resolve(baseArg),out=resolve(outArg),digest=bytes=>createHash('sha256').update(bytes).digest('hex');
if(existsSync(out)||out.startsWith(base+'/')||out.startsWith(base+'\\'))throw Error('Choose a fresh output outside the source bundle.');
const receipt=JSON.parse(readFileSync(join(base,'bundle.json')));
if(receipt.kind!=='image'||receipt.sourceRevision!==JSON.parse(readFileSync(join(root,'upstreams.json'))).image.revision)throw Error('A pinned full image bundle is required.');
for(const [name,pin] of Object.entries(receipt.files)){
  if(!/^[A-Za-z0-9_./-]+$/.test(name)||name.split('/').some(part=>!part||part==='.'||part==='..'))throw Error('Invalid source asset path.');
  const bytes=readFileSync(join(base,name));if(bytes.length!==pin.bytes||digest(bytes)!==pin.sha256)throw Error('Source editor bytes differ from their receipt.');
}
cpSync(base,out,{recursive:true});await packageAi(out);
for(const name of ['index.html','bootstrap.js'])writeFileSync(join(out,name),readFileSync(join(root,'pages/image',name),'utf8').replace(/\r\n/g,'\n'));
const files={};function collect(directory){for(const entry of readdirSync(directory,{withFileTypes:true})){const file=join(directory,entry.name);if(entry.isDirectory())collect(file);else if(entry.isFile()&&file!==join(out,'bundle.json')){const bytes=readFileSync(file);files[relative(out,file).replaceAll('\\','/')]={bytes:bytes.length,sha256:digest(bytes)};}}}
collect(out);writeFileSync(join(out,'bundle.json'),JSON.stringify({...receipt,files},null,2)+'\n');
console.log(JSON.stringify({kind:'image',files:Object.keys(files).length,neuralModels:3,deviceTested:false}));
