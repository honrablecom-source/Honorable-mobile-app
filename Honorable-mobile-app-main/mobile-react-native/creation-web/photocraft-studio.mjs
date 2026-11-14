export const CORE_MAX = 512;
export const CORE_PROJECT_MAX = 3 * 1024 * 1024;
export const CORE_BLENDS = Object.freeze(['normal','multiply','screen','overlay','softLight']);
export function corePhotoInfo(url) {
  if(typeof url!=='string'||url.length>2*1024*1024||!/^data:image\/(?:png|jpeg);base64,[A-Za-z0-9+/]+=*$/.test(url))
    throw Error('Use a bounded local PNG or JPEG working copy.');
  const comma=url.indexOf(','),raw=atob(url.slice(comma+1));
  const info=imageInfo(Uint8Array.from(raw,c=>c.charCodeAt(0)));
  if(url.slice(5,comma)!==info.mimeType+';base64'||info.width>1024||info.height>1024)
    throw Error('Use a prepared local image up to1024 pixels per side.');
  return info;
}
export const CORE_EFFECTS = Object.freeze({
  emboss: {label:'Emboss',min:0,max:300,step:10,value:100},
  findEdges: {label:'Find edges',min:0,max:0,step:1,value:0},
  highPass: {label:'High pass',min:0.5,max:12,step:0.5,value:3},
  gaussianBlur: {label:'Gaussian blur',min:0.5,max:12,step:0.5,value:3},
  surfaceBlur: {label:'Surface blur',min:1,max:6,step:1,value:3},
  smartSharpen: {label:'Smart sharpen',min:0,max:150,step:10,value:60},
  unsharpMask: {label:'Unsharp mask',min:0,max:150,step:10,value:60},
  mosaic: {label:'Mosaic pixels',min:2,max:32,step:1,value:8},
  twirl: {label:'Twirl',min:-180,max:180,step:5,value:45},
  solarize: {label:'Solarize',min:0,max:0,step:1,value:0},
  invert: {label:'Invert',min:0,max:0,step:1,value:0},
  desaturate: {label:'Desaturate',min:0,max:0,step:1,value:0},
});
export function checkedEffect(effect) {
  const rule=Object.prototype.hasOwnProperty.call(CORE_EFFECTS,effect?.id)?CORE_EFFECTS[effect.id]:null;
  if(!rule || !Number.isFinite(effect.value) || effect.value<rule.min || effect.value>rule.max ||
     !Number.isSafeInteger(effect.layerId)||effect.layerId<1)throw Error('Choose a supported PhotoCraft effect and amount.');
  return effect;
}
export function checkedCoreProject(project) {
  if(!project||!Number.isInteger(project.width)||!Number.isInteger(project.height)||project.width<1||project.height<1||
      project.width>CORE_MAX||project.height>CORE_MAX||!Array.isArray(project.layers)||project.layers.length<1||project.layers.length>4)
    throw Error('Use up to four local layers on a canvas up to512 pixels per side.');
  const ids=new Set();
  for(const layer of project.layers){
    if(!layer||!Number.isSafeInteger(layer.id)||layer.id<1||ids.has(layer.id)||typeof layer.name!=='string'||layer.name.length>40||
        typeof layer.png!=='string'||layer.png.length>2*1024*1024||!/^data:image\/png;base64,[A-Za-z0-9+/]+=*$/.test(layer.png)||
        !CORE_BLENDS.includes(layer.blend)||!Number.isFinite(layer.opacity)||layer.opacity<0||layer.opacity>1||typeof layer.visible!=='boolean')
      throw Error('The raster layer is incomplete or too large.');
    ids.add(layer.id);
  }
  if(JSON.stringify(project).length>CORE_PROJECT_MAX)throw Error('This raster composition is too large. Remove a layer or use a smaller photo.');
  return project;
}
export function coreHistory(history,previous,next) {
  checkedCoreProject(previous);checkedCoreProject(next);
  const result=[...history.slice(-9),previous];
  while(result.length>1&&JSON.stringify([result,next]).length>8*1024*1024)result.shift();
  if(JSON.stringify([result,next]).length>8*1024*1024)throw Error('There is not enough room to retain Undo. Use a smaller composition.');
  return result;
}
export function checkedCoreJob(value) {
  const {width,height,layers,effect}=value||{};
  if(!Number.isInteger(width)||!Number.isInteger(height)||width<1||height<1||width>CORE_MAX||height>CORE_MAX||
      !Array.isArray(layers)||layers.length<1||layers.length>4)throw Error('Prepare a bounded local raster composition.');
  const ids=new Set();
  for(const layer of layers){
    if(!layer||!Number.isSafeInteger(layer.id)||layer.id<1||ids.has(layer.id)||!CORE_BLENDS.includes(layer.blend)||
       !Number.isFinite(layer.opacity)||layer.opacity<0||layer.opacity>1||typeof layer.visible!=='boolean'||
       !(layer.pixels instanceof ArrayBuffer)||layer.pixels.byteLength!==width*height*4)
      throw Error('Prepare complete bounded RGBA layers.');
    ids.add(layer.id);
  }
  if(effect){checkedEffect(effect);if(!ids.has(effect.layerId))throw Error('Choose an existing layer for the effect.');}
  return value;
}
import {imageInfo} from './image-assets.mjs';
