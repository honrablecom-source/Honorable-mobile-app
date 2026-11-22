import assert from 'node:assert/strict';
import {EventEmitter} from 'node:events';
import {createServer} from 'node:http';
import test from 'node:test';
import {createDesktopMcp} from './desktop-mcp.mjs';

test('desktop MCP discovers editing tools and rejects browser requests before reaching Studio', async t => {
  const channel = new EventEmitter();
  const seen = [];
  channel.postMessage = message => {
    seen.push(message);
    const video = message.type === 'video-edit-request';
    channel.emit('message',{data:{
      type:video ? 'video-edit-result' : 'photo-review-result',id:message.id,
      result:video ? {data:{sequence:10}} : [{kind:'brightness',amount:8}],
    }});
  };
  const server = createServer(createDesktopMcp(channel));
  await new Promise(resolve => server.listen(0,'127.0.0.1',resolve));
  t.after(() => new Promise(resolve => server.close(resolve)));
  const url = 'http://127.0.0.1:' + server.address().port;
  const rpc = (method,params,headers = {}) => fetch(url,{
    method:'POST',headers:{'Content-Type':'application/json',...headers},
    body:JSON.stringify({jsonrpc:'2.0',id:method.startsWith('notifications/') ? undefined : 1,method,params}),
  });
  const legacy = await rpc('initialize',{protocolVersion:'2025-11-25'});
  assert.equal((await legacy.json()).result.protocolVersion,'2025-11-25');
  assert.equal((await rpc('notifications/initialized',{})).status,202);
  const modern = {'MCP-Protocol-Version':'2026-07-28'};
  const discover = await (await rpc('server/discover',{},modern)).json();
  assert.ok(discover.result.supportedVersions.includes('2026-07-28'));
  const list = await (await rpc('tools/list',{},modern)).json();
  assert.equal(list.result.resultType,'complete');
  assert.deepEqual(list.result.tools.slice(0,19).map(tool => tool.name),[
    'preview_photo_edit','list_video_media','inspect_video_media','build_video_edit','inspect_video_edit',
    'revise_video_edit','preview_video_edit','export_video_edit','video_export_status',
    'duplicate_video_edit','edit_video_clip','transform_video_clip','grade_video_clip','add_video_transition',
    'mix_video_audio','add_video_title','add_video_captions','render_video_frame','save_video_project',
  ]);
  const names=list.result.tools.map(tool=>tool.name);
  let cursor=list.result.nextCursor;
  while(cursor) {
    const page=await (await rpc('tools/list',{cursor},modern)).json();
    names.push(...page.result.tools.map(tool=>tool.name));
    cursor=page.result.nextCursor;
  }
  assert.equal(new Set(names).size,names.length);
  assert.ok(names.includes('photo_image_canvas_size') && names.includes('video_markers_add'));
  assert.ok(names.includes('preview_photo_cutout') && names.includes('preview_photo_neural_color'));
  assert.ok(!names.includes('photo_file_scripts_browse') && !names.includes('photo_plugin_run'));
  const search=await (await rpc('tools/call',{name:'find_editor_skills',arguments:{studio:'photo',query:'canvas size'}})).json();
  assert.ok(search.result.structuredContent.items.some(x=>x.tool==='photo_image_canvas_size'&&x.inputSchema.properties.parameters));
  const workflows=await (await rpc('tools/call',{name:'find_editor_skills',arguments:{studio:'video',query:'build_video_edit'}})).json();
  assert.ok(workflows.result.structuredContent.items.some(x=>x.tool==='build_video_edit'));
  const unsafeImport=await (await rpc('tools/call',{name:'run_editor_skill',arguments:{tool:'photo_swatches_import',arguments:{documentId:1,revision:0,data:'bad!',name:'../palette'}}})).json();
  assert.equal(unsafeImport.error.code,-32602);
  const noRevision=await (await rpc('tools/call',{name:'photo_image_canvas_size',arguments:{documentId:1}})).json();
  assert.equal(noRevision.error.code,-32602);
  const params = {name:'preview_photo_edit',arguments:{brief:'Brighten softly'}};
  assert.equal((await rpc('tools/call',params,{Origin:'https://example.org'})).status,403);
  const invalid = await (await rpc('tools/call',{...params,arguments:{brief:'Brighten',path:'test'}})).json();
  assert.equal(invalid.error.code,-32602);
  assert.equal(seen.length,0);
  const preview = await (await rpc('tools/call',params,modern)).json();
  assert.deepEqual(preview.result.structuredContent,{status:'awaiting_approval',edits:[{kind:'brightness',amount:8}]});
  assert.deepEqual(seen,[{type:'photo-review-request',id:1,brief:'Brighten softly',finish:'natural'}]);
  const build = {name:'build_video_edit',arguments:{
    name:'Real cut',revision:1,segments:[{item:1,sourceFrame:10,frames:20,framing:'fill'}],
  }};
  const invalidBuild = await (await rpc('tools/call',{...build,arguments:{...build.arguments,path:'C:/test'}})).json();
  assert.equal(invalidBuild.error.code,-32602);
  const invalidFrames = await (await rpc('tools/call',{...build,arguments:{
    ...build.arguments,segments:[{item:1,sourceFrame:0,frames:2880},{item:2,sourceFrame:0,frames:1}],
  }})).json();
  assert.equal(invalidFrames.error.code,-32602);
  assert.equal(seen.length,1);
  const edit = await (await rpc('tools/call',build,modern)).json();
  assert.deepEqual(edit.result.structuredContent,{sequence:10});
  assert.deepEqual(seen[1],{type:'video-edit-request',id:2,tool:'build_video_edit',args:build.arguments});
  const clipEdit = {name:'edit_video_clip',arguments:{
    sequence:10,revision:2,clip:12,operation:{type:'split',at:20},
  }};
  const invalidOperation = await (await rpc('tools/call',{...clipEdit,arguments:{
    ...clipEdit.arguments,operation:{type:'split',at:20,track:1},
  }},modern)).json();
  assert.equal(invalidOperation.error.code,-32602);
  assert.equal(seen.length,2);
  const split = await (await rpc('tools/call',clipEdit,modern)).json();
  assert.deepEqual(split.result.structuredContent,{sequence:10});
  assert.deepEqual(seen[2],{type:'video-edit-request',id:3,tool:clipEdit.name,args:clipEdit.arguments});
});
