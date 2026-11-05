import MagicWand from 'magic-wand-tool';
import{photoBytes,MAX_PHOTO_SIDE}from'./image-assets.mjs';
export function removeConnectedColor(input,width,height,seed,tolerance){
 if(!(input instanceof Uint8Array||input instanceof Uint8ClampedArray)||!Number.isInteger(width)||!Number.isInteger(height)||width<8||height<8||width>MAX_PHOTO_SIDE||height>MAX_PHOTO_SIDE||input.length!==width*height*4)throw Error('Use a local photo from 8 to 1024 pixels on each side.');
 if(!seed||!Number.isInteger(seed.x)||!Number.isInteger(seed.y)||seed.x<0||seed.y<0||seed.x>=width||seed.y>=height)throw Error('Tap a background pixel inside the photo.');
 if(!Number.isInteger(tolerance)||tolerance<0||tolerance>128)throw Error('Use a whole-number tolerance from 0 to 128.');
 if(input[(seed.y*width+seed.x)*4+3]===0)throw Error('Tap a visible background pixel, not an already transparent area.');
 const visited=new Uint8Array(width*height);let visible=0;for(let i=0;i<visited.length;i++){if(input[i*4+3]===0)visited[i]=1;else visible++;}
 // Transparent pixels are barriers. Only matching RGB, four-connected to the seed, is selected.
 const mask=MagicWand.floodFill({data:input,width,height,bytes:4},seed.x,seed.y,tolerance,visited,false);
 if(!mask||mask.data.length!==width*height)throw Error('The connected area could not be selected. Choose another point.');
 const data=new Uint8ClampedArray(input);let removed=0;for(let i=0;i<mask.data.length;i++)if(mask.data[i]){data.fill(0,i*4,i*4+4);removed++;}
 if(!removed)throw Error('No visible pixels were selected. Choose another point.');
 if(removed===visible)throw Error('This would remove all visible artwork. Choose another point or lower the tolerance.');
 return{data,width,height,removed,visible,remaining:visible-removed};
}
export async function removePhotoBackground(dataURL,seed,tolerance,requireCurrent=()=>{}){
 requireCurrent();const info=photoBytes(dataURL),image=new Image();image.decoding='async';await new Promise((resolve,reject)=>{image.onload=resolve;image.onerror=()=>reject(Error('The embedded source photo could not be opened.'));image.src=dataURL;});requireCurrent();if(image.naturalWidth!==info.width||image.naturalHeight!==info.height)throw Error('The source dimensions changed.');
 const canvas=document.createElement('canvas');try{canvas.width=info.width;canvas.height=info.height;const context=canvas.getContext('2d',{willReadFrequently:true});if(!context)throw Error('Local photo processing is unavailable.');context.drawImage(image,0,0);const pixels=context.getImageData(0,0,info.width,info.height).data;
  await new Promise(resolve=>setTimeout(resolve,0));requireCurrent();const result=removeConnectedColor(pixels,info.width,info.height,seed,tolerance);requireCurrent();context.putImageData(new ImageData(result.data,info.width,info.height),0,0);
  const blob=await new Promise((resolve,reject)=>canvas.toBlob(value=>value?resolve(value):reject(Error('The transparent PNG could not be encoded.')),'image/png'));requireCurrent();return{blob,width:info.width,height:info.height,removed:result.removed,remaining:result.remaining};
 }finally{canvas.width=canvas.height=0;}
}
