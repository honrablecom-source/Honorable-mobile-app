// Source-anchored browser review mirror of VideoMotion.kt; validated against Kotlin samples.
const f=Math.fround;
export const DEFAULT_MOTION=Object.freeze({enabled:false,startZoom:1,endZoom:1,startX:0,startY:0,endX:0,endY:0,anchorStartMs:0,anchorEndMs:1,ease:'SMOOTH'});
export function validateMotion(input){
 const value={...input};if(typeof value.enabled!=='boolean')throw Error('Choose an enabled state.');
 for(const [key,min,max]of [['startZoom',1,3],['endZoom',1,3],['startX',-1,1],['startY',-1,1],['endX',-1,1],['endY',-1,1]]){if(!Number.isFinite(value[key])||value[key]<min||value[key]>max)throw Error('Invalid motion endpoint.');value[key]=f(value[key]);}
 if(!Number.isInteger(value.anchorStartMs)||!Number.isInteger(value.anchorEndMs)||value.anchorStartMs<0||value.anchorEndMs<=value.anchorStartMs||value.anchorEndMs>2147483647||!['LINEAR','SMOOTH'].includes(value.ease))throw Error('Invalid source motion timing.');
 return value;
}
export function sameMotion(a,b){return Object.keys(DEFAULT_MOTION).every(key=>Object.is(a[key],b[key]));}
export function motionFrame(input,sourceMs){
 if(!Number.isFinite(sourceMs))throw Error('Invalid motion timestamp.');
 const value=validateMotion(input);if(!value.enabled)return{zoom:1,translateX:0,translateY:0};
 const fraction=Math.max(0,Math.min(1,(sourceMs-value.anchorStartMs)/(value.anchorEndMs-value.anchorStartMs))),t=value.ease==='LINEAR'?fraction:fraction*fraction*(3-2*fraction);
 const mix=(a,b)=>f(a+f(b-a)*t),zoom=mix(value.startZoom,value.endZoom),x=mix(value.startX,value.endX),y=mix(value.startY,value.endY);
 return{zoom,translateX:f(-x*f(zoom-1)),translateY:f(y*f(zoom-1))};
}
export function motionForEditing(input,start,end){
 const original=validateMotion(input);if(sameMotion(original,DEFAULT_MOTION))return validateMotion({...DEFAULT_MOTION,enabled:true,endZoom:1.2,anchorStartMs:start,anchorEndMs:end});
 const from=motionFrame({...original,enabled:true},start),to=motionFrame({...original,enabled:true},end),pan=(offset,zoom)=>zoom===1?0:Math.max(-1,Math.min(1,f(offset/f(zoom-1))));
 return validateMotion({...original,startZoom:from.zoom,endZoom:to.zoom,startX:pan(-from.translateX,from.zoom),startY:pan(from.translateY,from.zoom),endX:pan(-to.translateX,to.zoom),endY:pan(to.translateY,to.zoom),anchorStartMs:start,anchorEndMs:end});
}
