import {checkedObjectImage} from './ai-object.mjs';
export const COLOR_MODEL=Object.freeze({file:'adaptive-color.onnx',bytes:1084794,sha256:'fce4887fa153575bd20678a397b0fe8e7dcb0e611c154ecb4f19434a535bd607'});
export const COLOR_TABLES=Object.freeze({file:'lut-bases.f32',bytes:1293732,sha256:'f2279e4b88cd9d5e50c2f72e37b99a7dad050ac5b32af8add5b0f933448594d7'});
export function colorTensor(image){
  checkedObjectImage(image);
  if(image.width!==256||image.height!==256)throw Error('Prepare the color-model input at256x256.');
  const output=new Float32Array(3*256*256);
  for(let i=0;i<256*256;i++)for(let c=0;c<3;c++)output[c*256*256+i]=image.rgba[i*4+c]/255;
  return output;
}
export function fusedColorLut(bases,weights){
  const plane=3*33**3;
  if(!(bases instanceof Float32Array)||bases.length!==plane*3||weights.length!==3||!Array.from(weights).every(value=>Number.isFinite(value)&&Math.abs(value)<=64))throw Error('The color model returned an unsupported result.');
  const result=new Float32Array(plane);
  for(let i=0;i<plane;i++){result[i]=weights[0]*bases[i]+weights[1]*bases[plane+i]+weights[2]*bases[plane*2+i];if(!Number.isFinite(result[i]))throw Error('The learned color table is invalid.');}
  return result;
}
// Original model convention: channels are separate, red varies fastest, followed
// by green and blue. Its1.000001 bin width keeps the white endpoint in bounds.
export function sampleColorLut(lut,r,g,b){
  if(!(lut instanceof Float32Array)||lut.length!==3*33**3||![r,g,b].every(value=>Number.isFinite(value)&&value>=0&&value<=255))throw Error('Invalid color sample.');
  const output=[0,0,0];sampleInto(lut,r,g,b,output);return output;
}
function sampleInto(lut,r,g,b,output){
  const grid=33**3,red=r/255*32/1.000001,green=g/255*32/1.000001,blue=b/255*32/1.000001;
  const ri=Math.floor(red),gi=Math.floor(green),bi=Math.floor(blue),rd=red-ri,gd=green-gi,bd=blue-bi;
  let first=0,second=0,third=0;
  for(let db=0;db<2;db++)for(let dg=0;dg<2;dg++)for(let dr=0;dr<2;dr++){
    const index=ri+dr+(gi+dg)*33+(bi+db)*33*33,weight=(dr?rd:1-rd)*(dg?gd:1-gd)*(db?bd:1-bd);
    first+=lut[index]*weight;second+=lut[grid+index]*weight;third+=lut[grid*2+index]*weight;
  }
  output[0]=first;output[1]=second;output[2]=third;
}
export function colorPixels(image,bases,weights,strength=.7){
  checkedObjectImage(image);
  if(!Number.isFinite(strength)||strength<0||strength>1)throw Error('Choose a color strength from0to100%.');
  const lut=fusedColorLut(bases,weights),result=image.rgba.slice(),mapped=[0,0,0];
  for(let i=0;i<result.length;i+=4){
    sampleInto(lut,image.rgba[i],image.rgba[i+1],image.rgba[i+2],mapped);
    for(let channel=0;channel<3;channel++)result[i+channel]=Math.round(Math.max(0,Math.min(255,mapped[channel]*255))*strength+image.rgba[i+channel]*(1-strength));
  }
  return result;
}
export function mixColorPixels(image,enhanced,strength){
  checkedObjectImage(image);
  if(!(enhanced instanceof Uint8ClampedArray)||enhanced.length!==image.rgba.length||!Number.isFinite(strength)||strength<0||strength>1)throw Error('Invalid color preview strength.');
  const result=image.rgba.slice();
  for(let i=0;i<result.length;i++)if(i%4!==3)result[i]=Math.round(enhanced[i]*strength+image.rgba[i]*(1-strength));
  return result;
}
