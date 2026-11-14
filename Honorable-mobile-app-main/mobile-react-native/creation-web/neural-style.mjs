// The published model has a fixed 224-square input. Padding preserves aspect;
// alpha and the original photo are kept rather than inferred by this RGB model.
import {checkedDenoiseImage} from './neural-denoise.mjs';
export const STYLE_SIDE = 224;
export const STYLE_MODEL = Object.freeze({
  bytes: 6728029,
  sha256: 'fa646dedade881243f8d5a2ceb7de2b93675b21fc24f7482894ac4851a9a0a47',
  file: 'mosaic-9.onnx',
});
export function checkedStyleImage(image) {
  if (!image || !Number.isInteger(image.width) || !Number.isInteger(image.height) ||
      image.width < 1 || image.height < 1 || image.width > STYLE_SIDE || image.height > STYLE_SIDE ||
      !(image.rgba instanceof Uint8ClampedArray) || image.rgba.length !== image.width * image.height * 4)
    throw Error('Prepare a local texture up to 224 pixels per side.');
  return image;
}
export function styleTensor(image) {
  const {width, height, rgba} = checkedStyleImage(image);
  const left = Math.floor((STYLE_SIDE - width) / 2), top = Math.floor((STYLE_SIDE - height) / 2);
  const tensor = new Float32Array(3 * STYLE_SIDE * STYLE_SIDE);
  for (let y = 0; y < STYLE_SIDE; y++) for (let x = 0; x < STYLE_SIDE; x++) {
    const sx = Math.max(0, Math.min(width - 1, x - left)), sy = Math.max(0, Math.min(height - 1, y - top));
    const index = (sy * width + sx) * 4;
    for (let c = 0; c < 3; c++) tensor[c * STYLE_SIDE * STYLE_SIDE + y * STYLE_SIDE + x] = rgba[index + c];
  }
  return {tensor, left, top};
}
export function stylePixels(image, output, dims) {
  const {width, height, rgba} = checkedStyleImage(image);
  if (!(output instanceof Float32Array) || output.length !== 3 * STYLE_SIDE * STYLE_SIDE ||
      JSON.stringify(dims) !== '[1,3,224,224]') throw Error('The neural model returned an unsupported image.');
  const left = Math.floor((STYLE_SIDE - width) / 2), top = Math.floor((STYLE_SIDE - height) / 2);
  const result = new Uint8ClampedArray(rgba.length);
  for (let y = 0; y < height; y++) for (let x = 0; x < width; x++) {
    const target = (y * width + x) * 4;
    for (let c = 0; c < 3; c++) {
      const value = output[c * STYLE_SIDE * STYLE_SIDE + (y + top) * STYLE_SIDE + x + left];
      if (!Number.isFinite(value)) throw Error('The neural model returned invalid pixels.');
      result[target + c] = Math.floor(Math.max(0, Math.min(255, value)));
    }
    result[target + 3] = rgba[target + 3];
  }
  return result;
}
export function blendStyle(original, styled, strength) {
  checkedDenoiseImage(original);
  if (!(styled instanceof Uint8ClampedArray) || styled.length !== original.rgba.length ||
      !Number.isFinite(strength) || strength < 0 || strength > 1) throw Error('Invalid neural paint blend.');
  const result = new Uint8ClampedArray(styled.length);
  for (let i = 0; i < result.length; i++) result[i] = i % 4 === 3 ? original.rgba[i] :
    Math.round(original.rgba[i] * (1 - strength) + styled[i] * strength);
  return result;
}
