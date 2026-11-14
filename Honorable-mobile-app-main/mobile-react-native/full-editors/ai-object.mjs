export const OBJECT_MODEL = Object.freeze({file:'u2netp.onnx', bytes:4574861,
  sha256:'309c8469258dda742793dce0ebea8e6dd393174f89934733ecc8b14c76f4ddd8'});
export const OBJECT_SIDE = 320;
export function checkedObjectImage(image) {
  if (!image || !Number.isInteger(image.width) || !Number.isInteger(image.height) ||
      image.width < 1 || image.height < 1 || image.width > 1024 || image.height > 1024 ||
      !(image.rgba instanceof Uint8ClampedArray) || image.rgba.length !== image.width*image.height*4)
    throw Error('Choose a working photo up to 1024 pixels per side.');
  return image;
}
// The caller supplies a 320x320 browser-resampled RGB image. Normalization follows
// the pinned U2NETP/rembg adapter, including its per-image maximum for dark input.
export function objectTensor(image) {
  checkedObjectImage(image);
  if (image.width !== OBJECT_SIDE || image.height !== OBJECT_SIDE) throw Error('Prepare the model input at 320x320.');
  const plane = OBJECT_SIDE**2, output = new Float32Array(plane*3);
  let maximum = 1;
  for(let i=0;i<image.rgba.length;i+=4)for(let c=0;c<3;c++)maximum=Math.max(maximum,image.rgba[i+c]);
  const mean=[0.485,0.456,0.406], std=[0.229,0.224,0.225];
  for(let i=0;i<plane;i++)for(let c=0;c<3;c++)output[c*plane+i]=(image.rgba[i*4+c]/maximum-mean[c])/std[c];
  return output;
}
export function objectPixels(image, data, dims) {
  checkedObjectImage(image);
  if (!(data instanceof Float32Array) || data.length !== OBJECT_SIDE**2 || JSON.stringify(dims)!=='[1,1,320,320]')
    throw Error('The object model returned an unsupported mask.');
  let low=Infinity,high=-Infinity;
  for(const value of data){if(!Number.isFinite(value))throw Error('The object model returned invalid pixels.');low=Math.min(low,value);high=Math.max(high,value);}
  if(high-low<1e-6)throw Error('No useful foreground mask was found. Try a photo with a clear subject.');
  const output=image.rgba.slice(), span=high-low;
  for(let y=0;y<image.height;y++)for(let x=0;x<image.width;x++){
    const sx=Math.max(0,Math.min(319,(x+0.5)*320/image.width-0.5));
    const sy=Math.max(0,Math.min(319,(y+0.5)*320/image.height-0.5));
    const x0=Math.floor(sx),y0=Math.floor(sy),x1=Math.min(319,x0+1),y1=Math.min(319,y0+1),fx=sx-x0,fy=sy-y0;
    const top=data[y0*320+x0]*(1-fx)+data[y0*320+x1]*fx;
    const bottom=data[y1*320+x0]*(1-fx)+data[y1*320+x1]*fx;
    const alpha=Math.max(0,Math.min(1,(top*(1-fy)+bottom*fy-low)/span));
    const i=(y*image.width+x)*4;
    output[i+3]=Math.round(image.rgba[i+3]*alpha);
  }
  return output;
}
