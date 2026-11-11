const{test}=require('node:test');const assert=require('node:assert/strict');const p=require('./project');
test('one deterministic project state preserves source media and virtual paths cannot escape',()=>{let project=p.create({uri:'content://media/1',name:'Portrait',type:'IMAGE'});project=p.fileOperation(project,'folder','scripts/tools');project=p.fileOperation(project,'create','scripts/tools/a.py',null,'project.active_layer.opacity = 0.5');project=p.fileOperation(project,'move','scripts/tools','scripts/actions');assert(project.files['scripts/actions/a.py']);assert(!project.files['scripts/tools']);assert.equal(project.media[0].uri,'content://media/1');assert.equal(project.media[0].preserveOriginal,true);assert.equal(p.serialize(JSON.parse(p.serialize(project))),p.serialize(project));for(const path of ['../secrets','/tmp/file','a/../../x','__proto__','a\\b'])assert.throws(()=>p.fileOperation(project,'create',path))});
test('script runtimes do not execute and capability router never bypasses entitlement or invents output',()=>{assert.throws(()=>p.sandbox.run('python','import os'),/unavailable/);assert.equal(p.sandbox.python,'EDITOR_ONLY');assert.equal(p.route('generativeFill').reason,'STUDIO_REQUIRED');assert.equal(p.route('generativeFill',{studio:true}).reason,'COMING_SOON');assert.equal(p.route('basicPoseRig').allowed,false);assert.equal(p.route('basicEdit').allowed,true);assert.equal(p.route('basicEdit',{requiresCloud:true}).reason,'UPLOAD_CONSENT_REQUIRED')});
test('timeline trim split marker serialize with entitlement and bounds enforced',()=>{let project=p.create({uri:'local.mp4',type:'VIDEO'});const op=o=>project=p.timelineOperation(project,o,{studio:true});assert.throws(()=>p.timelineOperation(project,{type:'addTrack',id:'v'}),/STUDIO/);op({type:'addTrack',id:'v'});op({type:'addClip',trackId:'v',id:'a',mediaId:'source',duration:10});op({type:'split',trackId:'v',clipId:'a',id:'b',time:4});assert.equal(project.timeline.tracks[0].clips.length,2);assert.equal(project.timeline.tracks[0].clips[1].start,4);assert.throws(()=>op({type:'trim',trackId:'v',clipId:'a',in:3,out:2}),/Invalid/);op({type:'marker',time:2,label:'Cut'});assert.equal(JSON.parse(p.serialize(project)).timeline.markers[0].time,2)});
test('file destinations cannot nest under scripts or replace implicit folders; failures preserve source',()=>{
 const project=p.create({uri:'content://media/original'}),before=p.serialize(project);
 for(const op of ['create','folder'])assert.throws(()=>p.fileOperation(project,op,'scripts/main.py/child'),/Parent is not a folder/);
 for(const op of ['move','duplicate'])assert.throws(()=>p.fileOperation(project,op,'assets','scripts/main.py/child'),/Parent is not a folder/);
 assert.throws(()=>p.fileOperation(project,'create','scripts'),/Destination exists/);
 for(const op of ['move','duplicate'])assert.throws(()=>p.fileOperation(project,op,'assets','scripts'),/Destination exists/);
 assert.equal(p.serialize(project),before);
});
test('valid nested folder transfers preserve every script, source media and serialized state',()=>{
 let project=p.create({uri:'content://media/original'});
 project=p.fileOperation(project,'folder','tools');
 project=p.fileOperation(project,'create','tools/nested/a.py',null,'keep me');
 for(const op of ['duplicate','move']){
  const result=p.fileOperation(project,op,'tools','assets/tools');
  assert.equal(result.files['assets/tools/nested/a.py'].text,'keep me');
  assert.equal(Boolean(result.files.tools),op==='duplicate');
  assert.equal(result.media[0].uri,'content://media/original');
  assert.deepEqual(JSON.parse(p.serialize(result)),result);
 }
 assert.equal(project.files['tools/nested/a.py'].text,'keep me');
});
test('ordinary inherited object names are usable filenames without phantom entries',()=>{
 let project=p.create({uri:'local'});
 assert.throws(()=>p.fileOperation(project,'save','toString',null,'x'),/File not found/);
 project=p.fileOperation(project,'create','toString',null,'script');
 project=p.fileOperation(project,'duplicate','toString','valueOf');
 assert.equal(project.files.valueOf.text,'script');
 project=p.fileOperation(project,'delete','toString');
 assert.equal(Object.hasOwn(project.files,'toString'),false);
});
