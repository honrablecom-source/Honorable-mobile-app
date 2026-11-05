import test from 'node:test';import assert from 'node:assert/strict';
import { projectFilename, checkedProjectFile, decodeProjectFile, sendProjectFile, receiveProjectFile, PROJECT_CHUNK_CHARS } from '../project-files.mjs';
import { exportProject, MAX_PORTABLE_BYTES } from '../projects.mjs';
const box={id:'a',type:'rectangle',x:0,y:0,width:50,height:50};const raw=exportProject('My project',[box]);
test('bounded strict UTF8 files validate through the actual project codec with BOM compatibility',()=>{
 assert.equal(decodeProjectFile(new TextEncoder().encode('\uFEFF'+raw)).project.name,'My project');
 assert.equal(checkedProjectFile(raw).raw,raw);
 for(const bytes of [new Uint8Array(),new Uint8Array([0xc3,0x28]),new Uint8Array(MAX_PORTABLE_BYTES+1)])assert.throws(()=>decodeProjectFile(bytes));
 for(const value of ['{}','{"format":"noble-drawing","version":99}','x'.repeat(MAX_PORTABLE_BYTES+1)])assert.throws(()=>checkedProjectFile(value));
 assert.equal(projectFilename(' folder/name '),'folder_name.noble.json');assert.equal(projectFilename('...'),'Noble project.noble.json');assert.throws(()=>projectFilename('bad\nname'));assert.throws(()=>projectFilename('bad\u0085name'));
});
test('chunked native save reconstructs exact large Unicode payload without splitting pairs',()=>{
 const huge=exportProject('作品🙂',Array.from({length:60},(_,i)=>({id:`t${i}`,type:'text',text:'作品🙂'.repeat(300),x:i,y:0,width:500,height:500})));
 const parts=[];let finished=false;const bridge={beginProjectFileSave:(id,name,length)=>{assert.equal(id,1);assert.equal(name,'作品🙂');assert.equal(length,huge.length);return true;},appendProjectFileChunk:(id,value)=>{assert(value.length<=PROJECT_CHUNK_CHARS);assert(!/[\uD800-\uDBFF]$/.test(value));parts.push(value);return true;},finishProjectFileSave:()=>{finished=true;},releaseProjectFile:()=>assert.fail('unexpected release')};
 sendProjectFile(bridge,1,'作品🙂',huge);assert(parts.length>2);assert.equal(parts.join(''),huge);assert(finished);
});
test('failed chunk upload releases its request and busy rejection does not cancel another operation',()=>{
 let releases=0,finish=0;const bridge={beginProjectFileSave:()=>true,appendProjectFileChunk:()=>false,finishProjectFileSave:()=>finish++,releaseProjectFile:()=>releases++};
 assert.throws(()=>sendProjectFile(bridge,2,'My project',raw),/stopped/);assert.equal(releases,1);assert.equal(finish,0);
 bridge.beginProjectFileSave=()=>false;assert.throws(()=>sendProjectFile(bridge,3,'My project',raw),/busy/);assert.equal(releases,1);
});
test('native reads validate size/progress/document and always release on success or failure',()=>{
 let releases=0;const bridge={readProjectFileChunk:(_,offset)=>raw.slice(offset,offset+10),releaseProjectFile:()=>releases++};
 assert.equal(receiveProjectFile(bridge,1,raw.length).raw,raw);assert.equal(releases,1);
 for(const length of [0,NaN,MAX_PORTABLE_BYTES+1])assert.throws(()=>receiveProjectFile(bridge,1,length));
 bridge.readProjectFileChunk=()=>'';assert.throws(()=>receiveProjectFile(bridge,1,raw.length),/incomplete/);
 bridge.readProjectFileChunk=()=>'{oops}';assert.throws(()=>receiveProjectFile(bridge,1,6));assert.equal(releases,6);
});


test('editable selection files keep label/frame/hidden/photo state and omit outside artwork without mutation', async () => {
 const {projectFileArtwork}=await import('../project-files.mjs');const {photoFile}=await import('../image-assets.mjs');
 const file=photoFile('data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLbtAAAAABJRU5ErkJggg==',0);
 const elements=[{id:'frame',type:'frame',x:100,y:100,width:300,height:200},{...box,id:'card',x:120,y:120,frameId:'frame',boundElements:[{id:'label',type:'text'}]},{id:'label',type:'text',text:'Keep me',x:125,y:125,width:50,height:20,containerId:'card',frameId:'frame'},{id:'photo',type:'image',x:150,y:150,width:40,height:40,frameId:'frame',opacity:0,locked:true,fileId:file.id,scale:[-1,1],crop:null,status:'saved'},{...box,id:'outside',x:900}];
 const files={[file.id]:{...file,sourceUri:'content://synthetic-private',name:'original-name'}},before=structuredClone({elements,files});
 const chosen=JSON.parse(projectFileArtwork('Part',elements,{},files,{frame:true}));const scene=JSON.parse(chosen.scene);
 assert.deepEqual(scene.elements.map(e=>e.id),['frame','card','label','photo']);assert.equal(scene.elements.find(e=>e.id==='photo').opacity,0);assert.equal(scene.elements.find(e=>e.id==='photo').locked,true);assert.deepEqual(scene.files,{[file.id]:file});assert(!JSON.stringify(scene).includes('synthetic-private'));assert(!JSON.stringify(scene).includes('original-name'));
 assert.equal(JSON.parse(JSON.parse(projectFileArtwork('Whole',elements,{},files)).scene).elements.length,5);
 for(const ids of [{},{missing:true},false,[]])assert.throws(()=>projectFileArtwork('Bad',elements,{},files,ids),/Select/);
 assert.deepEqual({elements,files},before);
});

test('recovery archive preserves opaque exact text without accepting it as an editable project', async()=>{
 const {recoveryFile,checkedRecoveryFile}=await import('../project-files.mjs');
 for(const text of ['{broken JSON', ' \n作品🙂\u0000\uD800 \\ " ',JSON.stringify({version:99,elements:[],future:'keep'})]){
  const raw=recoveryFile(text);assert.equal(checkedRecoveryFile(raw).draft,text);assert.throws(()=>checkedProjectFile(raw),/Unsupported/);
 }
 for(const text of [null,undefined,''])assert.throws(()=>recoveryFile(text));
 assert.throws(()=>checkedRecoveryFile('{"format":"noble-drawing-recovery","version":2,"draft":"x"}'));
 assert.throws(()=>checkedRecoveryFile('{"format":"noble-drawing-recovery","version":1,"draft":"x","extra":"no"}'));
});
test('recovery envelope exact byte ceiling rejects without truncation or accepting unsafe editable content',async()=>{
 const {recoveryFile,checkedRecoveryFile}=await import('../project-files.mjs');const overhead=new TextEncoder().encode(recoveryFile('x')).length-1;
 const text='x'.repeat(MAX_PORTABLE_BYTES-overhead);const result=recoveryFile(text);assert.equal(new TextEncoder().encode(result).length,MAX_PORTABLE_BYTES);assert.equal(checkedRecoveryFile(result).draft,text);
 for(const huge of [text+'x','\u0000'.repeat(Math.ceil(MAX_PORTABLE_BYTES/6)),'界'.repeat(Math.ceil(MAX_PORTABLE_BYTES/3))])assert.throws(()=>recoveryFile(huge),/limit|large/);
});
test('recovery uses bounded chunk protocol but ordinary send still refuses recovery envelope',async()=>{
 const {recoveryFile,sendRecoveryFile}=await import('../project-files.mjs');const original='x'.repeat(PROJECT_CHUNK_CHARS-1)+'🙂作品'+ 'y'.repeat(70000),raw=recoveryFile(original),parts=[];let finish=0;
 const bridge={beginProjectFileSave:(id,name,length)=>{assert.equal(id,12);assert.equal(name,'Preserved drawing recovery');assert.equal(length,raw.length);return true;},appendProjectFileChunk:(_,text)=>{assert(text.length<=PROJECT_CHUNK_CHARS);assert(!/[\uD800-\uDBFF]$/.test(text));parts.push(text);return true;},finishProjectFileSave:()=>finish++,releaseProjectFile:()=>assert.fail('unexpected release')};
 sendRecoveryFile(bridge,12,raw);assert.equal(parts.join(''),raw);assert.equal(JSON.parse(parts.join('')).draft,original);assert.equal(finish,1);assert.throws(()=>sendProjectFile(bridge,12,'Name',raw),/Unsupported/);
});
