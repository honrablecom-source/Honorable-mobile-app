import { checkedGifOptions, checkedGif } from './model-gif.mjs';
export function startGifWorker(
  source,
  options,
  signal,
  makeWorker = url => new Worker(url),
) {
  checkedGifOptions(options);
  if (signal?.aborted) throw Error('GIF export stopped.');
  const url = URL.createObjectURL(
    new Blob([source], { type: 'text/javascript' }),
  );
  let worker,
    closed = false,
    pending = null,
    sequence = 0;
  const close = () => {
    if (closed) return;
    closed = true;
    worker?.terminate();
    URL.revokeObjectURL(url);
    signal?.removeEventListener('abort', abort);
  };
  const stop = message => {
    const waiting = pending;
    pending = null;
    close();
    waiting?.reject(Error(message));
  };
  const abort = () => stop('GIF export stopped. No GIF was shared.');
  try {
    worker = makeWorker(url);
  } catch (error) {
    URL.revokeObjectURL(url);
    throw Error('GIF worker could not start: ' + error.message);
  }
  worker.onerror = () => stop('GIF encoding failed. Try a shorter animation.');
  worker.onmessage = event => {
    const data = event.data || {};
    if (closed || !pending || data.id !== pending.id) return;
    const waiting = pending;
    pending = null;
    if (data.type === 'error') {
      close();
      waiting.reject(Error(data.message || 'GIF encoding failed.'));
    } else waiting.resolve(data);
  };
  const call = (type, data = {}, transfer = []) =>
    new Promise((resolve, reject) => {
      if (closed || signal?.aborted) {
        close();
        reject(Error('GIF export stopped.'));
        return;
      }
      if (pending) {
        reject(Error('Finish the current GIF frame first.'));
        return;
      }
      const id = ++sequence;
      pending = { id, resolve, reject };
      try {
        worker.postMessage({ id, type, ...data }, transfer);
      } catch (error) {
        pending = null;
        close();
        reject(error);
      }
    });
  signal?.addEventListener('abort', abort, { once: true });
  return {
    ready: call('init', { options }),
    async frame(number, pixels) {
      const result = await call('frame', { number, pixels }, [pixels]);
      if (result.type !== 'frame' || result.count !== number + 1) {
        stop('GIF frame confirmation failed.');
        throw Error('GIF frame confirmation failed.');
      }
    },
    async finish() {
      try {
        const result = await call('finish');
        if (result.type !== 'done' || !(result.bytes instanceof ArrayBuffer))
          throw Error('GIF output is incomplete.');
        return checkedGif(new Uint8Array(result.bytes));
      } finally {
        close();
      }
    },
    stop: abort,
  };
}
