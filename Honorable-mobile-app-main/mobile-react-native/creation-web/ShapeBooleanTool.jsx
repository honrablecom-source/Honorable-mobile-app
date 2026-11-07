import React, { useEffect, useRef, useState } from 'react';
import { startShapeTask } from './shape-boolean-task.mjs';
import { makeShapeWorker } from './shape-boolean-worker-client.mjs';
const descriptions = {
  union: 'Keep all areas covered by the selected shapes.',
  difference: 'Cut the front selected shape out of the union of the others. The result uses the front-most remaining base shape’s colors.',
  intersection: 'Keep only the area shared by every selected shape.',
  xor: 'Keep areas covered by an odd number of selected shapes. With two shapes, this removes their shared overlap.',
};
const path = polygon => polygon.map(ring => `M${ring.map(point => point.join(',')).join('L')}Z`).join('');
export function ShapeBooleanTool({ captureSource, isCurrent, onApply, onCancel }) {
  const [operation, setOperation] = useState('union'), [proposal, setProposal] = useState(null), [busy, setBusy] = useState(false), [message, setMessage] = useState('');
  const alive = useRef(true), ticket = useRef(0), task = useRef(null);
  const stop = () => { ticket.current++; task.current?.cancel(); task.current = null; setBusy(false); setProposal(null); };
  useEffect(() => {
    alive.current = true;
    const hidden = () => { if (document.visibilityState === 'hidden') { stop(); setMessage('Preview stopped while the drawing was hidden. Originals are unchanged.'); } };
    document.addEventListener('visibilitychange', hidden);
    return () => { alive.current = false; ticket.current++; task.current?.cancel(); document.removeEventListener('visibilitychange', hidden); };
  }, []);
  const preview = async () => {
    const currentTicket = ++ticket.current; task.current?.cancel(); setProposal(null); setBusy(true); setMessage('Combining selected shapes on this device…');
    try {
      const source = captureSource();
      const current = () => alive.current && ticket.current === currentTicket && isCurrent(source);
      const work = startShapeTask(source.input, operation, { makeWorker: makeShapeWorker, isCurrent: current }); task.current = work;
      const result = await work.promise;
      if (!current()) throw Error('The drawing changed. Preview the selected shapes again.');
      setProposal({ result, source }); setMessage('Preview ready. Originals stay unchanged until you insert a separate result.');
    } catch (error) { if (alive.current && ticket.current === currentTicket) setMessage(error.message || 'The preview could not be prepared.'); }
    finally { if (alive.current && ticket.current === currentTicket) { task.current = null; setBusy(false); } }
  };
  const result = proposal?.result, style = proposal && proposal.source.input.shapes[result.styleIndex];
  return <>
    <h3>Combine shapes</h3>
    <p>Select 2–8 visible, unlocked rectangles, ellipses or diamonds on the canvas first. Use sharp rectangle corners and move shapes out of frames. Selected text, photos and other object types are not combined.</p>
    <label>Shape operation<select aria-label="Shape operation" value={operation} disabled={busy} onChange={event => { stop(); setOperation(event.target.value); setMessage(''); }}>
      <option value="union">Combine covered areas</option><option value="difference">Cut out the front shape</option><option value="intersection">Keep shared overlap</option><option value="xor">Keep odd-overlap areas</option>
    </select></label>
    <p>{descriptions[operation]} Front means the highest selected shape in the drawing’s stacking order.</p>
    <button disabled={busy} onClick={preview}>{busy ? 'Preparing shapes…' : 'Preview combined shapes'}</button>
    {busy && <button onClick={() => { stop(); setMessage('Preview stopped. Originals are unchanged.'); }}>Stop shape preview</button>}
    {result && <>
      <svg role="img" aria-label="Combined shape preview" viewBox={`${result.bounds[0] - 8} ${result.bounds[1] - 8} ${result.width + 16} ${result.height + 16}`} style={{ display: 'block', width: '100%', maxHeight: 240, margin: '12px 0', background: '#eeeeee', border: '1px solid #ccc' }}>
        {result.polygons.map((polygon, index) => <g key={index}><path d={path(polygon)} fill={style.backgroundColor} fillRule="evenodd" opacity={style.opacity / 100} />{style.strokeWidth > 0 && polygon.map((ring, ringIndex) => <path key={ringIndex} d={path([ring])} fill="none" stroke={style.strokeColor} strokeWidth={style.strokeWidth} strokeDasharray={style.strokeStyle === 'dashed' ? '8 6' : style.strokeStyle === 'dotted' ? '1 4' : undefined} opacity={style.opacity / 100} />)}</g>)}
      </svg>
      <p>{result.sourceCount} source shapes → {result.polygons.length} parts with {result.plan.length} editable paths. The result is grouped beside the originals; Undo removes the insertion.</p>
      <p>Colors and opacity come from the front selected shape, or the front remaining base for a cutout. Results use solid fills and crisp outlines, rather than hatching or sketch effects. {result.ellipseApproximation && 'Ellipses become fine polygonal curves; zoom in to check their edges.'}</p>
      <button onClick={() => { try { if (!isCurrent(proposal.source)) throw Error('The drawing changed. Preview again before inserting.'); onApply(result, proposal.source); } catch (error) { setProposal(null); setMessage(error.message); } }}>Insert combined shape</button>
    </>}
    <button onClick={onCancel}>Keep originals only</button>
    <p aria-live="polite">{message}</p>
    <p>Preview uses the selection at that moment. Changing the selection does not retarget an existing preview. Processing is local; no photo, query or drawing is uploaded.</p>
  </>;
}
