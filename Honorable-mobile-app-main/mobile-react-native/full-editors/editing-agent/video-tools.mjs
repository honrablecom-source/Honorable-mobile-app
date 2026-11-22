const integer = (min, max) => ({type:'integer',minimum:min,maximum:max});
const item = integer(1,Number.MAX_SAFE_INTEGER);
const frame = integer(0,360000);
const duration = integer(1,2880);
const name = {type:'string',minLength:1,maxLength:80};
const object = (properties,required = Object.keys(properties)) => ({type:'object',additionalProperties:false,properties,required});
const array = (items,maxItems) => ({type:'array',minItems:1,maxItems,items});
const segments = array(object({
  item,sourceFrame:frame,frames:duration,zoom:integer(100,160),
  framing:{type:'string',enum:['fit','fill'],description:'Use fill to crop without bars or fit to keep the whole source visible.'},
},['item','sourceFrame','frames']),96);
const music = object({item,sourceFrame:frame,gainDb:integer(-48,6)},['item']);

function tool(name,description,inputSchema,readOnlyHint = false) {
  return {name,description,inputSchema,annotations:{readOnlyHint,destructiveHint:false,openWorldHint:false}};
}

export const videoTools = [
  tool('list_video_media','List the footage, still images, audio, and sequences already imported into the open Noble Video Studio project. Returns media item IDs and a project revision. All edit times use 24 fps frames.',object({}),true),
  tool('inspect_video_media','Measure motion candidates or audio beats in imported media. Optionally return actual frame previews when the editor has enabled frame sharing. Use this before choosing shots.',object({
    items:array(item,6),frames:{type:'boolean'},
  },['items']),true),
  tool('build_video_edit','Build a real editable 24 fps timeline from your exact shot decisions. Use only IDs from list_video_media. Provide contiguous shots in playback order, with sourceFrame and frames at 24 fps. Optional soundtrack, zoom, and opening title. No generated footage, and no local model required.',object({
    revision:integer(0,Number.MAX_SAFE_INTEGER),name,segments,aspect:{type:'string',enum:['vertical','landscape']},music,title:name,
  },['revision','name','segments'])),
  tool('inspect_video_edit','Read the timing, placement and media IDs in an actual Noble timeline.',object({sequence:item}),true),
  tool('revise_video_edit','Replace a range of whole shots in a tool-created timeline with a new sequence. Use zero-based shot indexes. The replacement must have the same total number of frames so the opening, soundtrack and ending stay in place.',object({
    revision:integer(0,Number.MAX_SAFE_INTEGER),sequence:item,name,startIndex:integer(0,95),replaceCount:integer(1,96),segments,
  })),
  tool('preview_video_edit','Open an editable sequence in Noble and seek to a frame. Optional play starts playback. To return to an earlier edit, preview its original sequence ID.',object({
    sequence:item,frame,play:{type:'boolean'},
  },['sequence'])),
  tool('export_video_edit','Start an H.264 MP4 export of a Noble sequence. The finished file is saved under Videos/Noble Exports. Call video_export_status for progress and the actual file path.',object({
    sequence:item,quality:{type:'string',enum:['standard','high']},
  },['sequence'])),
  tool('video_export_status','Check a Noble MP4 export for progress, failure, or the finished local file path.',object({job:item}),true),
];

function check(value,schema) {
  switch(schema.type) {
    case 'object':
      return Boolean(value && typeof value === 'object' && !Array.isArray(value) &&
        schema.required.every(key=>Object.hasOwn(value,key)) &&
        Object.entries(value).every(([key,entry])=>Object.hasOwn(schema.properties,key) && check(entry,schema.properties[key])));
    case 'array':
      return Array.isArray(value) && value.length >= schema.minItems && value.length <= schema.maxItems &&
        value.every(entry=>check(entry,schema.items));
    case 'integer':
      return Number.isSafeInteger(value) && value >= schema.minimum && value <= schema.maximum;
    case 'string':
      return typeof value === 'string' && (schema.enum ? schema.enum.includes(value) :
        value.trim().length >= schema.minLength && value.length <= schema.maxLength && !/[\u0000-\u001f]/.test(value));
    case 'boolean':
      return typeof value === 'boolean';
    default:
      return false;
  }
}

export function validateVideoCall(schema,args) {
  if (!check(args,schema.inputSchema)) return false;
  return !args.segments || args.segments.reduce((total,segment)=>total+segment.frames,0) <= 2880;
}
