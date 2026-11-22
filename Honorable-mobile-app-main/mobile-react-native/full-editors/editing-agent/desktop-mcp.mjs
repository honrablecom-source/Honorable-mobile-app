import {recipeSchema} from './photo-recipe.mjs';
import {videoTools,validateVideoCall} from './video-tools.mjs';

const legacyVersions = ['2025-11-25','2025-06-18','2025-03-26'];
const modernVersion = '2026-07-28';
const tool = {
  name:'preview_photo_edit',
  title:'Preview a photo edit in Noble Studio',
  description:'Ask Noble Image Studio to prepare an editable preview on the photo currently open in its Windows window. Use for photo tone and color edits. The user approves changes in Noble.',
  inputSchema:{
    type:'object',additionalProperties:false,required:['brief'],
    properties:{
      brief:{type:'string',minLength:1,maxLength:1000},
      finish:{type:'string',enum:['natural','creative'],default:'natural'},
    },
  },
  outputSchema:{
    type:'object',additionalProperties:false,required:['edits','status'],
    properties:{edits:recipeSchema.properties.edits,status:{const:'awaiting_approval'}},
  },
  annotations:{readOnlyHint:false,destructiveHint:false,openWorldHint:false},
};
const serverInfo = {name:'noble-studio',title:'Noble Studio',version:'1.0.0'};
const capabilities = {tools:{}};
const instructions = 'Open Noble Video Studio with imported media. Use list_video_media and build_video_edit to make a cut. For an existing timeline, inspect_video_edit, duplicate_video_edit, then use clip, transform, grade, transition, audio, title and caption tools. Use fresh revisions after changes. Render a review frame when permitted, save the editable project and export the video. For photos, use preview_photo_edit.';

export function createDesktopMcp(port) {
  let pending;
  let lastId = 0;
  port.on('message', ({data}) => {
    if (!['photo-review-result','video-edit-result'].includes(data?.type) || pending?.id !== data.id) return;
    clearTimeout(pending.timer);
    const {resolve,reject} = pending;
    pending = undefined;
    if (data.error) reject(Error(data.error));
    else resolve(data.result);
  });

  const invoke = message => new Promise((resolve,reject) => {
    if (pending) {
      reject(Error('Noble is processing another tool request.'));
      return;
    }
    const id = ++lastId;
    const timer = setTimeout(() => {
      pending = undefined;
      reject(Error('Noble took too long to complete the request.'));
    },240000);
    pending = {id,timer,resolve,reject};
    port.postMessage({...message,id});
  });

  return async (req,res) => {
    // This control endpoint is for native clients on this machine, with no browser access.
    if (req.headers.origin || req.headers['sec-fetch-site']) return void res.writeHead(403).end();
    if (req.method !== 'POST') return void res.writeHead(405,{'Allow':'POST'}).end();
    if (req.headers['content-type']?.split(';')[0].trim() !== 'application/json') return void res.writeHead(415).end();
    let message;
    try {
      const chunks = [];
      let length = 0;
      for await (const chunk of req) {
        length += chunk.length;
        if (length > 16000) return void res.writeHead(413).end();
        chunks.push(chunk);
      }
      message = JSON.parse(Buffer.concat(chunks));
    } catch {
      res.writeHead(400).end();
      return;
    }
    const {id,method,params} = message ?? {};
    const reply = (content,code = 200) => res.writeHead(code,{'Content-Type':'application/json'}).end(
      JSON.stringify({jsonrpc:'2.0',id:id ?? null,...content}));
    const error = (code,message,data) => ({error:{code,message,data}});
    if (message?.jsonrpc !== '2.0' || typeof method !== 'string') return void reply(error(-32600,'Invalid request'),400);
    const header = req.headers['mcp-protocol-version'];
    if (header && header !== modernVersion && !legacyVersions.includes(header)) {
      return void reply(error(-32022,'Unsupported protocol version',{supported:[modernVersion,...legacyVersions],requested:header}),400);
    }
    if (id === undefined && method.startsWith('notifications/')) return void res.writeHead(202).end();
    if (typeof id !== 'string' && !Number.isInteger(id)) return void reply(error(-32600,'Invalid request'),400);
    const result = data => reply({result:header === modernVersion ? {resultType:'complete',...data} : data});
    if (method === 'initialize') {
      const requested = params?.protocolVersion;
      return void result({protocolVersion:legacyVersions.includes(requested) ? requested : legacyVersions[0],serverInfo,capabilities,instructions});
    }
    if (method === 'server/discover') {
      return void result({
        resultType:'complete',supportedVersions:[modernVersion,...legacyVersions],capabilities,instructions,
        _meta:{'io.modelcontextprotocol/serverInfo':serverInfo},
      });
    }
    if (method === 'ping') return void result({});
    if (method === 'tools/list') return void result({tools:[tool,...videoTools]});
    if (method !== 'tools/call') return void reply(error(-32601,'Method not found'));
    const args = params?.arguments ?? {};
    const video = videoTools.find(entry=>entry.name === params?.name);
    if (video) {
      if (!validateVideoCall(video,args)) return void reply(error(-32602,'Check the input schema for '+video.name+'.'));
      try {
        const answer = await invoke({type:'video-edit-request',tool:video.name,args});
        result({
          content:[{type:'text',text:JSON.stringify(answer.data)},...(answer.images ?? [])],
          structuredContent:answer.data,
        });
      } catch (failure) {
        result({isError:true,content:[{type:'text',text:failure.message}]});
      }
      return;
    }
    if (params?.name !== tool.name || !args || Array.isArray(args) ||
        Object.keys(args).some(key => key !== 'brief' && key !== 'finish') ||
        typeof args.brief !== 'string' || !args.brief.trim() || args.brief.length > 1000 ||
        (args.finish !== undefined && args.finish !== 'natural' && args.finish !== 'creative')) {
      return void reply(error(-32602,'Use preview_photo_edit with a brief and an optional natural or creative finish.'));
    }
    try {
      const edits = await invoke({type:'photo-review-request',brief:args.brief.trim(),finish:args.finish ?? 'natural'});
      result({
        content:[{type:'text',text:'Preview ready in Noble Image Studio. Use Apply selected to commit the adjustment layers.'}],
        structuredContent:{edits,status:'awaiting_approval'},
      });
    } catch (failure) {
      result({isError:true,content:[{type:'text',text:failure.message}]});
    }
  };
}
