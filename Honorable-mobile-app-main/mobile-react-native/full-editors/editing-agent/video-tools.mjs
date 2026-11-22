const integer = (min, max) => ({type:'integer',minimum:min,maximum:max});
const item = integer(1,Number.MAX_SAFE_INTEGER);
const frame = integer(0,360000);
const duration = integer(1,2880);
const revision = integer(0,Number.MAX_SAFE_INTEGER);
const name = {type:'string',minLength:1,maxLength:80};
const text = {type:'string',minLength:1,maxLength:240};
const number = (minimum,maximum) => ({type:'number',minimum,maximum});
const choice = (...values) => ({type:'string',enum:values});
const flag = {type:'boolean'};
const object = (properties,required = Object.keys(properties)) => ({type:'object',additionalProperties:false,properties,required});
const array = (items,maxItems) => ({type:'array',minItems:1,maxItems,items});
const point = {type:'array',minItems:2,maxItems:2,items:number(-8192,8192)};
const context = {sequence:item,revision};
const target = {...context,clip:item};
const operation = (type,properties,required = Object.keys(properties)) =>
  object({type:choice(type),...properties},['type',...required]);
const segments = array(object({
  item,sourceFrame:frame,frames:duration,zoom:integer(100,160),
  framing:{type:'string',enum:['fit','fill'],description:'Use fill to crop without bars or fit to keep the whole source visible.'},
},['item','sourceFrame','frames']),96);
const music = object({item,sourceFrame:frame,gainDb:integer(-48,6)},['item']);

function tool(name,description,inputSchema,readOnlyHint = false,destructiveHint = false) {
  return {name,description,inputSchema,annotations:{readOnlyHint,destructiveHint,openWorldHint:false}};
}

export const videoControlTools = [
  tool('duplicate_video_edit','Duplicate any editable Noble sequence. Make a working copy before experimenting, then inspect its clip and track IDs.',object({...context,name},['sequence','revision'])),
  tool('edit_video_clip','Trim, split, move, retime, or remove a clip in an existing sequence. Use a fresh revision and actual clip and track IDs. Times use 24 fps frames. Move into an empty destination, or request ripple insert.',object({
    ...target,operation:{oneOf:[
      operation('trim',{edge:choice('in','out'),delta:integer(-2400,2400),ripple:flag},['edge','delta']),
      operation('split',{at:frame}),
      operation('move',{track:item,at:frame,insert:flag},['track','at']),
      operation('retime',{percent:number(25,400),reverse:flag,ripple:flag},['percent']),
      operation('remove',{ripple:flag},[]),
    ]},
  }),false,true),
  tool('transform_video_clip','Set or animate clip scale, position, rotation, or opacity. Supply at in timeline frames to create a keyframe. Position is in canvas pixels, and scale and opacity are percentages.',object({
    ...target,property:choice('scale','position','rotation','opacity'),value:{oneOf:[number(-8192,8192),point]},at:frame,
  },['sequence','revision','clip','property','value'])),
  tool('grade_video_clip','Adjust a clip with a native editable color effect. Exposure is measured in stops, contrast is centered at zero, and saturation is a percentage.',object({
    ...target,property:choice('exposure','contrast','saturation'),value:number(-100,200),
  })),
  tool('add_video_transition','Add an editable dissolve or audio crossfade at a clip edge. Use the native clip ID and a duration in 24 fps frames.',object({
    ...target,kind:choice('cross_dissolve','dip_to_black','film_dissolve','constant_power'),
    edge:choice('in','out'),frames:integer(1,120),alignment:choice('center','start','end'),
  },['sequence','revision','clip','kind','edge','frames'])),
  tool('mix_video_audio','Set an audio track or the master gain and pan. Optionally animate gain or pan at an exact 24 fps timeline frame. Get audio track IDs using inspect_video_edit.',object({
    ...context,track:{oneOf:[item,choice('master')]},property:choice('volumeDb','pan'),value:number(-100,100),at:frame,
  },['sequence','revision','track','property','value'])),
  tool('add_video_title','Add editable title text at an exact frame. Optional size and canvas position. The title is placed on a graphics track.',object({
    ...context,text,at:frame,frames:duration,size:integer(12,160),position:point,
  },['sequence','revision','text','at','frames'])),
  tool('add_video_captions','Add a new editable subtitle track from timed text. Supply non-overlapping cues with start frame and duration at 24 fps.',object({
    ...context,name,cues:array(object({text,at:frame,frames:duration}),48),
  },['sequence','revision','cues'])),
  tool('render_video_frame','Return a real composited Program frame for visual review, including effects and subtitles. The user must enable frame sharing in Video Studio.',object({sequence:item,at:frame}),true),
  tool('save_video_project','Save a native editable project copy under Videos/Noble Projects and return its actual path. The project references the original source media.',object({...context,name},['sequence','revision'])),
];

export const videoTools = [
  tool('list_video_media','List the footage, still images, audio, and sequences already imported into the open Noble Video Studio project. Returns media item IDs and a project revision. All edit times use 24 fps frames.',object({}),true),
  tool('inspect_video_media','Measure motion candidates or audio beats in imported media. Optionally return actual frame previews when the editor has enabled frame sharing. Use this before choosing shots.',object({
    items:array(item,6),frames:{type:'boolean'},
  },['items']),true),
  tool('build_video_edit','Build a real editable 24 fps timeline from your exact shot decisions. Use only IDs from list_video_media. Provide contiguous shots in playback order, with sourceFrame and frames at 24 fps. Optional soundtrack, zoom, and opening title. No generated footage, and no local model required.',object({
    revision,name,segments,aspect:{type:'string',enum:['vertical','landscape']},music,title:name,
  },['revision','name','segments'])),
  tool('inspect_video_edit','Read the timing, placement and media IDs in an actual Noble timeline.',object({sequence:item}),true),
  tool('revise_video_edit','Replace a range of whole shots in a tool-created timeline with a new sequence. Use zero-based shot indexes. The replacement must have the same total number of frames so the opening, soundtrack and ending stay in place.',object({
    revision,sequence:item,name,startIndex:integer(0,95),replaceCount:integer(1,96),segments,
  })),
  tool('preview_video_edit','Open an editable sequence in Noble and seek to a frame. Optional play starts playback. To return to an earlier edit, preview its original sequence ID.',object({
    sequence:item,frame,play:{type:'boolean'},
  },['sequence'])),
  tool('export_video_edit','Start an H.264 MP4 export of a Noble sequence. The finished file is saved under Videos/Noble Exports. Call video_export_status for progress and the actual file path.',object({
    sequence:item,quality:{type:'string',enum:['standard','high']},
  },['sequence'])),
  tool('video_export_status','Check a Noble MP4 export for progress, failure, or the finished local file path.',object({job:item}),true),
  ...videoControlTools,
];

function check(value,schema) {
  if (schema.oneOf) return schema.oneOf.filter(option=>check(value,option)).length === 1;
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
    case 'number':
      return Number.isFinite(value) && value >= schema.minimum && value <= schema.maximum;
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
