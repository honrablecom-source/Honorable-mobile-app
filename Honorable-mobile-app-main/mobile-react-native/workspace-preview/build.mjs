import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
import fs from 'node:fs/promises';
import {createHash} from 'node:crypto';
const root=path.dirname(fileURLToPath(import.meta.url));
const modules=process.env.NOBLE_PREVIEW_NODE_MODULES||path.join(root,'node_modules');
const require=createRequire(path.join(modules,'../package.json'));
const {build}=require('esbuild');
const output=path.resolve(root,'../../../.context/noble-mobile-preview');
await fs.mkdir(output,{recursive:true});
const nativeReviews=await Promise.all(['regions-review.html','photo-layer-review.html','timeline-review.html'].map(name=>fs.stat(path.resolve(root,'../../../.context/noble-editor-preview',name)).then(()=>true,()=>false)));
const kotlin=await fs.readFile(path.join(root,'../android/app/src/main/java/com/honorablemobile/CreatedPhotoCanvas.kt'),'utf8');
const formats=Object.fromEntries([...kotlin.matchAll(/\w+\("([a-z_]+)",(\d+),(\d+)\)/g)].map(([,id,w,h])=>[id,[Number(w),Number(h)]]));
if(Object.keys(formats).length!==12)throw Error('Review source canvas formats before previewing.');
const sourceContract=JSON.parse(await fs.readFile(path.join(root,'native-ui-contract.json'),'utf8'));
for(const [name,expected]of Object.entries(sourceContract.sources)){
 const bytes=await fs.readFile(path.resolve(root,'../../android-app/app/src/main/java/app/honorable/editor',name));
 if(createHash('sha256').update(bytes).digest('hex')!==expected)throw Error(`Review the browser mirror and refresh native-ui-contract.json after ${name} changes.`);
}
const photoSource=await fs.readFile(path.resolve(root,'../../android-app/app/src/main/java/app/honorable/editor/PhotoEditorActivity.kt'),'utf8');
const directBlock=photoSource.split('val directTools=listOf<Pair<String,()->Unit>>(')[1]?.split('directTools.forEach')[0];
if(!directBlock)throw Error('Native photo toolbar changed; review the preview contract.');
const directTools=[...directBlock.matchAll(/"([^"\n]+)" to \{/g)].map(match=>match[1]);
const inspectorBlock=photoSource.split('private fun openInspector()')[1]?.split('private fun ')[0];
const inspector=[];
for(const line of inspectorBlock.split('\n')){
 const group=line.match(/^\s*"([^"\n]+)" to /);if(!group)continue;
 let tools=[...line.matchAll(/t\("([^"\n]+)"\)/g)].map(match=>match[1]);
 const enumMatch=line.match(/to (Photo\w+Tool)\.values/);
 if(enumMatch){const filename={PhotoCreativeTool:'PhotoCreative',PhotoEffectTool:'PhotoEffects',PhotoCompositionTool:'PhotoComposition'}[enumMatch[1]];if(!filename)throw Error('Unknown native inspector enum');const enumSource=await fs.readFile(path.resolve(root,`../../android-app/app/src/main/java/app/honorable/editor/${filename}.kt`),'utf8');tools=[...enumSource.split('\n}')[0].matchAll(/[A-Z_]+\("([^"\n]+)"\)/g)].map(match=>match[1]);}
 inspector.push({title:group[1],tools});
}

if(directTools.length!==12||!inspector.some(group=>group.tools.includes('Local effect regions')))throw Error('Native photo UI extraction needs review.');
const videoSource=await fs.readFile(path.resolve(root,'../android/app/src/main/java/com/honorablemobile/HonorableVideoEditorActivity.kt'),'utf8');
const videoBlock=videoSource.split('val options=listOf(')[1]?.split('options.forEach')[0];
const videoTools=[...videoBlock.matchAll(/"([^"\n]+)" to "([^"\n]+)"/g)].map(([,title,group])=>({title,group}));
if(videoTools.length!==8)throw Error('Review native video toolbar extraction.');
const videoGroups={};
for(const [group,parent] of Object.entries({Picture:'pictureTools',Project:'projectTools',Assist:'assistTools',Audio:'audioTools',Titles:'creativeTools'})){
 const pattern=new RegExp(parent+'\\.addView\\(Button\\(this\\)\\.apply\\s*\\{\\s*text\\s*=\\s*"([^"\\n]+)"','g');
 videoGroups[group]=[...videoSource.matchAll(pattern)].map(match=>match[1]);
}
videoGroups.Audio.push(...[...videoSource.matchAll(/(?:musicButton|audioButton)\("([^"\n]+)"\)/g)].map(match=>match[1]));
videoGroups.Titles.unshift('Title','Video fade');videoGroups.Project.push('Preview clip','Preview project','Save project','Restore project','Delete saved project');

const storySource=await fs.readFile(path.resolve(root,'../android/app/src/main/java/com/honorablemobile/PhotoVideoCreatorActivity.kt'),'utf8');
const storyTitle=storySource.match(/text="(Noble · photos to video)"/)?.[1];
const storyDescription=storySource.match(/text="(Choose 1–20 photos\.[^"\n]+)"/)?.[1];
const storyButtons=[...storySource.matchAll(/button\("([^"\n]+)"\)/g)].map(match=>match[1]);
const projectSource=await fs.readFile(path.resolve(root,'../android/app/src/main/java/com/honorablemobile/PhotoVideoProject.kt'),'utf8');
const storyFormats=[...projectSource.matchAll(/([A-Z]+)\(\d+,\d+,"([^"\n]+)"\)/g)].map(([,id,title])=>({id:id.toLowerCase(),title}));
if(!storyTitle||!storyDescription||storyButtons.length!==4||storyFormats.length!==3)throw Error('Review native photo-video creator extraction.');
const selectiveControls=await fs.readFile(path.resolve(root,'../../android-app/app/src/main/java/app/honorable/editor/PhotoSelectiveColorControls.kt'),'utf8');
const selectiveInfo=selectiveControls.match(/text="(Choose a circular hue[^"\n]+)"/)?.[1];
const selectiveLabels=[...selectiveControls.matchAll(/slider\("([^"\n]+)"/g)].map(match=>match[1]);
if(selectiveLabels.length!==6)throw Error('Review native selective color controls.');
const drawingSource=await fs.readFile(path.resolve(root,'../android/app/src/main/java/com/honorablemobile/HonorableDrawingActivity.kt'),'utf8');
const drawingButtons=[...drawingSource.matchAll(/actions\.addView\(Button\(this\)\.apply\{text="([^"\n]+)"/g)].map(match=>match[1]);
const drawingMessage=drawingSource.match(/text="(Offline drawing[^"\n]+)"/)?.[1];
if(drawingButtons.length!==2||!drawingMessage)throw Error('Review native drawing wrapper extraction.');
const motionSource=await fs.readFile(path.resolve(root,'../android/app/src/main/java/com/honorablemobile/VideoMotionControls.kt'),'utf8');
const motionLabels=[...motionSource.matchAll(/slider\("([^"\n]+)"/g)].map(match=>match[1]);
const motionInfo=motionSource.match(/text="(Animate the picture[^"\n]+)"/)?.[1];
if(motionLabels.length!==6||!motionInfo)throw Error('Review native clip motion controls.');
const aliases={name:'noble-preview-boundaries',setup(builder){
 builder.onResolve({filter:/^react-native$/},()=>({path:path.join(root,'rn-web.mjs')}));
 builder.onResolve({filter:/^react-native-safe-area-context$/},()=>({path:path.join(root,'safe-area.jsx')}));
 builder.onResolve({filter:/^lucide-react-native$/},()=>({path:require.resolve('lucide-react')}));
 builder.onResolve({filter:/\/MemoryPassContext$/},()=>({path:path.join(root,'pass-browser.mjs')}));
 builder.onResolve({filter:/\/HonorableNative$/},()=>({path:path.join(root,'native-browser.mjs')}));
 builder.onResolve({filter:/^(react|react-dom)(\/.*)?$/},args=>({path:require.resolve(args.path)}));
}};
await build({entryPoints:[path.join(root,'app.jsx')],bundle:true,minify:true,format:'iife',outfile:path.join(output,'app.js'),plugins:[aliases],nodePaths:[modules],target:['chrome100'],define:{'process.env.NODE_ENV':'"production"','NOBLE_CANVAS_FORMATS':JSON.stringify(formats),'NOBLE_HAS_NATIVE_REVIEWS':JSON.stringify(nativeReviews.every(Boolean)),'NOBLE_NATIVE_PHOTO_UI':JSON.stringify({directTools,inspector}),'NOBLE_NATIVE_VIDEO_TOOLS':JSON.stringify(videoTools),'NOBLE_NATIVE_VIDEO_GROUPS':JSON.stringify(videoGroups),'NOBLE_NATIVE_DRAWING_UI':JSON.stringify({buttons:drawingButtons,message:drawingMessage}),'NOBLE_MOTION_UI':JSON.stringify({labels:motionLabels,info:motionInfo}),'NOBLE_SELECTIVE_LABELS':JSON.stringify(selectiveLabels),'NOBLE_SELECTIVE_INFO':JSON.stringify(selectiveInfo),'NOBLE_NATIVE_STORY_UI':JSON.stringify({title:storyTitle,description:storyDescription,buttons:storyButtons,formats:storyFormats})},external:['/fonts/*'],legalComments:'external',loader:{'.png':'file','.jpg':'file'},assetNames:'assets/[name]-[hash]'});
await fs.mkdir(path.join(output,'fonts'),{recursive:true});
for(const weight of [400,500,600,700])await fs.copyFile(path.resolve(root,`../../android-app/test-lab/web-test-shell/Roboto-${weight}.ttf`),path.join(output,`fonts/Roboto-${weight}.ttf`));
await fs.copyFile(path.resolve(root,'../../android-app/test-lab/web-test-shell/Roboto-LICENSE.txt'),path.join(output,'fonts/Roboto-LICENSE.txt'));
for(const name of ['index.html','preview.css'])await fs.copyFile(path.join(root,name),path.join(output,name));
console.log('Noble source UI preview compiled: shared welcome/guest, native editor mirrors; Android pixel acceptance pending.');

const version=createHash('sha256').update(await fs.readFile(path.join(output,'app.js'),'utf8')+photoSource+videoSource+storySource+await fs.readFile(path.join(root,'app.jsx'),'utf8')+await fs.readFile(path.join(root,'preview.css'),'utf8')+await fs.readFile(path.join(root,'../src/auth/AccountGate.tsx'),'utf8')+await fs.readFile(path.join(root,'../src/screens/GuestEditingScreen.tsx'),'utf8')).digest('hex');
await fs.writeFile(path.join(output,'build-info.json'),JSON.stringify({version}));
