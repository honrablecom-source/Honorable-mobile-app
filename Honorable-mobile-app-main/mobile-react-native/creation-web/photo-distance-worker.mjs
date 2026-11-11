import {distanceFieldPixels} from './photo-distance-field.mjs';
let owned=false;
self.onmessage=event=>{
 const{id,pixels,width,height,threshold,spread}=event.data||{};if(owned||!Number.isInteger(id)||id<1||id>1e9)return;owned=true;
 try{const result=distanceFieldPixels(pixels,width,height,threshold,spread);self.postMessage({id,kind:'result',result},[result.data.buffer]);}
 catch(error){self.postMessage({id,kind:'error',error:error instanceof Error?error.message.slice(0,200):'Distance measurement could not finish.'});}
};
