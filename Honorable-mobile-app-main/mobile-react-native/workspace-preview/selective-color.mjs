// Browser review mirror of PhotoSelectiveColorKernel. Kotlin sample comparison lives in test.cjs.
export const DEFAULT_SELECTIVE=Object.freeze({enabled:false,hue:0,radius:30,softness:.3,shift:0,saturation:1,exposure:0});
const f=Math.fround,clamp=value=>Math.max(0,Math.min(1,value));
export function selectiveKernel(value){
 const e={...value};if(typeof e.enabled!=='boolean')throw Error('Choose an enabled state.');
 for(const [name,min,max]of [['hue',0,360],['radius',1,180],['softness',0,1],['shift',-180,180],['saturation',0,2],['exposure',-2,2]]){if(!Number.isFinite(e[name])||e[name]<min||e[name]>max)throw Error('Invalid selective-color setting.');e[name]=f(e[name]);}
 const identity=!e.enabled||(e.shift===0&&e.saturation===1&&e.exposure===0),gain=f(2**e.exposure);
 return pixel=>{
  if(identity||pixel>>>24===0)return pixel|0;
  const r=f((pixel>>>16&255)/255),g=f((pixel>>>8&255)/255),b=f((pixel&255)/255),high=Math.max(r,g,b),low=Math.min(r,g,b),delta=f(high-low);
  if(delta<f(.01)||high===0)return pixel|0;
  let hue=high===r?f(60*(f(f(g-b)/delta)%6)):high===g?f(60*f(f(f(b-r)/delta)+2)):f(60*f(f(f(r-g)/delta)+4));
  if(hue<0)hue=f(hue+360);
  const difference=Math.abs(f(hue-e.hue))%360,distance=Math.min(difference,f(360-difference));if(distance>e.radius)return pixel|0;
  const inner=f(e.radius*f(1-e.softness)),linear=distance<=inner||e.softness===0?1:clamp(f(f(e.radius-distance)/f(e.radius-inner))),weight=f(f(linear*linear)*f(3-f(2*linear)));
  if(weight===0)return pixel|0;
  const shifted=f(f(f(hue+e.shift)%360+360)%360),sector=Math.trunc(f(shifted/60)),fraction=f(f(shifted/60)-sector);
  const v=clamp(f(high*gain)),s=clamp(f(f(delta/high)*e.saturation)),p=f(v*f(1-s)),q=f(v*f(1-f(fraction*s))),t=f(v*f(1-f(f(1-fraction)*s)));
  const nr=sector===0||sector===5?v:sector===1?q:sector===2||sector===3?p:t,ng=sector===0?t:sector===1||sector===2?v:sector===3?q:p,nb=sector===0||sector===1?p:sector===2?t:sector===3||sector===4?v:q;
  const channel=(old,next)=>Math.max(0,Math.min(255,Math.round(f(f(old+f(f(next-old)*weight))*255))));
  return (pixel&0xff000000)|(channel(r,nr)<<16)|(channel(g,ng)<<8)|channel(b,nb);
 };
}
