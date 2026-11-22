// Normalized command metadata from Noble's pinned, installed Windows editors.
import {readFileSync} from 'node:fs';
import {createHash} from 'node:crypto';

const catalog = JSON.parse(readFileSync(new URL('./editor-catalog.json',import.meta.url),'utf8'));
const models = {object:'cutout',subject:'subject_color',color:'neural_color',denoise:'denoise',style:'neural_painting'};
const integer = {type:'integer',minimum:0,maximum:Number.MAX_SAFE_INTEGER};
const context = {documentId:integer,revision:integer};
const schema = (properties,required=Object.keys(properties)) => ({type:'object',properties,required,additionalProperties:false});
const tools = new Map();
const presetInputs = {
  'brush.presets.importAbr':'ABR brush set','brush.presets.importImage':'brush tip image',
  'gradient.presets.importGrd':'GRD gradient set','pattern.import':'PAT pattern set',
  'shape.presets.importCsh':'CSH shape set','style.presets.importAsl':'ASL style set','swatches.import':'ACO, ACT or ASE color palette',
};
const maxPresetBytes=128*1024;
const projectControls = new Set([
  'actions.list','actions.newSet','actions.assignSet','actions.duplicate','actions.move','actions.rename','actions.setColor','actions.toggle',
  'actions.record','actions.stop','actions.setHistory','actions.setPlayback','actions.stopMessage','actions.allowToolRecording',
  'export.presets.favorite','export.presets.get','export.presets.list','file.export.presets.get','file.export.presets.list',
  'export.queue.list','export.queue.cancel','export.queue.move','export.queue.remove','export.queue.stop',
  'file.autoSaveStatus','graphics.template.list','jobs.list','perf.stats','prefs.schema','shortcuts.get','shortcuts.list','shortcuts.presets',
  'sequence.renderAudio','sequence.renderEffectsInToOut','sequence.renderInToOut','sequence.renderSelection',
  'shortcuts.clear','shortcuts.loadPreset','shortcuts.savePreset','shortcuts.set','shortcuts.undo','shortcuts.redo',
  'file.newFromClipboard','edit.installedProfiles','events.list','file.newProject','file.openDemoProject',
]);

function add(name,studio,route,id,description,inputSchema) {
  if (tools.has(name) || name.length>64) throw Error('Invalid editor tool name '+name);
  tools.set(name,{studio,route,id,definition:{name,description,inputSchema,
    annotations:{readOnlyHint:route==='context',openWorldHint:false}}});
}

// Keep machine administration, executable actions, raw media extraction and filesystem access
// on their existing interfaces. Native skills operate in the current editor's working project.
function manual(studio,id,params) {
  if (projectControls.has(id)) return false;
  if (/^edit\.(?:copy|copyMerged|cut|paste|pasteAttributes|pasteInsert|pasteSpecial\.paste(?:InPlace|Into|Outside))$/.test(id)) return false;
  if (['document.sampleColor','document.pixel','filter.lastFilter','image.variables.define','layer.newLayerBasedSlice','sequence.renderBar','file.mediaProperties','export.formats','file.templates','graphics.template.controls','graphics.template.set','tts.inspect','tts.voices','audio.voiceover.settings','shortcuts.forKey','shortcuts.resolve','shortcuts.conflicts','shortcuts.audit'].includes(id)) return false;
  if (['file.projectSettings.general','file.replaceFonts','media.status','markers.edit','masks.set','lumetri.presets','multicam.cut','document.activate','document.move'].includes(id)) return false;
  if (id.startsWith('transcript.') && !id.includes('downloadModel')) return false;
  if (/revealInFinder|saveContents|fromFile|\.scan$|\.voiceover|\.smartObjects\.update|\.smartObjects\.embedAllLinked/i.test(id)) return true;
  if (/^(?:plugin|actions|prefs|updates|help|jobs|command|noble|mediaBrowser|mediaCache|export|events|shortcuts|app|session|state|perf)\./.test(id)) return true;
  if (id.startsWith('file.')) return !/^(file\.new(?:$|Sequence|Bin|SearchBin|AdjustmentLayer|BarsAndTone|BlackVideo|ColorMatte|CountingLeader|TransparentVideo)|file\.automate\.(?:fitImage|conditionalModeChange|cropAndStraightenPhotos)$|file\.scripts\.(?:deleteAllEmptyLayers|flattenAllLayerEffects|flattenAllMasks)$)/.test(id);
  if (/(?:clipboard|(?:^|\.)(?:copy|cut|paste)(?:$|Merged|Special|Into|InPlace|Attributes)|import|export|download|install|plugin|script|preferences|purge|relink|reload|systemReport|recent|thumbnail|openExternal|readFile|writeFile|replaceFootage|makeOffline|deleteRenderFiles|voiceover|playAction|scratchDisk|fileInfo|linkedAsset|editContents|linkedContents)/i.test(id)) return true;
  if (studio==='photo' && /^(?:document\.(?:inspect|pixel|sampleColor|move|activate)|filter\.lastFilter|layer\.smartObjects\.(?:convertToLinked|replaceContents|relinkToFile)|image\.variables\.)/.test(id)) return true;
  if (studio==='video' && /^(?:tts\.|project\.ingestSettings|transcript\.(?:models|downloadModel)|graphics\.template\.|media\.(?:linkMedia|search|findMissing)|lumetri\.(?:setInputLut|setLook)|sequence\.render|project\.inspect|metadata\.get)/.test(id)) return true;
  return /"(?:file|fileName|filePath|folder|directory|url|executable|sourcePath|outputPath|destinationPath|script|steps|commands|paths)"\s*:\s*(?:path|str|\[\s*(?:path|str)|file)/i.test(params) || /"path"\s*:\s*(?:str|filepath|file\b|path(?:\?|\s*[,}]))/i.test(params);
}

export const inventory=[];
// Invoke only the editor's own dialog for these workflows. Never accept a host pathname.
const interactive=/^(?:file\.(?:open|openAs|openAsSmartObject|saveAs|saveACopy|import|placeEmbedded|placeLinked|exportMedia|exportFrame|projectManager)|edit\.preferences\.[\w]+|mode\.export|file\.automate\.(?:photomerge|mergeToHdrPro|lensCorrection|contactSheetII))$/;
// These menu entries open the editor's own settings, import or export UI. No paths
// or job parameters are accepted from the client on these routes.
const editorDialogs=/^(?:app\.(?:about|keyboardShortcuts|language\.\w+|settings\.\w+)|help\.about|file\.(?:export\.(?:exportAs|exportPreferences|saveForWebLegacy|artboardsToFiles|artboardsToPdf|layersToFiles|layerCompsToFiles|pathsToIllustrator|colorLookupTables|dataSetsAsFiles|renderVideo)|export(?:Aaf|Ale|Edl|Fcp7Xml|Fcpxml|GraphicsTemplate|Omf|Otio|SelectionProject)|import\.(?:notes|variableDataSets|videoFramesToLayers)|importImageSequence|importDemoFootage|openExrParts|newOfflineFile|mediaPropertiesFile|saveAsTemplate|saveCopy)|layer\.(?:exportAs|smartObjects\.(?:convertToLinked|relinkToFile|replaceContents|exportContents)|videoLayers\.(?:newVideoLayerFromFile|replaceFootage))|graphics\.(?:newFromFile|template\.export)|media\.(?:attachProxies|linkMedia|reconnectFullRes)|markers\.exportCsv|captions\.export|image\.variables\.dataSets|edit\.presets\.exportImportPresets)$/;
for (const [studio,rows] of Object.entries(catalog)) {
  for (const [id,label,nativeParams,source] of rows) {
    const base = studio+'_'+id.replace(/([a-z0-9])([A-Z])/g,'$1_$2').replace(/[.\-]/g,'_').toLowerCase();
    const tool = base.length<=64 && !tools.has(base) ? base : base.slice(0,55)+'_'+createHash('sha256').update(id).digest('hex').slice(0,8);
    const editorOnly=manual(studio,id,nativeParams);
    const viaMenu=source!=='engine' && (interactive.test(id) || editorDialogs.test(id)) && editorOnly;
    const preset=studio==='photo' && Object.hasOwn(presetInputs,id);
    const route=preset?'preset':source==='palette'?'palette':source==='menu'||viaMenu?'menu':'engine';
    const params=route==='menu'?'{}':nativeParams;
    const enabled=!editorOnly || viaMenu || preset;
    inventory.push({studio,id,label,tool:enabled?tool:null,route:enabled?route:'studio_only'});
    if (!enabled) continue;
    if (preset) {
      add(tool,studio,route,id,'Import a '+presetInputs[id]+' from base64-encoded file contents (up to 128 KiB) into the active photo editor.',schema({...context,data:{type:'string',contentEncoding:'base64',maxLength:4*Math.ceil(maxPresetBytes/3)},name:{type:'string',maxLength:100}},['documentId','revision','data']));
      continue;
    }
    const fields = studio==='photo'?{...context}:{revision:integer};
    if (params!=='{}') fields.parameters={type:'object',description:'Native command parameters: '+params};
    add(tool,studio,route,id,label+' in the active Noble '+studio+' workspace. Use a fresh revision.'+(viaMenu?' Interactive editor action. Complete any dialog in Noble.':'')+(params!=='{}'?' Parameters: '+params:''),schema(fields,studio==='photo'?['documentId','revision']:['revision']));
  }
}
add('get_photo_context','photo','context','context','Read the active photo ID, revision, dimensions, layer IDs and undo state.',schema({}));
for(const [id,suffix] of Object.entries(models)){
  add('preview_photo_'+suffix,'photo','model',id,'Run Noble’s local '+suffix.replaceAll('_',' ')+' model on the active document and show the result for review. The source is preserved.',schema(context));
}
add('set_photo_review_strength','photo','strength','strength','Set the mix percentage of an existing photo-agent preview and wait for the corrected proof. Zero disables Apply.',schema({...context,percent:{type:'integer',minimum:0,maximum:100}}));
add('open_photo_picker','photo','open','open','Open the Windows file picker for photos or layered projects. The user chooses the local files.',schema({}));
for(const format of ['pcraft','png','jpg']) add('save_photo_'+format,'photo','save',format,'Open the normal Save dialog for a '+format+' copy of the current document. The user chooses the destination.',schema(context));
export const editorSkills = tools;
export const runSkill = {name:'run_editor_skill',description:'Run a named tool returned by find_editor_skills using its exact arguments. Useful for clients that load only the first tool page.',inputSchema:schema({tool:{type:'string',maxLength:64},arguments:{type:'object'}})};
export const searchSkills = {name:'find_editor_skills',description:'Find individual photo or video tools by native command ID, label or tool name. Includes editor-only commands when requested. Call the returned tool name with its documented parameters.',inputSchema:schema({studio:{type:'string',enum:['photo','video']},query:{type:'string',maxLength:100},offset:integer,includeManual:{type:'boolean'}},['studio']),annotations:{readOnlyHint:true,openWorldHint:false}};

export function findSkills({studio,query='',offset=0,includeManual=false},builtins=[]) {
  const terms=query.toLowerCase().split(/[\s._]+/).filter(Boolean);
  const available=[...tools.entries()].filter(([,s])=>s.studio===studio).map(([name,s])=>({id:s.id,tool:name,label:s.definition.description.split(' in the active Noble ')[0]}));
  available.push(...builtins.map(def=>({id:def.name,tool:def.name,label:def.description,inputSchema:def.inputSchema})));
  if(includeManual) available.push(...inventory.filter(row=>row.studio===studio&&!row.tool).map(row=>({...row,status:'studio_only'})));
  const matches=available.filter(row=>terms.every(word=>(row.id+' '+row.label+' '+(row.tool??'')).toLowerCase().includes(word)));
  const items=matches.slice(offset,offset+20).map(row=>tools.has(row.tool)?{...row,inputSchema:tools.get(row.tool).definition.inputSchema}:row);
  return {total:matches.length,items,nextOffset:offset+20<matches.length?offset+20:null};
}

function bounded(value,depth=0) {
  if (depth>12) return false;
  if (value===null || typeof value==='boolean') return true;
  if (typeof value==='number') return Number.isFinite(value);
  if (typeof value==='string') return value.length<=4096 && !/[\u0000]/.test(value);
  if (Array.isArray(value)) return value.length<=1024 && value.every(v=>bounded(v,depth+1));
  return typeof value==='object' && Object.entries(value).every(([key,v])=>!['constructor','__proto__','prototype'].includes(key) && bounded(v,depth+1));
}
export function validSkillArgs(def,args) {
  if (!args || Array.isArray(args) || typeof args!=='object') return false;
  const {properties,required}=def.inputSchema;
  if (def===runSkill) return typeof args.tool==='string' && args.tool.length<=64 && Object.keys(args).length===2 && args.arguments && typeof args.arguments==='object' && !Array.isArray(args.arguments);
  if (properties.data?.contentEncoding==='base64') {
    const {data,...rest}=args;
    if (typeof data!=='string' || !data || data.length>properties.data.maxLength || data.length%4 || !/^[A-Za-z0-9+/]+={0,2}$/.test(data) || Buffer.from(data,'base64').length>maxPresetBytes || !bounded(rest)) return false;
    if (args.name!==undefined && (typeof args.name!=='string' || /[/\\:\x00-\x1f]/.test(args.name))) return false;
  } else if (!bounded(args)) return false;
  const params=args.parameters;
  if (params && properties.parameters) {
    const hint=properties.parameters.description;
    const known=new Set(Array.from(hint.matchAll(/"([\w.-]+)"\s*:/g),match=>match[1]));
    if (known.size && !/…|\.\.\./.test(hint) && Object.keys(params).some(key=>!known.has(key))) return false;
    if (['photo_file_new','photo_image_canvas_size','photo_image_image_size','video_file_new_sequence'].includes(def.name)) {
      if ([params.width,params.height].some(v=>typeof v==='number' && Math.abs(v)>32768)) return false;
      if (params.width>0 && params.height>0 && params.width*params.height>134217728) return false;
    }
  }
  return required.every(key=>Object.hasOwn(args,key)) && Object.entries(args).every(([key,v])=>{
    if (!Object.hasOwn(properties,key)) return false;
    const p=properties[key];
    if(p.type==='integer') return Number.isSafeInteger(v) && v>=p.minimum && v<=p.maximum;
    if(p.type==='object') return !!v && typeof v==='object' && !Array.isArray(v);
    return typeof v===p.type && (!p.enum||p.enum.includes(v)) && (!p.maxLength||v.length<=p.maxLength);
  });
}
