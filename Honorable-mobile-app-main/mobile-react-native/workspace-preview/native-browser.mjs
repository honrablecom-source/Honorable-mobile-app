// Browser-only adapter. Never shipped as a native bridge and never sends media to this server.
let navigate=()=>{},message=()=>{},files=new Map();
export function connectBrowser(onNavigate,onMessage){navigate=onNavigate;message=onMessage;}
const opened=async(screen,extra={})=>{navigate({screen,...extra});return{opened:true};};
function pick(kind){return new Promise(resolve=>{const input=document.createElement('input');input.type='file';input.accept=kind==='IMAGE'?'image/*':'video/*';input.oncancel=()=>resolve(null);input.onchange=()=>{const file=input.files?.[0];if(!file)return resolve(null);const uri=URL.createObjectURL(file);files.set(uri,file);resolve({uri});};input.click();});}
function blank(width,height,background='white'){
 if(!Number.isInteger(width)||!Number.isInteger(height)||width<64||height<64||width>4096||height>4096||width*height>4194304)throw Error('Choose 64–4096 pixels per side, up to 4,194,304 pixels.');
 const canvas=document.createElement('canvas');canvas.width=width;canvas.height=height;
 if(background!=='transparent'){const context=canvas.getContext('2d');context.fillStyle=background==='black'?'#000':'#fff';context.fillRect(0,0,width,height);}
 return opened('photo',{uri:canvas.toDataURL('image/png'),name:'New canvas'});
}
export function releaseBrowserMedia(){for(const uri of files.keys())URL.revokeObjectURL(uri);files.clear();}
export const honorableNative={
 pickWorkspaceMedia:pick,
 editPhotoGuest:uri=>opened('photo',{uri,name:files.get(uri)?.name||'Selected photo'}),
 editVideoGuest:uri=>opened('video',{uri,name:files.get(uri)?.name||'Selected video'}),
 createDrawingGuest:()=>opened('drawing'),
 createImageGuest:format=>{const size=NOBLE_CANVAS_FORMATS[format];if(!size)throw Error('Unknown canvas format');return blank(...size);},
 createCustomImageGuest:blank,
 createPhotoVideoGuest:()=>opened('photo-video'),
 openCanvasProjectsGuest:async()=>{message('Native saved image projects live inside the Android app. Drawing projects are usable here under Draw an image → Projects.');return{opened:false};},
 openPhotoVideoProjectsGuest:async()=>{message('Native prepared video projects require Android. This preview can play selected local videos and plan a photo sequence.');return{opened:false};},
 canvasSetups:async(action,name,width,height,background)=>{
  const key='noble-preview-canvas-setups-v1';let values=JSON.parse(localStorage.getItem(key)||'[]');
  if(!Array.isArray(values)||values.length>20)throw Error('Preview canvas setups are unreadable.');
  if(action==='save'){
   if(!name||name.length>40||Array.from(name).some(character=>character.charCodeAt(0)<32||character.charCodeAt(0)===127))throw Error('Choose a setup name.');
   if(width<64||height<64||width>4096||height>4096||width*height>4194304)throw Error('Invalid canvas dimensions.');
   values=values.filter(value=>value.name.toLowerCase()!==name.toLowerCase());if(values.length>=20)throw Error('Keep up to 20 setups.');values.push({name,width,height,background});
  }else if(action==='delete')values=values.filter(value=>value.name!==name);else if(action!=='list')throw Error('Unknown setup action.');
  localStorage.setItem(key,JSON.stringify(values));return JSON.stringify(values);
 }
};
