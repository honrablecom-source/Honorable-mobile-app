import {photoFinish,recipeSchema,validatePhotoRecipe} from './photo-recipe.mjs';

export const photoDraftTool = {
  name:'draft_photo_edit',
  description:'Use measured tone and color from the active photo to propose editable adjustment layers.',
  inputSchema:{
    type:'object',additionalProperties:false,required:['brief','measurements'],
    properties:{brief:{type:'string',minLength:1,maxLength:1000},measurements:{type:'object'},finish:{type:'string',enum:['natural','creative']}},
  },
};
export const inspectPhotoTool = {
  name:'inspect_photo',
  description:'Read the sampled tone and color statistics of the active photo.',
  inputSchema:{type:'object',properties:{},additionalProperties:false},
};

export async function planPhotoWithOllama(brief, model, invoke, finish = 'natural') {
  if (typeof brief !== 'string' || !brief.trim() || brief.length > 1000) throw Error('Describe a photo adjustment in 1–1000 characters.');
  if (finish !== 'natural' && finish !== 'creative') throw Error('Choose a natural or creative finish.');
  const messages = [
    {role:'system',content:'You use an inspection tool to measure tone and color in a photo before making editing suggestions.'},
    {role:'user',content:'First call inspect_photo to measure the open photo. The edit I want after inspection is: ' + brief.trim()},
  ];
  async function chat(options) {
    let response;
    try {
      response = await fetch('http://127.0.0.1:11434/api/chat', {
        method:'POST',redirect:'error',signal:AbortSignal.timeout(90000),
        headers:{'Content-Type':'application/json'},
        body:JSON.stringify({model,messages,stream:false,think:false,options:{temperature:0,num_ctx:4096,num_predict:256},...options}),
      });
    } catch {
      throw Error('Start Ollama and check that ' + model + ' is installed.');
    }
    if (!response.ok) throw Error('Ollama could not run ' + model + '. Check that it is installed and supports tool calls.');
    return (await response.json()).message;
  }
  const tools = [{type:'function',function:{name:inspectPhotoTool.name,description:inspectPhotoTool.description,parameters:inspectPhotoTool.inputSchema}}];
  let selection = await chat({tools});
  let call = selection?.tool_calls?.find(entry => entry.function?.name === inspectPhotoTool.name);
  if (!call) {
    // Some Ollama templates express the request in structured JSON rather than a tool_calls field.
    messages.push({role:'user',content:'Request the inspect_photo tool now. Return its name as JSON.'});
    const structured = await chat({format:{
      type:'object',required:['name'],additionalProperties:false,properties:{name:{type:'string',enum:[inspectPhotoTool.name]}},
    }});
    let requested;
    try { requested = JSON.parse(structured?.content); } catch {}
    if (requested?.name !== inspectPhotoTool.name) throw Error('The local model did not request photo inspection.');
    call = {function:{name:requested.name,arguments:{}}};
    selection = {tool_calls:[call]};
  }
  const measurements = await invoke(call.function.name);
  messages.push({role:'assistant',content:'',tool_calls:[call]});
  messages.push({role:'tool',tool_name:inspectPhotoTool.name,content:JSON.stringify(measurements)});
  messages.push({role:'user',content:
    'Return the ordered edits for the original request as JSON. Use measured global tone and color only. Supported kinds and integer amounts: ' +
    'brightness -40 to 40, contrast -35 to 40, saturation -45 to 45, warmth -40 to 40, monochrome exactly 100. ' +
    'Positive warmth warms the photo. Positive brightness lightens it. Use each of up to four kinds once, omit zero amounts, ' +
    'and return an empty edits array for unsupported requests. ' +
    (finish === 'natural'
      ? 'Use the smallest useful change. Preserve highlights and shadow detail, especially when those tones dominate. Keep saturation and warmth restrained unless requested.'
      : 'Follow the requested strength and look. Preserve the existing character of the photo wherever the brief leaves it unspecified.')});
  const reply = await chat({format:recipeSchema});
  let result;
  try { result = JSON.parse(reply?.content); }
  catch { throw Error('The local model did not return an editable photo plan.'); }
  const edits = photoFinish(validatePhotoRecipe(result),measurements,finish);
  return {edits,measurements,tool:inspectPhotoTool.name};
}
