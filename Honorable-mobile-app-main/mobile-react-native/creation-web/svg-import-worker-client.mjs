import source from 'noble:svg-import-worker';
export function makeVectorWorker() {
  const url = URL.createObjectURL(new Blob([source], { type: 'text/javascript' }));
  try { return { worker: new Worker(url), dispose: () => URL.revokeObjectURL(url) }; }
  catch (error) { URL.revokeObjectURL(url); throw error; }
}
