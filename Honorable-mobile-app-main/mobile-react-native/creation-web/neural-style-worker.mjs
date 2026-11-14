import * as ort from 'onnxruntime-web/wasm';
import { STYLE_MODEL, STYLE_SIDE, checkedStyleImage, styleTensor, stylePixels } from './neural-style.mjs';
import {DENOISE_MODEL, checkedDenoiseImage, noiseTiles, noiseTensor, mergeNoiseTile} from './neural-denoise.mjs';

let started = false;
self.onmessage = async event => {
  if (started) return;
  started = true;
  let session;
  try {
    const {id, base, width, height, rgba, kind = 'style'} = event.data || {};
    if (!Number.isSafeInteger(id) || id < 1) throw Error('Invalid neural paint request.');
    if (!['style','denoise'].includes(kind)) throw Error('Unsupported local model.');
    const denoise = kind === 'denoise', modelPin = denoise ? DENOISE_MODEL : STYLE_MODEL;
    const directory = new URL(base);
    if (!['http:', 'https:'].includes(directory.protocol) || directory.pathname !== '/drawing/' ||
        directory.search || directory.hash || directory.username || directory.password)
      throw Error('Neural assets must use the local drawing origin.');
    const image = (denoise ? checkedDenoiseImage : checkedStyleImage)({width, height, rgba: new Uint8ClampedArray(rgba)});
    self.postMessage({id, phase: 'Checking the bundled neural model…'});
    const response = await fetch(new URL('ai/'+modelPin.file, directory));
    if (!response.ok) throw Error('The bundled neural model is unavailable. Update Noble.');
    const model = await response.arrayBuffer();
    if (model.byteLength !== modelPin.bytes) throw Error('The neural model is incomplete. Update Noble.');
    const digest = Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', model)), x => x.toString(16).padStart(2, '0')).join('');
    if (digest !== modelPin.sha256) throw Error('The neural model integrity check failed. Update Noble.');
    ort.env.wasm.numThreads = 1;
    ort.env.wasm.proxy = false;
    ort.env.wasm.initTimeout = 30000;
    ort.env.wasm.wasmPaths = {
      wasm: new URL('ai/ort-wasm-simd-threaded.wasm', directory).href,
      mjs: new URL('ai/ort-wasm-simd-threaded.js', directory).href,
    };
    ort.env.logLevel = 'error';
    self.postMessage({id, phase: denoise ? 'Starting local neural noise cleanup…' : 'Painting locally with the neural model…'});
    session = await ort.InferenceSession.create(model, {executionProviders: ['wasm'], graphOptimizationLevel: 'all'});
    if (session.inputNames.length !== 1 || session.inputNames[0] !== (denoise ? 'noisy' : 'input1') ||
        session.outputNames.length !== 1 || session.outputNames[0] !== (denoise ? 'clean' : 'output1'))
      throw Error('Unsupported neural paint model.');
    let result;
    if (denoise) {
      result = image.rgba.slice();
      const tiles = noiseTiles(image);
      for (const [index,tile] of tiles.entries()) {
        self.postMessage({id, phase: `Removing luminance noise locally ${index+1}/${tiles.length}…`});
        const output = await session.run({noisy: new ort.Tensor('float32', noiseTensor(image,tile), [1,1,128,128])});
        mergeNoiseTile(image,result,tile,output.clean.data,output.clean.dims);
      }
    } else {
      const {tensor} = styleTensor(image);
      const output = await session.run({input1: new ort.Tensor('float32', tensor, [1, 3, STYLE_SIDE, STYLE_SIDE])});
      result = stylePixels(image, output.output1.data, output.output1.dims);
    }
    self.postMessage({id, width, height, pixels: result.buffer}, [result.buffer]);
  } catch {
    // Model/runtime diagnostics can contain file paths; expose only a local,
    // actionable message. No partial or non-neural fallback is returned.
    self.postMessage({id: event.data?.id, error: event.data?.kind === 'denoise' ?
      'Neural cleanup could not finish. Try again with an updated WebView. Your original texture is unchanged.' :
      'Neural painting could not finish. Try again with an updated WebView. Your original texture is unchanged.'});
  } finally {
    await session?.release().catch(() => {});
  }
};
