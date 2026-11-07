import source from 'noble:shape-boolean-worker';
export function makeShapeWorker() {
  const url = URL.createObjectURL(new Blob([source], { type: 'text/javascript' }));
  try { return { worker: new Worker(url), dispose: () => URL.revokeObjectURL(url) }; }
  catch (error) { URL.revokeObjectURL(url); throw error; }
}
