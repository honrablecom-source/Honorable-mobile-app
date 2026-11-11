import {photoBytes,photoFile,MAX_PHOTO_BYTES} from './image-assets.mjs';
import {verifyPhotoCopies} from './photo-import.mjs';
/** Read a reviewed data/texture PNG without padding, color changes or resampling. */
export async function readExactPhotoPng(blob,requireCurrent=()=>{}){
 requireCurrent();if(!blob||blob.type!=='image/png'||blob.size<1||blob.size>MAX_PHOTO_BYTES)throw Error('Use a reviewed PNG within the local photo limit.');
 const dataURL=await new Promise((resolve,reject)=>{const reader=new FileReader();reader.onload=()=>resolve(String(reader.result));reader.onerror=()=>reject(Error('The reviewed PNG could not be read.'));reader.onabort=()=>reject(Error('PNG preparation cancelled.'));reader.readAsDataURL(blob);});requireCurrent();
 const info=photoBytes(dataURL);if(info.mimeType!=='image/png'||info.width<8||info.height<8)throw Error('Use a reviewed PNG from 8 to 1024 pixels on each side.');return dataURL;
}
export async function prepareExactPhotoCopy(blob,accepts,requireCurrent=()=>{}){
 const dataURL=await readExactPhotoPng(blob,requireCurrent),file=photoFile(dataURL);await verifyPhotoCopies({[file.id]:file});requireCurrent();
 if(!accepts(file))throw Error('There is not enough room for this reviewed PNG. Remove a photo or use a smaller source; no resized copy was inserted.');return file;
}
