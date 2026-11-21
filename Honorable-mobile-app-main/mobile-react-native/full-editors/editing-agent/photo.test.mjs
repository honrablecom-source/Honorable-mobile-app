import test from 'node:test';
import assert from 'node:assert/strict';
import {dispatch} from './mcp.mjs';
import {photoAdjustmentLayers,photoFinish,photoMeasurements,validatePhotoRecipe} from './photo-recipe.mjs';

test('maps the reviewed recipe to a bounded set of native adjustment layers', () => {
  assert.deepEqual(photoAdjustmentLayers([{kind:'brightness',amount:12},{kind:'warmth',amount:-16}]),[
    {kind:'brightnessContrast',params:{brightness:12,name:'AI · Brightness +12'}},
    {kind:'photoFilter',params:{density:16,filter:'cooling82',name:'AI · Warmth −16'}},
  ]);
  for (const edits of [
    [{kind:'layer.delete',amount:1}],
    [{kind:['brightness'],amount:1}],
    [{kind:'brightness',amount:41}],
    [{kind:'monochrome',amount:1}],
    [{kind:'contrast',amount:10},{kind:'contrast',amount:-3}],
  ]) assert.throws(() => validatePhotoRecipe({edits}));
});

test('summarizes rendered pixels while excluding transparent samples', () => {
  const result = photoMeasurements({width:50,height:50},[[0,0,0,1],[1,1,1,1],[1,0,0,0]]);
  assert.deepEqual(result,{width:50,height:50,sampleCount:2,meanLuma:0.5,shadowFraction:0.5,highlightFraction:0.5,meanColorSpread:0});
  assert.throws(() => photoMeasurements({width:50,height:50},[[1,0,0,0]]));
});

test('Natural finish keeps edits restrained on an already bright, contrasty, vivid photo', () => {
  const measured = {highlightFraction:0.24,shadowFraction:0.38,meanColorSpread:0.28};
  const edits = [
    {kind:'brightness',amount:30},{kind:'contrast',amount:30},
    {kind:'saturation',amount:35},{kind:'warmth',amount:-30},
  ];
  assert.deepEqual(photoFinish(edits,measured),[
    {kind:'brightness',amount:8},{kind:'contrast',amount:10},
    {kind:'saturation',amount:5},{kind:'warmth',amount:-14},
  ]);
  assert.deepEqual(photoFinish(edits,measured,'creative'),edits);
  assert.deepEqual(photoFinish([{kind:'monochrome',amount:100}],measured),[{kind:'monochrome',amount:100}]);
});

test('applies review strength consistently to native adjustment opacity', () => {
  const edits = [{kind:'brightness',amount:12},{kind:'monochrome',amount:100}];
  for (const strength of [0,48,100]) {
    assert.deepEqual(photoAdjustmentLayers(edits,strength).map(layer => layer.params.opacity ?? 1),[
      strength / 100, strength / 100,
    ]);
  }
  for (const strength of [-1,101,2.5,'50',NaN]) assert.throws(() => photoAdjustmentLayers(edits,strength));
});

test('MCP lists photo drafting on Windows and keeps photo inspection bound to its request', async () => {
  const list = {jsonrpc:'2.0',id:1,method:'tools/list'};
  assert.deepEqual((await dispatch(list)).result.tools.map(t => t.name),['draft_vertical_edit']);
  const desktop = await dispatch(list,undefined,'local',{model:'local'});
  assert.deepEqual(desktop.result.tools.map(t => t.name),['draft_vertical_edit','draft_cinematic_edit','draft_photo_edit']);
  const call = {jsonrpc:'2.0',id:2,method:'tools/call',params:{name:'inspect_photo'}};
  assert.equal((await dispatch(call)).error.code,-32602);
  assert.equal((await dispatch(call,undefined,undefined,{measurements:{sampleCount:100}})).error.code,-32602);
  const measured = {width:400,height:300,sampleCount:64,meanLuma:0.4,shadowFraction:0.3,highlightFraction:0,meanColorSpread:0.2};
  assert.deepEqual((await dispatch(call,undefined,undefined,{measurements:{...measured,unrelated:'discarded'}})).result.structuredContent,measured);
});
