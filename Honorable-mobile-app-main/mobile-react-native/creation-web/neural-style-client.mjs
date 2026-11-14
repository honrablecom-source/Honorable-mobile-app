import { checkedStyleImage } from './neural-style.mjs';
import {checkedDenoiseImage} from './neural-denoise.mjs';

/** One worker owns one inference. Termination stops WASM and releases its heap. */
export function startNeuralStyle(source, image, base, progress = () => {}, factory = url => new Worker(url), kind = 'style') {
  if (!['style','denoise'].includes(kind)) throw Error('Choose an available local neural tool.');
  (kind === 'denoise' ? checkedDenoiseImage : checkedStyleImage)(image);
  const directory = new URL(base);
  if (directory.origin !== location.origin || directory.pathname !== '/drawing/' || directory.search || directory.hash)
    throw Error('Neural painting only loads bundled local assets.');
  const id = Math.floor(Math.random() * 900000000) + 1;
  let worker, timer, rejectTask, settled = false, objectUrl;
  const cleanup = () => {clearTimeout(timer); worker?.terminate(); if (objectUrl) URL.revokeObjectURL(objectUrl);};
  const promise = new Promise((resolve, reject) => {
    rejectTask = reject;
    const finish = (error, result) => {
      if (settled) return;
      settled = true; cleanup();
      if (error) reject(error); else resolve(result);
    };
    try {
      objectUrl = URL.createObjectURL(new Blob([source], {type: 'text/javascript'}));
      worker = factory(objectUrl);
      worker.onerror = () => finish(Error('Neural painting is unavailable in this WebView. Your texture is unchanged.'));
      worker.onmessage = event => {
        const value = event.data;
        if (settled || value?.id !== id) return;
        if (value.error) {finish(Error(String(value.error))); return;}
        if (typeof value.phase === 'string') {progress(value.phase); return;}
        if (value.width !== image.width || value.height !== image.height ||
            !(value.pixels instanceof ArrayBuffer) || value.pixels.byteLength !== image.rgba.byteLength) {
          finish(Error('The neural paint result was incomplete. Your texture is unchanged.')); return;
        }
        finish(null, new Uint8ClampedArray(value.pixels));
      };
      timer = setTimeout(() => finish(Error('Neural painting timed out. Your texture is unchanged.')), 180000);
      const copy = image.rgba.slice().buffer;
      worker.postMessage({id, base: directory.href, kind, width: image.width, height: image.height, rgba: copy}, [copy]);
    } catch (error) {finish(error);}
  });
  return {promise, stop() {
    if (settled) return;
    settled = true; cleanup(); rejectTask(Error('Neural painting stopped. Your texture is unchanged.'));
  }};
}
