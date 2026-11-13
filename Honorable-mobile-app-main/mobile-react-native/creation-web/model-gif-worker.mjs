import { createGifSession } from './model-gif.mjs';
let session = null;
self.onmessage = event => {
  const { id, type, options, number, pixels } = event.data || {};
  try {
    if (type === 'init') {
      if (session) throw Error('GIF encoder already started.');
      session = createGifSession(options);
      self.postMessage({ id, type: 'ready' });
    } else if (type === 'frame') {
      if (!session || !(pixels instanceof ArrayBuffer))
        throw Error('GIF frame is unavailable.');
      const count = session.frame(number, pixels);
      self.postMessage({ id, type: 'frame', count });
    } else if (type === 'finish') {
      if (!session) throw Error('GIF encoder is unavailable.');
      const bytes = session.finish();
      session = null;
      self.postMessage({ id, type: 'done', bytes: bytes.buffer }, [
        bytes.buffer,
      ]);
      self.close();
    } else throw Error('Unknown GIF operation.');
  } catch (error) {
    session = null;
    self.postMessage({ id, type: 'error', message: error.message });
    self.close();
  }
};
