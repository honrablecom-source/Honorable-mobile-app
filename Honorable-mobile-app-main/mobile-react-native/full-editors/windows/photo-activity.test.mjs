import test from 'node:test';
import assert from 'node:assert/strict';
import {actionDetail,geometryNote} from './photo-activity.js';
import {photoToolCatalog} from '../editing-agent/desktop-mcp.mjs';
import {editorSkills} from '../editing-agent/editor-skills.mjs';

test('every published photo skill has a unique activity lookup and the proposal is discoverable',()=>{
  const catalog=photoToolCatalog();
  const skills=[...editorSkills].filter(([,entry])=>entry.studio==='photo');
  assert.deepEqual(new Set(catalog.map(row=>row.name)),new Set([...skills.map(([name])=>name),'preview_photo_edit']));
  assert.equal(new Set(catalog.map(row=>row.route+'/'+row.id)).size,catalog.length);
});

test('geometry feedback distinguishes a dab from a stroke that loops back to its origin',()=>{
  const dab={points:[[384,384],[384,384]],size:24,color:'#ff8800'};
  assert.match(actionDetail('paint.stroke',dab),/Single point \(384, 384\).*24 px/);
  assert.match(geometryNote('paint.stroke',dab),/Brush dab/);
  const loop={points:[[1,1],[4,4],[1,1]]};
  assert.match(actionDetail('paint.stroke',loop),/3 points/);
  assert.equal(geometryNote('paint.stroke',loop),undefined);
  assert.match(geometryNote('paint.gradient',{from:[0,0],to:[0,0]}),/coincide/);
  assert.equal(geometryNote('paint.gradient',{from:[0,0],to:[20,20]}),undefined);
  assert.match(geometryNote('select.rect',{x:384,y:384,width:768,height:768},768,768),/top-left/);
  assert.equal(geometryNote('select.rect',{x:0,y:0,width:768,height:768},768,768),undefined);
});

test('activity details omit preset contents, file paths and arbitrary payloads',()=>{
  assert.equal(actionDetail('swatches.import',{data:'private contents',name:'private'}),'Preset import');
  assert.equal(actionDetail('file.new',{name:'C:\\private\\study.png'}),'study.png');
  assert.equal(actionDetail('unknown',{path:'secret',dataUrl:'private contents'}),'');
});
