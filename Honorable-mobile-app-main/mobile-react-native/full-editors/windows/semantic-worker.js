import * as ort from 'onnxruntime-web/wasm';

const side = 224;
const area = side * side;
const mean = [.48145466, .4578275, .40821073];
const deviation = [.26862954, .26130258, .27577711];

async function checkedAsset(file, expected) {
  const response = await fetch('/desktop/' + file);
  if (!response.ok) throw Error('The bundled search model is unavailable.');
  const data = await response.arrayBuffer();
  const digest = Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', data)),
    byte => byte.toString(16).padStart(2, '0')).join('');
  if (digest !== expected) throw Error('A bundled search asset failed verification.');
  return data;
}

function tokenizer({vocab, merges}) {
  const ranks = new Map(merges.map((pair, index) => [Array.isArray(pair) ? pair.join(' ') : pair, index]));
  const bytes = Array.from({length: 256}, (_, value) => value);
  let extra = 256;
  const alphabet = bytes.map(value => String.fromCharCode(
    (value >= 33 && value <= 126) || (value >= 161 && value <= 172) || value >= 174 ? value : extra++));
  const encoder = new TextEncoder();
  return text => {
    const ids = [49406];
    for (const token of text.normalize('NFC').toLowerCase().matchAll(/'s|'t|'re|'m|'ll|'d|[\p{L}]+|[\p{N}]+|[^\s\p{L}\p{N}]+/gu)) {
      let word = Array.from(encoder.encode(token[0]), byte => alphabet[byte]);
      word[word.length - 1] += '</w>';
      while (word.length > 1) {
        let best = Infinity;
        let pair;
        for (let i = 0; i + 1 < word.length; i++) {
          const rank = ranks.get(word[i] + ' ' + word[i + 1]);
          if (rank < best) {
            best = rank;
            pair = word.slice(i, i + 2);
          }
        }
        if (!pair) break;
        const merged = [];
        for (let i = 0; i < word.length; i++) {
          if (word[i] === pair[0] && word[i + 1] === pair[1]) {
            merged.push(pair.join(''));
            i++;
          } else {
            merged.push(word[i]);
          }
        }
        word = merged;
      }
      for (const part of word) if (Object.hasOwn(vocab, part)) ids.push(vocab[part]);
    }
    return [...ids.slice(0, 76), 49407];
  };
}

async function initialize() {
  const [model, dictionary] = await Promise.all([
    checkedAsset('model_int8.onnx', '10921310ddef06557ec1598d1260470a0a4db53f70ffe0deb60b946dcad6d27a'),
    checkedAsset('tokenizer.json', '6d9109cc838977f3ca94a379eec36aecc7c807e1785cd729660ca2fc0171fb35'),
  ]);
  ort.env.wasm.numThreads = 1;
  ort.env.wasm.wasmPaths = {
    wasm: new URL('/image/ai/ort-wasm-simd-threaded.wasm', location.href).href,
    mjs: new URL('/image/ai/ort-wasm-simd-threaded.js', location.href).href,
  };
  const session = await ort.InferenceSession.create(model, {executionProviders: ['wasm']});
  const encode = tokenizer(JSON.parse(new TextDecoder().decode(dictionary)).model);
  return async (text, pixels, index) => {
    const ids = encode(text);
    const inputs = {
      input_ids: new ort.Tensor('int64', BigInt64Array.from({length: 77}, (_, i) => BigInt(ids[i] ?? 49407)), [1, 77]),
      attention_mask: new ort.Tensor('int64', BigInt64Array.from({length: 77}, (_, i) => i < ids.length - 1 ? 1n : 0n), [1, 77]),
      pixel_values: new ort.Tensor('float32', pixels, [1, 3, side, side]),
    };
    const outputs = await session.run(inputs);
    const values = outputs[session.outputNames[index]].data;
    const norm = Math.sqrt(values.reduce((sum, value) => sum + value * value, 0));
    return Float32Array.from(values, value => norm ? value / norm : 0);
  };
}

async function photoPixels(file) {
  const bitmap = await createImageBitmap(file);
  const canvas = new OffscreenCanvas(side, side);
  const context = canvas.getContext('2d');
  const crop = Math.min(bitmap.width, bitmap.height);
  try {
    context.drawImage(bitmap, (bitmap.width - crop) / 2, (bitmap.height - crop) / 2, crop, crop, 0, 0, side, side);
  } finally {
    bitmap.close();
  }
  const rgba = context.getImageData(0, 0, side, side).data;
  const pixels = new Float32Array(3 * area);
  for (let channel = 0; channel < 3; channel++) {
    for (let i = 0; i < area; i++) {
      pixels[channel * area + i] = (rgba[4 * i + channel] / 255 - mean[channel]) / deviation[channel];
    }
  }
  return pixels;
}

let inference;
let indexed;
self.onmessage = async ({data: {query, files}}) => {
  try {
    if (files) indexed = undefined;
    const infer = await (inference ??= initialize());
    const queryVector = await infer(query, new Float32Array(3 * area), 2);
    if (!indexed && !files) throw Error('Choose a folder before searching.');
    if (files) {
      indexed = [];
      for (const [index, frames] of files.entries()) {
        const embeddings = [];
        for (const frame of frames) {
          let pixels;
          try {
            pixels = await photoPixels(frame);
          } catch {
            continue;
          }
          embeddings.push(await infer('a photo', pixels, 3));
        }
        if (embeddings.length) indexed.push({index, embeddings});
        self.postMessage({progress: index + 1});
      }
    }
    const ranked = indexed.map(({index, embeddings}) => ({
      index,
      score: Math.max(...embeddings.map(embedding => embedding.reduce((sum, value, i) => sum + value * queryVector[i], 0))),
    })).sort((a, b) => b.score - a.score);
    self.postMessage({ranked});
  } catch (error) {
    indexed = undefined;
    inference = undefined;
    self.postMessage({error: error.message});
  }
};
