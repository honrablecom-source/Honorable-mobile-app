/** Irreversible opaque RGB replacement in a separate raster; original pixels are never mutated. */
export function redactionPlan(width,height,regions){
 if(!Number.isInteger(width)||!Number.isInteger(height)||width<8||height<8||width>1024||height>1024)throw Error('Use an embedded photo from 8 to 1024 pixels on each side.');
 if(!Array.isArray(regions)||!regions.length||regions.length>24)throw Error('Add 1–24 rectangular privacy blocks.');
 return regions.map(region=>{
  if(!region||['x','y','width','height'].some(key=>!Number.isInteger(region[key]))||region.x<0||region.y<0||region.width<1||region.height<1||region.x+region.width>width||region.y+region.height>height)throw Error('Keep whole-pixel privacy blocks inside the embedded photo.');
  return{x:region.x,y:region.y,width:region.width,height:region.height};
 });
}
export function redactPixels(input,width,height,regions){
 const plan=redactionPlan(width,height,regions);
 if(!(input instanceof Uint8Array||input instanceof Uint8ClampedArray)||input.length!==width*height*4)throw Error('The embedded photo pixels are incomplete.');
 const data=new Uint8ClampedArray(input);let changed=0;
 for(const region of plan)for(let y=region.y;y<region.y+region.height;y++)for(let x=region.x;x<region.x+region.width;x++){
  const at=(y*width+x)*4;if(data[at]||data[at+1]||data[at+2]||data[at+3]!==255)changed++;
  data[at]=data[at+1]=data[at+2]=0;data[at+3]=255;
 }
 if(!changed)throw Error('These regions are already opaque black. Keep the original or choose another area.');
 return{data,width,height,changed};
}
