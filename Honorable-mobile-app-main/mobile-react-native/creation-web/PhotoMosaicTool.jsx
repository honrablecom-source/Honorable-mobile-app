import React, { useEffect, useRef, useState } from 'react';
import { mosaicPhoto } from './photo-mosaic.mjs';

export function PhotoMosaicTool({ target, isCurrent, onApply, onCancel }) {
  const [detail, setDetail] = useState('6'), [proposal, setProposal] = useState(null);
  const [busy, setBusy] = useState(false), [message, setMessage] = useState('');
  const alive = useRef(true), request = useRef(0);
  useEffect(() => { alive.current = true; return () => { alive.current = false; request.current++; }; }, []);
  const preview = async () => {
    const ticket = ++request.current; setBusy(true); setProposal(null);
    setMessage('Turning source colors into triangles on this device…');
    const guard = () => {
      if (!alive.current || request.current !== ticket || !isCurrent()) throw Error('The source or drawing changed. Open Photo mosaic again.');
    };
    try {
      guard();
      const result = await mosaicPhoto(target.dataURL, Number(detail), guard);
      guard(); setProposal(result);
      setMessage(`${result.plan.length} editable triangles. ${result.omittedTransparent} fully transparent facets omitted. Review before inserting.`);
    } catch (error) { if (alive.current && ticket === request.current) setMessage(error.message); }
    finally { if (alive.current && ticket === request.current) setBusy(false); }
  };
  return <>
    <h3>Editable photo mosaic</h3>
    <p>Turn a photo into colored triangles you can edit individually. Uses the full embedded source before crop, rotation, flip or opacity; the new mosaic starts upright. The original stays in your drawing.</p>
    <img src={target.dataURL} alt="Photo mosaic source" style={{ display: 'block', maxWidth: '100%', height: 'auto', margin: '12px auto' }} />
    <label>Triangle detail<select aria-label="Triangle detail" value={detail} disabled={busy} onChange={event => { request.current++; setDetail(event.target.value); setProposal(null); setMessage(''); }}>
      <option value="3">Large triangles</option><option value="6">Balanced</option><option value="12">Small triangles</option>
    </select></label>
    <button disabled={busy} onClick={preview}>{busy ? 'Preparing mosaic…' : 'Preview photo mosaic'}</button>
    {proposal && <>
      <svg role="img" aria-label="Editable photo mosaic preview" viewBox={`0 0 ${proposal.width} ${proposal.height}`} style={{ display: 'block', width: '100%', height: 'auto', margin: '12px 0', background: '#eee', border: '1px solid #ccc' }}>
        {proposal.plan.map((shape, index) => <polygon key={index} points={shape.points.slice(0, 3).map(([x, y]) => `${x + shape.x},${y + shape.y}`).join(' ')} fill={shape.backgroundColor} opacity={shape.opacity / 100} />)}
      </svg>
      <p>Each triangle averages source RGB colors by opacity. Transparency is approximate per triangle; fine edges and details change. Up to 288 facets, with fewer on small or narrow photos. This is a stylized illustration, not an exact photo copy. Small seams can appear along triangle edges.</p>
      <button disabled={busy} onClick={() => { try { if (!isCurrent()) throw Error('The source or drawing changed. Open Photo mosaic again.'); onApply(proposal); } catch (error) { setMessage(error.message); } }}>Insert editable mosaic</button>
    </>}
    <button onClick={onCancel}>Keep original only</button>
    <p aria-live="polite">{message}</p>
    <p>Nothing changes until Insert. Triangles arrive as a group beside the source. Ungroup to change their colors or points; Undo removes the insertion. Processing is local, and no photo is uploaded.</p>
  </>;
}
