import { validateDrawingElements } from './scene.mjs';
let sequence = 0;
export function startShapeTask(input, operation, { makeWorker, isCurrent, timeoutMs = 5000, schedule = setTimeout, clear = clearTimeout } = {}) {
  const id = sequence = sequence >= 1e9 ? 1 : sequence + 1;
  let handle, timer, done = false, rejectPromise;
  const dispose = () => { if (timer != null) clear(timer); if (handle) { handle.worker.onmessage = null; handle.worker.onerror = null; try { handle.worker.terminate(); } finally { handle.dispose(); } } };
  const promise = new Promise((resolve, reject) => {
    rejectPromise = reject;
    const finish = (error, result) => { if (done) return; done = true; try { dispose(); } catch { /* Preserve the operation result. */ } if (error) reject(error); else resolve(result); };
    try {
      if (typeof makeWorker !== 'function' || typeof isCurrent !== 'function' || !isCurrent()) throw Error('The drawing changed. Preview the selected shapes again.');
      if (!Number.isFinite(timeoutMs) || timeoutMs < 1 || timeoutMs > 5000) throw Error('Local preview timing is unavailable. Try again.');
      handle = makeWorker();
      handle.worker.onmessage = event => {
        if (done || event.data?.id !== id) return;
        try { if (!isCurrent()) throw Error('Changed source'); } catch { finish(Error('The drawing changed. Preview the selected shapes again.')); return; }
        if (event.data.kind === 'error') { finish(Error(typeof event.data.error === 'string' ? event.data.error.slice(0, 240) : 'Local shape processing stopped. Try again.')); return; }
        if (event.data.kind !== 'result') return;
        try {
          const result = event.data.result;
          if (!result || result.operation !== operation || result.sourceCount !== input.shapes.length || !Array.isArray(result.plan) || !result.plan.length || result.plan.length + input.existingCount > 500 || !Array.isArray(result.polygons) || !result.polygons.length || result.polygons.length > 32 || !Array.isArray(result.bounds) || result.bounds.length !== 4 || !result.bounds.every(Number.isFinite) || !Number.isFinite(result.area) || result.area <= 0) throw Error('Incomplete result.');
          if (!Number.isInteger(result.styleIndex) || result.styleIndex !== input.shapes.length - (operation === 'difference' ? 2 : 1) || typeof result.ellipseApproximation !== 'boolean' || ![result.width, result.height].every(value => Number.isFinite(value) && value > 0 && value <= 1e6) || result.bounds[2] <= result.bounds[0] || result.bounds[3] <= result.bounds[1] || Math.abs(result.width - (result.bounds[2] - result.bounds[0])) > 1e-6 || Math.abs(result.height - (result.bounds[3] - result.bounds[1])) > 1e-6) throw Error('Invalid preview dimensions or style.');
          if (result.polygons.reduce((n, polygon) => n + polygon.length, 0) > 64 || result.polygons.flat(2).length > 2048 || result.polygons.flat(2).some(point => !Array.isArray(point) || point.length !== 2 || point.some(n => !Number.isFinite(n)))) throw Error('Invalid result.');
          if (result.plan.some(element => element.type !== 'line' || !Array.isArray(element.points) || element.points.length < 4)) throw Error('Invalid paths.');
          validateDrawingElements(result.plan.map((element, index) => ({ ...element, id: `result-${index}` })));
          finish(null, result);
        } catch { finish(Error('The local shape result was incomplete. Try again.')); }
      };
      handle.worker.onerror = event => { event.preventDefault?.(); finish(Error('Local shape processing stopped. Try again.')); };
      timer = schedule(() => finish(Error('The preview took too long. Use fewer or simpler shapes.')), timeoutMs);
      handle.worker.postMessage({ id, input, operation });
    } catch (error) { finish(error instanceof Error ? error : Error('Local shape processing could not start.')); }
  });
  return { promise, cancel() { if (done) return; done = true; try { dispose(); } catch { /* Cancellation still settles once. */ } rejectPromise(Error('Shape preview stopped. Originals are unchanged.')); } };
}
