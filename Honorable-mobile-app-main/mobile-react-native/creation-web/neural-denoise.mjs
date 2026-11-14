export const DENOISE_SIDE = 128;
export const DENOISE_HALO = 18; // 17 three-by-three convolutions have radius17.
export const DENOISE_MAX = 256;
export const DENOISE_MODEL = Object.freeze({file:'dncnn25-128.onnx', bytes:2226850,
  sha256:'5dc5803550a81466bb3edc011cfb1b37ba082ea6fa396d466a9055aec298be37'});
export function checkedDenoiseImage(image) {
  if (!image || !Number.isInteger(image.width) || !Number.isInteger(image.height) || image.width < 1 || image.height < 1 ||
      image.width > DENOISE_MAX || image.height > DENOISE_MAX || !(image.rgba instanceof Uint8ClampedArray) ||
      image.rgba.length !== image.width*image.height*4) throw Error('Prepare a local image up to 256 pixels per side.');
  return image;
}
export function noiseTiles(image) {
  const {width,height}=checkedDenoiseImage(image),inner=DENOISE_SIDE-2*DENOISE_HALO,tiles=[];
  for(let y=0;y<height;y+=inner)for(let x=0;x<width;x+=inner)
    tiles.push({x,y,width:Math.min(inner,width-x),height:Math.min(inner,height-y)});
  return tiles;
}
function checkedTile(image,tile) {
  checkedDenoiseImage(image);
  const inner=DENOISE_SIDE-2*DENOISE_HALO;
  if(!tile||!['x','y','width','height'].every(key=>Number.isInteger(tile[key]))||tile.x<0||tile.y<0||
      tile.width<1||tile.height<1||tile.width>inner||tile.height>inner||tile.x+tile.width>image.width||tile.y+tile.height>image.height)
    throw Error('Invalid noise-removal tile.');
}
export const luminance = (r,g,b) => (0.299*r+0.587*g+0.114*b)/255;
export function noiseTensor(image,tile) {
  checkedTile(image,tile);
  const result=new Float32Array(DENOISE_SIDE*DENOISE_SIDE);
  for(let y=0;y<DENOISE_SIDE;y++)for(let x=0;x<DENOISE_SIDE;x++){
    const sx=Math.max(0,Math.min(image.width-1,tile.x+x-DENOISE_HALO)),sy=Math.max(0,Math.min(image.height-1,tile.y+y-DENOISE_HALO));
    const i=(sy*image.width+sx)*4;result[y*DENOISE_SIDE+x]=luminance(image.rgba[i],image.rgba[i+1],image.rgba[i+2]);
  }
  return result;
}
export function mergeNoiseTile(image,result,tile,output,dims) {
  checkedTile(image,tile);
  if(!(result instanceof Uint8ClampedArray)||result.length!==image.rgba.length||!(output instanceof Float32Array)||
      output.length!==DENOISE_SIDE*DENOISE_SIDE||JSON.stringify(dims)!=='[1,1,128,128]')
    throw Error('The noise model returned an unsupported image.');
  for(let y=0;y<tile.height;y++)for(let x=0;x<tile.width;x++){
    const i=((tile.y+y)*image.width+tile.x+x)*4,predicted=output[(y+DENOISE_HALO)*DENOISE_SIDE+x+DENOISE_HALO];
    if(!Number.isFinite(predicted))throw Error('The noise model returned invalid pixels.');
    const delta=(Math.max(0,Math.min(1,predicted))-luminance(image.rgba[i],image.rgba[i+1],image.rgba[i+2]))*255;
    for(let c=0;c<3;c++)result[i+c]=Math.round(Math.max(0,Math.min(255,image.rgba[i+c]+delta)));
    result[i+3]=image.rgba[i+3];
  }
  return result;
}
