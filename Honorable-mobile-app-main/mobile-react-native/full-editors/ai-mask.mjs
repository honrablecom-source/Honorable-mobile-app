import {checkedObjectImage} from './ai-object.mjs';
export function alphaSnapshot(image){
  checkedObjectImage(image);const alpha=new Uint8Array(image.width*image.height);
  for(let i=0;i<alpha.length;i++)alpha[i]=image.rgba[i*4+3];return alpha;
}
export function restoreAlpha(image,alpha){
  checkedObjectImage(image);if(!(alpha instanceof Uint8Array)||alpha.length!==image.width*image.height)throw Error('Invalid mask history.');
  for(let i=0;i<alpha.length;i++)image.rgba[i*4+3]=alpha[i];
}
export function paintAlpha(source,result,x,y,radius,mode){
  checkedObjectImage(source);checkedObjectImage(result);
  if(source.width!==result.width||source.height!==result.height||![x,y,radius].every(Number.isFinite)||radius<1||radius>128||!['erase','restore'].includes(mode))throw Error('Invalid mask brush.');
  const left=Math.max(0,Math.floor(x-radius)),right=Math.min(source.width-1,Math.ceil(x+radius));
  const top=Math.max(0,Math.floor(y-radius)),bottom=Math.min(source.height-1,Math.ceil(y+radius));
  for(let py=top;py<=bottom;py++)for(let px=left;px<=right;px++){
    const distance=Math.hypot(px+.5-x,py+.5-y);if(distance>=radius)continue;
    const weight=Math.min(1,(radius-distance)/Math.max(1,radius*.25));
    const i=(py*source.width+px)*4,target=mode==='erase'?0:source.rgba[i+3];
    result.rgba[i+3]=Math.min(source.rgba[i+3],Math.round(result.rgba[i+3]*(1-weight)+target*weight));
  }
}
