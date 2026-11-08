import { vectorArtwork } from './svg-import-geometry.mjs';
self.onmessage = event => {
  const { id, input } = event.data || {};
  if (!Number.isInteger(id) || id < 1 || id > 1e9) return;
  try { self.postMessage({ id, kind: 'result', result: vectorArtwork(input) }); }
  catch (error) { self.postMessage({ id, kind: 'error', error: error instanceof Error ? error.message.slice(0,240) : 'Local SVG processing stopped.' }); }
};
