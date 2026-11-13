import * as gifencModule from 'gifenc';
const { GIFEncoder, quantize, applyPalette } = gifencModule.GIFEncoder
  ? gifencModule
  : gifencModule.default;
export const MAX_GIF_BYTES = 8 * 1024 * 1024;
export function checkedGifOptions({ width, height, duration }) {
  if (
    !Number.isInteger(width) ||
    !Number.isInteger(height) ||
    width < 16 ||
    height < 16 ||
    width > 384 ||
    height > 384
  )
    throw Error('Frame the preview at 16–384 pixels on each side.');
  if (!Number.isInteger(duration) || duration < 1 || duration > 6)
    throw Error(
      'Choose 1–6 whole seconds for GIF export. Longer animations can use GLB.',
    );
  return { width, height, duration, frames: duration * 10, delay: 100 };
}
export function checkedGif(bytes) {
  if (
    !(bytes instanceof Uint8Array) ||
    bytes.length < 20 ||
    bytes.length > MAX_GIF_BYTES ||
    String.fromCharCode(...bytes.subarray(0, 6)) !== 'GIF89a' ||
    bytes[bytes.length - 1] !== 0x3b
  )
    throw Error('GIF export is incomplete or exceeds 8 MiB.');
  const width = bytes[6] | (bytes[7] << 8),
    height = bytes[8] | (bytes[9] << 8);
  if (width < 16 || height < 16 || width > 384 || height > 384)
    throw Error('GIF dimensions exceed the preview budget.');
  return bytes;
}
/** One frame at a time; the worker owns quantization and the output stream. */
export function createGifSession(input) {
  const options = checkedGifOptions(input),
    gif = GIFEncoder();
  let next = 0,
    finished = false;
  return {
    options,
    frame(number, pixels) {
      if (finished || number !== next || number >= options.frames)
        throw Error('GIF frames arrived out of order.');
      const rgba =
        pixels instanceof Uint8Array ? pixels : new Uint8Array(pixels);
      if (rgba.length !== options.width * options.height * 4)
        throw Error('GIF frame pixels are incomplete.');
      for (let i = 3; i < rgba.length; i += 4)
        if (rgba[i] !== 255) throw Error('GIF needs an opaque framed preview.');
      const palette = quantize(rgba, 256),
        index = applyPalette(rgba, palette);
      gif.writeFrame(index, options.width, options.height, {
        palette,
        delay: options.delay,
        repeat: 0,
        dispose: 1,
      });
      next++;
      if (gif.bytesView().length > MAX_GIF_BYTES)
        throw Error('GIF exceeds 8 MiB. Choose a shorter animation.');
      return next;
    },
    finish() {
      if (finished || next !== options.frames)
        throw Error('GIF export stopped before every frame was encoded.');
      finished = true;
      gif.finish();
      return checkedGif(gif.bytes());
    },
  };
}
export function sendGif(bridge, id, bytes) {
  checkedGif(bytes);
  if (!Number.isInteger(id) || id < 1 || id > 1_000_000_000)
    throw Error('GIF request is unavailable.');
  for (const name of [
    'beginGifShare',
    'appendGifShareChunk',
    'finishGifShare',
    'releaseGifShare',
  ])
    if (typeof bridge?.[name] !== 'function')
      throw Error('Update Noble to share GIF animation.');
  let began = false;
  try {
    if (bridge.beginGifShare(id, bytes.length) !== true)
      throw Error('Another operation is running. Try sharing again.');
    began = true;
    for (let i = 0; i < bytes.length; i += 24576)
      if (
        bridge.appendGifShareChunk(
          id,
          btoa(String.fromCharCode(...bytes.subarray(i, i + 24576))),
        ) !== true
      )
        throw Error('GIF transfer stopped. Try again when active.');
    bridge.finishGifShare(id);
  } catch (error) {
    if (began) bridge.releaseGifShare(id);
    throw error;
  }
}
