import { combineShapes } from './shape-boolean.mjs';
self.onmessage = event => {
  const { id, input, operation } = event.data || {};
  if (!Number.isInteger(id) || id < 1 || id > 1e9) return;
  try { self.postMessage({ id, kind: 'result', result: combineShapes(input, operation) }); }
  catch (error) { self.postMessage({ id, kind: 'error', error: error instanceof Error ? error.message : 'These shapes could not be combined safely.' }); }
};
