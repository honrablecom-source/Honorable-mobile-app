import test from 'node:test';
import assert from 'node:assert/strict';
import { readProjects,writeProjects,saveProject,exportProject,importProject,MAX_SHELF_BYTES } from '../projects.mjs';
const box={id:'a',type:'rectangle',x:0,y:0,width:50,height:50};
test('named shelf roundtrip and replacement preserve editability',()=>{const first=saveProject([],' Cover ',[box],{collaborators:{secret:true}});const next=saveProject(first,'cover',[{...box,x:80}]);assert.equal(next.length,1);assert.equal(next[0].name,'cover');const restored=readProjects(writeProjects(next));assert.equal(JSON.parse(restored[0].scene).elements[0].x,80);assert.equal(JSON.parse(restored[0].scene).appState.collaborators,undefined);assert.equal(first[0].name,'Cover');});
test('portable project transfer restores shapes and rejects remote content',()=>{const result=importProject(exportProject('My drawing',[box]));assert.equal(result.name,'My drawing');assert.deepEqual(result.elements,[box]);assert.throws(()=>exportProject('Images',[{...box,type:'image',fileId:'remote'}]));assert.throws(()=>exportProject('Links',[{...box,link:'https://example.com'}]));});
test('future corrupt oversized and duplicate shelves fail closed',()=>{for(const raw of ['broken','null','{"version":2,"projects":[]}','x'.repeat(MAX_SHELF_BYTES+1)])assert.throws(()=>readProjects(raw));const project=saveProject([],'a',[box])[0];assert.throws(()=>writeProjects([project,{...project,name:'A'}]));assert.throws(()=>writeProjects(Array(13).fill(project)));assert.deepEqual(readProjects(null),[]);});
test('invalid names and portable schemas are refused',()=>{for(const name of ['', ' ', 'x'.repeat(41), 'bad\nname'])assert.throws(()=>saveProject([],name,[box]));for(const raw of ['{}','{"format":"noble-drawing","version":2}','x'.repeat(1024*1024+4097)])assert.throws(()=>importProject(raw));});
test('malformed opacity IDs and hidden metadata are rejected',()=>{for(const patch of [{opacity:101},{angle:Infinity},{customData:{nobleHidden:{opacity:-1,locked:false}}}])assert.throws(()=>exportProject('Bad',[{...box,...patch}]));assert.throws(()=>exportProject('Duplicates',[box,box]));});

test('rename and duplicate preserve saved geometry and refuse collisions without mutation', async()=>{
 const {renameProject,duplicateProject}=await import('../projects.mjs');
 const original=saveProject([],'Original',[box]);
 const renamed=renameProject(original,'Original','Renamed');
 assert.equal(original[0].name,'Original');assert.equal(renamed[0].name,'Renamed');assert.equal(renamed[0].scene,original[0].scene);
 const copied=duplicateProject(renamed,'Renamed','Copy');assert.equal(copied.length,2);assert.equal(copied[1].scene,copied[0].scene);
 const edited=saveProject(copied,'Copy',[{...box,x:100}]);assert.equal(JSON.parse(edited[0].scene).elements[0].x,0);assert.equal(JSON.parse(edited[1].scene).elements[0].x,100);
 assert.throws(()=>renameProject(original,'Original','original'));assert.throws(()=>duplicateProject(original,'missing','New'));assert.throws(()=>duplicateProject(original,'Original','Original'));
});


test('saved version guard refuses unseen replacements, renamed/missing entries and invalid expectations', async () => {
 const {requireSavedVersion}=await import('../projects.mjs');
 const shown=saveProject([],'Cover',[box]);
 const changed=saveProject(shown,'Cover',[{...box,x:80}]);
 const before=JSON.stringify(changed);
 assert.throws(()=>requireSavedVersion(changed,shown[0]),/Saved work changed/);
 assert.throws(()=>requireSavedVersion([{...shown[0],name:'cover'}],shown[0]),/Reopen Projects/);
 assert.throws(()=>requireSavedVersion([],shown[0]),/Reopen Projects/);
 for(const missing of [null,undefined,{}, {name:'Cover'}]) assert.throws(()=>requireSavedVersion(shown,missing),/Reopen Projects/);
 assert.equal(JSON.stringify(changed),before);
});

test('saved version guard permits the shown version alongside unrelated additions and preserves its data', async () => {
 const {requireSavedVersion}=await import('../projects.mjs');
 const shown=saveProject([],'Cover',[box]);
 const current=saveProject(shown,'Other',[{...box,x:150}]);
 const before=JSON.stringify(current);
 assert.equal(requireSavedVersion(current,{...shown[0]}),current[0]);
 assert.equal(JSON.stringify(current),before);
});


test('portable imports reject missing references and cyclic or wrong-type frames before renderer restore',()=>{
 const invalid=[ [{...box,boundElements:[{id:'missing',type:'text'}]}], [{...box,id:'a',type:'frame',frameId:'b'},{...box,id:'b',type:'frame',frameId:'a'}], [{...box,id:'parent'},{...box,id:'child',frameId:'parent'}] ];
 for(const elements of invalid){const raw=exportProject('Invalid',elements);assert.throws(()=>importProject(raw),/missing|cycle|invalid/);assert.equal(exportProject('Invalid',elements),raw);}
 const valid=[{...box,id:'card',boundElements:[{id:'label',type:'text'}]},{id:'label',type:'text',text:'Bound label',x:5,y:5,width:40,height:20,containerId:'card'}];assert.deepEqual(importProject(exportProject('Valid',valid)).elements,valid);
});
